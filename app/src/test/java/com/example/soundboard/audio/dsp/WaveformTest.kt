package com.example.soundboard.audio.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformTest {
    @Test
    fun loudestSliceIsOneAndOthersScaleWithIt() {
        val samples = ShortArray(400)
        samples[50] = 8_000 // slice 0
        samples[150] = -16_000 // slice 1
        samples[399] = 4_000 // slice 3; slice 2 stays silent
        val peaks = Waveform.peaks(samples, 4)
        assertArrayEquals(floatArrayOf(0.5f, 1f, 0f, 0.25f), peaks, 1e-6f)
    }

    @Test
    fun fullScaleNegativeSampleDoesNotOverflow() {
        val peaks = Waveform.peaks(shortArrayOf(Short.MIN_VALUE, 16_384), 2)
        assertArrayEquals(floatArrayOf(1f, 0.5f), peaks, 1e-6f)
    }

    @Test
    fun silenceAndEmptyInputGiveZeros() {
        assertArrayEquals(FloatArray(8), Waveform.peaks(ShortArray(1_000), 8), 0f)
        assertArrayEquals(FloatArray(8), Waveform.peaks(ShortArray(0), 8), 0f)
    }

    @Test
    fun fewerSamplesThanBinsStillCoversEverySample() {
        val peaks = Waveform.peaks(shortArrayOf(1_000, 2_000, 4_000), 10)
        assertEquals(10, peaks.size)
        assertEquals(1f, peaks.max(), 0f)
        assertEquals(3, peaks.count { it > 0f })
    }
}
