package com.example.soundboard.audio.dsp.melody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyTest {
    @Test
    fun fourBarsOfSixteenths() {
        assertEquals(64, Melody(TimeSignature.FourFour).steps)
        assertEquals(48, Melody(TimeSignature.SixEight).steps)
    }

    @Test
    fun stepLengthFollowsTheBeat() {
        // 120 BPM: a quarter note is 0.5 s, so a sixteenth is 0.125 s.
        assertEquals(0.125, Melody(TimeSignature.FourFour, bpm = 120).stepSeconds, 1e-12)
        // 6/8 at 120 BPM: a dotted quarter is 0.5 s and holds 6 sixteenths.
        assertEquals(0.5 / 6, Melody(TimeSignature.SixEight, bpm = 120).stepSeconds, 1e-12)
    }

    @Test
    fun aNewNoteReplacesTheOneOnTheSameStep() {
        val melody = Melody().withNote(4, 60).withNote(0, 62).withNote(4, 67)
        assertEquals(listOf(Note(0, 62), Note(4, 67)), melody.notes)
    }

    @Test
    fun removingANote() {
        val melody = Melody().withNote(4, 60).withNote(8, 62).withoutNote(4)
        assertEquals(listOf(Note(8, 62)), melody.notes)
        assertTrue(Melody().withNote(3, 60).withoutNote(3).isEmpty)
    }

    @Test
    fun switchingToSixEightKeepsNotesThatFitInTheirBar() {
        // Bar 2 (steps 16..31): position 0 and 11 fit in a 12-step bar, position 14 does not.
        val melody = Melody().withNote(16, 60).withNote(27, 62).withNote(30, 64)
        assertEquals(1, melody.notesDroppedBy(TimeSignature.SixEight))
        val switched = melody.withTimeSignature(TimeSignature.SixEight)
        assertEquals(listOf(Note(12, 60), Note(23, 62)), switched.notes)
    }

    @Test
    fun switchingBackToFourFourKeepsEveryNote() {
        val melody = Melody(TimeSignature.SixEight).withNote(0, 60).withNote(47, 72)
        assertEquals(0, melody.notesDroppedBy(TimeSignature.FourFour))
        assertEquals(listOf(Note(0, 60), Note(59, 72)), melody.withTimeSignature(TimeSignature.FourFour).notes)
    }

    @Test
    fun tempoIsClamped() {
        assertEquals(Melody.MIN_BPM, Melody().withBpm(10).bpm)
        assertEquals(Melody.MAX_BPM, Melody().withBpm(999).bpm)
    }

    @Test
    fun outOfRangeNotesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Melody().withNote(64, 60) }
        assertThrows(IllegalArgumentException::class.java) { Melody().withNote(0, Melody.HIGHEST_MIDI + 1) }
    }

    @Test
    fun codecRoundTrip() {
        val notes = listOf(Note(0, 48), Note(5, 60), Note(63, 72))
        assertEquals("0:48,5:60,63:72", NoteCodec.encode(notes))
        assertEquals(notes, NoteCodec.decode(NoteCodec.encode(notes), steps = 64))
        assertEquals(emptyList<Note>(), NoteCodec.decode("", steps = 64))
    }

    @Test
    fun codecSkipsDamagedEntries() {
        val decoded = NoteCodec.decode("4:60,x:1,2:62,70:60,3:99,4:65,9", steps = 64)
        assertEquals(listOf(Note(2, 62), Note(4, 60)), decoded)
    }

    @Test
    fun unknownTimeSignatureFallsBack() {
        assertEquals(TimeSignature.SixEight, TimeSignature.byId("6/8"))
        assertEquals(TimeSignature.DEFAULT, TimeSignature.byId("7/8"))
    }
}
