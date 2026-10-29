package com.example.soundboard.ui.melody

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.soundboard.R
import com.example.soundboard.data.SavedMelody

/**
 * Stateful wrapper around [MelodyPickerScreen].
 *
 * @param onPicked a melody was chosen for the button (picked, or just created).
 * @param onOpenEditor opens the melody editor for a melody.
 * @param onPickedAndDone after a row was picked: return to the button editor.
 */
@Composable
fun MelodyPickerRoute(
    viewModel: MelodyPickerViewModel,
    selectedId: String?,
    onPicked: (SavedMelody) -> Unit,
    onOpenEditor: (SavedMelody) -> Unit,
    onBack: () -> Unit,
    onPickedAndDone: () -> Unit,
) {
    val resources = LocalResources.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    MelodyPickerScreen(
        state = state,
        selectedId = selectedId,
        pendingDelete = pendingDelete,
        onBack = onBack,
        onNew = {
            viewModel.onCreate(format = { resources.getString(R.string.melody_default_name, it) }) { created ->
                // A new melody is for this button: pick it, then arrange it.
                onPicked(created)
                onOpenEditor(created)
            }
        },
        onSelect = { melody ->
            onPicked(melody)
            onPickedAndDone()
        },
        onEdit = onOpenEditor,
        onDelete = { melody -> viewModel.onDeleteRequest(melody) { pendingDelete = it } },
        onDeleteConfirmed = { melody ->
            pendingDelete = null
            viewModel.onDeleteConfirmed(melody)
        },
        onDeleteDismissed = { pendingDelete = null },
    )
}

/** Stateful wrapper around [MelodyEditorScreen]; closes itself if the melody disappears. */
@Composable
fun MelodyEditorRoute(viewModel: MelodyEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    val actions = remember(viewModel) {
        MelodyEditorActions(
            onBack = onBack,
            onPlayToggle = viewModel::onPlayToggle,
            onCellTap = viewModel::onCellTap,
            onNoteLength = viewModel::onNoteLength,
            onBpmStep = viewModel::onBpmStep,
            onTimeSignature = viewModel::onTimeSignature,
            onClear = viewModel::onClear,
            onRename = viewModel::onRename,
        )
    }
    MelodyEditorScreen(state = state, actions = actions)
}
