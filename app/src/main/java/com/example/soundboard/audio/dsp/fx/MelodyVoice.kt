package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.dsp.melody.Melody
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The button's own sound singing [melody]. Monophonic: every note plays the sound from its start,
 * moved to the note's pitch, and lasts exactly the note's length at the melody's tempo: the sound
 * is cut when the note is shorter, and time-stretched (same pitch) when the note is longer.
 *
 * The "sound" is the voiced part of the take: the silence a take usually has before and after the
 * voice is trimmed, otherwise short notes would play only silence. Its pitch is moved like Robot
 * does ([PitchFlattener]), so it is still the user's voice, now on the note. A sound with no
 * detectable pitch is resampled instead, as if it were at C4.
 *
 * The result runs from the first note to the end of the last one: leading and trailing empty
 * steps add no silence; rests between notes do.
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

        val last = segments.last()
        val out = FloatArray(last.start + last.length)
        // A melody often repeats pitches: shift the sound once per pitch.
        val atPitch = HashMap<Int, FloatArray>()
        for (segment in segments) {
            val shifted = atPitch.getOrPut(segment.midi) {
                if (pitched) {
                    PitchFlattener.flatten(sound, track, sampleRate / midiToHz(segment.midi), sampleRate)
                } else {
                    resample(sound, midiToHz(segment.midi) / midiToHz(UNPITCHED_REFERENCE_MIDI))
                }
            }
            fit(shifted, segment.length, sampleRate).copyInto(out, segment.start)
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

        /** A sound without a detectable pitch is treated as if it were at C4. */
        private const val UNPITCHED_REFERENCE_MIDI = 60

        fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

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
         * when shorter. Always ends with a short fade.
         */
        fun fit(sound: FloatArray, length: Int, sampleRate: Int): FloatArray {
            val fitted = if (sound.size >= length) {
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
         * its length; [fit] sets the length afterwards. Only for sounds without a detectable pitch.
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
