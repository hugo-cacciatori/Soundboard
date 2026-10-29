package com.example.soundboard.ui.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.ui.theme.SoundboardTheme

/** A short inline tip shown next to the element it explains. Never modal, never blocking. */
@Composable
fun TutorialHint(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141019)
@Composable
private fun TutorialHintPreview() {
    SoundboardTheme {
        TutorialHint(text = "Tap the square to pick a color.", modifier = Modifier.padding(16.dp))
    }
}
