package com.example.soundboard.ui.melody

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.audio.dsp.melody.Melody
import com.example.soundboard.audio.dsp.melody.TimeSignature
import com.example.soundboard.data.SavedMelody
import com.example.soundboard.ui.theme.SoundboardTheme

/** Top bar shared by the melody screens: back, a title, then [actions]. */
@Composable
fun MelodyToolbar(
    onBack: () -> Unit,
    title: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.editor_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            title()
            actions()
        }
    }
}

/**
 * Saved melodies, one per row, plus "New melody". Tapping a row picks it for the button; the
 * pencil opens it in the melody editor.
 *
 * @param selectedId the button's current melody, marked in the list.
 */
@Composable
fun MelodyPickerScreen(
    state: MelodyPickerUiState,
    selectedId: String?,
    pendingDelete: PendingDelete?,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onSelect: (SavedMelody) -> Unit,
    onEdit: (SavedMelody) -> Unit,
    onDelete: (SavedMelody) -> Unit,
    onDeleteConfirmed: (SavedMelody) -> Unit,
    onDeleteDismissed: () -> Unit,
) {
    Scaffold(
        topBar = {
            MelodyToolbar(onBack = onBack, title = {
                Text(
                    text = stringResource(R.string.melodies_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.melodies_new), modifier = Modifier.padding(start = 8.dp))
                }
            }
            val melodies = state.melodies
            if (melodies != null && melodies.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.melodies_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                    )
                }
            }
            items(melodies.orEmpty(), key = { it.id }) { saved ->
                MelodyRow(
                    saved = saved,
                    selected = saved.id == selectedId,
                    onClick = { onSelect(saved) },
                    onEdit = { onEdit(saved) },
                    onDelete = { onDelete(saved) },
                )
            }
        }
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = onDeleteDismissed,
            title = { Text(stringResource(R.string.melody_delete_title, pending.melody.name)) },
            text = {
                Text(
                    if (pending.usageCount > 0) {
                        pluralStringResource(R.plurals.melody_delete_used, pending.usageCount, pending.usageCount)
                    } else {
                        stringResource(R.string.melody_delete_unused)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { onDeleteConfirmed(pending.melody) }) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismissed) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun MelodyRow(
    saved: SavedMelody,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (selected) R.drawable.ic_check else R.drawable.ic_fx_melody),
                contentDescription = if (selected) stringResource(R.string.melody_selected) else null,
                tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = saved.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = melodySummary(saved.melody),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.melody_edit, saved.name),
                    tint = if (selected) colors.onPrimaryContainer else colors.onSurface,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.melody_delete, saved.name),
                    tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** "4/4 · 120 BPM · 7 notes". */
@Composable
fun melodySummary(melody: Melody): String = stringResource(
    R.string.melody_summary,
    melody.timeSignature.id,
    melody.bpm,
    pluralStringResource(R.plurals.melody_note_count, melody.notes.size, melody.notes.size),
)

@Preview(showBackground = true, backgroundColor = 0xFF121218, widthDp = 360, heightDp = 480)
@Composable
private fun MelodyPickerPreview() {
    val melodies = listOf(
        SavedMelody("a", "Melody 1", Melody().withNote(0, 60).withNote(4, 64)),
        SavedMelody("b", "Happy birthday", Melody(TimeSignature.SixEight, bpm = 96).withNote(0, 67)),
        SavedMelody("c", "Melody 3", Melody()),
    )
    SoundboardTheme {
        MelodyPickerScreen(
            state = MelodyPickerUiState(melodies),
            selectedId = "b",
            pendingDelete = null,
            onBack = {},
            onNew = {},
            onSelect = {},
            onEdit = {},
            onDelete = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
        )
    }
}
