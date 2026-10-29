package com.example.soundboard.audio.dsp.fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Robot voice: the whole clip on one note. The voice's median pitch is snapped to the nearest
 * semitone, and every voiced period is moved to that pitch with TD-PSOLA (one grain cut around each
 * pitch mark, laid out again at the new spacing), so the duration and the formants stay and the
 * words remain intelligible. Unvoiced parts (consonants, breath) pass through. A feedback comb
 * tuned to the note then adds a metallic buzz.
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
        return comb(flatten(input, track, period, sampleRate), period.roundToInt())
    }

    private class Mark(val position: Int, val period: Int, val voiced: Boolean)

    /** TD-PSOLA to a constant [targetPeriod] (in samples), keeping the duration. */
    private fun flatten(x: FloatArray, track: PitchTrack, targetPeriod: Double, sampleRate: Int): FloatArray {
        val n = x.size
        val unvoicedStep = sampleRate / UNVOICED_STEPS_PER_SECOND
        val marks = pitchMarks(x, track, sampleRate, unvoicedStep)
        if (marks.isEmpty()) return x.copyOf()

        val maxHalf = max(marks.maxOf { it.period }, ceil(targetPeriod).toInt())
        val pad = maxHalf + 2
        val padded = FloatArray(n + 2 * pad)
        x.copyInto(padded, pad)
        val out = FloatArray(n + 2 * pad)

        var ts = 0.0
        var k = 0
        while (ts < n) {
            val s = ts.roundToInt()
            // Synthesis time follows analysis time 1:1, so take the analysis mark closest to it.
            while (k + 1 < marks.size && abs(marks[k + 1].position - s) <= abs(marks[k].position - s)) k++
            val mark = marks[k]
            val source: Int
            val half: Int
            val step: Double
            if (mark.voiced) {
                source = mark.position
                // One analysis period each side; wider only when the gap to the next grain would leave a hole.
                half = max(mark.period, ceil(targetPeriod / 2).toInt())
                step = targetPeriod
            } else {
                // Copy the input in place: Hann windows at half overlap sum to exactly 1.
                source = s
                half = unvoicedStep
                step = unvoicedStep.toDouble()
            }
            // Overlapping grains add up; scale them back to the input level.
            val gain = min(1.0, step / half)
            for (i in -half until half) {
                val w = 0.5 + 0.5 * cos(PI * i / half)
                out[pad + s + i] += (gain * w * padded[pad + source + i]).toFloat()
            }
            ts += step
        }
        return out.copyOfRange(pad, pad + n)
    }

    /**
     * Voiced marks sit on the waveform peak of each period, one period apart; unvoiced marks are
     * evenly spaced every [unvoicedStep] samples.
     */
    private fun pitchMarks(x: FloatArray, track: PitchTrack, sampleRate: Int, unvoicedStep: Int): List<Mark> {
        val marks = ArrayList<Mark>()
        var t = 0
        var lastVoiced = -1
        while (t < x.size) {
            val hz = track.at(t)
            if (hz <= 0f) {
                marks += Mark(t, unvoicedStep, voiced = false)
                lastVoiced = -1
                t += unvoicedStep
                continue
            }
            val period = (sampleRate / hz).roundToInt()
            val from = if (lastVoiced >= 0) lastVoiced + period - period / 4 else t
            val to = min(if (lastVoiced >= 0) lastVoiced + period + period / 4 else t + period - 1, x.lastIndex)
            if (from > to) break
            var peak = from
            for (i in from + 1..to) if (x[i] > x[peak]) peak = i
            marks += Mark(peak, period, voiced = true)
            lastVoiced = peak
            t = peak + period
        }
        return marks
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
        /** Unvoiced grain spacing: 200 per second is 5 ms. */
        private const val UNVOICED_STEPS_PER_SECOND = 200

        /** The equal-temperament note (A4 = 440 Hz) closest to [hz]. */
        fun nearestNoteHz(hz: Double): Double {
            val semitones = (12 * ln(hz / 440.0) / ln(2.0)).roundToInt()
            return 440.0 * 2.0.pow(semitones / 12.0)
        }
    }
}
