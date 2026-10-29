package com.example.soundboard.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.audio.dsp.fx.Fx
import com.example.soundboard.ui.onboarding.TutorialHint
import com.example.soundboard.ui.theme.ButtonPalette
import com.example.soundboard.ui.theme.SoundboardTheme

/** Which tutorial hints are currently visible (all false outside the first-run tutorial). */
data class EditorHints(val name: Boolean = false, val color: Boolean = false, val record: Boolean = false) {
    val any: Boolean get() = name || color || record
}

/** Every user action the editor can raise; the screen itself holds no business state. */
data class EditorActions(
    val onNameChange: (String) -> Unit,
    val onColorSelected: (String) -> Unit,
    val onColorPickerOpened: () -> Unit,
    val onFxSelected: (String) -> Unit,
    val onRecordTap: () -> Unit,
    val onPlayTap: () -> Unit,
    val onTrimStartChange: (Float) -> Unit,
    val onTrimEndChange: (Float) -> Unit,
    val onTrimFinished: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onBack: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onSkipHints: () -> Unit,
)

@Composable
fun ButtonEditorScreen(
    state: EditorUiState,
    hints: EditorHints,
    microphoneDenied: Boolean,
    actions: EditorActions,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            EditorToolbar(
                isEditing = state.isEditing,
                onBack = actions.onBack,
                onDelete = { confirmDelete = true },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = actions.onSave,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.editor_save))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (hints.any) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = actions.onSkipHints) { Text(stringResource(R.string.editor_skip_tips)) }
                }
            }
            NameSection(state = state, showHint = hints.name, onNameChange = actions.onNameChange)
            ColorSection(
                colorId = state.colorId,
                showHint = hints.color,
                onColorSelected = actions.onColorSelected,
                onPickerOpened = actions.onColorPickerOpened,
            )
            SoundSection(state = state, showHint = hints.record, actions = actions)
            FxSection(fxId = state.fxId, melodyMissing = state.melodyMissing, onFxSelected = actions.onFxSelected)
            if (microphoneDenied) {
                MicrophoneDeniedCard(onOpenSettings = actions.onOpenSettings)
            }
            state.error?.let { ErrorText(it) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        actions.onDelete()
                    },
                ) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun EditorToolbar(isEditing: Boolean, onBack: () -> Unit, onDelete: () -> Unit) {
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
            Text(
                text = stringResource(if (isEditing) R.string.editor_title_edit else R.string.editor_title_new),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (isEditing) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.editor_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NameSection(state: EditorUiState, showHint: Boolean, onNameChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            supportingText = {
                Text(
                    text = stringResource(
                        R.string.editor_name_counter,
                        state.name.length,
                        EditorViewModel.MAX_NAME_LENGTH,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
        )
        if (showHint) TutorialHint(stringResource(R.string.hint_name))
    }
}

@Composable
private fun ColorSection(
    colorId: String,
    showHint: Boolean,
    onColorSelected: (String) -> Unit,
    onPickerOpened: () -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selected = ButtonPalette.byId(colorId)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.editor_color_label))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // The popup is anchored to this Box, i.e. to the square.
            Box {
                ColorSquare(
                    color = selected,
                    onClick = {
                        pickerOpen = true
                        onPickerOpened()
                    },
                )
                ColorPickerPopup(
                    expanded = pickerOpen,
                    selectedId = colorId,
                    onSelect = onColorSelected,
                    onDismiss = { pickerOpen = false },
                )
            }
            Text(
                text = stringResource(paletteColorName(selected.id)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (showHint) TutorialHint(stringResource(R.string.hint_color))
    }
}

@Composable
private fun FxSection(fxId: String, melodyMissing: Boolean, onFxSelected: (String) -> Unit) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selected = Fx.byId(fxId)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.editor_fx_label))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // The popup is anchored to this Box, i.e. to the square.
            Box {
                FxSquare(fx = selected, onClick = { pickerOpen = true })
                FxPickerPopup(
                    expanded = pickerOpen,
                    selected = selected,
                    onSelect = { onFxSelected(it.id) },
                    onDismiss = { pickerOpen = false },
                )
            }
            Text(
                text = stringResource(fxName(selected)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Says why Save is disabled.
        if (melodyMissing) {
            Text(
                text = stringResource(R.string.editor_melody_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SoundSection(state: EditorUiState, showHint: Boolean, actions: EditorActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth()) { SectionLabel(stringResource(R.string.editor_sound_label)) }
        RecorderControl(
            recording = state.recording,
            hasSound = state.hasSound,
            onRecordTap = actions.onRecordTap,
            onPlayTap = actions.onPlayTap,
        )
        if (state.waveform.isNotEmpty() && state.recording is RecordingState.Idle) {
            WaveformTrimmer(
                waveform = state.waveform,
                trimStart = state.trimStart,
                trimEnd = state.trimEnd,
                sourceDurationMs = state.sourceDurationMs,
                selectedDurationMs = state.selectedDurationMs,
                onTrimStartChange = actions.onTrimStartChange,
                onTrimEndChange = actions.onTrimEndChange,
                onTrimFinished = actions.onTrimFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        if (showHint) TutorialHint(stringResource(R.string.hint_record))
    }
}

@Composable
private fun MicrophoneDeniedCard(onOpenSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.permission_explanation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings))
            }
        }
    }
}

