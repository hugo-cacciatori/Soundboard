package com.example.soundboard.audio.dsp

import com.example.soundboard.audio.AudioConfig
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiterTest {
    private val rate = AudioConfig.SAMPLE_RATE
    private val ceiling = AudioConfig.PEAK_TARGET_LINEAR
    private val chunk = AudioConfig.MIXER_BUFFER_FRAMES

    /** A full-level clip: what [SoundProcessor] stores, as floats. */
    private fun fullLevelSine(hz: Double, phase: Double, seconds: Double = 0.5): FloatArray {
        val n = (rate * seconds).toInt()
        return FloatArray(n) { (ceiling * sin(2.0 * PI * hz * it / rate + phase)).toFloat() }
    }

    private fun fullLevelNoise(seed: Long, seconds: Double = 0.5): FloatArray {
        val r = Random(seed)
        val n = (rate * seconds).toInt()
        val raw = FloatArray(n) { r.nextGaussian().toFloat() }
        val peak = raw.maxOf { abs(it) }
        return FloatArray(n) { raw[it] / peak * ceiling }
    }

    private fun sum(clips: List<FloatArray>): FloatArray {
        val out = FloatArray(clips.maxOf { it.size })
        for (c in clips) for (i in c.indices) out[i] += c[i]
        return out
    }

    private fun runLimiter(limiter: Limiter, input: FloatArray): FloatArray {
        val out = input.copyOf()
        var pos = 0
        while (pos < out.size) {
            val n = minOf(chunk, out.size - pos)
            val tmp = out.copyOfRange(pos, pos + n)
            limiter.process(tmp, n)
            tmp.copyInto(out, pos)
            pos += n
        }
        return out
    }

    @Test
    fun stackOfSinesNeverExceedsCeiling() {
        val clips = List(AudioConfig.MAX_VOICES) { fullLevelSine(100.0 + it * 37.0, it * 0.7) }
        val input = sum(clips)
        assertTrue("test input must overshoot", input.maxOf { abs(it) } > 4f * ceiling)
        val out = runLimiter(Limiter(), input)
        val peak = out.maxOf { abs(it) }
        assertTrue("peak $peak exceeds $ceiling", peak <= ceiling)
        assertTrue("limiter should drive the output close to the ceiling, was $peak", peak > 0.5f * ceiling)
    }

    @Test
    fun stackOfNoiseNeverExceedsCeiling() {
        val clips = List(AudioConfig.MAX_VOICES) { fullLevelNoise(it.toLong()) }
        val out = runLimiter(Limiter(), sum(clips))
        val peak = out.maxOf { abs(it) }
        assertTrue("peak $peak exceeds $ceiling", peak <= ceiling)
        assertTrue(peak > 0.5f * ceiling)
    }

    @Test
    fun suddenFullScaleStepNeverExceedsCeiling() {
        val input = FloatArray(rate / 2) { if (it < 1000) 0f else 8f }
        val out = runLimiter(Limiter(), input)
        assertTrue(out.all { abs(it) <= ceiling })
    }

    @Test
    fun singleClipPassesThroughUnchanged() {
        val limiter = Limiter()
        val latency = limiter.latencyFrames
        val clip = fullLevelSine(440.0, 0.3)
        val padded = clip + FloatArray(latency + chunk)
        val out = runLimiter(limiter, padded)
        for (i in 0 until latency) assertEquals("lead-in must be silent", 0f, out[i], 0f)
        for (i in clip.indices) {
            assertEquals("sample $i", clip[i], out[i + latency], 1e-6f)
        }
    }

    @Test
    fun singleNoiseClipPassesThroughUnchanged() {
        val limiter = Limiter()
        val latency = limiter.latencyFrames
        val clip = fullLevelNoise(99)
        val out = runLimiter(limiter, clip + FloatArray(latency + chunk))
        for (i in clip.indices) assertEquals("sample $i", clip[i], out[i + latency], 1e-6f)
    }

    @Test
    fun gainRecoversAfterLoudBurst() {
        val limiter = Limiter()
        val latency = limiter.latencyFrames
        val burst = FloatArray(rate / 10) { 6f * sin(2f * PI.toFloat() * 200f * it / rate) }
        val quiet = FloatArray(rate) { 0.1f * sin(2f * PI.toFloat() * 440f * it / rate) }
        val out = runLimiter(limiter, burst + quiet + FloatArray(latency))
        // After 800 ms of recovery (release is ~80 ms) the quiet signal passes at unity gain.
        val start = burst.size + (rate * 0.8).toInt()
        for (i in start until burst.size + quiet.size) {
            assertEquals(quiet[i - burst.size], out[i + latency], 1e-4f)
        }
    }

    @Test
    fun gainChangesAreSmooth() {
        val limiter = Limiter()
        val latency = limiter.latencyFrames
        val input = FloatArray(rate / 2) { if (it < 4000) 0.2f else 3f }
        val out = runLimiter(limiter, input + FloatArray(latency))
        // Applied gain = output / (delayed) input. It must move smoothly, never jump.
        var maxJump = 0f
        var previous = 1f
        for (i in input.indices) {
            val gain = out[i + latency] / input[i]
            maxJump = maxOf(maxJump, abs(gain - previous))
            previous = gain
        }
        assertTrue("gain jump $maxJump", maxJump < 0.02f)
        assertEquals(ceiling / 3f, previous, 1e-3f)
    }
}
