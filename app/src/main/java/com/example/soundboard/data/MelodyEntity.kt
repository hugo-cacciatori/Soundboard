package com.example.soundboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.soundboard.audio.dsp.melody.Melody
import com.example.soundboard.audio.dsp.melody.NoteCodec
import com.example.soundboard.audio.dsp.melody.TimeSignature

/** A saved melody. Buttons with the Melody effect point to one through `ButtonEntity.melodyId`. */
@Entity(tableName = "melodies")
data class MelodyEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Id of a [TimeSignature]. */
    val timeSignature: String,
    val bpm: Int,
    /** The notes in [NoteCodec] form. */
    val notes: String,
    /** Creation time in epoch millis; the picker lists melodies by it. */
    val createdAt: Long,
)

/** A melody with its stored identity and name. */
data class SavedMelody(val id: String, val name: String, val melody: Melody)

fun MelodyEntity.toSavedMelody(): SavedMelody {
    val signature = TimeSignature.byId(timeSignature)
    val melody = Melody(
        timeSignature = signature,
        bpm = bpm.coerceIn(Melody.MIN_BPM, Melody.MAX_BPM),
        notes = NoteCodec.decode(notes, Melody(signature).steps),
    )
    return SavedMelody(id, name, melody)
}
