package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.AudioConfig
import com.example.soundboard.audio.dsp.SoundProcessor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FxTest {
    private val rate = AudioConfig.SAMPLE_RATE

    private fun sineFloat(hz: Double, seconds: Double = 1.0, amplitude: Double = 0.5) =
        FloatArray((rate * seconds).toInt()) { (amplitude * sin(2.0 * PI * hz * it / rate)).toFloat() }

    private fun sineShort(hz: Double, seconds: Double = 1.0) =
        ShortArray((rate * seconds).toInt()) { (0.9 * sin(2.0 * PI * hz * it / rate) * 32767).toInt().toShort() }

    /** Frequency from upward zero crossings over the middle half (edges skipped). */
    private fun frequency(x: FloatArray): Double {
        val from = x.size / 4
        val to = x.size * 3 / 4
        var crossings = 0
        for (i in from + 1 until to) if (x[i - 1] < 0f && x[i] >= 0f) crossings++
        return crossings * rate.toDouble() / (to - from)
    }

    private fun frequency(x: ShortArray) = frequency(FloatArray(x.size) { x[it] / 32768f })

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return sqrt(sum / (to - from))
    }

    @Test
    fun speedUpDoublesFrequencyAndHalvesLength() {
        val out = SpeedUp2x().process(sineFloat(300.0), rate)
        assertEquals(rate / 2, out.size)
        assertEquals(600.0, frequency(out), 6.0)
    }

    @Test
    fun speedUpRemovesContentThatWouldAlias() {
        // 15 kHz would fold back to 9 kHz after decimation; it must be filtered out instead.
        val out = SpeedUp2x().process(sineFloat(15_000.0), rate)
        assertTrue(rms(out, 200, out.size - 200) < 0.005)
    }

    @Test
    fun timeStretchKeepsPitchAndDoublesLength() {
        val out = TimeStretch(2.0).process(sineFloat(440.0), rate)
        assertEquals(rate * 2, out.size)
        assertEquals(440.0, frequency(out), 4.0)
        // WSOLA aligns the grains, so the level stays steady (no cancellation dips).
        val expected = 0.5 / sqrt(2.0)
        for (block in 4 until 36) {
            val from = block * rate / 20
            assertEquals("block $block", expected, rms(out, from, from + rate / 20), 0.05)
        }
    }

    @Test
    fun highPassRemovesRumbleAndKeepsVoice() {
        val hp = HighPass(150.0)
        val low = hp.process(sineFloat(30.0), rate)
        val voice = hp.process(sineFloat(1000.0), rate)
        assertTrue(rms(low, rate / 2, rate) < 0.02)
        assertEquals(0.5 / sqrt(2.0), rms(voice, rate / 2, rate), 0.01)
    }

    @Test
    fun mouseyfierShiftsUpOneOctaveAndKeepsDuration() {
        val input = sineShort(220.0)
        val out = FxRenderer.render(input, Fx.Mouseyfier)
        assertTrue("length ${out.size}", abs(out.size - input.size) <= 2)
        assertEquals(440.0, frequency(out), 6.0)
    }

    @Test
    fun mouseyfierOutputIsNormalizedAndNeverExceedsCeiling() {
        val noise = java.util.Random(7)
        val input = ShortArray(rate * 2) { (noise.nextGaussian() * 9000).toInt().coerceIn(-32768, 32767).toShort() }
        val out = FxRenderer.render(input, Fx.Mouseyfier)
        val peak = out.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= SoundProcessor.maxOutputMagnitude)
        assertTrue("peak $peak", peak >= SoundProcessor.maxOutputMagnitude - 1)
    }

    @Test
    fun noEffectReturnsTheSameClip() {
        val input = sineShort(220.0)
        assertSame(input, FxRenderer.render(input, Fx.None))
    }

    @Test
    fun maxLengthClipRendersQuickly() {
        val input = sineShort(180.0, seconds = AudioConfig.MAX_CLIP_SECONDS.toDouble())
        FxRenderer.render(input, Fx.Mouseyfier) // warm-up
        val start = System.nanoTime()
        FxRenderer.render(input, Fx.Mouseyfier)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Mouseyfier, ${AudioConfig.MAX_CLIP_SECONDS} s clip: $ms ms")
        assertTrue("took $ms ms", ms < 2_000)
    }

    @Test
    fun unknownIdFallsBackToNone() {
        assertEquals(Fx.None, Fx.byId("from-the-future"))
        assertEquals(Fx.None, Fx.byId(null))
        assertEquals(Fx.Mouseyfier, Fx.byId("mouseyfier"))
    }

    @Test
    fun idsAreUnique() {
        assertEquals(Fx.entries.size, Fx.entries.map { it.id }.toSet().size)
    }
}
