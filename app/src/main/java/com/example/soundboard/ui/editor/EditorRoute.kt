package com.example.soundboard.ui.editor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun hasMicrophonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Stateful wrapper around [ButtonEditorScreen]: collects the ViewModel, owns the RECORD_AUDIO
 * flow (asked the first time the user taps record) and the tutorial-hint visibility.
 *
 * @param tutorial true when opened from the first-run tutorial (shows the inline hints).
 * @param onFinished called after Save (`saved = true`) or Delete (`saved = false`).
 * @param onCancel called when the user backs out without saving.
 * @param onOpenMelodies opens the melody picker (Melody effect).
 */
@Composable
fun EditorRoute(
    viewModel: EditorViewModel,
    tutorial: Boolean,
    onFinished: (saved: Boolean) -> Unit,
    onCancel: () -> Unit,
    onOpenMelodies: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var microphoneDenied by rememberSaveable { mutableStateOf(false) }
    var tipsOn by rememberSaveable { mutableStateOf(tutorial) }
    var colorPickerSeen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Saved -> onFinished(true)
                EditorEvent.Deleted -> onFinished(false)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneDenied = !granted
        if (granted) viewModel.onRecordTap()
    }

    // Leaving the foreground stops (and releases) the recorder.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppStopped() }
    // Coming back from the system settings after granting the permission there.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (microphoneDenied && hasMicrophonePermission(context)) microphoneDenied = false
    }

    val hints = EditorHints(
        name = tipsOn && state.name.isBlank(),
        color = tipsOn && !colorPickerSeen,
        record = tipsOn && !state.hasSound && state.recording is RecordingState.Idle,
    )

    val actions = remember(viewModel, context) {
        EditorActions(
            onNameChange = viewModel::onNameChange,
            onColorSelected = viewModel::onColorSelected,
            onColorPickerOpened = { colorPickerSeen = true },
            onFxSelected = viewModel::onFxSelected,
            onOpenMelodies = onOpenMelodies,
            onRecordTap = {
                // Stopping never needs the permission; starting does.
                if (viewModel.state.value.recording !is RecordingState.Idle || hasMicrophonePermission(context)) {
                    microphoneDenied = false
                    viewModel.onRecordTap()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onPlayTap = viewModel::onPlayTap,
            onTrimStartChange = viewModel::onTrimStartChange,
            onTrimEndChange = viewModel::onTrimEndChange,
            onTrimFinished = viewModel::onTrimFinished,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
            onBack = onCancel,
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
            onSkipHints = { tipsOn = false },
        )
    }

    ButtonEditorScreen(
        state = state,
        hints = hints,
        microphoneDenied = microphoneDenied,
        actions = actions,
    )
}
