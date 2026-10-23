package com.example.soundboard.audio.dsp

import com.example.soundboard.audio.AudioConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundProcessorTest {
    private val rate = AudioConfig.SAMPLE_RATE
    private val targetInt = AudioConfig.PEAK_TARGET_LINEAR * 32767f

    private fun sine(amplitude: Double, dc: Double = 0.0, seconds: Double = 1.0, hz: Double = 440.0) =
        ShortArray((rate * seconds).toInt()) { i ->
            val v = dc + amplitude * sin(2.0 * PI * hz * i / rate)
            (v * 32768.0).toInt().coerceIn(-32768, 32767).toShort()
        }

    private fun process(input: ShortArray): ShortArray {
        val result = SoundProcessor.process(input)
        assertTrue("expected Success but was $result", result is ProcessResult.Success)
        return (result as ProcessResult.Success).samples
    }

    private fun peak(samples: ShortArray): Int = samples.maxOf { abs(it.toInt()) }

    private fun assertPeakAtTarget(out: ShortArray) {
        val p = peak(out)
        assertTrue("peak $p vs target $targetInt", abs(p - targetInt) <= 1f)
        assertTrue("peak $p exceeds ceiling", p <= SoundProcessor.maxOutputMagnitude)
    }

    @Test
    fun quietInputIsNormalizedToTarget() {
        // -40 dBFS sine: quiet but above the silence threshold.
        assertPeakAtTarget(process(sine(0.01)))
    }

    @Test
    fun loudInputIsNormalizedToTarget() {
        assertPeakAtTarget(process(sine(1.0)))
    }

    @Test
    fun inputWithDcOffsetIsNormalizedToTarget() {
        assertPeakAtTarget(process(sine(0.25, dc = 0.5)))
    }

    @Test
    fun dcOffsetIsRemoved() {
        val out = process(sine(0.25, dc = 0.5, hz = 441.0))
        val mean = out.sumOf { it.toLong() }.toDouble() / out.size
        assertTrue("mean $mean should be about zero", abs(mean) < 50.0)
    }

    @Test
    fun fullScaleNegativeSamplesDoNotOverflow() {
        val input = ShortArray(rate / 2) { if (it % 2 == 0) Short.MIN_VALUE else Short.MAX_VALUE }
        val out = process(input)
        assertPeakAtTarget(out)
    }

    @Test
    fun outputNeverExceedsCeiling() {
        val noise = java.util.Random(1)
        val input = ShortArray(rate) { (noise.nextGaussian() * 9000).toInt().coerceIn(-32768, 32767).toShort() }
        val out = process(input)
        assertTrue(out.all { abs(it.toInt()) <= SoundProcessor.maxOutputMagnitude })
    }

    @Test
    fun silenceIsRejected() {
        assertEquals(ProcessResult.TooQuiet, SoundProcessor.process(ShortArray(rate)))
    }

    @Test
    fun emptyInputIsRejected() {
        assertEquals(ProcessResult.TooQuiet, SoundProcessor.process(ShortArray(0)))
    }

    @Test
    fun nearSilenceIsRejected() {
        // -60 dBFS is below the -50 dBFS threshold.
        val amplitude = 10.0.pow(-60.0 / 20.0)
        assertEquals(ProcessResult.TooQuiet, SoundProcessor.process(sine(amplitude)))
    }

    @Test
    fun constantDcOnlyInputIsRejected() {
        val dcOnly = ShortArray(rate) { 12000 }
        assertEquals(ProcessResult.TooQuiet, SoundProcessor.process(dcOnly))
    }

    @Test
    fun clipEdgesAreFaded() {
        val out = process(sine(0.8))
        assertEquals(0, out.first().toInt())
        assertEquals(0, out.last().toInt())
        assertEquals(rate, out.size)
    }
}
