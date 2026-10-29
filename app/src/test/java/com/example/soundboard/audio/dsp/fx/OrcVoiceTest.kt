package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.AudioConfig
import com.example.soundboard.audio.dsp.SoundProcessor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcVoiceTest {
    private val rate = AudioConfig.SAMPLE_RATE

    private val orc = Fx.Orc.effect()!!

    /** Voice-like tone: 10 harmonics of [hz] at 1/k, with a slow vowel-like level wobble. */
    private fun voice(hz: Double = 200.0, seconds: Double = 1.5) = FloatArray((rate * seconds).toInt()) { i ->
        var s = 0.0
        for (k in 1..10) s += sin(2 * PI * k * hz * i / rate) / k
        (0.25 * s * (0.7 + 0.3 * sin(2 * PI * 3 * i / rate))).toFloat()
    }

    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return sqrt(sum / (to - from))
    }

    /**
     * Pitch around [expectedHz] in the block starting at [from], by autocorrelation over lags of
     * 0.75 to 1.4 times the expected period (so octave errors can't win), with parabolic interpolation.
     */
    private fun pitchNear(x: FloatArray, from: Int, expectedHz: Double, length: Int = 4096): Double {
        val minLag = (rate / (expectedHz * 1.4)).toInt()
        val maxLag = (rate / (expectedHz * 0.75)).toInt()
        val r = DoubleArray(maxLag + 2)
        for (lag in minLag - 1..maxLag + 1) {
            var acc = 0.0
            for (i in from until from + length) acc += x[i].toDouble() * x[i + lag]
            r[lag] = acc
        }
        var best = minLag
        for (lag in minLag..maxLag) if (r[lag] > r[best]) best = lag
        val a = r[best - 1]
        val b = r[best]
        val c = r[best + 1]
        return rate / (best + (a - c) / (2 * (a - 2 * b + c)))
    }

    @Test
    fun theVoiceComesOutMuchLower() {
        // 0.8 for the bigger body, then 4 semitones more: about 0.63, nearly 8 semitones down.
        val expected = 200.0 * 0.8 * 2.0.pow(-4.0 / 12)
        val out = orc.process(voice(hz = 200.0), rate)
        var at = rate / 4
        while (at < out.size - rate / 4 - 4096) {
            assertEquals("at $at", expected, pitchNear(out, at, expected), expected * 0.02)
            at += rate / 5
        }
    }

    @Test
    fun theDrawlMakesItALittleLonger() {
        val input = voice(seconds = 1.37)
        assertEquals((input.size * 1.12).roundToInt(), orc.process(input, rate).size)
    }

    @Test
    fun aSoundWithoutPitchStillComesOutAudible() {
        val random = java.util.Random(9)
        val hiss = FloatArray(rate) { (random.nextGaussian() * 0.2).toFloat() }
        val out = orc.process(hiss, rate)
        assertEquals((hiss.size * 1.12).roundToInt(), out.size)
        assertTrue(rms(out, rate / 10, out.size - rate / 10) > 0.01)
    }

    @Test
    fun theHighsAreDulled() {
        // A 6 kHz tone sits well above the 3.2 kHz low-pass (and still above it after the slow-down).
        val bright = FloatArray(rate) { i -> (0.5 * sin(2 * PI * 6_000 * i / rate)).toFloat() }
        val out = OrcVoice(body = 1.0, drawl = 1.0, pitchDropSemitones = 0.0, lowPassHz = 3_200.0, growl = 0.0)
            .process(bright, rate)
        assertTrue(rms(out, rate / 10, out.size) < rms(bright) * 0.4)
    }

    @Test
    fun theOrcIdIsStable() {
        assertEquals(Fx.Orc, Fx.byId("orc"))
        assertNotNull(Fx.Orc.effect())
    }

    @Test
    fun orcOutputIsNormalizedAndNeverExceedsCeiling() {
        val sample = voice()
        val clip = ShortArray(sample.size) { (sample[it] * 32767).toInt().toShort() }
        val out = FxRenderer.render(clip, Fx.Orc)
        val peak = out.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= SoundProcessor.maxOutputMagnitude)
        assertTrue("peak $peak", peak >= SoundProcessor.maxOutputMagnitude - 1)
    }

    @Test
    fun maxLengthClipRendersQuickly() {
        val sample = voice(seconds = AudioConfig.MAX_CLIP_SECONDS.toDouble())
        val clip = ShortArray(sample.size) { (sample[it] * 32767).toInt().toShort() }
        FxRenderer.render(clip, Fx.Orc) // warm-up
        val start = System.nanoTime()
        FxRenderer.render(clip, Fx.Orc)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Orc, ${AudioConfig.MAX_CLIP_SECONDS} s clip: $ms ms")
        assertTrue("took $ms ms", ms < 2_000)
    }
}
