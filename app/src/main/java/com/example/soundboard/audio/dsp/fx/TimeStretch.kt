package com.example.soundboard.audio.dsp.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Changes the duration by [factor] without changing the pitch (WSOLA: overlap-add of Hann-windowed
 * grains, each grain shifted by up to [tolerance] samples so its waveform lines up with the
 * previous one, which avoids the phasey/robotic sound of plain overlap-add).
 *
 * @param windowSize grain length in samples (1024 is about 21 ms at 48 kHz).
 * @param tolerance search range in samples; should cover one period of the lowest pitch.
 */
class TimeStretch(
    private val factor: Double,
    private val windowSize: Int = 1024,
    private val tolerance: Int = 256,
) : Effect {
    init {
        require(factor > 0.0) { "factor must be positive" }
        require(windowSize >= 4 && windowSize % 2 == 0) { "windowSize must be even" }
    }

    private val window = FloatArray(windowSize) { (0.5 - 0.5 * cos(2 * PI * it / windowSize)).toFloat() }

    override fun process(input: FloatArray, sampleRate: Int): FloatArray {
        val n = input.size
        val outLen = (n * factor).roundToInt()
        if (n == 0 || outLen == 0) return FloatArray(outLen)

        val w = windowSize
        val synthesisHop = w / 2 // 50 % overlap: periodic Hann windows sum to 1
        val analysisHop = synthesisHop / factor
        // Zero padding on both sides so every read below stays in bounds.
        val pad = w + tolerance + 2
        val x = FloatArray(n + 2 * pad)
        input.copyInto(x, pad)

        val out = FloatArray(outLen + w)
        val weight = FloatArray(outLen + w)
        var previous = pad
        var k = 0
        while (k * synthesisHop < outLen) {
            val outPos = k * synthesisHop
            val nominal = pad + (k * analysisHop).roundToInt()
            // The grain should continue the waveform where the previous grain naturally goes on.
            val pos = if (k == 0) nominal else bestMatch(x, previous + synthesisHop, nominal, synthesisHop)
            for (i in 0 until w) {
                out[outPos + i] += window[i] * x[pos + i]
                weight[outPos + i] += window[i]
            }
            previous = pos
            k++
        }
        // Only the very first samples have a window sum below 1; there out = window * x exactly.
        return FloatArray(outLen) { i ->
            val wt = weight[i]
            if (wt > 1e-3f) out[i] / wt else out[i]
        }
    }

    /** Start index near [nominal] whose first [len] samples best match the [template] segment. */
    private fun bestMatch(x: FloatArray, template: Int, nominal: Int, len: Int): Int {
        // Coarse search (every 2nd lag and sample), then refine around the winner.
        var best = nominal
        var bestScore = correlation(x, template, nominal, len, 2)
        var lag = -tolerance
        while (lag <= tolerance) {
            val score = correlation(x, template, nominal + lag, len, 2)
            if (score > bestScore) {
                bestScore = score
                best = nominal + lag
            }
            lag += 2
        }
        val coarse = best
        bestScore = correlation(x, template, coarse, len, 1)
        for (candidate in intArrayOf(coarse - 1, coarse + 1)) {
            val score = correlation(x, template, candidate, len, 1)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun correlation(x: FloatArray, a: Int, b: Int, len: Int, step: Int): Float {
        var acc = 0f
        var i = 0
        while (i < len) {
            acc += x[a + i] * x[b + i]
            i += step
        }
        return acc
    }
}
