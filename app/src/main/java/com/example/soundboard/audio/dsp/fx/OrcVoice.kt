package com.example.soundboard.audio.dsp.fx

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Orc voice: fat and dumb (SPEC §5). In order:
 * 1. a bigger body: the voice played [body] times as fast (slower, like a slowed-down tape), so
 *    pitch and formants go down together; a bigger vocal tract is what makes a voice sound large,
 * 2. a drawl: time-stretched to [drawl] times the input's length (a slow, thick delivery),
 * 3. the voiced parts [pitchDropSemitones] further down with [PitchFlattener], keeping the voice's
 *    own inflection; unvoiced parts pass through,
 * 4. a low-pass at [lowPassHz] (thick, dull), then [growl] of soft saturation mixed in.
 */
class OrcVoice(
    private val body: Double,
    private val drawl: Double,
    private val pitchDropSemitones: Double,
    private val lowPassHz: Double,
    private val growl: Double,
) : Effect {
    init {
        require(body > 0.0 && body <= 1.0) { "body must be in (0, 1]" }
        require(drawl > 0.0) { "drawl must be positive" }
        require(pitchDropSemitones >= 0.0) { "pitchDropSemitones must not be negative" }
        require(growl in 0.0..1.0) { "growl must be in [0, 1]" }
    }

    override fun process(input: FloatArray, sampleRate: Int): FloatArray {
        if (input.isEmpty()) return input.copyOf()
        val big = resample(input, body)
        val target = (input.size * drawl).roundToInt()
        val drawn = TimeStretch(target.toDouble() / big.size).process(big, sampleRate)
        val deeper = dropPitch(drawn, sampleRate)
        return saturate(LowPass(lowPassHz).process(deeper, sampleRate))
    }

    private fun dropPitch(x: FloatArray, sampleRate: Int): FloatArray {
        if (pitchDropSemitones == 0.0) return x
        val track = PitchTracker.track(x, sampleRate)
        val median = track.medianVoiced()
        if (median <= 0f) return x
        val factor = 2.0.pow(pitchDropSemitones / 12)
        return PitchFlattener.shift(x, track, sampleRate) { position ->
            // A mark at a voiced run's edge can fall on an unvoiced frame: use the median there.
            val hz = track.at(position).takeIf { it > 0f } ?: median
            sampleRate / hz * factor
        }
    }

    /** Soft clipping (tanh) on the peak-normalized signal, [growl] of it mixed with the clean one. */
    private fun saturate(x: FloatArray): FloatArray {
        if (growl == 0.0) return x
        var peak = 0f
        for (v in x) peak = max(peak, abs(v))
        if (peak == 0f) return x
        val wet = growl.toFloat()
        val dry = 1f - wet
        val norm = tanh(DRIVE).toFloat()
        return FloatArray(x.size) { i ->
            val v = x[i] / peak
            peak * (dry * v + wet * tanh(DRIVE * v).toFloat() / norm)
        }
    }

    private companion object {
        /** How hard the growl's saturation is driven at full level. */
        const val DRIVE = 2.5
    }
}
