package com.example.soundboard.data

import com.example.soundboard.audio.dsp.fx.Fx
import com.example.soundboard.audio.dsp.fx.FxRenderer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single source of truth for buttons: Room metadata + WAV files + the in-memory clip caches.
 *
 * The WAV file holds the dry sound (what the editor trims). Playback uses the playable clip: the
 * dry sound with the button's effect rendered in (the same array when there is no effect).
 */
class ButtonRepository(
    private val dao: ButtonDao,
    private val files: SoundFileStore,
    private val scope: CoroutineScope,
    private val dsp: CoroutineDispatcher = Dispatchers.Default,
) {
    private val dryClips = ConcurrentHashMap<String, ShortArray>()
    private val playableClips = ConcurrentHashMap<String, ShortArray>()

    val buttons: Flow<List<ButtonEntity>> = dao.observeAll()

    /** Loads every stored clip into memory on a background coroutine (call once at startup). */
    fun preloadClips() {
        scope.launch {
            val buttons = dao.getAll()
            // Buttons without an effect are playable as soon as they are read; effects are rendered after.
            for (button in buttons) {
                val dry = files.read(button.id) ?: continue
                dryClips.putIfAbsent(button.id, dry)
                if (Fx.byId(button.fxId) == Fx.None) playableClips.putIfAbsent(button.id, dry)
            }
            for (button in buttons) {
                val fx = Fx.byId(button.fxId)
                if (fx == Fx.None || playableClips.containsKey(button.id)) continue
                val dry = dryClips[button.id] ?: continue
                // putIfAbsent: a save that happened meanwhile wins over this startup render.
                playableClips.putIfAbsent(button.id, withContext(dsp) { FxRenderer.render(dry, fx) })
            }
        }
    }

    suspend fun count(): Int = dao.count()

    suspend fun get(id: String): ButtonEntity? = dao.getById(id)

    /** The playable clip if it is already in memory; never touches the disk. */
    fun cachedClip(id: String): ShortArray? = playableClips[id]

    /** The dry clip (no effect), from memory or the WAV file. */
    suspend fun loadSourceClip(id: String): ShortArray? {
        dryClips[id]?.let { return it }
        val clip = files.read(id) ?: return null
        dryClips[id] = clip
        return clip
    }

    /** The playable clip, loading and rendering it if needed (call off the main thread's critical path). */
    suspend fun loadPlayableClip(id: String): ShortArray? {
        playableClips[id]?.let { return it }
        val dry = loadSourceClip(id) ?: return null
        val fx = Fx.byId(dao.getById(id)?.fxId)
        val playable = withContext(dsp) { FxRenderer.render(dry, fx) }
        return playableClips.putIfAbsent(id, playable) ?: playable
    }

    /**
     * Creates a button (when [id] is null) or updates an existing one. [newSound] is the processed
     * dry clip; pass null to keep the existing sound. Returns the button id.
     */
    suspend fun save(id: String?, name: String, colorId: String, fxId: String, newSound: ShortArray?): String {
        val existing = id?.let { dao.getById(it) }
        val buttonId = existing?.id ?: id ?: UUID.randomUUID().toString()
        require(newSound != null || existing != null) { "A new button needs a sound" }

        if (newSound != null) files.write(buttonId, newSound)
        val dry = newSound ?: loadSourceClip(buttonId)
        val needsRender = newSound != null || existing?.fxId != fxId || !playableClips.containsKey(buttonId)
        val playable = if (dry != null && needsRender) {
            withContext(dsp) { FxRenderer.render(dry, Fx.byId(fxId)) }
        } else {
            null
        }
        val button = ButtonEntity(
            id = buttonId,
            name = name.trim(),
            colorId = colorId,
            sortOrder = existing?.sortOrder ?: System.currentTimeMillis(),
            fxId = fxId,
        )
        try {
            dao.upsert(button)
        } catch (e: Exception) {
            if (existing == null) files.delete(buttonId)
            throw e
        }
        if (newSound != null) dryClips[buttonId] = newSound
        if (playable != null) playableClips[buttonId] = playable
        return buttonId
    }

    /** Deletes the button, its WAV file and its cached clips. */
    suspend fun delete(id: String) {
        dao.deleteById(id)
        files.delete(id)
        dryClips.remove(id)
        playableClips.remove(id)
    }
}
