package com.example.soundboard.audio.dsp.fx

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Robot voice: the whole clip on one note. The voice's median pitch is snapped to the nearest
 * semitone and the clip is flattened onto it ([PitchFlattener]), so the duration and the formants
 * stay and the words remain intelligible. Then the robotic grain, in three stages:
 * 1. a feedback comb tuned to the note (metallic resonance),
 * 2. a ring modulator at the note's frequency, mixed in (an in-tune metallic buzz),
 * 3. a sample-and-hold that keeps every [holdSamples]th sample (digital grit).
 *
 * A clip with no voiced frames is returned dry.
 *
 * @param combFeedback 0 disables the comb; towards 1 the resonance gets stronger and rings longer.
 * @param ringMix share of the ring-modulated signal, 0 (off) to 1.
 * @param holdSamples 1 disables the sample-and-hold; higher is grittier.
 */
class RobotVoice(
    private val combFeedback: Double,
    private val ringMix: Double = 0.0,
    private val holdSamples: Int = 1,
) : Effect {
    init {
        require(combFeedback in 0.0..<1.0) { "combFeedback must be in [0, 1)" }
        require(ringMix in 0.0..1.0) { "ringMix must be in [0, 1]" }
        require(holdSamples >= 1) { "holdSamples must be at least 1" }
    }

    override fun process(input: FloatArray, sampleRate: Int): FloatArray {
        val track = PitchTracker.track(input, sampleRate)
        val median = track.medianVoiced()
        if (median <= 0f) return input.copyOf()
        val noteHz = nearestNoteHz(median.toDouble())
        val period = sampleRate / noteHz
        val flat = PitchFlattener.flatten(input, track, period, sampleRate)
        return hold(ring(comb(flat, period.roundToInt()), noteHz, sampleRate))
    }

    /** Feedback comb `y[n] = x[n] + g * y[n - delay]`: resonances on every harmonic of the note. */
    private fun comb(x: FloatArray, delay: Int): FloatArray {
        if (combFeedback == 0.0 || delay <= 0) return x
        val g = combFeedback.toFloat()
        val y = FloatArray(x.size)
        for (i in x.indices) y[i] = x[i] + if (i >= delay) g * y[i - delay] else 0f
        return y
    }

    /** Ring modulation by a sine at [hz]: on a voice at that pitch, every product lands on a harmonic. */
    private fun ring(x: FloatArray, hz: Double, sampleRate: Int): FloatArray {
        if (ringMix == 0.0) return x
        val dry = (1 - ringMix).toFloat()
        val wet = ringMix.toFloat()
        val w = 2 * PI * hz / sampleRate
        return FloatArray(x.size) { i -> x[i] * (dry + wet * sin(w * i).toFloat()) }
    }

    /** Sample-and-hold: each sample repeats for [holdSamples] samples, aliasing into grit. */
    private fun hold(x: FloatArray): FloatArray {
        if (holdSamples == 1) return x
        return FloatArray(x.size) { i -> x[i - i % holdSamples] }
    }

    companion object {
        /** The equal-temperament note (A4 = 440 Hz) closest to [hz]. */
        fun nearestNoteHz(hz: Double): Double {
            val semitones = (12 * ln(hz / 440.0) / ln(2.0)).roundToInt()
            return 440.0 * 2.0.pow(semitones / 12.0)
        }
    }
}
