package com.example.soundboard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * One swatch: a pad color and the content color used for the name on top of it.
 * The ARGB ints are kept so contrast can be checked in plain JVM tests.
 */
@Immutable
class PaletteColor(val id: String, val containerArgb: Int, val contentArgb: Int) {
    val container: Color get() = Color(containerArgb)
    val content: Color get() = Color(contentArgb)
}

/**
 * The button palette. Swatches share lightness and chroma (OKLCH L 0.78, C <= 0.15, hue steps
 * around the wheel), so they look like one family and all stand out on [Background]. Every swatch
 * uses the same near-black content color, giving a contrast of at least 8:1 (the spec asks for 4.5:1).
 */
object ButtonPalette {
    private const val CONTENT = 0xFF14141C.toInt()

    val colors: List<PaletteColor> = listOf(
        PaletteColor("red", 0xFFFF958E.toInt(), CONTENT),
        PaletteColor("orange", 0xFFFF9B5A.toInt(), CONTENT),
        PaletteColor("amber", 0xFFE7AC2A.toInt(), CONTENT),
        PaletteColor("lime", 0xFFA8C64E.toInt(), CONTENT),
        PaletteColor("green", 0xFF67D283.toInt(), CONTENT),
        PaletteColor("teal", 0xFF11D3BE.toInt(), CONTENT),
        PaletteColor("cyan", 0xFF12CDE9.toInt(), CONTENT),
        PaletteColor("blue", 0xFF76BEFF.toInt(), CONTENT),
        PaletteColor("indigo", 0xFF9FB3FE.toInt(), CONTENT),
        PaletteColor("violet", 0xFFC7A2FF.toInt(), CONTENT),
        PaletteColor("pink", 0xFFF390DA.toInt(), CONTENT),
        PaletteColor("rose", 0xFFFE91AC.toInt(), CONTENT),
    )

    const val DEFAULT_ID = "violet"

    val default: PaletteColor = colors.first { it.id == DEFAULT_ID }

    /** Falls back to the default color for unknown ids. */
    fun byId(id: String): PaletteColor = colors.firstOrNull { it.id == id } ?: default

    /** WCAG contrast ratio (1..21) between two opaque ARGB colors. */
    fun contrastRatio(argbA: Int, argbB: Int): Double {
        val a = relativeLuminance(argbA)
        val b = relativeLuminance(argbB)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
