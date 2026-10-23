package com.example.soundboard.ui.main

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.soundboard.R
import com.example.soundboard.data.ButtonEntity
import com.example.soundboard.ui.theme.ButtonPalette
import com.example.soundboard.ui.theme.SoundboardTheme

/** Stateful entry point: connects [MainViewModel] to the stateless [MainScreen]. */
@Composable
fun MainRoute(
    viewModel: MainViewModel,
    onNewButton: () -> Unit,
    onEditButton: (buttonId: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MainScreen(
        state = state,
        onPlay = viewModel::onPlay,
        onEdit = onEditButton,
        onAdd = onNewButton,
        onDismissHint = viewModel::onDismissHint,
    )
}

@Composable
fun MainScreen(
    state: MainUiState,
    onPlay: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onDismissHint: () -> Unit,
) {
    Scaffold(
        // A fixed toolbar: not a grid item, no scroll behavior, never collapses.
        topBar = { MainToolbar(onAdd = onAdd, attention = state.loaded && state.buttons.isEmpty()) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            if (state.showPlayHint) {
                PlayHint(
                    onDismiss = onDismissHint,
                    modifier = Modifier.padding(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
                )
            }
            if (state.loaded && state.buttons.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.main_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                ButtonGrid(
                    buttons = state.buttons,
                    onPlay = onPlay,
                    onEdit = onEdit,
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MainToolbar(onAdd: () -> Unit, attention: Boolean) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(72.dp)
                .padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            AddButton(onClick = onAdd, attention = attention)
        }
    }
}

/**
 * The toolbar's primary action: a filled pill in the theme's accent color, with the same
 * press-to-shrink feedback as the pads. With [attention] on, a soft ring pulses around it.
 */
@Composable
private fun AddButton(onClick: () -> Unit, attention: Boolean, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "add-scale")
    val haloColor = MaterialTheme.colorScheme.primary
    val pulse: State<Float>? = if (attention) {
        rememberInfiniteTransition(label = "add-halo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearOutSlowInEasing)),
            label = "add-halo-progress",
        )
    } else {
        null
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .drawBehind {
                // Read the pulse here so it only invalidates drawing, not composition.
                val progress = pulse?.value ?: return@drawBehind
                val spread = 8.dp.toPx() * progress
                drawRoundRect(
                    color = haloColor.copy(alpha = 0.45f * (1f - progress)),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + 2 * spread, size.height + 2 * spread),
                    cornerRadius = CornerRadius(AddButtonCorner.toPx() + spread),
                )
            }
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(AddButtonCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 20.dp),
        interactionSource = interactionSource,
    ) {
        // The visible label names the action, so the icon is decorative.
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.main_add_button),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val AddButtonCorner = 16.dp

@Composable
fun ButtonGrid(
    buttons: List<ButtonEntity>,
    onPlay: (String) -> Unit,
    onEdit: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = buttons, key = { it.id }) { button ->
            SoundButton(
                name = button.name,
                color = ButtonPalette.byId(button.colorId),
                onClick = { onPlay(button.id) },
                onLongClick = { onEdit(button.id) },
            )
        }
    }
}

@Composable
private fun PlayHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.main_hint),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.main_hint_dismiss),
                )
            }
        }
    }
}

private val previewButtons = listOf(
    ButtonEntity("1", "Airhorn", "amber", 1),
    ButtonEntity("2", "Ba dum tss", "violet", 2),
    ButtonEntity("3", "Wow, that is a really long name", "teal", 3),
    ButtonEntity("4", "Yes", "green", 4),
    ButtonEntity("5", "No", "rose", 5),
)

@Preview(name = "Main with hint", showBackground = true, backgroundColor = 0xFF121218, heightDp = 640)
@Composable
private fun MainScreenPreview() {
    SoundboardTheme {
        MainScreen(
            state = MainUiState(buttons = previewButtons, loaded = true, showPlayHint = true),
            onPlay = {},
            onEdit = {},
            onAdd = {},
            onDismissHint = {},
        )
    }
}

@Preview(name = "Main empty", showBackground = true, backgroundColor = 0xFF121218, heightDp = 400)
@Composable
private fun MainScreenEmptyPreview() {
    SoundboardTheme {
        MainScreen(
            state = MainUiState(loaded = true),
            onPlay = {},
            onEdit = {},
            onAdd = {},
            onDismissHint = {},
        )
    }
}
