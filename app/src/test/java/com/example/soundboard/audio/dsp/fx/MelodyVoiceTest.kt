package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.AudioConfig
import com.example.soundboard.audio.dsp.SoundProcessor
import com.example.soundboard.audio.dsp.fx.MelodyVoice.Segment
import com.example.soundboard.audio.dsp.melody.Melody
import com.example.soundboard.audio.dsp.melody.TimeSignature
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyVoiceTest {
    private val rate = AudioConfig.SAMPLE_RATE

    /** At 120 BPM in 4/4 a step is 0.125 s = 6000 samples. */
    private val step = rate / 8

    /** Voice-like tone: 10 harmonics of [hz] at 1/k, with a slow vowel-like level wobble. */
    private fun voice(hz: Double = 150.0, seconds: Double = 1.5) = FloatArray((rate * seconds).toInt()) { i ->
        var s = 0.0
        for (k in 1..10) s += sin(2 * PI * k * hz * i / rate) / k
        (0.25 * s * (0.7 + 0.3 * sin(2 * PI * 3 * i / rate))).toFloat()
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
    fun aNoteStopsAtItsLength() {
        // The sample (10 steps long) is longer than both notes; a rest follows the first one.
        val melody = Melody().withNote(0, 57, 2).withNote(4, 64)
        val segments = MelodyVoice.segments(melody, sampleLength = 10 * step, sampleRate = rate)
        assertEquals(listOf(Segment(0, 2 * step, 57), Segment(4 * step, step, 64)), segments)
    }

    @Test
    fun aShortSampleEndsBeforeTheNoteDoes() {
        val melody = Melody().withNote(0, 57, 8).withNote(8, 64, 8)
        val segments = MelodyVoice.segments(melody, sampleLength = 3 * step, sampleRate = rate)
        assertEquals(listOf(Segment(0, 3 * step, 57), Segment(8 * step, 3 * step, 64)), segments)
    }

    /** A take as recorded: [lead] seconds of room noise before the voice starts. */
    private fun take(lead: Double): FloatArray {
        val voice = voice()
        val random = java.util.Random(1)
        val start = (lead * rate).toInt()
        return FloatArray(start + voice.size) { i ->
            (random.nextGaussian() * 0.001).toFloat() + if (i >= start) voice[i - start] else 0f
        }
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return kotlin.math.sqrt(sum / (to - from))
    }

    @Test
    fun voiceOnsetSkipsTheSilenceBeforeTheVoice() {
        val onset = MelodyVoice.voiceOnset(take(0.3), rate)
        // 10 ms of lead are kept before the voice (which starts at 0.3 s).
        assertEquals(0.3 * rate - rate / 100, onset.toDouble(), rate * 0.003)
        assertEquals(0, MelodyVoice.voiceOnset(voice(), rate))
    }

    @Test
    fun consecutiveShortNotesAllSoundWithSilenceBeforeTheVoice() {
        // The bug: each 1-step note (125 ms) used to play only the 300 ms of silence at the start.
        val melody = Melody().withNote(0, 60).withNote(1, 62).withNote(2, 64).withNote(3, 65)
        val out = MelodyVoice(melody).process(take(0.3), rate)
        for (n in 0 until 4) {
            val level = rms(out, n * step + step / 4, n * step + step * 3 / 4)
            assertTrue("note $n level $level", level > 0.05)
        }
        assertEquals(4 * step, out.size)
    }

    @Test
    fun aRestBetweenNotesIsSilent() {
        val melody = Melody().withNote(0, 60, 2).withNote(4, 62, 2)
        val out = MelodyVoice(melody).process(voice(), rate)
        // Steps 2 and 3 rest; allow the vocoder's envelopes about 30 ms to die away.
        assertTrue(rms(out, 2 * step + rate * 3 / 100, 4 * step) < 0.01)
        assertTrue(rms(out, 4 * step + step / 4, 5 * step) > 0.05)
    }

    @Test
    fun leadingEmptyStepsAddNoSilence() {
        val melody = Melody().withNote(12, 60).withNote(14, 62)
        val segments = MelodyVoice.segments(melody, sampleLength = step, sampleRate = rate)
        assertEquals(0, segments.first().start)
        assertEquals(2 * step, segments.last().start)
    }

    @Test
    fun sixEightStepsFollowTheDottedQuarterBeat() {
        // 6/8 at 120 BPM: a dotted quarter (6 steps) lasts 0.5 s.
        val melody = Melody(TimeSignature.SixEight).withNote(0, 60).withNote(6, 62)
        val segments = MelodyVoice.segments(melody, sampleLength = rate, sampleRate = rate)
        assertEquals(rate / 2, segments[1].start)
    }

    @Test
    fun everyNoteRestartsTheSample() {
        val sample = FloatArray(10 * step) { (it % 997) / 997f }
        val melody = Melody().withNote(0, 57, 4).withNote(4, 64, 10)
        val segments = MelodyVoice.segments(melody, sample.size, rate)
        val modulator = MelodyVoice.modulator(sample, segments, rate)
        assertEquals(4 * step + sample.size, modulator.size)
        for (i in 0 until 1000) {
            assertEquals(sample[i], modulator[i], 0f)
            assertEquals(sample[i], modulator[4 * step + i], 0f)
        }
        // Faded to silence right where the second note starts.
        assertTrue(abs(modulator[4 * step - 1]) < 1e-6f)
    }

    @Test
    fun eachNoteSoundsAtItsPitch() {
        // A3 for one second, then E4: the voice itself is at 150 Hz.
        val melody = Melody().withNote(0, 57, 8).withNote(8, 64, 12)
        val out = MelodyVoice(melody).process(voice(), rate)
        assertEquals(220.0, pitchNear(out, rate * 3 / 10, 220.0), 220.0 * 0.01)
        assertEquals(329.63, pitchNear(out, rate * 13 / 10, 329.63), 329.63 * 0.01)
    }

    @Test
    fun outputRunsToTheEndOfTheLastNote() {
        val sample = voice(seconds = 1.5)
        val out = MelodyVoice(Melody().withNote(0, 57, 8).withNote(8, 64, 12)).process(sample, rate)
        assertEquals(8 * step + sample.size, out.size)
    }

    @Test
    fun noMelodyOrAnEmptyOneMeansNoEffect() {
        assertNull(Fx.Melody.effect(null))
        assertNull(Fx.Melody.effect(Melody()))
        val clip = ShortArray(1000) { (it * 7).toShort() }
        assertSame(clip, FxRenderer.render(clip, Fx.Melody, Melody()))
    }

    @Test
    fun renderedMelodyIsNormalizedAndNeverExceedsCeiling() {
        val sample = voice()
        val clip = ShortArray(sample.size) { (sample[it] * 32767).toInt().toShort() }
        val melody = Melody().withNote(0, 48).withNote(2, 72).withNote(3, 60).withNote(20, 67)
        val out = FxRenderer.render(clip, Fx.Melody, melody)
        val peak = out.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= SoundProcessor.maxOutputMagnitude)
        assertTrue("peak $peak", peak >= SoundProcessor.maxOutputMagnitude - 1)
    }

    @Test
    fun longestMelodyRendersInReasonableTime() {
        // Worst case: 60 BPM (a step is 0.25 s), a 5 s sample and a last note long enough to play all of it.
        val sample = voice(seconds = AudioConfig.MAX_CLIP_SECONDS.toDouble())
        val clip = ShortArray(sample.size) { (sample[it] * 32767).toInt().toShort() }
        val melody = Melody(bpm = Melody.MIN_BPM).withNote(0, 60, 20).withNote(44, 64, 20)
        FxRenderer.render(clip, Fx.Melody, melody) // warm-up
        val start = System.nanoTime()
        val out = FxRenderer.render(clip, Fx.Melody, melody)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Melody, ${out.size / rate} s output: $ms ms")
        assertTrue("took $ms ms", ms < 4_000)
    }
}
