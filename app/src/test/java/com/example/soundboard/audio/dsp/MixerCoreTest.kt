package com.example.soundboard.audio.dsp

import com.example.soundboard.audio.AudioConfig
import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerCoreTest {
    private val chunk = AudioConfig.MIXER_BUFFER_FRAMES
    private val ceiling = AudioConfig.PEAK_TARGET_LINEAR

    private fun constantClip(value: Short, frames: Int) = ShortArray(frames) { value }

    private fun noiseClip(seed: Long, frames: Int = 9600): ShortArray {
        val r = Random(seed)
        val raw = DoubleArray(frames) { r.nextGaussian() }
        val peak = raw.maxOf { abs(it) }
        val max = SoundProcessor.maxOutputMagnitude
        return ShortArray(frames) { (raw[it] / peak * max).toInt().toShort() }
    }

    /** Renders [frames] frames in mixer-sized chunks and returns them concatenated. */
    private fun render(core: MixerCore, frames: Int): FloatArray {
        val result = FloatArray(frames)
        val buf = FloatArray(chunk)
        var pos = 0
        while (pos < frames) {
            val n = minOf(chunk, frames - pos)
            core.render(buf, n)
            buf.copyInto(result, pos, 0, n)
            pos += n
        }
        return result
    }

    @Test
    fun retriggeringTheSameClipAddsVoices() {
        val core = MixerCore()
        val clip = constantClip(1000, 48_000)
        for (i in 1..10) {
            core.play(clip)
            assertEquals(i, core.activeVoiceCount)
            render(core, 100) // clip is still playing: nothing gets cut
            assertEquals(i, core.activeVoiceCount)
        }
    }

    @Test
    fun voicesAreFreedWhenClipsEnd() {
        val core = MixerCore()
        core.play(constantClip(1000, 500))
        core.play(constantClip(1000, 1500))
        assertEquals(2, core.activeVoiceCount)
        render(core, 1000)
        assertEquals(1, core.activeVoiceCount)
        render(core, 1000)
        assertEquals(0, core.activeVoiceCount)
    }

    @Test
    fun overlappingPlaybackSumsInsteadOfCutting() {
        val core = MixerCore()
        val latency = Limiter().latencyFrames
        val level = 3277 // about 0.1, far below the limiter threshold even when doubled
        core.play(constantClip(level.toShort(), 20_000))
        render(core, 1000)
        core.play(constantClip(level.toShort(), 20_000))
        val out = render(core, 2000)
        // After the second trigger (and the limiter delay) both voices are audible: 2 x level.
        val expected = 2f * level / 32768f
        assertEquals(expected, out[latency + 50], 1e-4f)
        assertEquals(expected, out[1900], 1e-4f)
    }

    @Test
    fun aSingleClipPlaysAtFullLevelUnaltered() {
        val core = MixerCore()
        val latency = Limiter().latencyFrames
        val clip = noiseClip(5, 4800)
        core.play(clip)
        val out = render(core, clip.size + latency + chunk)
        for (i in clip.indices) {
            assertEquals("sample $i", clip[i] / 32768f, out[i + latency], 1e-6f)
        }
    }

    @Test
    fun fullPoolOfLoudVoicesNeverExceedsCeiling() {
        val core = MixerCore()
        for (i in 0 until AudioConfig.MAX_VOICES) core.play(noiseClip(i.toLong()))
        assertEquals(AudioConfig.MAX_VOICES, core.activeVoiceCount)
        val out = render(core, 9600 + chunk)
        assertTrue(out.all { abs(it) <= ceiling })
        assertEquals(0, core.activeVoiceCount)
    }

    @Test
    fun voiceStealPathNeverExceedsCeiling() {
        val core = MixerCore()
        val peaks = ArrayList<Float>()
        // Trigger far more clips than there are voices, with a little audio between triggers.
        for (i in 0 until AudioConfig.MAX_VOICES * 3) {
            core.play(noiseClip(i.toLong() % 7, 24_000))
            assertTrue(core.activeVoiceCount <= AudioConfig.MAX_VOICES)
            peaks += render(core, 37).maxOf { abs(it) }
        }
        assertEquals(AudioConfig.MAX_VOICES, core.activeVoiceCount) // pool stayed full
        peaks += render(core, 24_000).maxOf { abs(it) }
        assertTrue("peak ${peaks.max()}", peaks.all { it <= ceiling })
    }

    @Test
    fun stolenVoiceFadesOutInsteadOfCutting() {
        val core = MixerCore(maxVoices = 1)
        val latency = Limiter().latencyFrames
        val level = 3277
        core.play(constantClip(level.toShort(), 40_000))
        render(core, 2000)
        core.play(ShortArray(40_000)) // pool is full: steals the oldest voice
        assertEquals(1, core.activeVoiceCount)
        val out = render(core, 1000)
        val start = level / 32768f
        // The stolen clip ramps down monotonically to zero over ~5 ms, with no step.
        var previous = start
        var maxStep = 0f
        for (i in latency until out.size) {
            assertTrue("fade must not rise at $i", out[i] <= previous + 1e-6f)
            maxStep = maxOf(maxStep, previous - out[i])
            previous = out[i]
        }
        assertTrue("step $maxStep too large", maxStep < start / 100f)
        assertEquals(0f, out.last(), 1e-6f)
    }

    @Test
    fun stopFadesOutOnlyThatClip() {
        val core = MixerCore()
        val stopped = constantClip(1000, 48_000)
        val kept = constantClip(2000, 48_000)
        core.play(stopped)
        core.play(kept)
        render(core, 2000)
        core.stop(stopped)
        assertEquals(1, core.activeVoiceCount)
        val after = render(core, 2000)
        // A short fade, not a jump: a cut would step by 1000 / 32768 (about 0.03) in one sample.
        for (i in 1 until after.size) assertTrue("jump at $i", abs(after[i] - after[i - 1]) < 0.005f)
        assertEquals(2000 / 32768f, after.last(), 1e-4f)
    }

    @Test
    fun stoppingAClipThatIsNotPlayingDoesNothing() {
        val core = MixerCore()
        core.play(constantClip(1000, 48_000))
        core.stop(constantClip(1000, 48_000)) // equal content, different instance
        assertEquals(1, core.activeVoiceCount)
    }
}
