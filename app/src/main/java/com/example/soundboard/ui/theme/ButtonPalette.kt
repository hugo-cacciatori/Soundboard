package com.example.soundboard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * One swatch: a pad face, the darker lip the face sits on, and the content color used for the
 * name on top of it. The ARGB ints are kept so contrast can be checked in plain JVM tests.
 */
@Immutable
class PaletteColor(val id: String, val containerArgb: Int, val lipArgb: Int, val contentArgb: Int) {
    val container: Color get() = Color(containerArgb)
    val lip: Color get() = Color(lipArgb)
    val content: Color get() = Color(contentArgb)
}

/**
 * The button palette ("Pocket keys", SPEC §1). Faces share lightness (about OKLCH L 0.78) at a
 * little more chroma than before, hue steps around the wheel, so they look like one family and all
 * stand out on [Background]. Each lip is the same hue, darker, so a pad reads as a physical key.
 * Every swatch uses the same near-black content color, giving a contrast of at least 8:1 (the spec
 * asks for 4.5:1).
 */
object ButtonPalette {
    private const val CONTENT = 0xFF14121A.toInt()

    val colors: List<PaletteColor> = listOf(
        PaletteColor("red", 0xFFFF8A80.toInt(), 0xFFC2453F.toInt(), CONTENT),
        PaletteColor("orange", 0xFFFF9E4F.toInt(), 0xFFC2621A.toInt(), CONTENT),
        PaletteColor("amber", 0xFFF5B83D.toInt(), 0xFFB87F0A.toInt(), CONTENT),
        PaletteColor("lime", 0xFFB5D654.toInt(), 0xFF7A9A1C.toInt(), CONTENT),
        PaletteColor("green", 0xFF5FDB8A.toInt(), 0xFF23A052.toInt(), CONTENT),
        PaletteColor("teal", 0xFF2ED9C3.toInt(), 0xFF0A9C8A.toInt(), CONTENT),
        PaletteColor("cyan", 0xFF3CCFF0.toInt(), 0xFF0B90B0.toInt(), CONTENT),
        PaletteColor("blue", 0xFF7AB6FF.toInt(), 0xFF3A76D0.toInt(), CONTENT),
        PaletteColor("indigo", 0xFFA3A8FF.toInt(), 0xFF5F63D6.toInt(), CONTENT),
        PaletteColor("violet", 0xFFC79BFF.toInt(), 0xFF8A57D6.toInt(), CONTENT),
        PaletteColor("pink", 0xFFF58BDD.toInt(), 0xFFB8479F.toInt(), CONTENT),
        PaletteColor("rose", 0xFFFF8FAE.toInt(), 0xFFC64B6E.toInt(), CONTENT),
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
