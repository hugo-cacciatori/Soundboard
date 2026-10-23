package com.example.soundboard.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.soundboard.audio.dsp.MixerCore
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Android output side of the custom mixer: one float [AudioTrack] stream fed by a dedicated
 * high-priority thread that loops `MixerCore.render()` then a blocking `write()`.
 *
 * [play] can be called from any thread (normally the UI thread): it only pushes a trigger onto a
 * lock-free queue, which the mixer thread drains at the start of each buffer. The mixer loop
 * itself does not allocate, lock or log.
 *
 * Start it when the app is in the foreground and release it in `onStop`.
 */
class MixerEngine(private val core: MixerCore = MixerCore()) {
    private val triggers = ConcurrentLinkedQueue<ShortArray>()
    private val lock = Any()
    private var thread: Thread? = null

    /** One per start(): a late-finishing old thread can never switch off a newer run. */
    private class Run {
        @Volatile
        var active = true
    }

    @Volatile
    private var currentRun: Run? = null

    /** Creates the output stream and the mixer thread. No-op when already running. */
    fun start() {
        synchronized(lock) {
            if (thread != null) return
            val track = try {
                buildTrack()
            } catch (e: Exception) {
                Log.e(TAG, "Could not create AudioTrack", e)
                return
            }
            triggers.clear()
            core.clear()
            val run = Run()
            currentRun = run
            thread = Thread({ runLoop(track, run) }, "SoundboardMixer").also { it.start() }
        }
    }

    /** Stops the mixer thread and releases the output stream. Safe to call repeatedly. */
    fun release() {
        val t = synchronized(lock) {
            currentRun?.active = false
            currentRun = null
            thread.also { thread = null }
        }
        try {
            t?.join(JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        triggers.clear()
    }

    /** Starts a new voice for [clip] on top of whatever is playing. Ignored while stopped. */
    fun play(clip: ShortArray) {
        if (currentRun?.active == true) triggers.offer(clip)
    }

    private fun buildTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        // Small buffer: two mixer buffers (5 ms each), but never below what the device requires.
        val bufferBytes = maxOf(minBytes, AudioConfig.MIXER_BUFFER_FRAMES * BYTES_PER_FLOAT * 2)
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(AudioConfig.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return builder.build()
    }

    private fun runLoop(track: AudioTrack, run: Run) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = FloatArray(AudioConfig.MIXER_BUFFER_FRAMES)
        try {
            track.play()
            while (run.active) {
                while (true) {
                    val clip = triggers.poll() ?: break
                    core.play(clip)
                }
                core.render(buffer, buffer.size)
                if (track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING) < 0) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mixer thread failed", e)
        } finally {
            run.active = false
            try {
                track.stop()
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
            track.release()
        }
    }

    private companion object {
        const val TAG = "Soundboard"
        const val BYTES_PER_FLOAT = 4
        const val JOIN_TIMEOUT_MS = 1000L
    }
}