@Composable
private fun ErrorText(error: EditorError) {
    Text(
        text = stringResource(
            when (error) {
                EditorError.TooQuiet -> R.string.record_error_too_quiet
                EditorError.RecordFailed -> R.string.record_error_failed
                EditorError.SaveFailed -> R.string.editor_save_failed
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val noActions = EditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Preview(name = "Tutorial, empty", showBackground = true, backgroundColor = 0xFF121218, heightDp = 760, widthDp = 360)
@Composable
private fun EditorTutorialPreview() {
    SoundboardTheme {
        ButtonEditorScreen(
            state = EditorUiState(),
            hints = EditorHints(name = true, color = true, record = true),
            microphoneDenied = false,
            actions = noActions,
        )
    }
}

@Preview(name = "Edit, ready to save", showBackground = true, backgroundColor = 0xFF121218, heightDp = 760, widthDp = 360)
@Composable
private fun EditorEditPreview() {
    SoundboardTheme {
        ButtonEditorScreen(
            state = EditorUiState(
                isEditing = true,
                name = "Airhorn",
                colorId = "amber",
                fxId = Fx.Mouseyfier.id,
                hasSound = true,
                waveform = List(96) { i -> if (i in 12..80) 0.3f + 0.7f * ((i * 37) % 11) / 10f else 0.05f },
                sourceDurationMs = 3_600,
                trimStart = 0.1f,
                trimEnd = 0.86f,
            ),
            hints = EditorHints(),
            microphoneDenied = false,
            actions = noActions,
        )
    }
}

@Preview(name = "Recording", showBackground = true, backgroundColor = 0xFF121218, heightDp = 760, widthDp = 360)
@Composable
private fun EditorRecordingPreview() {
    SoundboardTheme {
        ButtonEditorScreen(
            state = EditorUiState(
                name = "Hi",
                recording = RecordingState.Recording(progress = 0.45f, secondsLeft = 3),
            ),
            hints = EditorHints(),
            microphoneDenied = false,
            actions = noActions,
        )
    }
}

@Preview(name = "Mic denied", showBackground = true, backgroundColor = 0xFF121218, heightDp = 760, widthDp = 360)
@Composable
private fun EditorDeniedPreview() {
    SoundboardTheme {
        ButtonEditorScreen(
            state = EditorUiState(error = EditorError.TooQuiet),
            hints = EditorHints(),
            microphoneDenied = true,
            actions = noActions,
        )
    }
}
