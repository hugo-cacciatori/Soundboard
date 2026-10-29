package com.example.soundboard.data

import com.example.soundboard.audio.dsp.melody.Melody
import com.example.soundboard.audio.dsp.melody.NoteCodec
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Saved melodies. A melody is shared: every button that uses it plays the edited version, so each
 * change re-renders those buttons through [buttons].
 */
class MelodyRepository(
    private val dao: MelodyDao,
    private val buttons: ButtonRepository,
) {
    /** All melodies, oldest first. */
    val melodies: Flow<List<SavedMelody>> = dao.observeAll().map { list -> list.map { it.toSavedMelody() } }

    /** The melody, or null once it is deleted. */
    fun observe(id: String): Flow<SavedMelody?> = dao.observe(id).map { it?.toSavedMelody() }

    /** How many buttons play this melody. */
    fun observeUsageCount(id: String): Flow<Int> = dao.observeUsageCount(id)

    suspend fun get(id: String): SavedMelody? = dao.getById(id)?.toSavedMelody()

    /** Stores a new, empty melody named [name] and returns it. */
    suspend fun create(name: String): SavedMelody {
        val saved = SavedMelody(UUID.randomUUID().toString(), name.trim(), Melody())
        dao.upsert(saved.toEntity(createdAt = System.currentTimeMillis()))
        return saved
    }

    /** Saves the melody's new name or content; the buttons using it are rendered again. */
    suspend fun update(saved: SavedMelody) {
        val existing = dao.getById(saved.id) ?: return
        dao.upsert(saved.copy(name = saved.name.trim()).toEntity(createdAt = existing.createdAt))
        buttons.refreshPlayable(dao.buttonIdsUsing(saved.id))
    }

    /** Deletes the melody. The buttons that used it fall back to no effect. */
    suspend fun delete(id: String) {
        val affected = dao.buttonIdsUsing(id)
        dao.deleteAndResetButtons(id)
        buttons.refreshPlayable(affected)
    }

    private fun SavedMelody.toEntity(createdAt: Long) = MelodyEntity(
        id = id,
        name = name,
        timeSignature = melody.timeSignature.id,
        bpm = melody.bpm,
        notes = NoteCodec.encode(melody.notes),
        createdAt = createdAt,
    )
}
