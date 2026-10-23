package com.example.soundboard.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.ui.theme.ButtonPalette
import com.example.soundboard.ui.theme.PaletteColor
import com.example.soundboard.ui.theme.SoundboardTheme

/** One pad: tap plays, long press edits. Stateless; the pressed visuals are local UI state. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundButton(
    name: String,
    color: PaletteColor,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "pad-scale")
    // Pressed pads flash lighter; the dark content color keeps its contrast on the lighter fill.
    val container by animateColorAsState(
        targetValue = if (pressed) lerp(color.container, Color.White, 0.4f) else color.container,
        label = "pad-color",
    )

    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.button_play_label),
                onLongClickLabel = stringResource(R.string.button_edit_label),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            color = color.content,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "Pad", showBackground = true, backgroundColor = 0xFF121218)
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

@Preview(name = "Pad, long name", showBackground = true, backgroundColor = 0xFF121218)
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
