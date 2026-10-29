package com.example.soundboard.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.ui.theme.SoundboardTheme
import kotlin.math.abs
import kotlin.math.sin

private val HandleWidth = 14.dp
private val HandleHeight = 32.dp
private val LineWidth = 3.dp

/**
 * Waveform of the recorded sound with two handles that trim its start and end. Dragging anywhere
 * moves the nearer handle. The parts outside the selection are dimmed.
 *
 * @param waveform peak per slice, each in [0, 1].
 * @param trimStart selection start as a fraction of the sound.
 * @param trimEnd selection end as a fraction of the sound.
 * @param onTrimFinished called when the user lets go of a handle.
 */
@Composable
fun WaveformTrimmer(
    waveform: List<Float>,
    trimStart: Float,
    trimEnd: Float,
    sourceDurationMs: Int,
    selectedDurationMs: Int,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    onTrimFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture handler outlives recompositions; always read the latest values.
    val start by rememberUpdatedState(trimStart)
    val end by rememberUpdatedState(trimEnd)
    val onStart by rememberUpdatedState(onTrimStartChange)
    val onEnd by rememberUpdatedState(onTrimEndChange)
    val onFinished by rememberUpdatedState(onTrimFinished)

    val selectedColor = MaterialTheme.colorScheme.primary
    val dimmedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val shadeColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)
    val handleColor = MaterialTheme.colorScheme.onSurface

    val description = stringResource(
        R.string.trim_description,
        trimStart * sourceDurationMs / 1000f,
        trimEnd * sourceDurationMs / 1000f,
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    // Handles are inset by half their width so they are never clipped at the edges.
                    val inset = HandleWidth.toPx() / 2
                    fun fractionAt(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    var draggingStart = true
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val f = fractionAt(offset.x)
                            draggingStart = abs(f - start) < abs(f - end)
                        },
                        onDragEnd = { onFinished() },
                        onDragCancel = { onFinished() },
                    ) { change, _ ->
                        change.consume()
                        val f = fractionAt(change.position.x)
                        if (draggingStart) onStart(f) else onEnd(f)
                    }
                },
        ) {
            val inset = HandleWidth.toPx() / 2
            val usable = size.width - 2 * inset
            val startX = inset + trimStart * usable
            val endX = inset + trimEnd * usable
            val midY = size.height / 2
            val maxBar = size.height / 2 - 10.dp.toPx()

            // Bars, mirrored around the middle.
            if (waveform.isNotEmpty()) {
                val slot = usable / waveform.size
                val barWidth = (slot * 0.6f).coerceAtLeast(1f)
                waveform.forEachIndexed { i, peak ->
                    val x = inset + (i + 0.5f) * slot
                    val half = (peak * maxBar).coerceAtLeast(1.dp.toPx())
                    drawRoundRect(
                        color = if (x in startX..endX) selectedColor else dimmedColor,
                        topLeft = Offset(x - barWidth / 2, midY - half),
                        size = Size(barWidth, half * 2),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
            }

            // Shade the trimmed-away parts.
            drawRect(shadeColor, topLeft = Offset.Zero, size = Size(startX, size.height))
            drawRect(shadeColor, topLeft = Offset(endX, 0f), size = Size(size.width - endX, size.height))

            // Handles: a full-height line with a grip in the middle.
            val handleW = HandleWidth.toPx()
            val handleH = HandleHeight.toPx()
            for (x in floatArrayOf(startX, endX)) {
                drawRect(
                    color = handleColor,
                    topLeft = Offset(x - LineWidth.toPx() / 2, 0f),
                    size = Size(LineWidth.toPx(), size.height),
                )
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset(x - handleW / 2, midY - handleH / 2),
                    size = Size(handleW, handleH),
                    cornerRadius = CornerRadius(handleW / 2),
                )
            }
        }
        Text(
            text = stringResource(
                R.string.trim_selection,
                selectedDurationMs / 1000f,
                sourceDurationMs / 1000f,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141019, widthDp = 360)
@Composable
private fun WaveformTrimmerPreview() {
    val fake = List(96) { i -> (0.15f + 0.85f * abs(sin(i / 7f)) * (if (i in 20..75) 1f else 0.15f)) }
    SoundboardTheme {
        WaveformTrimmer(
            waveform = fake,
            trimStart = 0.18f,
            trimEnd = 0.82f,
            sourceDurationMs = 4_200,
            selectedDurationMs = 2_690,
            onTrimStartChange = {},
            onTrimEndChange = {},
            onTrimFinished = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
