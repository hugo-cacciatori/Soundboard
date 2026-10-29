package com.example.soundboard.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soundboard.R
import com.example.soundboard.ui.KeyLip
import com.example.soundboard.ui.PrimaryKey
import com.example.soundboard.ui.theme.ButtonPalette
import com.example.soundboard.ui.theme.PaletteColor
import com.example.soundboard.ui.theme.SoundboardTheme

/** Step 1 of the first run: three floating pads, one line of welcome and a single action. */
@Composable
fun OnboardingScreen(onCreateFirstButton: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                FloatingPads()
            }
            Text(
                text = stringResource(R.string.onboarding_welcome),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            PrimaryKey(
                onClick = onCreateFirstButton,
                corner = 20.dp,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text(stringResource(R.string.onboarding_create_first), style = MaterialTheme.typography.labelLarge, fontSize = 18.sp)
            }
        }
    }
}

/** Decoration, not controls: three tilted pads bobbing gently, the front one with a mic. */
@Composable
private fun FloatingPads() {
    val bob = rememberInfiniteTransition(label = "pads-bob")
    Box(
        Modifier
            .size(260.dp, 230.dp)
            .clearAndSetSemantics {},
    ) {
        val pads = listOf(
            Triple(ButtonPalette.byId("amber"), Pair(6.dp, 40.dp), -8f),
            Triple(ButtonPalette.byId("teal"), Pair(140.dp, 10.dp), 6f),
            Triple(ButtonPalette.byId("violet"), Pair(82.dp, 108.dp), -2f),
        )
        pads.forEachIndexed { i, (color, at, tilt) ->
            val lift = bob.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(BOB_MILLIS / 2, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * BOB_MILLIS / 3),
                ),
                label = "pad-bob-$i",
            )
            DecorPad(
                color = color,
                withMic = i == pads.lastIndex,
                modifier = Modifier
                    .offset(at.first, at.second)
                    .graphicsLayer {
                        rotationZ = tilt
                        translationY = -8.dp.toPx() * lift.value
                    },
            )
        }
    }
}

@Composable
private fun DecorPad(color: PaletteColor, withMic: Boolean, modifier: Modifier = Modifier, side: Dp = 104.dp) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .size(side, side + KeyLip)
            .background(color.lip, shape),
    ) {
        Box(
            Modifier
                .size(side)
                .background(color.container, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (withMic) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    tint = color.content,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

private const val BOB_MILLIS = 2_400

@Preview(showBackground = true, backgroundColor = 0xFF141019, heightDp = 640, widthDp = 360)
@Composable
private fun OnboardingScreenPreview() {
    SoundboardTheme { OnboardingScreen(onCreateFirstButton = {}) }
}
