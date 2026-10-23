package com.example.soundboard.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.AppContainer
import com.example.soundboard.audio.MixerEngine
import com.example.soundboard.data.AppPreferences
import com.example.soundboard.data.ButtonEntity
import com.example.soundboard.data.ButtonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val buttons: List<ButtonEntity> = emptyList(),
    /** False until the first emission from the database, so the empty state doesn't flash. */
    val loaded: Boolean = false,
    /** One-time "Tap to play · Hold to edit" tip. */
    val showPlayHint: Boolean = false,
)

class MainViewModel(
    private val repository: ButtonRepository,
    private val engine: MixerEngine,
    private val preferences: AppPreferences,
) : ViewModel() {

    /** null until the persisted flag has been read. */
    private val hintDismissed = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<MainUiState> = combine(repository.buttons, hintDismissed) { buttons, dismissed ->
        MainUiState(
            buttons = buttons,
            loaded = true,
            showPlayHint = dismissed == false && buttons.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState())

    init {
        viewModelScope.launch { hintDismissed.value = preferences.isPlayHintDismissed() }
    }

    fun onPlay(buttonId: String) {
        val clip = repository.cachedClip(buttonId)
        if (clip != null) {
            engine.play(clip)
        } else {
            // Not preloaded yet (just after startup): load it off the main thread, then play.
            viewModelScope.launch { repository.loadPlayableClip(buttonId)?.let(engine::play) }
        }
    }

    fun onDismissHint() {
        hintDismissed.value = true
        viewModelScope.launch { preferences.dismissPlayHint() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(container.buttonRepository, container.mixerEngine, container.preferences) as T
        }
    }
}
