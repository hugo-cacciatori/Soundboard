package com.example.soundboard.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.ui.Key
import com.example.soundboard.ui.KeyLip
import com.example.soundboard.ui.theme.ButtonPalette
import com.example.soundboard.ui.theme.PaletteColor
import com.example.soundboard.ui.theme.SoundboardTheme

private const val SWATCHES_PER_ROW = 4

@StringRes
fun paletteColorName(id: String): Int = when (id) {
    "red" -> R.string.color_red
    "orange" -> R.string.color_orange
    "amber" -> R.string.color_amber
    "lime" -> R.string.color_lime
    "green" -> R.string.color_green
    "teal" -> R.string.color_teal
    "cyan" -> R.string.color_cyan
    "blue" -> R.string.color_blue
    "indigo" -> R.string.color_indigo
    "violet" -> R.string.color_violet
    "pink" -> R.string.color_pink
    "rose" -> R.string.color_rose
    else -> R.string.color_violet
}

/** The colored key that shows the current color and opens the picker when tapped. */
@Composable
fun ColorSquare(color: PaletteColor, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.editor_color_description,
        stringResource(paletteColorName(color.id)),
    )
    Key(
        face = color.container,
        lip = color.lip,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .size(width = 56.dp, height = 56.dp + KeyLip)
            .semantics { contentDescription = description },
    ) {}
}

/**
 * Small popup anchored to the color square (place it in the same Box). One tap picks a swatch
 * and closes it. No hex input, no sliders.
 */
@Composable
fun ColorPickerPopup(
    expanded: Boolean,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ButtonPalette.colors.chunked(SWATCHES_PER_ROW).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowColors.forEach { swatch ->
                        Swatch(
                            color = swatch,
                            selected = swatch.id == selectedId,
                            onClick = {
                                onSelect(swatch.id)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: PaletteColor, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(paletteColorName(color.id))
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.container)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = color.content,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141019)
@Composable
private fun ColorSquarePreview() {
    SoundboardTheme { ColorSquare(color = ButtonPalette.default, onClick = {}, modifier = Modifier.padding(16.dp)) }
}

@Preview(showBackground = true, backgroundColor = 0xFF141019, widthDp = 300, heightDp = 300)
@Composable
private fun ColorPickerPopupPreview() {
    SoundboardTheme {
        Box(Modifier.padding(16.dp)) {
            ColorSquare(color = ButtonPalette.default, onClick = {})
            ColorPickerPopup(expanded = true, selectedId = ButtonPalette.DEFAULT_ID, onSelect = {}, onDismiss = {})
        }
    }
}
