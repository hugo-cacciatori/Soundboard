package com.example.soundboard.audio.dsp

import com.example.soundboard.audio.AudioConfig
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Outcome of [SoundProcessor.process]. */
sealed interface ProcessResult {
    /** Processed 16-bit mono PCM, peak-normalized to [AudioConfig.PEAK_TARGET_DBFS]. */
    class Success(val samples: ShortArray) : ProcessResult

    /** Digital silence or a clip too quiet to normalize without amplifying noise. */
    data object TooQuiet : ProcessResult
}

/**
 * Runs once after recording; the result is what gets stored. Pure Kotlin (no android.*).
 *
 * Steps: float conversion, DC removal, edge fades, peak measurement, silence rejection,
 * peak normalization to the target, 16-bit quantization (round + clamp).
 */
object SoundProcessor {
    private const val SCALE_IN = 32768f
    private const val SCALE_OUT = 32767f

    /** Largest allowed output magnitude: floor(target * 32767), so rounding can never overshoot. */
    val maxOutputMagnitude: Int =
        floor(AudioConfig.PEAK_TARGET_LINEAR.toDouble() * SCALE_OUT).toInt()

    fun process(
        input: ShortArray,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        silenceThresholdDbfs: Double = AudioConfig.SILENCE_THRESHOLD_DBFS,
    ): ProcessResult {
        if (input.isEmpty()) return ProcessResult.TooQuiet

        // 1. float in [-1, 1]
        val x = FloatArray(input.size) { input[it] / SCALE_IN }
        return processFloat(x, sampleRate, silenceThresholdDbfs)
    }

    /**
     * Steps 2 to 7 on a float signal (modified in place). Also used to finish a clip after an
     * effect, since effects change the peak.
     */
    fun processFloat(
        x: FloatArray,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        silenceThresholdDbfs: Double = AudioConfig.SILENCE_THRESHOLD_DBFS,
    ): ProcessResult {
        if (x.isEmpty()) return ProcessResult.TooQuiet
        val n = x.size

        // 2. remove DC offset (mean accumulated in double)
        var sum = 0.0
        for (v in x) sum += v
        val mean = (sum / n).toFloat()
        for (i in 0 until n) x[i] -= mean

        // 3. short fade-in / fade-out (about 5 ms) to avoid clicks at the clip edges
        val fadeLen = minOf((sampleRate * AudioConfig.EDGE_FADE_MS / 1000.0).toInt(), n / 2)
        for (i in 0 until fadeLen) {
            val g = i.toFloat() / fadeLen
            x[i] *= g
            x[n - 1 - i] *= g
        }

        // 4. sample peak
        var peak = 0f
        for (v in x) {
            val a = abs(v)
            if (a > peak) peak = a
        }

        // 5. reject silence / near-silence instead of amplifying noise
        val threshold = 10.0.pow(silenceThresholdDbfs / 20.0).toFloat()
        if (peak < threshold) return ProcessResult.TooQuiet

        // 6. peak normalization: gain = 10^(target/20) / peak
        val gain = AudioConfig.PEAK_TARGET_LINEAR / peak

        // 7. quantize with rounding, clamp so the peak can never exceed the target
        val limit = maxOutputMagnitude
        val out = ShortArray(n)
        for (i in 0 until n) {
            val q = (x[i] * gain * SCALE_OUT).roundToInt()
            out[i] = (if (q > limit) limit else if (q < -limit) -limit else q).toShort()
        }
        return ProcessResult.Success(out)
    }
}
