package com.example.soundboard.ui.melody

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.AppContainer
import com.example.soundboard.audio.AudioConfig
import com.example.soundboard.audio.MixerEngine
import com.example.soundboard.audio.dsp.fx.Fx
import com.example.soundboard.audio.dsp.fx.FxRenderer
import com.example.soundboard.audio.dsp.melody.Melody
import com.example.soundboard.audio.dsp.melody.Note
import com.example.soundboard.audio.dsp.melody.TimeSignature
import com.example.soundboard.data.MelodyRepository
import com.example.soundboard.data.SavedMelody
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MelodyEditorUiState(
    val loading: Boolean = true,
    /** The melody no longer exists (deleted elsewhere): the screen closes. */
    val gone: Boolean = false,
    val name: String = "",
    val melody: Melody = Melody(),
    /** How many buttons play this melody. */
    val usageCount: Int = 0,
    /** The button being edited has a sound to preview the melody with. */
    val hasSound: Boolean = false,
    val playing: Boolean = false,
    /** Playhead position in steps while playing. */
    val playhead: Float? = null,
) {
    val canPlay: Boolean get() = hasSound && !melody.isEmpty
}

/**
 * Edits one melody. Every change is saved automatically (debounced by [MelodyRepository.saveSoon]).
 * Play and note auditions use [sound], the dry sound of the button being edited (unsaved changes
 * included); without one they are disabled.
 */
class MelodyEditorViewModel(
    private val melodyId: String,
    private val repository: MelodyRepository,
    private val engine: MixerEngine,
    private val sound: ShortArray?,
    private val dsp: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(MelodyEditorUiState(hasSound = sound != null))
    val state: StateFlow<MelodyEditorUiState> = _state.asStateFlow()

    private var playback: Job? = null
    private var playingClip: ShortArray? = null
    private var auditionClip: ShortArray? = null

    /** Last full render, so Play right after an unchanged melody starts at once. */
    private var rendered: Pair<Melody, ShortArray>? = null
    private var warmJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = repository.get(melodyId)
            _state.update {
                if (saved == null) {
                    it.copy(loading = false, gone = true)
                } else {
                    it.copy(loading = false, name = saved.name, melody = saved.melody)
                }
            }
            warm()
        }
        viewModelScope.launch {
            repository.observeUsageCount(melodyId).collect { count -> _state.update { it.copy(usageCount = count) } }
        }
        // Deleted elsewhere (not possible from this screen today, but the screen must not edit a ghost).
        viewModelScope.launch {
            repository.observe(melodyId).collect { saved ->
                if (saved == null && !_state.value.loading) _state.update { it.copy(gone = true) }
            }
        }
    }

    /** An empty cell places a note (replacing any on that step); the same note again removes it. */
    fun onCellTap(step: Int, midi: Int) {
        val melody = _state.value.melody
        if (step !in 0 until melody.steps || midi !in Melody.LOWEST_MIDI..Melody.HIGHEST_MIDI) return
        val existing = melody.notes.firstOrNull { it.step == step }
        if (existing?.midi == midi) {
            change(melody.withoutNote(step))
        } else {
            change(melody.withNote(step, midi))
            audition(midi)
        }
    }

    fun onBpmStep(delta: Int) {
        val melody = _state.value.melody
        val updated = melody.withBpm(melody.bpm + delta)
        if (updated != melody) change(updated)
    }

    /** The screen has already asked for confirmation when notes would be dropped. */
    fun onTimeSignature(signature: TimeSignature) {
        change(_state.value.melody.withTimeSignature(signature))
    }

    fun onClear() {
        val melody = _state.value.melody
        change(Melody(melody.timeSignature, melody.bpm))
    }

    fun onRename(name: String) {
        val cleaned = name.replace('\n', ' ').trim().take(MAX_NAME_LENGTH)
        if (cleaned.isEmpty()) return
        _state.update { it.copy(name = cleaned) }
        save()
    }

    fun onPlayToggle() {
        if (_state.value.playing) {
            stopPlayback()
            return
        }
        val dry = sound ?: return
        val melody = _state.value.melody
        if (melody.isEmpty) return
        val first = melody.notes.first().step
        playback = viewModelScope.launch {
            _state.update { it.copy(playing = true, playhead = first.toFloat()) }
            val clip = render(dry, melody)
            playingClip = clip
            engine.play(clip)
            val start = System.nanoTime()
            val durationSeconds = clip.size.toDouble() / AudioConfig.SAMPLE_RATE
            while (true) {
                val elapsed = (System.nanoTime() - start) / 1e9
                if (elapsed >= durationSeconds) break
                val step = (first + elapsed / melody.stepSeconds).toFloat().coerceAtMost(melody.steps.toFloat())
                _state.update { it.copy(playhead = step) }
                delay(FRAME_MS)
            }
            playingClip = null
            _state.update { it.copy(playing = false, playhead = null) }
        }
    }

    private fun stopPlayback() {
        playback?.cancel()
        playback = null
        playingClip?.let(engine::stop)
        playingClip = null
        _state.update { it.copy(playing = false, playhead = null) }
    }

    private fun change(melody: Melody) {
        _state.update { it.copy(melody = melody) }
        save()
        warm()
    }

    private fun save() {
        val s = _state.value
        if (s.loading || s.gone) return
        repository.saveSoon(SavedMelody(melodyId, s.name, s.melody))
    }

    /** Renders the melody in the background after a pause in editing, so Play starts right away. */
    private fun warm() {
        val dry = sound ?: return
        warmJob?.cancel()
        warmJob = viewModelScope.launch {
            delay(WARM_DELAY_MS)
            val melody = _state.value.melody
            if (!melody.isEmpty) render(dry, melody)
        }
    }

    private suspend fun render(dry: ShortArray, melody: Melody): ShortArray {
        rendered?.let { (m, clip) -> if (m == melody) return clip }
        val clip = withContext(dsp) { FxRenderer.render(dry, Fx.Melody, melody) }
        rendered = melody to clip
        return clip
    }

    /** Plays the start of the sound at [midi], cutting the previous audition. */
    private fun audition(midi: Int) {
        val dry = sound ?: return
        viewModelScope.launch {
            val snippet = dry.copyOf(min(dry.size, AUDITION_SAMPLES))
            val clip = withContext(dsp) {
                FxRenderer.render(snippet, Fx.Melody, Melody(notes = listOf(Note(0, midi))))
            }
            auditionClip?.let(engine::stop)
            auditionClip = clip
            engine.play(clip)
        }
    }

    override fun onCleared() {
        playingClip?.let(engine::stop)
        auditionClip?.let(engine::stop)
    }

    companion object {
        const val MAX_NAME_LENGTH = 30
        private const val FRAME_MS = 16L
        private const val WARM_DELAY_MS = 600L
        private const val AUDITION_SAMPLES = AudioConfig.SAMPLE_RATE * 2 / 5

        fun factory(container: AppContainer, melodyId: String, sound: ShortArray?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MelodyEditorViewModel(
                    melodyId = melodyId,
                    repository = container.melodyRepository,
                    engine = container.mixerEngine,
                    sound = sound,
                ) as T
            }
    }
}
