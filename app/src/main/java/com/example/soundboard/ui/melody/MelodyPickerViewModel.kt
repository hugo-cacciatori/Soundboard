package com.example.soundboard.ui.melody

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.AppContainer
import com.example.soundboard.data.MelodyRepository
import com.example.soundboard.data.SavedMelody
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The saved melodies, or null while they load. */
data class MelodyPickerUiState(val melodies: List<SavedMelody>? = null)

/** A delete waiting for confirmation: [usageCount] buttons will fall back to no effect. */
data class PendingDelete(val melody: SavedMelody, val usageCount: Int)

class MelodyPickerViewModel(private val repository: MelodyRepository) : ViewModel() {
    val state: StateFlow<MelodyPickerUiState> = repository.melodies
        .map { MelodyPickerUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MelodyPickerUiState())

    /** Creates an empty melody (name through [format]) and hands it to [onCreated]. */
    fun onCreate(format: (Int) -> String, onCreated: (SavedMelody) -> Unit) {
        viewModelScope.launch {
            val names = state.value.melodies.orEmpty().map { it.name }
            onCreated(repository.create(nextMelodyName(names, format)))
        }
    }

    /** Looks up how many buttons use [melody], for the confirmation dialog. */
    fun onDeleteRequest(melody: SavedMelody, onReady: (PendingDelete) -> Unit) {
        viewModelScope.launch { onReady(PendingDelete(melody, repository.usageCount(melody.id))) }
    }

    fun onDeleteConfirmed(melody: SavedMelody) {
        viewModelScope.launch { repository.delete(melody.id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MelodyPickerViewModel(container.melodyRepository) as T
        }
    }
}
