package com.example.soundboard.ui.launch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.soundboard.ui.theme.ButtonPalette

/**
 * The title card's mark until the app icon exists (M1.8): a dot sending out three arcs of sound,
 * in palette colors. Flat strokes only: the mark must never look like a pad or a button (SPEC §1).
 * Drawn on a 150 x 120 grid.
 */
@Composable
fun SoundMark(modifier: Modifier = Modifier) {
    val violet = ButtonPalette.byId("violet").container
    val waves = listOf(
        Wave(centerX = 42f, radius = 34f, sweep = 90f, color = violet),
        Wave(centerX = 46f, radius = 58f, sweep = 87f, color = ButtonPalette.byId("amber").container),
        Wave(centerX = 51f, radius = 80f, sweep = 85f, color = ButtonPalette.byId("teal").container),
    )
    Canvas(modifier.size(150.dp, 120.dp)) {
        val unit = size.width / 150f
        val y = 60f * unit
        drawCircle(violet, radius = 11f * unit, center = Offset(40f * unit, y))
        for (wave in waves) {
            val r = wave.radius * unit
            // An arc facing right, centred near the dot.
            drawArc(
                color = wave.color,
                startAngle = -wave.sweep / 2,
                sweepAngle = wave.sweep,
                useCenter = false,
                topLeft = Offset(wave.centerX * unit - r, y - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = 9f * unit, cap = StrokeCap.Round),
            )
        }
    }
}

private class Wave(val centerX: Float, val radius: Float, val sweep: Float, val color: Color)
