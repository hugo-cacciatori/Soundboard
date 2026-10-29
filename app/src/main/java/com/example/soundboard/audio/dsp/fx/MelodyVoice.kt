package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.dsp.melody.Melody
import java.util.Random
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The sample sung as [melody]. Monophonic: every note plays the sample from its start until the
 * sample ends or the next note starts, which cuts it (with a short fade) and restarts the sample.
 * That retriggered sample is the modulator of a [ChannelVocoder] whose carrier is a band-limited
 * sawtooth at each note's pitch, with a little noise so consonants stay audible.
 *
 * The result runs from the first note to the end of the last note's sound: leading and trailing
 * empty steps add no silence.
 */
class MelodyVoice(
    private val melody: Melody,
    private val vocoder: ChannelVocoder = ChannelVocoder(),
    private val noiseLevel: Float = DEFAULT_NOISE_LEVEL,
) : Effect {

    /** One note in samples: the sample plays from [start] for [length] samples at MIDI pitch [midi]. */
    data class Segment(val start: Int, val length: Int, val midi: Int)

    override fun process(input: FloatArray, sampleRate: Int): FloatArray {
        val segments = segments(melody, input.size, sampleRate)
        if (segments.isEmpty()) return input.copyOf()
        val modulator = modulator(input, segments, sampleRate)
        return vocoder.process(modulator, carrier(segments, modulator.size, sampleRate), sampleRate)
    }

    /** Sawtooth (PolyBLEP, so no aliasing buzz) at each note's pitch, plus a little noise. */
    private fun carrier(segments: List<Segment>, length: Int, sampleRate: Int): FloatArray {
        val out = FloatArray(length)
        val random = Random(NOISE_SEED)
        var phase = 0.0
        var s = 0
        for (i in 0 until length) {
            while (s + 1 < segments.size && i >= segments[s + 1].start) s++
            // The phase runs on across notes: only the frequency changes, so the carrier never clicks.
            val dt = midiToHz(segments[s].midi) / sampleRate
            val saw = 2 * phase - 1 - polyBlep(phase, dt)
            out[i] = saw.toFloat() + noiseLevel * (2 * random.nextFloat() - 1)
            phase += dt
            if (phase >= 1.0) phase -= 1.0
        }
        return out
    }

    private fun polyBlep(t: Double, dt: Double): Double = when {
        t < dt -> {
            val x = t / dt
            x + x - x * x - 1
        }
        t > 1 - dt -> {
            val x = (t - 1) / dt
            x * x + x + x + 1
        }
        else -> 0.0
    }

    companion object {
        /** Carrier noise relative to the sawtooth's full scale. */
        const val DEFAULT_NOISE_LEVEL = 0.05f

        /** Fade applied where a note is cut by the next one: 5 ms. */
        private const val CUT_FADE_SECONDS = 0.005

        /** Fixed, so the same button always renders the same sound. */
        private const val NOISE_SEED = 0x5EEDL

        fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

        /** Where each note plays, relative to the first note, for a sample of [sampleLength] samples. */
        fun segments(melody: Melody, sampleLength: Int, sampleRate: Int): List<Segment> {
            if (melody.isEmpty || sampleLength == 0) return emptyList()
            val stepSamples = melody.stepSeconds * sampleRate
            val first = melody.notes.first().step
            val starts = melody.notes.map { ((it.step - first) * stepSamples).roundToInt() }
            return melody.notes.mapIndexed { i, note ->
                val next = starts.getOrNull(i + 1)
                val length = if (next == null) sampleLength else min(sampleLength, next - starts[i])
                Segment(starts[i], length, note.midi)
            }
        }

        /** The sample retriggered at each segment, faded out where the next note cuts it. */
        fun modulator(sample: FloatArray, segments: List<Segment>, sampleRate: Int): FloatArray {
            val last = segments.last()
            val out = FloatArray(last.start + last.length)
            val fade = (CUT_FADE_SECONDS * sampleRate).roundToInt()
            for (segment in segments) {
                sample.copyInto(out, segment.start, 0, segment.length)
                if (segment.length < sample.size) {
                    val fadeLength = min(fade, segment.length)
                    val end = segment.start + segment.length
                    for (k in 0 until fadeLength) out[end - fadeLength + k] *= 1f - (k + 1f) / fadeLength
                }
            }
            return out
        }
    }
}
