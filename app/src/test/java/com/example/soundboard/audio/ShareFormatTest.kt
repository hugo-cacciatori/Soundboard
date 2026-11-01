package com.example.soundboard.audio

import kotlin.math.abs
import kotlin.math.log10
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFormatTest {
    @Test
    fun fileNameKeepsAPlainName() {
        assertEquals("Airhorn.mp3", ShareFormat.fileName("Airhorn", "Sound"))
        assertEquals("Bonjour à toi.mp3", ShareFormat.fileName("  Bonjour   à toi ", "Sound"))
    }

    @Test
    fun fileNameDropsPathAndReservedCharacters() {
        val name = ShareFormat.fileName("../a/b\\c:d*e?f\"g<h>i|j\u0007", "Sound")
        assertTrue(name, name.none { it in "/\\:*?\"<>|" || it.isISOControl() })
        assertTrue(name, !name.startsWith("."))
    }

    @Test
    fun fileNameFallsBackWhenNothingIsLeft() {
        assertEquals("Sound.mp3", ShareFormat.fileName("", "Sound"))
        assertEquals("Sound.mp3", ShareFormat.fileName(" /// ", "Sound"))
        assertEquals("Sound.mp3", ShareFormat.fileName("...", "Sound"))
    }

    @Test
    fun fileNameIsShort() {
        val name = ShareFormat.fileName("x".repeat(500), "Sound")
        assertEquals(ShareFormat.MAX_NAME_CHARS + ".mp3".length, name.length)
    }

    @Test
    fun headroomPutsThePeakAtMinusOneDb() {
        val clip = ShortArray(4800) { (32400 * kotlin.math.sin(it * 0.05)).toInt().toShort() }
        val out = ShareFormat.withHeadroom(clip)
        val peak = out.maxOf { abs(it.toInt()) }
        val db = 20 * log10(peak / 32767.0)
        assertEquals(AudioConfig.SHARE_PEAK_DBFS, db, 0.01)
    }

    @Test
    fun headroomLeavesSilenceAlone() {
        val silence = ShortArray(100)
        assertArrayEquals(silence, ShareFormat.withHeadroom(silence))
    }
}
