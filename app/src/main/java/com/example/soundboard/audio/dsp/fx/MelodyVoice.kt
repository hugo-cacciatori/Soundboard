package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.dsp.melody.Melody
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The button's own sound singing [melody]. Monophonic: every note plays the sound from its start,
 * moved to the note's pitch, and lasts exactly the note's length at the melody's tempo: the sound
 * is cut when the note is shorter, and time-stretched (same pitch) when the note is longer. The
 * last note is the exception: it plays the whole sound at its original duration, whatever its
 * length. Moving the pitch never changes the speed: only the note's length cuts or stretches.
 *
 * The "sound" is the voiced part of the take: the silence a take usually has before and after the
 * voice is trimmed, otherwise short notes would play only silence. Its pitch is moved with
 * [PitchFlattener], so it is still the user's voice, now on the note. Unlike Robot, the pitch is
 * not held dead constant (that is what sounds robotic): the voice's fast natural movement (jitter,
 * vibrato) is kept around the note, and only its slow drift and speech glides are removed, so the
 * note is in tune on average. A longer note repeats pitch periods, which keeps the voice natural.
 * A sound with no detectable pitch is resampled instead, as if it were at C4, then time-stretched
 * back to its own length.
 *
 * The result runs from the first note to the end of the last note's sound: leading and trailing
 * empty steps add no silence; rests between notes do.
 */
class MelodyVoice(private val melody: Melody) : Effect {

    /** One note in samples: from [start], for [length] samples, at MIDI pitch [midi]. */
    data class Segment(val start: Int, val length: Int, val midi: Int)

    override fun process(input: FloatArray, sampleRate: Int): FloatArray {
        val segments = segments(melody, sampleRate)
        val sound = voicedPart(input, sampleRate)
        if (segments.isEmpty() || sound.isEmpty()) return input.copyOf()
        val track = PitchTracker.track(sound, sampleRate)
        val pitched = track.medianVoiced() > 0f

        val movement = if (pitched) naturalMovement(track, sampleRate) else FloatArray(0)

        val last = segments.last()
        val out = FloatArray(last.start + sound.size)
        // A melody often repeats notes: render each pitch (and each stretched length) once.
        val rendered = HashMap<Pair<Int, Int>, FloatArray>()
        for (segment in segments) {
            // The last note plays the whole sound; others no longer than the sound share one render, cut afterwards.
            val length = if (segment === last) sound.size else segment.length
            val stretchedLength = if (length > sound.size) length else 0
            val note = rendered.getOrPut(segment.midi to stretchedLength) {
                val stretch = if (stretchedLength > 0) stretchedLength.toDouble() / sound.size else 1.0
                if (pitched) {
                    PitchFlattener.shift(sound, track, sampleRate, stretch) { position ->
                        val frame = ((position + track.hopSize / 2) / track.hopSize).coerceIn(0, movement.lastIndex)
                        sampleRate / midiToHz(segment.midi + movement[frame].toDouble())
                    }
                } else {
                    // Resampling moves the pitch but also the speed: stretch it back, plus the note's own stretch.
                    val shifted = resample(sound, midiToHz(segment.midi) / midiToHz(UNPITCHED_REFERENCE_MIDI))
                    // Short grains: long ones would smear each attack over the grain, ahead of its time.
                    TimeStretch(sound.size * stretch / shifted.size, UNPITCHED_GRAIN, UNPITCHED_GRAIN / 4)
                        .process(shifted, sampleRate)
                }
            }
            fit(note, length, sampleRate).copyInto(out, segment.start)
        }
        return out
    }

