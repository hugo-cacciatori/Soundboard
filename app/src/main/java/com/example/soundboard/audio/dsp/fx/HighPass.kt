package com.example.soundboard.audio.dsp.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 2nd-order Butterworth high-pass (RBJ biquad) at [cutoffHz]. */
class HighPass(private val cutoffHz: Double) : Effect {
    override fun process(input: FloatArray, sampleRate: Int): FloatArray {
        val w0 = 2 * PI * cutoffHz / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2 * BUTTERWORTH_Q)
        val a0 = 1 + alpha
        val b0 = (1 + cosW) / 2 / a0
        val b1 = -(1 + cosW) / a0
        val b2 = b0
        val a1 = -2 * cosW / a0
        val a2 = (1 - alpha) / a0

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        return FloatArray(input.size) { i ->
            val x0 = input[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            y0.toFloat()
        }
    }

    private companion object {
        const val BUTTERWORTH_Q = 0.7071067811865476
    }
}
