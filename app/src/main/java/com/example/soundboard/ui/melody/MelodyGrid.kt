package com.example.soundboard.ui.melody

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soundboard.R
import com.example.soundboard.audio.dsp.melody.Melody
import kotlin.math.roundToInt

private val Cell = 36.dp
private val KeyWidth = 52.dp
private val HeaderHeight = 24.dp
private val NoteInset = 3.dp
private val NoteCorner = 6.dp

/** Pitch rows, highest at the top. */
private const val ROWS = Melody.HIGHEST_MIDI - Melody.LOWEST_MIDI + 1

/** The grid opens scrolled so this pitch is the top row (the middle of a typical voice). */
private const val INITIAL_TOP_MIDI = 67

/**
 * Piano-roll grid: pitches as rows (keys pinned on the left, black keys shaded), sixteenth-note
 * steps as columns (bar numbers on top; step, beat and bar lines). Scrolls both ways. The pinned
 * key column and header follow the scroll in the draw phase, so scrolling never recomposes.
 *
 * @param playhead position in steps while playing, or null.
 */
@Composable
fun MelodyGrid(
    melody: Melody,
    playhead: Float?,
    onCellTap: (step: Int, midi: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val cellPx = with(density) { Cell.toPx() }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val measurer = rememberTextMeasurer()
    val onTap by rememberUpdatedState(onCellTap)
    val steps = melody.steps
    val stepsPerBar = melody.timeSignature.stepsPerBar
    val stepsPerBeat = melody.timeSignature.stepsPerBeat
    val keyStyle = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)
    val cStyle = keyStyle.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
    val barStyle = TextStyle(fontSize = 12.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
    val description = stringResource(R.string.melody_grid_description, melodySummary(melody))

    LaunchedEffect(Unit) {
        vertical.scrollTo(((Melody.HIGHEST_MIDI - INITIAL_TOP_MIDI) * cellPx).roundToInt())
    }
    // Keep the playhead in view while playing.
    LaunchedEffect(playhead) {
        val x = (playhead ?: return@LaunchedEffect) * cellPx
        val viewport = horizontal.viewportSize
        if (viewport > 0 && (x < horizontal.value || x > horizontal.value + viewport - cellPx)) {
            horizontal.animateScrollTo((x - cellPx).roundToInt().coerceAtLeast(0))
        }
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(HeaderHeight)) {
            Box(Modifier.width(KeyWidth))
            // Bar numbers.
            Canvas(Modifier.fillMaxSize().clipToBounds()) {
                translate(left = -horizontal.value.toFloat()) {
                    for (bar in 0 until Melody.BARS) {
                        val x = bar * stepsPerBar * cellPx
                        drawLine(colors.outline, Offset(x, size.height * 0.4f), Offset(x, size.height), strokeWidth = 2f)
                        drawText(measurer, "${bar + 1}", Offset(x + 6.dp.toPx(), 2.dp.toPx()), barStyle)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // Keys.
            Canvas(Modifier.width(KeyWidth).fillMaxHeight().clipToBounds()) {
                translate(top = -vertical.value.toFloat()) {
                    for (row in 0 until ROWS) {
                        val midi = Melody.HIGHEST_MIDI - row
                        val top = row * cellPx
                        drawRect(
                            color = if (isBlackKey(midi)) colors.surfaceContainerLowest else colors.surfaceContainerHighest,
                            topLeft = Offset(0f, top),
                            size = Size(size.width, cellPx - 1f),
                        )
                        val c = midi % 12 == 0
                        val label = measurer.measure(noteName(midi), if (c) cStyle else keyStyle)
                        drawText(
                            label,
                            topLeft = Offset(6.dp.toPx(), top + (cellPx - label.size.height) / 2),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(vertical)
                    .horizontalScroll(horizontal)
                    .semantics { contentDescription = description },
            ) {
                Canvas(
                    Modifier
                        .size(Cell * steps, Cell * ROWS)
                        .pointerInput(steps) {
                            detectTapGestures { offset ->
                                val step = (offset.x / cellPx).toInt()
                                val row = (offset.y / cellPx).toInt()
                                if (step in 0 until steps && row in 0 until ROWS) onTap(step, Melody.HIGHEST_MIDI - row)
                            }
                        },
                ) {
                    val width = steps * cellPx
                    for (row in 0 until ROWS) {
                        val midi = Melody.HIGHEST_MIDI - row
                        drawRect(
                            color = if (isBlackKey(midi)) colors.surfaceContainerLow else colors.surfaceContainer,
                            topLeft = Offset(0f, row * cellPx),
                            size = Size(width, cellPx),
                        )
                        // A stronger line under every C marks the octaves.
                        val y = (row + 1) * cellPx
                        drawLine(
                            color = if (midi % 12 == 0) colors.outline else colors.outlineVariant.copy(alpha = 0.4f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f,
                        )
                    }
                    for (step in 0..steps) {
                        val x = step * cellPx
                        val (color, stroke) = when {
                            step % stepsPerBar == 0 -> colors.outline to 2.dp.toPx()
                            step % stepsPerBeat == 0 -> colors.outlineVariant to 1.dp.toPx()
                            else -> colors.outlineVariant.copy(alpha = 0.3f) to 1f
                        }
                        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
                    }
                    val inset = NoteInset.toPx()
                    val corner = CornerRadius(NoteCorner.toPx())
                    for (note in melody.notes) {
                        val row = Melody.HIGHEST_MIDI - note.midi
                        drawRoundRect(
                            color = colors.primary,
                            topLeft = Offset(note.step * cellPx + inset, row * cellPx + inset),
                            size = Size(cellPx - 2 * inset, cellPx - 2 * inset),
                            cornerRadius = corner,
                        )
                    }
                    playhead?.let {
                        val x = it * cellPx
                        drawLine(colors.tertiary, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                    }
                }
            }
        }
    }
}
