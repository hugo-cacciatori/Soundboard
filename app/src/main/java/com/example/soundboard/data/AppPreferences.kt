package com.example.soundboard.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tiny flag store (SharedPreferences, accessed off the main thread). */
class AppPreferences(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val prefs = context.applicationContext.getSharedPreferences("soundboard_prefs", Context.MODE_PRIVATE)

    suspend fun isPlayHintDismissed(): Boolean = withContext(io) { prefs.getBoolean(KEY_PLAY_HINT_DISMISSED, false) }

    suspend fun dismissPlayHint() = withContext(io) { prefs.edit { putBoolean(KEY_PLAY_HINT_DISMISSED, true) } }

    private companion object {
        const val KEY_PLAY_HINT_DISMISSED = "play_hint_dismissed"
    }
}
