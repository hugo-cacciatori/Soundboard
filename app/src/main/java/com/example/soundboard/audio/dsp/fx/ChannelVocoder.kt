package com.example.soundboard.audio.dsp.fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Channel vocoder: imposes the spectral envelope of a modulator (the voice) on a carrier (a
 * synth). Both are split into [bands] log-spaced band-pass channels between [lowHz] and [highHz];
 * each carrier channel is flattened to unit level, then scaled by the modulator channel's envelope.
 * Flattening makes every channel equally available to the voice, so the words stay intelligible
 * whatever the carrier's own spectrum.
 */
class ChannelVocoder(
    private val bands: Int = 20,
    private val lowHz: Double = 100.0,
    private val highHz: Double = 8_000.0,
    private val envelopeSeconds: Double = 0.010,
) {
    init {
        require(bands >= 2 && lowHz > 0 && highHz > lowHz) { "invalid band layout" }
    }

    /** [modulator] and [carrier] have the same length; so has the result. */
    fun process(modulator: FloatArray, carrier: FloatArray, sampleRate: Int): FloatArray {
        require(modulator.size == carrier.size) { "modulator and carrier lengths differ" }
        val n = modulator.size
        val out = FloatArray(n)
        val modBand = FloatArray(n)
        val carBand = FloatArray(n)
        val ratio = (highHz / lowHz).pow(1.0 / (bands - 1))
        // Neighbouring bands meet around their -3 dB points.
        val q = sqrt(ratio) / (ratio - 1)
        val decay = exp(-1.0 / (envelopeSeconds * sampleRate)).toFloat()

        for (b in 0 until bands) {
            val centerHz = lowHz * ratio.pow(b)
            if (centerHz >= sampleRate * 0.45) break
            val filter = BandPass(centerHz, q, sampleRate)
            filter.twice(modulator, modBand)
            filter.twice(carrier, carBand)
            var modEnv = 0f
            var carEnv = 0f
            for (i in 0 until n) {
                modEnv = decay * modEnv + (1 - decay) * abs(modBand[i])
                carEnv = decay * carEnv + (1 - decay) * abs(carBand[i])
                out[i] += carBand[i] * modEnv / max(carEnv, CARRIER_FLOOR)
            }
        }
        return out
    }

    /** RBJ band-pass (0 dB peak gain), run twice in series for steeper skirts. */
    private class BandPass(centerHz: Double, q: Double, sampleRate: Int) {
        private val b0: Double
        private val a1: Double
        private val a2: Double

        init {
            val w0 = 2 * PI * centerHz / sampleRate
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            b0 = alpha / a0
            a1 = -2 * cos(w0) / a0
            a2 = (1 - alpha) / a0
        }

        fun twice(input: FloatArray, out: FloatArray) {
            run(input, out)
            run(out, out)
        }

        /** In place is fine: each output sample only reads its own input sample before writing. */
        private fun run(input: FloatArray, out: FloatArray) {
            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            for (i in input.indices) {
                val x0 = input[i].toDouble()
                // b1 = 0 and b2 = -b0 for this band-pass.
                val y0 = b0 * (x0 - x2) - a1 * y1 - a2 * y2
                x2 = x1
                x1 = x0
                y2 = y1
                y1 = y0
                out[i] = y0.toFloat()
            }
        }
    }

    private companion object {
        /** Keeps a silent carrier channel from dividing by zero. */
        const val CARRIER_FLOOR = 1e-5f
    }
}
