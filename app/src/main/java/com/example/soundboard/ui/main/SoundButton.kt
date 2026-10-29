package com.example.soundboard.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.ui.Key
import com.example.soundboard.ui.PadCorner
import com.example.soundboard.ui.theme.ButtonPalette
import com.example.soundboard.ui.theme.PaletteColor
import com.example.soundboard.ui.theme.SoundboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How a pad arrives on the board. */
sealed interface PadAppear {
    /** Already there. */
    data object None : PadAppear

    /** Part of the board's entrance at launch: pops in after [delayMillis] (the stagger). */
    data class Entrance(val delayMillis: Int) : PadAppear

    /** Just saved: drops into its slot with a small bounce. */
    data object Drop : PadAppear
}

/**
 * One pad: a key in its palette color. Tap plays (on release, at once), long press edits. Each
 * tap sends a ring in the pad's color outwards. [appear] animates the pad's arrival; it waits
 * while [held] is true (the title card is still up).
 */
@Composable
fun SoundButton(
    name: String,
    color: PaletteColor,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    appear: PadAppear = PadAppear.None,
    held: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    // 1 = no ring showing.
    val ring = remember { Animatable(1f) }
    val arrival = remember { Animatable(if (appear == PadAppear.None) 1f else 0f) }
    LaunchedEffect(appear, held) {
        if (held || arrival.value == 1f) return@LaunchedEffect
        when (appear) {
            PadAppear.None -> arrival.snapTo(1f)
            is PadAppear.Entrance -> {
                delay(appear.delayMillis.toLong())
                arrival.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            }
            PadAppear.Drop -> arrival.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
    val ringColor = color.container

    Key(
        face = color.container,
        lip = color.lip,
        contentColor = color.content,
        onClick = {
            onClick()
            scope.launch {
                ring.snapTo(0f)
                ring.animateTo(1f, tween(RING_MILLIS, easing = FastOutSlowInEasing))
            }
        },
        onLongClick = onLongClick,
        onClickLabel = stringResource(R.string.button_play_label),
        onLongClickLabel = stringResource(R.string.button_edit_label),
        modifier = modifier
            .graphicsLayer {
                val t = arrival.value
                alpha = t.coerceIn(0f, 1f)
                if (appear == PadAppear.Drop) {
                    translationY = (1f - t) * -40.dp.toPx()
                } else {
                    val s = 0.6f + 0.4f * t
                    scaleX = s
                    scaleY = s
                }
            }
            .drawBehind {
                // Read the ring here so it only invalidates drawing, not composition.
                val p = ring.value
                if (p >= 1f) return@drawBehind
                val grow = 0.2f * p
                val dx = size.width * grow / 2
                val dy = size.height * grow / 2
                val stroke = 3.dp.toPx()
                drawRoundRect(
                    color = ringColor.copy(alpha = 0.9f * (1f - p)),
                    topLeft = Offset(-dx, -dy),
                    size = Size(size.width + 2 * dx, size.height + 2 * dy),
                    cornerRadius = CornerRadius(PadCorner.toPx() * (1 + grow)),
                    style = Stroke(stroke),
                )
            }
            .aspectRatio(1f),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** The playing ring grows about 20 % and fades over this long. */
private const val RING_MILLIS = 700

@Preview(name = "Pad", showBackground = true, backgroundColor = 0xFF141019)
@Composable
private fun SoundButtonPreview() {
    SoundboardTheme {
        SoundButton(
            name = "Airhorn",
            color = ButtonPalette.byId("amber"),
            onClick = {},
            onLongClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Pad, long name", showBackground = true, backgroundColor = 0xFF141019)
@Composable
private fun SoundButtonLongNamePreview() {
    SoundboardTheme {
        SoundButton(
            name = "A really long button name that has to be cut off",
            color = ButtonPalette.byId("teal"),
            onClick = {},
            onLongClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
