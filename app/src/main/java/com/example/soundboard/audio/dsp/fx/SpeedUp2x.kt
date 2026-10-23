package com.example.soundboard.audio.dsp.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Plays the sound twice as fast, like a tape at double speed: everything (pitch and formants)
 * goes up one octave and the clip becomes half as long.
 *
 * Anti-aliasing low-pass (windowed sinc, below the new Nyquist frequency), then every other sample
 * is kept.
 */
class SpeedUp2x : Effect {
    private val kernel: FloatArray = buildKernel()

    override fun process(input: FloatArray, sampleRate: Int): FloatArray {
        val n = input.size
        val half = TAPS / 2
        return FloatArray((n + 1) / 2) { m ->
            val center = 2 * m
            var acc = 0f
            for (k in 0 until TAPS) {
                val i = center + half - k
                if (i in 0 until n) acc += kernel[k] * input[i]
            }
            acc
        }
    }

    private companion object {
        const val TAPS = 127

        /** Cutoff as a fraction of the input rate: 0.22 is about 10.5 kHz at 48 kHz (new Nyquist: 12 kHz). */
        const val CUTOFF = 0.22

        fun buildKernel(): FloatArray {
            val m = TAPS - 1
            val h = DoubleArray(TAPS) { k ->
                val t = k - m / 2.0
                val sinc = if (t == 0.0) 2 * CUTOFF else sin(2 * PI * CUTOFF * t) / (PI * t)
                val blackman = 0.42 - 0.5 * cos(2 * PI * k / m) + 0.08 * cos(4 * PI * k / m)
                sinc * blackman
            }
            val sum = h.sum()
            return FloatArray(TAPS) { (h[it] / sum).toFloat() }
        }
    }
}
