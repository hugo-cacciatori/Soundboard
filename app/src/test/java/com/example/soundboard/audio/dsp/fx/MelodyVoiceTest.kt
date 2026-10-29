package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.AudioConfig
import com.example.soundboard.audio.dsp.SoundProcessor
import com.example.soundboard.audio.dsp.fx.MelodyVoice.Segment
import com.example.soundboard.audio.dsp.melody.Melody
import com.example.soundboard.audio.dsp.melody.TimeSignature
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
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

    /** A take as recorded: room noise for [lead] seconds, the voice, then [tail] seconds of room noise. */
    private fun take(lead: Double, tail: Double = 0.0, hz: Double = 150.0): FloatArray {
        val voice = voice(hz)
        val random = java.util.Random(1)
        val start = (lead * rate).toInt()
        return FloatArray(start + voice.size + (tail * rate).toInt()) { i ->
            val v = if (i >= start && i - start < voice.size) voice[i - start] else 0f
            (random.nextGaussian() * 0.001).toFloat() + v
        }
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += x[i].toDouble() * x[i]
        return sqrt(sum / (to - from))
    }

    /** Normalized correlation of a and b over [from, to): 1 means the same waveform. */
    private fun similarity(a: FloatArray, b: FloatArray, from: Int, to: Int): Double {
        var ab = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in from until to) {
            ab += a[i].toDouble() * b[i]
            aa += a[i].toDouble() * a[i]
            bb += b[i].toDouble() * b[i]
        }
        return ab / sqrt(aa * bb)
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
    fun noteLengthsFollowTheTempo() {
        val melody = Melody().withNote(0, 57, 2).withNote(4, 64)
        assertEquals(
            listOf(Segment(0, 2 * step, 57), Segment(4 * step, step, 64)),
            MelodyVoice.segments(melody, rate),
        )
        // Half the tempo, twice as long.
        assertEquals(
            listOf(Segment(0, 4 * step, 57), Segment(8 * step, 2 * step, 64)),
            MelodyVoice.segments(melody.withBpm(60), rate),
        )
    }

    @Test
    fun sixEightStepsFollowTheDottedQuarterBeat() {
        // 6/8 at 120 BPM: a dotted quarter (6 steps) lasts 0.5 s.
        val melody = Melody(TimeSignature.SixEight).withNote(0, 60).withNote(6, 62)
        assertEquals(rate / 2, MelodyVoice.segments(melody, rate)[1].start)
    }

    @Test
    fun leadingEmptyStepsAddNoSilence() {
        val segments = MelodyVoice.segments(Melody().withNote(12, 60).withNote(14, 62), rate)
        assertEquals(0, segments.first().start)
        assertEquals(2 * step, segments.last().start)
    }

    @Test
    fun theSilenceAroundTheVoiceIsTrimmed() {
        val take = take(lead = 0.3, tail = 0.4)
        // 10 ms are kept on each side of the voice (0.3 s to 1.8 s).
        assertEquals(0.3 * rate - rate / 100, MelodyVoice.voiceOnset(take, rate).toDouble(), rate * 0.003)
        assertEquals(1.8 * rate + rate / 100, MelodyVoice.voiceEnd(take, rate).toDouble(), rate * 0.003)
        assertEquals(0, MelodyVoice.voiceOnset(voice(), rate))
    }

    @Test
    fun aNoteAtTheSoundsOwnPitchIsTheOriginalSound() {
        // The voice is at 220 Hz (A3) and lasts 1.5 s = 12 steps: one A3 note of 12 steps gives it back.
        val sound = voice(hz = 220.0)
        val out = MelodyVoice(Melody().withNote(0, 57, 12)).process(sound, rate)
        assertEquals(sound.size, out.size)
        assertTrue(similarity(sound, out, rate / 10, out.size - rate / 10) > 0.95)
    }

    @Test
    fun aShortNoteCutsTheSound() {
        val sound = voice(hz = 220.0)
        val out = MelodyVoice(Melody().withNote(0, 57, 2)).process(sound, rate)
        assertEquals(2 * step, out.size)
        // The start of the original sound, not something else.
        assertTrue(similarity(sound, out, rate / 50, out.size - rate / 50) > 0.95)
    }

    @Test
    fun aLongNoteStretchesTheSoundAtTheSamePitch() {
        // A 0.5 s sound on a 2 s note.
        val sound = voice(hz = 220.0, seconds = 0.5)
        val out = MelodyVoice(Melody().withNote(0, 57, 16)).process(sound, rate)
        assertEquals(16 * step, out.size)
        assertEquals(220.0, pitchNear(out, rate, 220.0), 220.0 * 0.01)
        // Still sounding near the end: stretched, not padded with silence.
        assertTrue(rms(out, out.size * 3 / 4, out.size - rate / 50) > 0.05)
    }

    @Test
    fun eachNoteSoundsAtItsPitch() {
        // A3 for one second, then E4: the voice itself is at 150 Hz.
        val melody = Melody().withNote(0, 57, 8).withNote(8, 64, 12)
        val out = MelodyVoice(melody).process(voice(), rate)
        assertEquals(220.0, pitchNear(out, rate * 3 / 10, 220.0), 220.0 * 0.01)
        assertEquals(329.63, pitchNear(out, rate * 13 / 10, 329.63), 329.63 * 0.01)
    }

    /** Voice-like tone (10 harmonics at 1/k) whose pitch follows [hzAt] (seconds -> Hz). */
    private fun singing(seconds: Double = 1.5, hzAt: (Double) -> Double): FloatArray {
        var phase = 0.0
        return FloatArray((rate * seconds).toInt()) { i ->
            phase += 2 * PI * hzAt(i.toDouble() / rate) / rate
            var s = 0.0
            for (k in 1..10) s += sin(k * phase) / k
            (0.25 * s).toFloat()
        }
    }

    @Test
    fun aSlowGlideInTheVoiceComesOutOnTheNote() {
        // The voice slides from 200 to 240 Hz over 1.5 s; the A3 note must not follow the slide.
        val sound = singing { t -> 200.0 + 40.0 * t / 1.5 }
        val out = MelodyVoice(Melody().withNote(0, 57, 12)).process(sound, rate)
        var at = rate / 5
        while (at < out.size - rate / 5 - 4096) {
            assertEquals("at $at", 220.0, pitchNear(out, at, 220.0), 220.0 * 0.015)
            at += rate / 10
        }
    }

    @Test
    fun theVoicesVibratoIsKeptSoTheNoteIsNotRobotic() {
        // 5.5 Hz vibrato of +-3 % around 220 Hz: it must still wobble around the A3 note, not be flattened.
        val sound = singing { t -> 220.0 * (1 + 0.03 * sin(2 * PI * 5.5 * t)) }
        val out = MelodyVoice(Melody().withNote(0, 57, 12)).process(sound, rate)
        val pitches = (0 until 40).map { b -> pitchNear(out, rate / 5 + b * rate / 40, 220.0, length = 1024) }
        val spread = (pitches.max() - pitches.min()) / 220.0
        assertTrue("spread $spread", spread > 0.03)
        assertEquals(220.0, pitches.average(), 220.0 * 0.01)
    }

    @Test
    fun consecutiveShortNotesAllSoundWithSilenceBeforeTheVoice() {
        // Each 1-step note (125 ms) must play the voice, not the 300 ms of silence before it.
        val melody = Melody().withNote(0, 60).withNote(1, 62).withNote(2, 64).withNote(3, 65)
        val out = MelodyVoice(melody).process(take(lead = 0.3), rate)
        assertEquals(4 * step, out.size)
        for (n in 0 until 4) {
            val level = rms(out, n * step + step / 4, n * step + step * 3 / 4)
            assertTrue("note $n level $level", level > 0.05)
        }
    }

    @Test
    fun aRestBetweenNotesIsSilent() {
        val melody = Melody().withNote(0, 60, 2).withNote(4, 62, 2)
        val out = MelodyVoice(melody).process(voice(), rate)
        assertTrue(rms(out, 2 * step, 4 * step) < 1e-6)
        assertTrue(rms(out, 4 * step + step / 4, 5 * step) > 0.05)
    }

    @Test
    fun aSoundWithoutPitchStillPlaysEveryNote() {
        val random = java.util.Random(5)
        val hiss = FloatArray(rate / 2) { (random.nextGaussian() * 0.2).toFloat() }
        val out = MelodyVoice(Melody().withNote(0, 48, 4).withNote(4, 72, 4)).process(hiss, rate)
        assertEquals(8 * step, out.size)
        assertTrue(rms(out, step, 3 * step) > 0.05)
        assertTrue(rms(out, 5 * step, 7 * step) > 0.05)
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
        val melody = Melody().withNote(0, 48).withNote(2, 72).withNote(3, 60, 6).withNote(20, 67, 30)
        val out = FxRenderer.render(clip, Fx.Melody, melody)
        val peak = out.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= SoundProcessor.maxOutputMagnitude)
        assertTrue("peak $peak", peak >= SoundProcessor.maxOutputMagnitude - 1)
    }

    @Test
    fun aBusyMelodyRendersInReasonableTime() {
        // Worst case: 60 BPM (a step is 0.25 s), a 5 s sound, 16 different pitches of 4 steps each.
        val sample = voice(seconds = AudioConfig.MAX_CLIP_SECONDS.toDouble())
        val clip = ShortArray(sample.size) { (sample[it] * 32767).toInt().toShort() }
        var melody = Melody(bpm = Melody.MIN_BPM)
        for (n in 0 until 16) melody = melody.withNote(n * 4, Melody.LOWEST_MIDI + n, 4)
        FxRenderer.render(clip, Fx.Melody, melody) // warm-up
        val start = System.nanoTime()
        val out = FxRenderer.render(clip, Fx.Melody, melody)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("Melody, ${out.size / rate} s output: $ms ms")
        assertTrue("took $ms ms", ms < 4_000)
    }
}