    companion object {
        /** Short fades where a note starts, is cut or ends, so nothing clicks: 5 ms. */
        private const val FADE_SECONDS = 0.005

        /** The voice spans where the level comes within this much of the peak. */
        private const val VOICE_BELOW_PEAK_DB = 30.0

        /** Kept around the voice so its attack and release aren't clipped: 10 ms. */
        private const val VOICE_MARGIN_SECONDS = 0.010

        /** Stretched renders can come out a sample or two short of the note. */
        private const val ROUNDING_SLACK = 4

        /** A sound without a detectable pitch is treated as if it were at C4. */
        private const val UNPITCHED_REFERENCE_MIDI = 60

        /** Time-stretch grain for sounds without a pitch, in samples (about 5 ms at 48 kHz). */
        private const val UNPITCHED_GRAIN = 256

        /** Pitch movement slower than this (a glide, a drift) is removed; faster movement is kept. */
        private const val SLOW_MOVEMENT_SECONDS = 0.2

        /** The kept movement never takes a note further than this from its pitch, in semitones. */
        private const val MAX_MOVEMENT_SEMITONES = 1.0f

        fun midiToHz(midi: Double): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

        fun midiToHz(midi: Int): Double = midiToHz(midi.toDouble())

        /**
         * Per pitch-track frame, the voice's fast pitch movement in semitones: its pitch minus the
         * average pitch over the surrounding [SLOW_MOVEMENT_SECONDS], capped at
         * [MAX_MOVEMENT_SEMITONES]. Unvoiced frames are 0.
         */
        fun naturalMovement(track: PitchTrack, sampleRate: Int): FloatArray {
            val semitones = FloatArray(track.hz.size) { j ->
                val hz = track.hz[j]
                if (hz > 0f) (12 * ln(hz / 440.0) / ln(2.0)).toFloat() else Float.NaN
            }
            // Frames on each side of the averaging window.
            val radius = (SLOW_MOVEMENT_SECONDS / 2 * sampleRate / track.hopSize).roundToInt().coerceAtLeast(1)
            return FloatArray(semitones.size) { j ->
                if (semitones[j].isNaN()) return@FloatArray 0f
                var sum = 0.0
                var count = 0
                for (k in j - radius..j + radius) {
                    if (k in semitones.indices && !semitones[k].isNaN()) {
                        sum += semitones[k]
                        count++
                    }
                }
                (semitones[j] - (sum / count).toFloat()).coerceIn(-MAX_MOVEMENT_SEMITONES, MAX_MOVEMENT_SEMITONES)
            }
        }

        /** Where each note plays, relative to the first note, at the melody's tempo. */
        fun segments(melody: Melody, sampleRate: Int): List<Segment> {
            if (melody.isEmpty) return emptyList()
            val stepSamples = melody.stepSeconds * sampleRate
            val first = melody.notes.first().step
            return melody.notes.map { note ->
                val start = ((note.step - first) * stepSamples).roundToInt()
                val end = ((note.end - first) * stepSamples).roundToInt()
                Segment(start, end - start, note.midi)
            }
        }

        /** Sample index where the voice starts (see [VOICE_BELOW_PEAK_DB]), minus a short margin. */
        fun voiceOnset(sample: FloatArray, sampleRate: Int): Int {
            val threshold = voiceThreshold(sample) ?: return 0
            val first = sample.indexOfFirst { abs(it) >= threshold }
            return (first - (VOICE_MARGIN_SECONDS * sampleRate).roundToInt()).coerceAtLeast(0)
        }

        /** Sample index just after the voice ends, plus a short margin. */
        fun voiceEnd(sample: FloatArray, sampleRate: Int): Int {
            val threshold = voiceThreshold(sample) ?: return sample.size
            val last = sample.indexOfLast { abs(it) >= threshold }
            return (last + 1 + (VOICE_MARGIN_SECONDS * sampleRate).roundToInt()).coerceAtMost(sample.size)
        }

        private fun voiceThreshold(sample: FloatArray): Float? {
            var peak = 0f
            for (v in sample) peak = max(peak, abs(v))
            return if (peak == 0f) null else peak * 10.0.pow(-VOICE_BELOW_PEAK_DB / 20).toFloat()
        }

        /** The take without the silence before and after the voice, with short fades at the new edges. */
        fun voicedPart(sample: FloatArray, sampleRate: Int): FloatArray {
            val from = voiceOnset(sample, sampleRate)
            val to = voiceEnd(sample, sampleRate)
            if (to <= from) return FloatArray(0)
            val voice = sample.copyOfRange(from, to)
            fadeEdges(voice, sampleRate, fadeIn = from > 0, fadeOut = to < sample.size)
            return voice
        }

        /**
         * [sound] lasting exactly [length] samples: cut when longer, time-stretched (same pitch)
         * when shorter (a few samples short, from rounding, are padded instead). Always ends with
         * a short fade.
         */
        fun fit(sound: FloatArray, length: Int, sampleRate: Int): FloatArray {
            val fitted = if (sound.size >= length - ROUNDING_SLACK) {
                sound.copyOf(length)
            } else {
                TimeStretch(length.toDouble() / sound.size).process(sound, sampleRate).copyOf(length)
            }
            fadeEdges(fitted, sampleRate, fadeIn = false, fadeOut = true)
            return fitted
        }

        private fun fadeEdges(x: FloatArray, sampleRate: Int, fadeIn: Boolean, fadeOut: Boolean) {
            val fade = min((FADE_SECONDS * sampleRate).roundToInt(), x.size / 2)
            for (k in 0 until fade) {
                val g = k.toFloat() / fade
                if (fadeIn) x[k] *= g
                if (fadeOut) x[x.size - 1 - k] *= g
            }
        }

        /**
         * Pitch shift by playing [x] [ratio] times faster (linear interpolation), which also changes
         * its length and speed; the caller time-stretches it back. Only for sounds without a detectable pitch.
         */
        private fun resample(x: FloatArray, ratio: Double): FloatArray {
            if (x.size < 2) return x.copyOf()
            val length = ((x.size - 1) / ratio).toInt() + 1
            return FloatArray(length) { i ->
                val position = i * ratio
                val j = position.toInt().coerceAtMost(x.size - 2)
                val frac = (position - j).toFloat()
                x[j] + (x[j + 1] - x[j]) * frac
            }
        }
    }
}
