package com.example.soundboard.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonPaletteTest {
    @Test
    fun contentColorIsLegibleOnEverySwatch() {
        for (c in ButtonPalette.colors) {
            val ratio = ButtonPalette.contrastRatio(c.containerArgb, c.contentArgb)
            assertTrue("${c.id}: contrast $ratio < 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun swatchesStandOutOnTheAppBackground() {
        for (c in ButtonPalette.colors) {
            val ratio = ButtonPalette.contrastRatio(c.containerArgb, BACKGROUND_ARGB)
            assertTrue("${c.id}: contrast against background $ratio < 3", ratio >= 3.0)
        }
    }

    @Test
    fun idsAreUniqueAndSizeFitsThePicker() {
        val ids = ButtonPalette.colors.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.size in 8..12)
    }

    @Test
    fun defaultExistsAndUnknownIdsFallBackToIt() {
        assertTrue(ButtonPalette.colors.any { it.id == ButtonPalette.DEFAULT_ID })
        assertEquals(ButtonPalette.default.id, ButtonPalette.byId("does-not-exist").id)
    }

    @Test
    fun contrastRatioMatchesKnownValues() {
        assertEquals(21.0, ButtonPalette.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ButtonPalette.contrastRatio(0xFF123456.toInt(), 0xFF123456.toInt()), 0.0001)
    }
}
