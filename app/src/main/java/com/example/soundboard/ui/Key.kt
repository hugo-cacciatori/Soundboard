package com.example.soundboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.soundboard.ui.theme.PrimaryLip

/** Height of the darker lip under a key's face. */
val KeyLip = 6.dp

/** Pads' corner radius. */
val PadCorner = 28.dp

/**
 * A physical key ("Pocket keys", SPEC §1): a [face] sitting on a darker [lip]. While the finger is
 * down the face sinks onto the lip and lightens; [onClick] fires on release. The lip adds [lipHeight]
 * below the face, inside this composable's bounds.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Key(
    face: Color,
    lip: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(PadCorner),
    contentColor: Color = Color(0xFF14121A),
    lipHeight: Dp = KeyLip,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val sink by animateDpAsState(if (pressed) lipHeight else 0.dp, tween(KEY_PRESS_MILLIS), label = "key-sink")
    // Lighter while pressed; the dark content color keeps its contrast on the lighter face.
    val faceColor by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
            pressed -> lerp(face, Color.White, 0.3f)
            else -> face
        },
        tween(KEY_PRESS_MILLIS),
        label = "key-face",
    )
    val lipColor = if (enabled) lip else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .background(lipColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(bottom = lipHeight),
        // A sized key (pad, "+") gives its face the whole size; a wrapping key (Save) wraps its content.
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, sink.roundToPx()) }
                .background(faceColor, shape),
            contentAlignment = contentAlignment,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                content()
            }
        }
    }
}

/** The app's primary action as a violet key (Save, "+", "Create your first button"). */
@Composable
fun PrimaryKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Key(
        face = MaterialTheme.colorScheme.primary,
        lip = PrimaryLip,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        lipHeight = 5.dp,
        enabled = enabled,
        content = content,
    )
}

/** Finger-down response: fast, so the key feels physical and never delays the sound. */
const val KEY_PRESS_MILLIS = 60
