package com.example.soundboard.audio.dsp.fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Moves every voiced period of a sound to one constant pitch with TD-PSOLA: one grain cut around
 * each pitch mark, laid out again at the new spacing. The duration and the formants stay, so the
 * words remain intelligible; unvoiced parts (consonants, breath) pass through. Used by Robot (the
 * nearest note) and Melody (each note's pitch).
 */
object PitchFlattener {
    /** Unvoiced grain spacing: 200 per second is 5 ms. */
    private const val UNVOICED_STEPS_PER_SECOND = 200

    private class Mark(val position: Int, val period: Int, val voiced: Boolean)

    /** [x] with its voiced parts at a constant [targetPeriod] (in samples); [track] is [x]'s pitch. */
    fun flatten(x: FloatArray, track: PitchTrack, targetPeriod: Double, sampleRate: Int): FloatArray {
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
        var inVoice = false
        while (ts < n) {
            // Synthesis time follows analysis time 1:1, so take the analysis mark closest to it.
            while (k + 1 < marks.size && abs(marks[k + 1].position - ts) <= abs(marks[k].position - ts)) k++
            val mark = marks[k]
            // The first grain of a voiced run goes exactly where it was cut, so the voice keeps its
            // timing (and, at its own pitch, comes out unchanged).
            if (mark.voiced && !inVoice) ts = mark.position.toDouble()
            inVoice = mark.voiced
            val s = ts.roundToInt()
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
}
