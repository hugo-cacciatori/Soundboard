package com.example.soundboard.audio.dsp.fx

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Voice pitch per hop of [hopSize] input samples. Frame `j` describes the input around sample
 * `j * hopSize`; its value is in Hz, or 0 where the frame is unvoiced (no clear period, or too quiet).
 */
class PitchTrack(val hz: FloatArray, val hopSize: Int) {
    /** Pitch at input sample [t], 0 if unvoiced. */
    fun at(t: Int): Float = if (hz.isEmpty()) 0f else hz[((t + hopSize / 2) / hopSize).coerceIn(0, hz.lastIndex)]

    /** Median pitch of the voiced frames, or 0 if there are none. */
    fun medianVoiced(): Float {
        val voiced = hz.filter { it > 0f }.sorted()
        return if (voiced.isEmpty()) 0f else voiced[voiced.size / 2]
    }
}

/**
 * YIN pitch detection (de Cheveigné & Kawahara, 2002) on a 4x decimated copy of the clip: voice
 * pitch lives well below 3 kHz, and the decimation makes it about 16x cheaper.
 */
object PitchTracker {
    private const val DECIMATION = 4
    private const val ANTI_ALIAS_HZ = 2_400.0
    private const val MIN_HZ = 70.0
    private const val MAX_HZ = 800.0
    private const val WINDOW_SECONDS = 0.04
    private const val HOPS_PER_SECOND = 100

    /** YIN threshold on the cumulative mean normalized difference: lower means stricter. */
    private const val THRESHOLD = 0.15

    /** Frames this far below the loudest frame are unvoiced (room noise between words). */
    private const val SILENCE_DB = -35.0

    /** Shorter voiced runs are treated as detection noise. */
    private const val MIN_VOICED_FRAMES = 3

    /** Each voiced frame is replaced by the median of the voiced frames this close to it. */
    private const val MEDIAN_RADIUS = 2

    fun track(input: FloatArray, sampleRate: Int): PitchTrack {
        val hop = sampleRate / HOPS_PER_SECOND
        val frames = (input.size + hop - 1) / hop
        val rate = sampleRate.toDouble() / DECIMATION
        val window = (rate * WINDOW_SECONDS).roundToInt()
        val minTau = floor(rate / MAX_HZ).toInt().coerceAtLeast(2)
        val maxTau = ceil(rate / MIN_HZ).toInt()

        // Zero padding on both sides so every frame reads in bounds.
        val pad = window + maxTau
        val decimated = decimate(input, sampleRate)
        val x = FloatArray(decimated.size + 2 * pad)
        decimated.copyInto(x, pad)

        val diff = DoubleArray(maxTau + 1)
        val cmnd = DoubleArray(maxTau + 1)
        val raw = FloatArray(frames)
        val level = DoubleArray(frames)
        for (j in 0 until frames) {
            val start = pad + j * hop / DECIMATION - window / 2
            level[j] = rms(x, start, window)
            raw[j] = yin(x, start, window, minTau, maxTau, diff, cmnd, rate)
        }

        val floor = (level.maxOrNull() ?: 0.0) * 10.0.pow(SILENCE_DB / 20)
        for (j in 0 until frames) if (level[j] <= floor) raw[j] = 0f
        removeShortRuns(raw)
        return PitchTrack(medianSmooth(raw), hop)
    }

    /** Pitch of one frame in Hz, or 0 if no lag is periodic enough. */
    private fun yin(
        x: FloatArray,
        start: Int,
        window: Int,
        minTau: Int,
        maxTau: Int,
        diff: DoubleArray,
        cmnd: DoubleArray,
        rate: Double,
    ): Float {
        for (tau in 1..maxTau) {
            var sum = 0.0
            for (i in start until start + window) {
                val d = (x[i] - x[i + tau]).toDouble()
                sum += d * d
            }
            diff[tau] = sum
        }
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..maxTau) {
            running += diff[tau]
            cmnd[tau] = if (running > 0.0) diff[tau] * tau / running else 1.0
        }
        var tau = minTau
        while (tau < maxTau && cmnd[tau] >= THRESHOLD) tau++
        if (tau >= maxTau) return 0f
        while (tau + 1 < maxTau && cmnd[tau + 1] < cmnd[tau]) tau++

        // Parabolic interpolation around the minimum for sub-sample precision.
        val a = cmnd[tau - 1]
        val b = cmnd[tau]
        val c = cmnd[tau + 1]
        val denominator = a - 2 * b + c
        val refined = if (denominator > 1e-12) tau + (a - c) / (2 * denominator) else tau.toDouble()
        return (rate / refined).toFloat()
    }

    private fun rms(x: FloatArray, start: Int, length: Int): Double {
        var sum = 0.0
        for (i in start until start + length) sum += x[i].toDouble() * x[i]
        return sqrt(sum / length)
    }

    /** Low-pass (two 2nd-order sections), then every [DECIMATION]th sample. */
    private fun decimate(input: FloatArray, sampleRate: Int): FloatArray {
        val filtered = lowPass(lowPass(input, sampleRate), sampleRate)
        return FloatArray(input.size / DECIMATION) { filtered[it * DECIMATION] }
    }

    /** 2nd-order Butterworth low-pass (RBJ biquad) at [ANTI_ALIAS_HZ]. */
    private fun lowPass(input: FloatArray, sampleRate: Int): FloatArray {
        val w0 = 2 * PI * ANTI_ALIAS_HZ / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2 * 0.7071067811865476)
        val a0 = 1 + alpha
        val b0 = (1 - cosW) / 2 / a0
        val b1 = (1 - cosW) / a0
        val a1 = -2 * cosW / a0
        val a2 = (1 - alpha) / a0

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        return FloatArray(input.size) { i ->
            val x0 = input[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            y0.toFloat()
        }
    }

    private fun removeShortRuns(hz: FloatArray) {
        var j = 0
        while (j < hz.size) {
            if (hz[j] <= 0f) {
                j++
                continue
            }
            var end = j
            while (end < hz.size && hz[end] > 0f) end++
            if (end - j < MIN_VOICED_FRAMES) for (k in j until end) hz[k] = 0f
            j = end
        }
    }

    /** Removes single-frame jumps (octave errors) inside voiced runs. Unvoiced frames stay 0. */
    private fun medianSmooth(hz: FloatArray): FloatArray {
        val neighbours = FloatArray(2 * MEDIAN_RADIUS + 1)
        return FloatArray(hz.size) { j ->
            if (hz[j] <= 0f) return@FloatArray 0f
            var count = 0
            for (k in j - MEDIAN_RADIUS..j + MEDIAN_RADIUS) {
                if (k in hz.indices && hz[k] > 0f) neighbours[count++] = hz[k]
            }
            neighbours.sort(0, count)
            neighbours[count / 2]
        }
    }
}
