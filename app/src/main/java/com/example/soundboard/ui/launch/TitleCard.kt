package com.example.soundboard.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundboard.R
import com.example.soundboard.ui.appVersionLabel
import com.example.soundboard.ui.theme.SoundboardTheme
import com.example.soundboard.ui.theme.glowBackground

/** True while the title card covers the first screen, so its entrance animations can wait. */
val LocalTitleCardUp = compositionLocalOf { false }

/** How long the title card stays up on launch before fading out (it can be tapped away sooner). */
const val TITLE_CARD_MILLIS = 1_200L

/** Short branded card shown over the first screen when the app launches. Tap anywhere to skip. */
@Composable
fun TitleCard(onSkip: () -> Unit, modifier: Modifier = Modifier, animateIn: Boolean = true) {
    // Gentle entrance: fade in and settle from a slightly smaller scale.
    val entrance = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(durationMillis = 400)) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            // Opaque: the card covers the first screen until it fades.
            .glowBackground()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSkip),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = entrance.value
                    val scale = 0.92f + 0.08f * entrance.value
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The app icon's mark itself (one source of truth); the 108-unit icon canvas is mostly
            // padding, so it is drawn larger than its box and centred on it.
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(318.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.title_card_tagline),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = appVersionLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141019, heightDp = 640, widthDp = 360)
@Composable
private fun TitleCardPreview() {
    SoundboardTheme { TitleCard(onSkip = {}, animateIn = false) }
}
