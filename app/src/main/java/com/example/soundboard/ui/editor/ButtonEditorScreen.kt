package com.example.soundboard.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.audio.dsp.fx.Fx
import com.example.soundboard.ui.PrimaryKey
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
    val onOpenMelodies: () -> Unit,
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
                canSave = state.canSave,
                onBack = actions.onBack,
                onSave = actions.onSave,
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (hints.any) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = actions.onSkipHints) { Text(stringResource(R.string.editor_skip_tips)) }
                }
            }
            // Name and color side by side; their hints underneath.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NameField(state = state, onNameChange = actions.onNameChange, modifier = Modifier.weight(1f))
                ColorSection(
                    colorId = state.colorId,
                    onColorSelected = actions.onColorSelected,
                    onPickerOpened = actions.onColorPickerOpened,
                )
            }
            if (hints.name) TutorialHint(stringResource(R.string.hint_name))
            if (hints.color) TutorialHint(stringResource(R.string.hint_color))
            SoundSection(state = state, showHint = hints.record, actions = actions)
            FxSection(
                state = state,
                onFxSelected = actions.onFxSelected,
                onOpenMelodies = actions.onOpenMelodies,
            )
            if (microphoneDenied) {
                MicrophoneDeniedCard(onOpenSettings = actions.onOpenSettings)
            }
            state.error?.let { ErrorText(it) }
            if (state.isEditing) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.editor_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
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
private fun EditorToolbar(isEditing: Boolean, canSave: Boolean, onBack: () -> Unit, onSave: () -> Unit) {
    Surface(color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(72.dp)
                .padding(start = 4.dp, end = 16.dp),
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
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            // Always in reach, also while the keyboard is up.
            PrimaryKey(onClick = onSave, enabled = canSave, corner = 16.dp, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = stringResource(R.string.editor_save),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NameField(state: EditorUiState, onNameChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        modifier = modifier,
        label = { Text(stringResource(R.string.editor_name_label)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall,
        shape = RoundedCornerShape(16.dp),
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
}

@Composable
private fun ColorSection(
    colorId: String,
    onColorSelected: (String) -> Unit,
    onPickerOpened: () -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selected = ButtonPalette.byId(colorId)
    // The popup is anchored to this Box, i.e. to the color key; it sits level with the name field.
    Box(Modifier.padding(top = 4.dp)) {
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
}

@Composable
private fun FxSection(state: EditorUiState, onFxSelected: (String) -> Unit, onOpenMelodies: () -> Unit) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selected = Fx.byId(state.fxId)
    EditorCard(verticalSpacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
            Column {
                SectionLabel(stringResource(R.string.editor_fx_label))
                Text(
                    text = stringResource(fxName(selected)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (selected == Fx.Melody) MelodyRow(melodyName = state.melodyName, onClick = onOpenMelodies)
        // Says why Save is disabled.
        if (state.melodyMissing) {
            Text(
                text = stringResource(R.string.editor_melody_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A section of the editor on a card, a step up from the background. */
@Composable
private fun EditorCard(
    verticalSpacing: Dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

/** Only with the Melody effect: the button's melody, opening the melody picker. */
@Composable
private fun MelodyRow(melodyName: String?, onClick: () -> Unit) {
    if (melodyName == null) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_fx_melody), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.editor_melody_choose), modifier = Modifier.padding(start = 8.dp))
        }
        return
    }
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.editor_melody_description, melodyName)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fx_melody),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = melodyName,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            TextButton(onClick = onClick) { Text(stringResource(R.string.editor_melody_change)) }
        }
    }
}

@Composable
private fun SoundSection(state: EditorUiState, showHint: Boolean, actions: EditorActions) {
    EditorCard(verticalSpacing = 12.dp, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) { SectionLabel(stringResource(R.string.editor_sound_label)) }
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RecorderControl(
            recording = state.recording,
            hasSound = state.hasSound,
            onRecordTap = actions.onRecordTap,
            onPlayTap = actions.onPlayTap,
        )
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

private val noActions = EditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Preview(name = "Tutorial, empty", showBackground = true, backgroundColor = 0xFF141019, heightDp = 760, widthDp = 360)
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

@Preview(name = "Edit, ready to save", showBackground = true, backgroundColor = 0xFF141019, heightDp = 760, widthDp = 360)
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

@Preview(name = "Recording", showBackground = true, backgroundColor = 0xFF141019, heightDp = 760, widthDp = 360)
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

@Preview(name = "Mic denied", showBackground = true, backgroundColor = 0xFF141019, heightDp = 760, widthDp = 360)
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
