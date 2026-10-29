package com.example.soundboard.ui.editor

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soundboard.AppContainer
import com.example.soundboard.audio.AudioConfig
import com.example.soundboard.audio.MixerEngine
import com.example.soundboard.audio.RecordOutcome
import com.example.soundboard.audio.SoundRecorder
import com.example.soundboard.audio.dsp.ProcessResult
import com.example.soundboard.audio.dsp.SoundProcessor
import com.example.soundboard.audio.dsp.Waveform
import com.example.soundboard.audio.dsp.fx.Fx
import com.example.soundboard.audio.dsp.fx.FxRenderer
import com.example.soundboard.audio.dsp.melody.Melody
import com.example.soundboard.data.ButtonRepository
import com.example.soundboard.data.MelodyRepository
import com.example.soundboard.ui.theme.ButtonPalette
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RecordingState {
    data object Idle : RecordingState

    /** [progress] runs from 0 to 1 over [AudioConfig.MAX_CLIP_SECONDS]. */
    data class Recording(val progress: Float, val secondsLeft: Int) : RecordingState

    /** Running [SoundProcessor] on the captured audio. */
    data object Processing : RecordingState
}

enum class EditorError { TooQuiet, RecordFailed, SaveFailed }

data class EditorUiState(
    /** True while an existing button is being loaded. */
    val loading: Boolean = false,
    val isEditing: Boolean = false,
    val name: String = "",
    val colorId: String = ButtonPalette.DEFAULT_ID,
    /** Id of the selected [Fx]. */
    val fxId: String = Fx.DEFAULT.id,
    /** A recorded clip exists (stored or pending). */
    val hasSound: Boolean = false,
    val recording: RecordingState = RecordingState.Idle,
    val saving: Boolean = false,
    val error: EditorError? = null,
    /** Peak overview of the untrimmed sound, each value in [0, 1]. Empty when there is no sound. */
    val waveform: List<Float> = emptyList(),
    /** Untrimmed sound length. */
    val sourceDurationMs: Int = 0,
    /** Selection as fractions of the untrimmed sound, `trimStart < trimEnd`. */
    val trimStart: Float = 0f,
    val trimEnd: Float = 1f,
    /** The selected part is too quiet to keep. */
    val trimTooQuiet: Boolean = false,
    /** The melody the Melody effect plays; kept while another effect is selected, saved only with Melody. */
    val melodyId: String? = null,
    val melodyName: String? = null,
    val melodyHasNotes: Boolean = false,
) {
    /** The Melody effect is selected without a melody that has notes: the button can't be saved. */
    val melodyMissing: Boolean
        get() = Fx.byId(fxId) == Fx.Melody && (melodyId == null || !melodyHasNotes)

    /** Save needs a name and a usable sound (and a melody for the Melody effect), and nothing in flight. */
    val canSave: Boolean
        get() = !loading && !saving && recording is RecordingState.Idle && name.isNotBlank() && hasSound &&
            !trimTooQuiet && !melodyMissing

    val selectedDurationMs: Int
        get() = ((trimEnd - trimStart) * sourceDurationMs).roundToInt()
}

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
}

/**
 * Holds the editor state. A new recording stays in memory ([pendingSound]) until Save, so
 * cancelling leaves the stored sound untouched.
 *
 * Trimming: [source] is the untrimmed, already processed sound (a new take or the stored one).
 * When the user lets go of a trim handle, the selected range goes through [SoundProcessor] again,
 * so the new edges get their fades and the selection is re-normalized to the peak target. The
 * result becomes [pendingSound], which is what Save stores.
 *
 * Effects: [pendingSound] / [source] stay dry. Play uses a preview with the selected effect
 * rendered in, cached per (clip, effect) and rendered ahead of time when either changes.
 *
 * @param buttonId the button being edited, or null to create a new one.
 */
