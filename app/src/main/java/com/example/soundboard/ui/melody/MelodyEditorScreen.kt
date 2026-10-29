package com.example.soundboard.ui.melody

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.audio.dsp.melody.Melody
import com.example.soundboard.audio.dsp.melody.TimeSignature
import com.example.soundboard.ui.theme.SoundboardTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MelodyEditorActions(
    val onBack: () -> Unit,
    val onPlayToggle: () -> Unit,
    val onCellTap: (step: Int, midi: Int) -> Unit,
    val onNoteLength: (step: Int, length: Int) -> Unit,
    val onBpmStep: (Int) -> Unit,
    val onTimeSignature: (TimeSignature) -> Unit,
    val onClear: () -> Unit,
    val onRename: (String) -> Unit,
)

/** The "mini DAW": name, play/stop, time signature, tempo, Clear and the piano-roll grid. */
@Composable
fun MelodyEditorScreen(state: MelodyEditorUiState, actions: MelodyEditorActions) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmSwitchTo by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            MelodyToolbar(
                onBack = actions.onBack,
                title = {
                    val renameDescription = stringResource(R.string.melody_rename, state.name)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = { renaming = true })
                            .semantics(mergeDescendants = true) { contentDescription = renameDescription }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(18.dp),
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = actions.onPlayToggle,
                        enabled = state.canPlay || state.playing,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(if (state.playing) R.drawable.ic_stop else R.drawable.ic_play),
                            contentDescription = stringResource(if (state.playing) R.string.melody_stop else R.string.melody_play),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            Controls(
                melody = state.melody,
                onTimeSignature = { signature ->
                    if (state.melody.notesDroppedBy(signature) > 0) {
                        confirmSwitchTo = signature.id
                    } else {
                        actions.onTimeSignature(signature)
                    }
                },
                onBpmStep = actions.onBpmStep,
            )
            InfoRow(state = state, onClear = { confirmClear = true })
            MelodyGrid(
                melody = state.melody,
                playhead = state.playhead,
                onCellTap = actions.onCellTap,
                onNoteLength = actions.onNoteLength,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (renaming) {
        RenameDialog(
            initial = state.name,
            onConfirm = {
                actions.onRename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.melody_clear_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        actions.onClear()
                    },
                ) { Text(stringResource(R.string.melody_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    confirmSwitchTo?.let { id ->
        val signature = TimeSignature.byId(id)
        val dropped = state.melody.notesDroppedBy(signature)
        AlertDialog(
            onDismissRequest = { confirmSwitchTo = null },
            title = { Text(stringResource(R.string.melody_switch_title, signature.id)) },
            text = { Text(pluralStringResource(R.plurals.melody_switch_message, dropped, dropped)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSwitchTo = null
                        actions.onTimeSignature(signature)
                    },
                ) { Text(stringResource(R.string.melody_switch_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSwitchTo = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(melody: Melody, onTimeSignature: (TimeSignature) -> Unit, onBpmStep: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = stringResource(R.string.melody_time_signature)
        SingleChoiceSegmentedButtonRow(Modifier.semantics { contentDescription = label }) {
            TimeSignature.entries.forEachIndexed { index, signature ->
                SegmentedButton(
                    selected = melody.timeSignature == signature,
                    onClick = { onTimeSignature(signature) },
                    shape = SegmentedButtonDefaults.itemShape(index, TimeSignature.entries.size),
                    icon = {},
                ) { Text(signature.id) }
            }
        }
        Spacer(Modifier.weight(1f))
        TempoStepper(bpm = melody.bpm, onStep = onBpmStep)
    }
}

@Composable
private fun TempoStepper(bpm: Int, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RepeatButton(
            iconRes = R.drawable.ic_remove,
            description = stringResource(R.string.melody_tempo_down),
            enabled = bpm > Melody.MIN_BPM,
            onStep = { onStep(-1) },
        )
        Text(
            text = stringResource(R.string.melody_tempo, bpm),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        RepeatButton(
            iconRes = R.drawable.ic_add,
            description = stringResource(R.string.melody_tempo_up),
            enabled = bpm < Melody.MAX_BPM,
            onStep = { onStep(1) },
        )
    }
}

/** Round icon button that steps once on press, then repeats while held. */
@Composable
private fun RepeatButton(iconRes: Int, description: String, enabled: Boolean, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val canStep by rememberUpdatedState(enabled)
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHighest,
        contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .size(40.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (enabled) {
                    onClick {
                        step()
                        true
                    }
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (!canStep) return@awaitEachGesture
                        step()
                        val repeat = launch {
                            delay(REPEAT_START_MS)
                            while (canStep) {
                                step()
                                delay(REPEAT_EVERY_MS)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

private const val REPEAT_START_MS = 400L
private const val REPEAT_EVERY_MS = 60L

@Composable
private fun InfoRow(state: MelodyEditorUiState, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val info = when {
            !state.hasSound -> stringResource(R.string.melody_no_sound)
            state.melody.isEmpty -> stringResource(R.string.melody_empty_hint)
            state.usageCount > 1 -> pluralStringResource(R.plurals.melody_used_by, state.usageCount, state.usageCount)
            else -> ""
        }
        Text(
            text = info,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear, enabled = !state.melody.isEmpty) { Text(stringResource(R.string.melody_clear)) }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.melody_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.replace('\n', ' ').take(MelodyEditorViewModel.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.melody_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.melody_rename_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF141019, widthDp = 360, heightDp = 720)
@Composable
private fun MelodyEditorPreview() {
    val melody = Melody().withNote(0, 60, 2).withNote(4, 64).withNote(5, 67, 3).withNote(12, 72, 4).withNote(16, 67)
    SoundboardTheme {
        MelodyEditorScreen(
            state = MelodyEditorUiState(
                loading = false,
                name = "Melody 1",
                melody = melody,
                usageCount = 3,
                hasSound = true,
                playing = true,
                playhead = 5.5f,
            ),
            actions = MelodyEditorActions({}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}),
        )
    }
}
