package com.example.soundboard.audio.dsp.fx

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Moves the voiced periods of a sound to a new pitch with TD-PSOLA: one grain cut around each
 * pitch mark, laid out again at the new spacing. The formants stay, so the words remain
 * intelligible; unvoiced parts (consonants, breath) pass through. It can also stretch the sound
 * (grains repeated), which keeps a voice more natural than a generic time stretch. Used by Robot
 * (one constant note) and Melody (each note, with the voice's natural movement around it).
 */
object PitchFlattener {
    /** Unvoiced grain spacing: 200 per second is 5 ms. */
    private const val UNVOICED_STEPS_PER_SECOND = 200

    /** Room for the longest grain: periods down to 50 Hz. */
    private const val LOWEST_HZ = 50

    /** Fixed, so the same sound always renders the same way. */
    private const val JITTER_SEED = 0x5EEDL

    private class Mark(val position: Int, val period: Int, val voiced: Boolean)

    /** [x] with its voiced parts at one constant [targetPeriod] (in samples); [track] is [x]'s pitch. */
    fun flatten(x: FloatArray, track: PitchTrack, targetPeriod: Double, sampleRate: Int): FloatArray =
        shift(x, track, sampleRate) { targetPeriod }

    /**
     * [x] with each voiced grain at [targetPeriod] (in samples) of its position in [x], and
     * [stretch] times as long. [track] is [x]'s pitch.
     */
    fun shift(
        x: FloatArray,
        track: PitchTrack,
        sampleRate: Int,
        stretch: Double = 1.0,
        targetPeriod: (sourcePosition: Int) -> Double,
    ): FloatArray {
        require(stretch > 0.0) { "stretch must be positive" }
        val n = x.size
        val outLength = (n * stretch).roundToInt()
        val unvoicedStep = sampleRate / UNVOICED_STEPS_PER_SECOND
        val marks = pitchMarks(x, track, sampleRate, unvoicedStep)
        if (marks.isEmpty() || outLength == 0) return x.copyOf(outLength)

        val pad = max(marks.maxOf { it.period }, sampleRate / LOWEST_HZ) + 2
        val padded = FloatArray(n + 2 * pad)
        x.copyInto(padded, pad)
        val out = FloatArray(outLength + 2 * pad)
        // A stretched noise would repeat the same grains (a phasey comb); small random offsets avoid it.
        val jitter = if (stretch > 1.05) Random(JITTER_SEED) else null

        var ts = 0.0
        var k = 0
        var inVoice = false
        while (ts < outLength) {
            // Analysis time runs [stretch] times slower than synthesis time: take the mark closest to it.
            var ta = ts / stretch
            while (k + 1 < marks.size && abs(marks[k + 1].position - ta) <= abs(marks[k].position - ta)) k++
            val mark = marks[k]
            // The first grain of a voiced run goes exactly where it was cut, so the voice keeps its
            // timing (and, at its own pitch, comes out unchanged).
            if (mark.voiced && !inVoice) {
                ts = mark.position * stretch
                ta = mark.position.toDouble()
            }
            inVoice = mark.voiced
            val s = ts.roundToInt()
            if (s >= outLength) break
            val source: Int
            val half: Int
            val step: Double
            if (mark.voiced) {
                source = mark.position
                step = targetPeriod(mark.position)
                // One analysis period each side; wider only when the gap to the next grain would leave a hole.
                half = max(mark.period, ceil(step / 2).toInt())
            } else {
                // Copy the input in place: Hann windows at half overlap sum to exactly 1.
                val offset = jitter?.let { it.nextInt(unvoicedStep + 1) - unvoicedStep / 2 } ?: 0
                source = (ta.roundToInt() + offset).coerceIn(0, n - 1)
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
        return out.copyOfRange(pad, pad + outLength)
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
