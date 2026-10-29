package com.example.soundboard.ui.melody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyNamesTest {
    private val format = { n: Int -> "Melody $n" }

    @Test
    fun countsUp() {
        assertEquals("Melody 1", nextMelodyName(emptyList(), format))
        assertEquals("Melody 3", nextMelodyName(listOf("Melody 1", "Melody 2"), format))
    }

    @Test
    fun skipsTakenNames() {
        // "Melody 1" was deleted, so there are two melodies, but "Melody 3" is taken.
        assertEquals("Melody 4", nextMelodyName(listOf("Melody 2", "Melody 3"), format))
        assertEquals("Melody 2", nextMelodyName(listOf("Chorus"), format))
    }

    @Test
    fun noteNames() {
        assertEquals("C4", noteName(60))
        assertEquals("C3", noteName(48))
        assertEquals("A♯3", noteName(58))
        assertEquals("C5", noteName(72))
        assertTrue(isBlackKey(61))
        assertFalse(isBlackKey(64))
    }
}
