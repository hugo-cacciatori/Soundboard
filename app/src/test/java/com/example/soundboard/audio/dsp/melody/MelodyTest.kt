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
        assertEquals("0:48:1,5:60:1,63:72:1", NoteCodec.encode(notes))
        assertEquals(notes, NoteCodec.decode(NoteCodec.encode(notes), steps = 64))
        assertEquals(emptyList<Note>(), NoteCodec.decode("", steps = 64))
    }

    @Test
    fun codecSkipsDamagedEntries() {
        val decoded = NoteCodec.decode("4:60,x:1,2:62,70:60,3:99,4:65,9", steps = 64)
        assertEquals(listOf(Note(2, 62), Note(4, 60)), decoded)
    }

    @Test
    fun codecReadsLengthsAndOlderPairs() {
        val notes = listOf(Note(0, 60, 3), Note(4, 62, 1), Note(8, 64, 8))
        assertEquals(notes, NoteCodec.decode(NoteCodec.encode(notes), steps = 64))
        // Rows saved before notes had lengths are 1 step long; overlaps are shortened.
        assertEquals(listOf(Note(0, 60, 4), Note(4, 62, 1)), NoteCodec.decode("0:60:8,4:62", steps = 64))
        assertEquals(listOf(Note(60, 60, 4)), NoteCodec.decode("60:60:9,7:60:0", steps = 64))
    }

    @Test
    fun placingInsideASoundingNoteShortensIt() {
        val melody = Melody().withNote(0, 60, 4).withNote(2, 62)
        assertEquals(listOf(Note(0, 60, 2), Note(2, 62, 1)), melody.notes)
    }

    @Test
    fun aNewNoteStopsBeforeTheNextOne() {
        val melody = Melody().withNote(4, 60).withNote(0, 62, 8)
        assertEquals(listOf(Note(0, 62, 4), Note(4, 60, 1)), melody.notes)
        assertEquals(listOf(Note(60, 62, 4)), Melody().withNote(60, 62, 8).notes)
    }

    @Test
    fun stretchingStopsAtTheNextNoteAndTheEnd() {
        val melody = Melody().withNote(0, 60).withNote(3, 62)
        assertEquals(3, melody.withLength(0, 10).notes[0].length)
        assertEquals(61, melody.withLength(3, 100).notes[1].length)
        assertEquals(1, melody.withNote(0, 60, 3).withLength(0, 0).notes[0].length)
        assertEquals(melody, melody.withLength(7, 4)) // no note starts there
    }

    @Test
    fun noteAtCoversTheWholeLength() {
        val melody = Melody().withNote(4, 60, 3)
        assertEquals(null, melody.noteAt(3))
        assertEquals(Note(4, 60, 3), melody.noteAt(6))
        assertEquals(null, melody.noteAt(7))
    }

    @Test
    fun overlappingNotesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Melody(notes = listOf(Note(0, 60, 3), Note(2, 62))) }
        assertThrows(IllegalArgumentException::class.java) { Melody(notes = listOf(Note(62, 60, 3))) }
    }

    @Test
    fun switchingTimeSignatureShortensNotesThatWouldOverlap() {
        // Bar 1, position 10, 6 steps long; bar 2 starts at step 12 in 6/8, so it shrinks to 2.
        val melody = Melody().withNote(10, 60, 6).withNote(16, 62)
        assertEquals(listOf(Note(10, 60, 2), Note(12, 62, 1)), melody.withTimeSignature(TimeSignature.SixEight).notes)
    }

    @Test
    fun unknownTimeSignatureFallsBack() {
        assertEquals(TimeSignature.SixEight, TimeSignature.byId("6/8"))
        assertEquals(TimeSignature.DEFAULT, TimeSignature.byId("7/8"))
    }
}
