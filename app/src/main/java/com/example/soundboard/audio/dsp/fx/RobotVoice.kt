package com.example.soundboard.audio.dsp.fx

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Robot voice: the whole clip on one note. The voice's median pitch is snapped to the nearest
 * semitone and the clip is flattened onto it ([PitchFlattener]), so the duration and the formants
 * stay and the words remain intelligible. A feedback comb tuned to the note then adds a metallic
 * buzz.
 *
 * A clip with no voiced frames is returned dry.
 *
 * @param combFeedback 0 disables the comb; towards 1 the buzz gets stronger and rings longer.
 */
class RobotVoice(private val combFeedback: Double) : Effect {
    init {
        require(combFeedback in 0.0..<1.0) { "combFeedback must be in [0, 1)" }
    }

    override fun process(input: FloatArray, sampleRate: Int): FloatArray {
        val track = PitchTracker.track(input, sampleRate)
        val median = track.medianVoiced()
        if (median <= 0f) return input.copyOf()
        val period = sampleRate / nearestNoteHz(median.toDouble())
        return comb(PitchFlattener.flatten(input, track, period, sampleRate), period.roundToInt())
    }

    /** Feedback comb `y[n] = x[n] + g * y[n - delay]`: resonances on every harmonic of the note. */
    private fun comb(x: FloatArray, delay: Int): FloatArray {
        if (combFeedback == 0.0 || delay <= 0) return x
        val g = combFeedback.toFloat()
        val y = FloatArray(x.size)
        for (i in x.indices) y[i] = x[i] + if (i >= delay) g * y[i - delay] else 0f
        return y
    }

    companion object {
        /** The equal-temperament note (A4 = 440 Hz) closest to [hz]. */
        fun nearestNoteHz(hz: Double): Double {
            val semitones = (12 * ln(hz / 440.0) / ln(2.0)).roundToInt()
            return 440.0 * 2.0.pow(semitones / 12.0)
        }
    }
}
