package com.example.soundboard.audio.dsp

import kotlin.math.abs

/** Waveform overview for display. Pure Kotlin (no android.*). */
object Waveform {
    /**
     * Splits [samples] into [bins] equal slices and returns each slice's peak magnitude, scaled so
     * the loudest slice is 1. All zeros for silence. Slices are empty (0) when there are fewer
     * samples than bins.
     */
    fun peaks(samples: ShortArray, bins: Int): FloatArray {
        require(bins > 0) { "bins must be positive" }
        val out = FloatArray(bins)
        val n = samples.size.toLong()
        var max = 0
        for (b in 0 until bins) {
            val from = (b * n / bins).toInt()
            val to = ((b + 1) * n / bins).toInt()
            var peak = 0
            // Int math: abs(-32768) does not fit in a Short.
            for (i in from until to) {
                val a = abs(samples[i].toInt())
                if (a > peak) peak = a
            }
            out[b] = peak.toFloat()
            if (peak > max) max = peak
        }
        if (max > 0) {
            for (b in 0 until bins) out[b] /= max
        }
        return out
    }
}
