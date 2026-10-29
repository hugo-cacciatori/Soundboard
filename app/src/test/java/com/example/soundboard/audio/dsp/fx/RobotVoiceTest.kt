package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.AudioConfig
import com.example.soundboard.audio.dsp.SoundProcessor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotVoiceTest {
    private val rate = AudioConfig.SAMPLE_RATE

    /** Voice-like tone: 6 harmonics at 1/k, gliding linearly from [fromHz] to [toHz]. */
    private fun glide(fromHz: Double, toHz: Double, seconds: Double = 1.0): FloatArray {
        val n = (rate * seconds).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            phase += 2 * PI * (fromHz + (toHz - fromHz) * i / n) / rate
            var s = 0.0
            for (k in 1..6) s += sin(k * phase) / k
            (0.3 * s).toFloat()
        }
    }

    /**
     * Pitch of the block starting at [from], by normalized autocorrelation over lags of 160 to
     * 320 Hz (one octave, so no octave errors), with parabolic interpolation.
     */
    private fun blockPitch(x: FloatArray, from: Int, length: Int = 4096): Double {
        val minLag = rate / 320
        val maxLag = rate / 160
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
        val refined = best + (a - c) / (2 * (a - 2 * b + c))
        return rate / refined
    }

    private fun assertFlatAt(out: FloatArray, expectedHz: Double) {
        // Skip the first and last 100 ms; check every 50 ms block in between.
        var from = rate / 10
        while (from + 4096 + rate / 160 + 2 < out.size - rate / 10) {
            val hz = blockPitch(out, from)
            assertEquals("block at $from", expectedHz, hz, expectedHz * 0.01)
            from += rate / 20
        }
    }

    @Test
    fun glideComesOutOnOneNote() {
        // 200 -> 240 Hz has its median at 220 Hz (A3); the input itself is off by up to 20 Hz.
        val out = RobotVoice(combFeedback = 0.0).process(glide(200.0, 240.0), rate)
        assertFlatAt(out, 220.0)
    }

    @Test
    fun pitchSnapsToTheNearestSemitone() {
        // 230 Hz is 0.77 semitones above A3, so the nearest note is A#3 (233.08 Hz).
        val out = RobotVoice(combFeedback = 0.0).process(glide(230.0, 230.0), rate)
        assertFlatAt(out, 233.08)
    }

    @Test
    fun combKeepsTheNote() {
        val out = RobotVoice(combFeedback = 0.4).process(glide(200.0, 240.0), rate)
        assertFlatAt(out, 220.0)
    }

    @Test
    fun fullRoboticGrainKeepsTheNote() {
        // Comb, ring modulator and sample-and-hold at the app's settings, or stronger.
        val out = RobotVoice(combFeedback = 0.6, ringMix = 0.35, holdSamples = 3).process(glide(200.0, 240.0), rate)
        assertFlatAt(out, 220.0)
    }

    @Test
    fun sampleAndHoldRepeatsSamples() {
        val out = RobotVoice(combFeedback = 0.0, holdSamples = 3).process(glide(200.0, 240.0), rate)
        for (i in 1 until 3000) if (i % 3 != 0) assertEquals(out[i - 1], out[i], 0f)
    }

    @Test
    fun durationIsUnchanged() {
        val input = glide(150.0, 300.0, seconds = 1.37)
        assertEquals(input.size, RobotVoice(combFeedback = 0.4).process(input, rate).size)
    }

    @Test
    fun movingSyllablesAnOctaveKeepsTheirTiming() {
        // Syllables at 110, 220 and 440 Hz with pauses: the note is A3 (220 Hz), so the first moves an
        // octave up and the last an octave down. Each must start and end where it did (no comb: it rings on).
        val syllables = listOf(Triple(0.10, 0.40, 110.0), Triple(0.55, 0.80, 220.0), Triple(1.00, 1.30, 440.0))
        val input = FloatArray((rate * 1.4).toInt())
        for ((from, to, hz) in syllables) {
            for (i in (from * rate).toInt() until (to * rate).toInt()) {
                var s = 0.0
                for (k in 1..6) s += sin(2 * PI * k * hz * i / rate) / k
                input[i] = (0.3 * s).toFloat()
            }
        }
        val out = RobotVoice(combFeedback = 0.0).process(input, rate)
        assertEquals(input.size, out.size)
        // Times where the 5 ms level crosses a quarter of its peak, rising then falling.
        val block = rate / 200
        val levels = (0 until out.size / block).map { b ->
            var sum = 0.0
            for (i in b * block until (b + 1) * block) sum += out[i].toDouble() * out[i]
            sum / block
        }
        val threshold = levels.max() / 16
        val events = ArrayList<Double>()
        for (b in 1 until levels.size) {
            if ((levels[b] >= threshold) != (levels[b - 1] >= threshold)) events += b.toDouble() * block / rate
        }
        val expected = syllables.flatMap { listOf(it.first, it.second) }
        assertEquals("$events", expected.size, events.size)
        for ((e, a) in expected.zip(events)) assertEquals("$events", e, a, 0.012)
    }

    @Test
    fun noiseHasNoVoiceAndComesBackDry() {
        val random = java.util.Random(3)
        val noise = FloatArray(rate) { (random.nextGaussian() * 0.2).toFloat() }
        assertArrayEquals(noise, RobotVoice(combFeedback = 0.4).process(noise, rate), 0f)
    }

    @Test
    fun nearestNote() {
        assertEquals(440.0, RobotVoice.nearestNoteHz(452.0), 1e-9)
        assertEquals(466.1638, RobotVoice.nearestNoteHz(460.0), 1e-3)
        assertEquals(110.0, RobotVoice.nearestNoteHz(108.0), 1e-9)
    }

    @Test
    fun robotOutputIsNormalizedAndNeverExceedsCeiling() {
        val input = glide(180.0, 260.0, seconds = 2.0)
        val clip = ShortArray(input.size) { (input[it] * 32767).toInt().toShort() }
        val out = FxRenderer.render(clip, Fx.Robot)
        assertEquals(clip.size, out.size)
        val peak = out.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= SoundProcessor.maxOutputMagnitude)
        assertTrue("peak $peak", peak >= SoundProcessor.maxOutputMagnitude - 1)
    }

    @Test
    fun maxLengthClipRendersQuickly() {
        val input = glide(120.0, 250.0, seconds = AudioConfig.MAX_CLIP_SECONDS.toDouble())
        val clip = ShortArray(input.size) { (input[it] * 32767).toInt().toShort() }
        FxRenderer.render(clip, Fx.Robot) // warm-up
        val start = System.nanoTime()
        FxRenderer.render(clip, Fx.Robot)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Robot, ${AudioConfig.MAX_CLIP_SECONDS} s clip: $ms ms")
        assertTrue("took $ms ms", ms < 2_000)
    }
}
