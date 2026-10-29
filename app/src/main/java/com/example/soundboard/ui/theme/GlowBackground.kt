package com.example.soundboard.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.hypot
import kotlin.math.max

/**
 * The app's ground with its coloured glow (SPEC §1): soft light in pad colours, violet from above
 * the top-left corner, teal from the right edge, amber from below the bottom-left. Quiet enough
 * that every pad keeps its contrast. Drawing only: no layout, no recomposition.
 */
fun Modifier.glowBackground(): Modifier = background(Background).drawBehind {
    glow(Color(0xFFC79BFF), alpha = 0.28f, x = 0.10f, y = -0.05f, reach = 0.55f)
    glow(Color(0xFF2ED9C3), alpha = 0.14f, x = 1.05f, y = 0.55f, reach = 0.45f)
    glow(Color(0xFFF5B83D), alpha = 0.12f, x = 0.20f, y = 1.05f, reach = 0.45f)
}

/**
 * A radial glow centred at ([x], [y]) as fractions of the size, fading to nothing at [reach] times
 * the distance to the farthest corner (like a CSS `radial-gradient(circle at x y, ...)`).
 */
private fun DrawScope.glow(color: Color, alpha: Float, x: Float, y: Float, reach: Float) {
    val center = Offset(size.width * x, size.height * y)
    val farthest = hypot(
        max(center.x, size.width - center.x).toDouble(),
        max(center.y, size.height - center.y).toDouble(),
    ).toFloat()
    val radius = farthest * reach
    if (radius <= 0f) return
    drawRect(
        Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
    )
}
