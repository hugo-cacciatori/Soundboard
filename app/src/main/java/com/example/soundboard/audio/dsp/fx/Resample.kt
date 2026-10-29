package com.example.soundboard.audio.dsp.fx

/**
 * [x] played [ratio] times faster (linear interpolation), like a tape at another speed: pitch and
 * formants move together and the length becomes about `x.size / ratio`. Slower ([ratio] < 1) is
 * safe; faster should only be used where the result is shaped or filtered afterwards.
 */
internal fun resample(x: FloatArray, ratio: Double): FloatArray {
    if (x.size < 2) return x.copyOf()
    val length = ((x.size - 1) / ratio).toInt() + 1
    return FloatArray(length) { i ->
        val position = i * ratio
        val j = position.toInt().coerceAtMost(x.size - 2)
        val frac = (position - j).toFloat()
        x[j] + (x[j + 1] - x[j]) * frac
    }
}
