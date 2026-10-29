package com.example.soundboard.ui.main

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import com.example.soundboard.ui.PrimaryKey
import com.example.soundboard.ui.appVersionLabel
import com.example.soundboard.ui.launch.LocalTitleCardUp
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
                    loaded = state.loaded,
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
                .height(84.dp)
                .padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // Which build this is, so a test never runs on a stale install.
                Text(
                    text = appVersionLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AddButton(onClick = onAdd, attention = attention)
        }
    }
}

/**
 * The toolbar's primary action: a violet key with a plus. With [attention] on, a soft ring
 * pulses around it.
 */
@Composable
private fun AddButton(onClick: () -> Unit, attention: Boolean, modifier: Modifier = Modifier) {
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

    PrimaryKey(
        onClick = onClick,
        modifier = modifier
            .drawBehind {
                // Read the pulse here so it only invalidates drawing, not composition.
                val progress = pulse?.value ?: return@drawBehind
                val spread = 8.dp.toPx() * progress
                drawRoundRect(
                    color = haloColor.copy(alpha = 0.45f * (1f - progress)),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + 2 * spread, size.height + 2 * spread),
                    cornerRadius = CornerRadius(18.dp.toPx() + spread),
                )
            }
            .size(52.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(R.string.main_add_button),
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * The pads. The first time the board loads they pop in one after another (once the title card is
 * gone); later, a pad that wasn't there before (just saved) drops into its slot.
 */
@Composable
fun ButtonGrid(
    buttons: List<ButtonEntity>,
    loaded: Boolean,
    onPlay: (String) -> Unit,
    onEdit: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Ids already on the board; null until the board has loaded once. Saveable, so returning
    // from the editor or rotating doesn't replay the entrance.
    var seen by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(buttons, loaded) {
        if (loaded) seen = buttons.map { it.id }
    }
    val held = LocalTitleCardUp.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(items = buttons, key = { _, button -> button.id }) { index, button ->
            // Decided once per pad, when it first shows.
            val appear = remember(button.id) {
                val known = seen
                when {
                    known == null -> PadAppear.Entrance(
                        delayMillis = (index * ENTRANCE_STAGGER_MILLIS).coerceAtMost(ENTRANCE_MAX_DELAY_MILLIS),
                    )
                    button.id !in known -> PadAppear.Drop
                    else -> PadAppear.None
                }
            }
            SoundButton(
                name = button.name,
                color = ButtonPalette.byId(button.colorId),
                onClick = { onPlay(button.id) },
                onLongClick = { onEdit(button.id) },
                appear = appear,
                held = held,
            )
        }
    }
}

private const val ENTRANCE_STAGGER_MILLIS = 35
private const val ENTRANCE_MAX_DELAY_MILLIS = 400

@Composable
private fun PlayHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
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

@Preview(name = "Main with hint", showBackground = true, backgroundColor = 0xFF141019, heightDp = 640)
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

@Preview(name = "Main empty", showBackground = true, backgroundColor = 0xFF141019, heightDp = 400)
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
