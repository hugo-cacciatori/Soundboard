package com.example.soundboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.soundboard.R

/**
 * Bricolage Grotesque, one variable font bundled in `res/font` (OFL, licence in `assets/licenses`).
 * Each weight is an instance of the same file; [opticalSize] tunes the letter shapes for the size
 * (large sizes get tighter, more characterful forms). Android before 8.0 shows the default instance.
 */
@OptIn(ExperimentalTextApi::class)
private fun bricolage(opticalSize: Float) = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.bricolage_grotesque,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

/** Text and UI sizes. */
val Bricolage = bricolage(opticalSize = 14f)

/** Display sizes: the app name, the first-run headline. */
val BricolageDisplay = bricolage(opticalSize = 72f)

private val Base = Typography()

private fun TextStyle.text(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = copy(
    fontFamily = Bricolage,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun TextStyle.display(size: Int, line: Int) = copy(
    fontFamily = BricolageDisplay,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.02 * size).sp,
)

val Typography = Typography(
    displayLarge = Base.displayLarge.display(48, 52),
    displayMedium = Base.displayMedium.display(40, 44),
    // The app name on the title card.
    displaySmall = Base.displaySmall.display(40, 44),
    headlineLarge = Base.headlineLarge.display(34, 37),
    // The first-run headline.
    headlineMedium = Base.headlineMedium.display(34, 37),
    headlineSmall = Base.headlineSmall.text(24, 30, 700),
    // The app name in the main toolbar.
    titleLarge = Base.titleLarge.copy(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    // Screen titles and pad names: bold and compact so two lines fit comfortably on a pad.
    titleMedium = Base.titleMedium.text(16, 19, 700),
    titleSmall = Base.titleSmall.text(15, 20, 700),
    bodyLarge = Base.bodyLarge.text(15, 22, 500),
    bodyMedium = Base.bodyMedium.text(14, 20, 500),
    bodySmall = Base.bodySmall.text(12, 16, 500),
    labelLarge = Base.labelLarge.text(16, 20, 700),
    // Section labels (Name, Color, Sound, Effect).
    labelMedium = Base.labelMedium.text(13, 18, 600),
    // The version line.
    labelSmall = Base.labelSmall.text(12, 16, 500),
)
