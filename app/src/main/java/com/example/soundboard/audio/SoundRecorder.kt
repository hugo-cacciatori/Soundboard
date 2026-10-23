package com.example.soundboard.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Result of one [SoundRecorder.record] call. */
sealed interface RecordOutcome {
    /** Raw 16-bit mono PCM at [AudioConfig.SAMPLE_RATE], at most [AudioConfig.MAX_CLIP_SAMPLES] long. */
    class Recorded(val samples: ShortArray) : RecordOutcome

    /** The microphone could not be opened or read. */
    data object Failed : RecordOutcome
}

/**
 * `AudioRecord` wrapper: mono, PCM 16-bit, `AudioSource.MIC` (not VOICE_COMMUNICATION, whose AGC
 * and echo cancellation pump the level). The 5 s cap is enforced by sample count, not by a timer.
 * RECORD_AUDIO must already be granted. Use one instance per recording: a [stop] that arrives
 * before the capture loop starts is honoured instead of being lost.
 */
class SoundRecorder(private val io: CoroutineDispatcher = Dispatchers.IO) {
    @Volatile
    private var stopRequested = false

    /** Asks a running [record] call to finish and return what it has captured so far. */
    fun stop() {
        stopRequested = true
    }

    /**
     * Records until [stop] is called, the coroutine is cancelled (the recorder is still released),
     * or [AudioConfig.MAX_CLIP_SAMPLES] samples were captured. [onProgress] receives the number of
     * samples captured so far (from a background thread).
     */
    @SuppressLint("MissingPermission")
    suspend fun record(onProgress: (capturedSamples: Int) -> Unit): RecordOutcome = withContext(io) {
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "getMinBufferSize failed: $minBuffer")
            return@withContext RecordOutcome.Failed
        }
        val bufferBytes = maxOf(minBuffer, AudioConfig.SAMPLE_RATE / 5 * 2) // at least ~200 ms
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConfig.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord construction failed", e)
            return@withContext RecordOutcome.Failed
        }

        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized at ${AudioConfig.SAMPLE_RATE} Hz")
                return@withContext RecordOutcome.Failed
            }
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord refused to start")
                return@withContext RecordOutcome.Failed
            }

            val clip = ShortArray(AudioConfig.MAX_CLIP_SAMPLES)
            val chunk = ShortArray(CHUNK_SAMPLES)
            var total = 0
            while (total < clip.size && !stopRequested && currentCoroutineContext().isActive) {
                val want = minOf(chunk.size, clip.size - total)
                val read = recorder.read(chunk, 0, want)
                if (read < 0) {
                    Log.e(TAG, "AudioRecord.read error: $read")
                    return@withContext RecordOutcome.Failed
                }
                chunk.copyInto(clip, total, 0, read)
                total += read
                onProgress(total)
            }
            currentCoroutineContext().ensureActive()
            RecordOutcome.Recorded(clip.copyOf(total))
        } finally {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
                // Never started; nothing to stop.
            }
            recorder.release()
        }
    }

    private companion object {
        const val TAG = "Soundboard"
        const val CHUNK_SAMPLES = 1024
    }
}
