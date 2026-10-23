package com.example.soundboard

import android.content.Context
import com.example.soundboard.audio.MixerEngine
import com.example.soundboard.audio.SoundRecorder
import com.example.soundboard.data.AppPreferences
import com.example.soundboard.data.ButtonRepository
import com.example.soundboard.data.SoundFileStore
import com.example.soundboard.data.SoundboardDatabase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container, created once in [SoundboardApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = SoundboardDatabase.create(appContext)
    private val soundFileStore = SoundFileStore(File(appContext.filesDir, "sounds"))

    val buttonRepository = ButtonRepository(database.buttonDao(), soundFileStore, applicationScope)
    val preferences = AppPreferences(appContext)
    val mixerEngine = MixerEngine()

    /** A recorder is single-use: create a fresh one for every recording. */
    fun newRecorder() = SoundRecorder()

    init {
        buttonRepository.preloadClips()
    }
}
