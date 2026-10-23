package com.example.soundboard.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.ui.theme.SoundboardTheme

/**
 * Tap to start, tap to stop (auto-stops at 5 s). While recording a progress ring fills up and
 * the status line counts down. Once a sound exists a play button appears next to the mic, and
 * the mic becomes "record again".
 */
@Composable
fun RecorderControl(
    recording: RecordingState,
    hasSound: Boolean,
    onRecordTap: () -> Unit,
    onPlayTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RecordButton(recording = recording, hasSound = hasSound, onClick = onRecordTap)
            if (hasSound && recording is RecordingState.Idle) {
                PlayButton(onClick = onPlayTap)
            }
        }
        Text(
            text = statusText(recording, hasSound),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun statusText(recording: RecordingState, hasSound: Boolean): String = when (recording) {
    RecordingState.Idle -> stringResource(
        if (hasSound) R.string.record_status_ready else R.string.record_status_idle,
    )
    is RecordingState.Recording -> stringResource(R.string.record_status_recording, recording.secondsLeft)
    RecordingState.Processing -> stringResource(R.string.record_status_processing)
}

@Composable
private fun RecordButton(recording: RecordingState, hasSound: Boolean, onClick: () -> Unit) {
    val isRecording = recording is RecordingState.Recording
    val busy = recording is RecordingState.Processing
    val container = when {
        isRecording -> MaterialTheme.colorScheme.errorContainer
        hasSound -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.primary
    }
    val iconTint = when {
        isRecording -> MaterialTheme.colorScheme.onErrorContainer
        hasSound -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val label = stringResource(
        when {
            isRecording -> R.string.record_stop
            hasSound -> R.string.record_again
            else -> R.string.record_start
        },
    )

    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        // Progress ring: the full circle is the 5 s budget.
        when (recording) {
            is RecordingState.Recording -> CircularProgressIndicator(
                progress = { recording.progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 6.dp,
            )
            RecordingState.Processing -> CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 6.dp,
            )
            RecordingState.Idle -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(container)
                .clickable(enabled = !busy, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic),
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = stringResource(R.string.record_play),
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Preview(name = "Idle, no sound", showBackground = true, backgroundColor = 0xFF121218)
@Composable
private fun RecorderIdlePreview() {
    SoundboardTheme {
        RecorderControl(RecordingState.Idle, hasSound = false, onRecordTap = {}, onPlayTap = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Recording", showBackground = true, backgroundColor = 0xFF121218)
@Composable
private fun RecorderRecordingPreview() {
    SoundboardTheme {
        RecorderControl(
            RecordingState.Recording(progress = 0.4f, secondsLeft = 3),
            hasSound = false,
            onRecordTap = {},
            onPlayTap = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Has sound", showBackground = true, backgroundColor = 0xFF121218)
@Composable
private fun RecorderReadyPreview() {
    SoundboardTheme {
        RecorderControl(RecordingState.Idle, hasSound = true, onRecordTap = {}, onPlayTap = {}, modifier = Modifier.padding(16.dp))
    }
}
