package com.example.soundboard.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.ui.Key
import com.example.soundboard.ui.theme.ErrorLip
import com.example.soundboard.ui.theme.SoundboardTheme

/**
 * Tap to start, tap to stop (auto-stops at 5 s). The record key is red; while recording a ring
 * around it fills up, the key pulses with the live voice level and the status line counts down.
 * Once a sound exists a play button appears beside it, and the key means "record again".
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
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (hasSound && recording is RecordingState.Idle) {
                PlayButton(onClick = onPlayTap)
            }
            RecordButton(recording = recording, hasSound = hasSound, onClick = onRecordTap)
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
    val label = stringResource(
        when {
            isRecording -> R.string.record_stop
            hasSound -> R.string.record_again
            else -> R.string.record_start
        },
    )
    // Smoothed, so the pulse follows the voice without flicker.
    val level by animateFloatAsState(
        targetValue = (recording as? RecordingState.Recording)?.level ?: 0f,
        animationSpec = tween(LEVEL_SMOOTHING_MILLIS),
        label = "record-level",
    )

    Box(modifier = Modifier.size(108.dp), contentAlignment = Alignment.Center) {
        // Progress ring: the full circle is the 5 s budget.
        val ringModifier = Modifier.fillMaxSize()
        when (recording) {
            is RecordingState.Recording -> CircularProgressIndicator(
                progress = { recording.progress },
                modifier = ringModifier,
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = 5.dp,
                strokeCap = StrokeCap.Round,
            )
            RecordingState.Processing -> CircularProgressIndicator(
                modifier = ringModifier,
                color = MaterialTheme.colorScheme.error,
                strokeWidth = 5.dp,
            )
            RecordingState.Idle -> CircularProgressIndicator(
                progress = { 0f },
                modifier = ringModifier,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = 5.dp,
            )
        }
        Key(
            face = MaterialTheme.colorScheme.error,
            lip = ErrorLip,
            onClick = onClick,
            enabled = !busy,
            shape = CircleShape,
            lipHeight = 5.dp,
            modifier = Modifier
                .graphicsLayer {
                    val pulse = 1f + PULSE_SCALE * level.coerceIn(0f, 1f)
                    scaleX = pulse
                    scaleY = pulse
                }
                .size(width = 80.dp, height = 85.dp)
                .semantics { contentDescription = label },
        ) {
            if (isRecording) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.onError, RoundedCornerShape(6.dp)),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = stringResource(R.string.record_play),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** How much a full-scale voice grows the record key. */
private const val PULSE_SCALE = 0.12f

private const val LEVEL_SMOOTHING_MILLIS = 90

@Preview(name = "Idle, no sound", showBackground = true, backgroundColor = 0xFF141019)
@Composable
private fun RecorderIdlePreview() {
    SoundboardTheme {
        RecorderControl(RecordingState.Idle, hasSound = false, onRecordTap = {}, onPlayTap = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Recording", showBackground = true, backgroundColor = 0xFF141019)
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

@Preview(name = "Has sound", showBackground = true, backgroundColor = 0xFF141019)
@Composable
private fun RecorderReadyPreview() {
    SoundboardTheme {
        RecorderControl(RecordingState.Idle, hasSound = true, onRecordTap = {}, onPlayTap = {}, modifier = Modifier.padding(16.dp))
    }
}