class EditorViewModel(
    private val buttonId: String?,
    private val repository: ButtonRepository,
    private val melodies: MelodyRepository,
    private val engine: MixerEngine,
    private val recorderFactory: () -> SoundRecorder,
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState(loading = buttonId != null, isEditing = buttonId != null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    /** Sound to store on Save; null keeps the stored sound as it is. */
    private var pendingSound: ShortArray? = null
    private var recorder: SoundRecorder? = null

    /** Untrimmed sound the trim selection applies to. */
    private var source: ShortArray? = null
    private var sourceIsStored = false
    private var trimJob: Job? = null

    private class Preview(val clip: ShortArray, val fx: Fx, val melody: Melody?, val rendered: Deferred<ShortArray>)

    /** Last effect preview. Only touched on the main thread. */
    private var preview: Preview? = null

    /** Content of the selected melody, kept current while it is edited elsewhere. */
    private var melody: Melody? = null
    private var melodyJob: Job? = null

    init {
        if (buttonId != null) {
            viewModelScope.launch {
                val button = repository.get(buttonId)
                val clip = repository.loadSourceClip(buttonId)
                _state.update {
                    if (button == null) {
                        it.copy(loading = false)
                    } else {
                        it.copy(
                            loading = false,
                            name = button.name,
                            colorId = button.colorId,
                            fxId = button.fxId,
                            hasSound = clip != null,
                        )
                    }
                }
                button?.melodyId?.let(::watchMelody)
                if (clip != null) setSource(clip, stored = true)
                warmPreview()
            }
        }
    }

    fun onNameChange(value: String) {
        val cleaned = value.replace('\n', ' ').take(MAX_NAME_LENGTH)
        _state.update { it.copy(name = cleaned) }
    }

    fun onColorSelected(colorId: String) {
        _state.update { it.copy(colorId = colorId) }
    }

    fun onFxSelected(fxId: String) {
        _state.update { it.copy(fxId = fxId) }
        warmPreview()
    }

    /** A melody was picked for the Melody effect. */
    fun onMelodySelected(melodyId: String) {
        watchMelody(melodyId)
    }

    /** Starts recording when idle, stops it when recording. The caller has checked RECORD_AUDIO. */
    fun onRecordTap() {
        when (_state.value.recording) {
            RecordingState.Idle -> startRecording()
            is RecordingState.Recording -> recorder?.stop()
            RecordingState.Processing -> Unit
        }
    }

    /** The app left the foreground: stop (and thereby release) the recorder. */
    fun onAppStopped() {
        recorder?.stop()
    }

    /** Plays the selected part of the sound, with its effect, through the shared mixer. */
    fun onPlayTap() {
        viewModelScope.launch {
            trimJob?.join()
            if (_state.value.trimTooQuiet) return@launch
            val clip = pendingSound ?: source ?: return@launch
            engine.play(previewFor(clip, Fx.byId(_state.value.fxId)).await())
        }
    }

    /** Moves the start handle (fraction of the untrimmed sound) while dragging. */
    fun onTrimStartChange(fraction: Float) {
        val gap = minTrimFraction() ?: return
        _state.update { it.copy(trimStart = fraction.coerceIn(0f, (it.trimEnd - gap).coerceAtLeast(0f))) }
    }

    /** Moves the end handle (fraction of the untrimmed sound) while dragging. */
    fun onTrimEndChange(fraction: Float) {
        val gap = minTrimFraction() ?: return
        _state.update { it.copy(trimEnd = fraction.coerceIn((it.trimStart + gap).coerceAtMost(1f), 1f)) }
    }

    /** A handle was released: build the trimmed sound off the main thread. */
    fun onTrimFinished() {
        val src = source ?: return
        val s = _state.value
        val from = (s.trimStart * src.size).roundToInt().coerceIn(0, src.size)
        val to = (s.trimEnd * src.size).roundToInt().coerceIn(from, src.size)
        trimJob?.cancel()
        trimJob = viewModelScope.launch {
            if (from == 0 && to == src.size) {
                // Full length again: nothing to re-process, and a stored sound stays untouched.
                pendingSound = if (sourceIsStored) null else src
                setTrimTooQuiet(false)
                warmPreview()
                return@launch
            }
            val result = withContext(processingDispatcher) { SoundProcessor.process(src.copyOfRange(from, to)) }
            when (result) {
                is ProcessResult.Success -> {
                    pendingSound = result.samples
                    setTrimTooQuiet(false)
                    warmPreview()
                }
                ProcessResult.TooQuiet -> {
                    pendingSound = null
                    setTrimTooQuiet(true)
                }
            }
        }
    }

    fun onSave() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                // A handle may have just been released; save what is actually selected.
                trimJob?.join()
                if (_state.value.trimTooQuiet) {
                    _state.update { it.copy(saving = false) }
                    return@launch
                }
                repository.save(buttonId, s.name, s.colorId, s.fxId, s.melodyId, pendingSound)
                _events.send(EditorEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving button failed", e)
                _state.update { it.copy(saving = false, error = EditorError.SaveFailed) }
            }
        }
    }

    fun onDelete() {
        val id = buttonId ?: return
        viewModelScope.launch {
            try {
                repository.delete(id)
                _events.send(EditorEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deleting button failed", e)
            }
        }
    }

    private fun startRecording() {
        val newRecorder = recorderFactory()
        recorder = newRecorder
        _state.update {
            it.copy(
                recording = RecordingState.Recording(progress = 0f, secondsLeft = AudioConfig.MAX_CLIP_SECONDS),
                error = null,
            )
        }
        viewModelScope.launch {
            val outcome = newRecorder.record { captured -> onCaptureProgress(captured) }
            recorder = null
            when (outcome) {
                RecordOutcome.Failed -> _state.update {
                    it.copy(recording = RecordingState.Idle, error = EditorError.RecordFailed)
                }
                is RecordOutcome.Recorded -> process(outcome.samples)
            }
        }
    }

    private fun onCaptureProgress(capturedSamples: Int) {
        val progress = (capturedSamples.toFloat() / AudioConfig.MAX_CLIP_SAMPLES).coerceIn(0f, 1f)
        val secondsLeft = AudioConfig.MAX_CLIP_SECONDS -
            (capturedSamples / AudioConfig.SAMPLE_RATE).coerceAtMost(AudioConfig.MAX_CLIP_SECONDS)
        _state.update { s ->
            if (s.recording is RecordingState.Recording) {
                s.copy(recording = RecordingState.Recording(progress, secondsLeft))
            } else {
                s
            }
        }
    }

    private suspend fun process(raw: ShortArray) {
        _state.update { it.copy(recording = RecordingState.Processing) }
        val result = withContext(processingDispatcher) { SoundProcessor.process(raw) }
        when (result) {
            is ProcessResult.Success -> {
                trimJob?.cancel() // a late trim of the old take must not overwrite the new one
                pendingSound = result.samples
                setSource(result.samples, stored = false)
                _state.update { it.copy(recording = RecordingState.Idle, hasSound = true, error = null) }
                warmPreview()
            }
            ProcessResult.TooQuiet -> _state.update {
                // A rejected take never replaces the existing sound (or its trim).
                it.copy(recording = RecordingState.Idle, error = EditorError.TooQuiet)
            }
        }
    }

    /** The [clip] with [fx] rendered in; reuses the last preview when nothing changed. */
    private fun previewFor(clip: ShortArray, fx: Fx): Deferred<ShortArray> {
        val melody = melody.takeIf { fx == Fx.Melody }
        preview?.let { if (it.clip === clip && it.fx == fx && it.melody == melody) return it.rendered }
        val rendered = viewModelScope.async(processingDispatcher) { FxRenderer.render(clip, fx, melody) }
        preview = Preview(clip, fx, melody, rendered)
        return rendered
    }

    /** Follows the melody [melodyId] (it can be edited or deleted elsewhere) and keeps the state in sync. */
    private fun watchMelody(melodyId: String) {
        melodyJob?.cancel()
        _state.update { it.copy(melodyId = melodyId) }
        melodyJob = viewModelScope.launch {
            melodies.observe(melodyId).collect { saved ->
                melody = saved?.melody
                _state.update {
                    if (saved == null) {
                        it.copy(melodyId = null, melodyName = null, melodyHasNotes = false)
                    } else {
                        it.copy(melodyName = saved.name, melodyHasNotes = !saved.melody.isEmpty)
                    }
                }
                warmPreview()
            }
        }
    }

    /** Renders the preview in the background so Play responds right away. */
    private fun warmPreview() {
        val fx = Fx.byId(_state.value.fxId)
        val clip = pendingSound ?: source ?: return
        if (fx != Fx.None) previewFor(clip, fx)
    }

    /** Makes [samples] the untrimmed sound and resets the selection to all of it. */
    private suspend fun setSource(samples: ShortArray, stored: Boolean) {
        source = samples
        sourceIsStored = stored
        val peaks = withContext(processingDispatcher) { Waveform.peaks(samples, WAVEFORM_BINS) }
        _state.update {
            it.copy(
                waveform = peaks.toList(),
                sourceDurationMs = (samples.size * 1000L / AudioConfig.SAMPLE_RATE).toInt(),
                trimStart = 0f,
                trimEnd = 1f,
                trimTooQuiet = false,
            )
        }
    }

    private fun setTrimTooQuiet(tooQuiet: Boolean) {
        _state.update {
            val error = when {
                tooQuiet -> EditorError.TooQuiet
                it.error == EditorError.TooQuiet -> null
                else -> it.error
            }
            it.copy(trimTooQuiet = tooQuiet, error = error)
        }
    }

    /** Smallest selection as a fraction of the source, or null when there is no source. */
    private fun minTrimFraction(): Float? {
        val size = source?.size?.takeIf { it > 0 } ?: return null
        return (AudioConfig.MIN_TRIM_SAMPLES.toFloat() / size).coerceAtMost(1f)
    }

    override fun onCleared() {
        recorder?.stop()
    }

    companion object {
        const val MAX_NAME_LENGTH = 30
        private const val WAVEFORM_BINS = 96
        private const val TAG = "Soundboard"

        fun factory(container: AppContainer, buttonId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(
                    buttonId = buttonId,
                    repository = container.buttonRepository,
                    melodies = container.melodyRepository,
                    engine = container.mixerEngine,
                    recorderFactory = container::newRecorder,
                ) as T
            }
    }
}
