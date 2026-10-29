package com.example.soundboard.ui.editor

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.audio.dsp.fx.Fx
import com.example.soundboard.ui.theme.SoundboardTheme

private const val FX_PER_ROW = 3

@StringRes
fun fxName(fx: Fx): Int = when (fx) {
    Fx.None -> R.string.fx_none
    Fx.Mouseyfier -> R.string.fx_mouseyfier
    Fx.Robot -> R.string.fx_robot
    Fx.Melody -> R.string.fx_melody
}

@DrawableRes
fun fxIcon(fx: Fx): Int = when (fx) {
    Fx.None -> R.drawable.ic_fx_none
    Fx.Mouseyfier -> R.drawable.ic_fx_mouse
    Fx.Robot -> R.drawable.ic_fx_robot
    Fx.Melody -> R.drawable.ic_fx_melody
}

/** The square that shows the current effect's icon and opens the picker when tapped. */
@Composable
fun FxSquare(fx: Fx, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.editor_fx_description, stringResource(fxName(fx)))
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(fxIcon(fx)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * Small popup anchored to the effect square (place it in the same Box), like the color picker.
 * One tap picks an effect and closes it.
 */
@Composable
fun FxPickerPopup(
    expanded: Boolean,
    selected: Fx,
    onSelect: (Fx) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Fx.entries.chunked(FX_PER_ROW).forEach { rowFx ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowFx.forEach { fx ->
                        FxTile(
                            fx = fx,
                            selected = fx == selected,
                            onClick = {
                                onSelect(fx)
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
private fun FxTile(fx: Fx, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(fxName(fx))
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = name
                this.selected = selected
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) colors.primaryContainer else colors.surfaceContainerHighest)
                .then(
                    if (selected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(14.dp)) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(fxIcon(fx)),
                contentDescription = null,
                tint = if (selected) colors.onPrimaryContainer else colors.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141019)
@Composable
private fun FxSquarePreview() {
    SoundboardTheme { FxSquare(fx = Fx.Mouseyfier, onClick = {}, modifier = Modifier.padding(16.dp)) }
}

@Preview(showBackground = true, backgroundColor = 0xFF141019, widthDp = 320, heightDp = 240)
@Composable
private fun FxPickerPopupPreview() {
    SoundboardTheme {
        Box(Modifier.padding(16.dp)) {
            FxSquare(fx = Fx.Mouseyfier, onClick = {})
            FxPickerPopup(expanded = true, selected = Fx.Mouseyfier, onSelect = {}, onDismiss = {})
        }
    }
}
