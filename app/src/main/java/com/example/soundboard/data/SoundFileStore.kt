package com.example.soundboard.data

import com.example.soundboard.audio.AudioConfig
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes the per-button WAV files in app-private storage (`filesDir/sounds`). */
class SoundFileStore(
    private val soundsDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun fileFor(buttonId: String): File = File(soundsDir, "$buttonId.wav")

    suspend fun write(buttonId: String, samples: ShortArray) = withContext(io) {
        soundsDir.mkdirs()
        val target = fileFor(buttonId)
        val temp = File(soundsDir, "$buttonId.wav.tmp")
        temp.writeBytes(WavCodec.encode(samples, AudioConfig.SAMPLE_RATE))
        if (!temp.renameTo(target)) {
            // renameTo can fail when the target exists on some filesystems.
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw java.io.IOException("Could not store sound for $buttonId")
            }
        }
    }

    /** Returns the stored clip, or null when the file is missing or not a valid WAV. */
    suspend fun read(buttonId: String): ShortArray? = withContext(io) {
        val file = fileFor(buttonId)
        if (!file.isFile) return@withContext null
        WavCodec.decode(file.readBytes())?.samples
    }

    suspend fun delete(buttonId: String) = withContext(io) {
        fileFor(buttonId).delete()
        File(soundsDir, "$buttonId.wav.tmp").delete()
    }
}
