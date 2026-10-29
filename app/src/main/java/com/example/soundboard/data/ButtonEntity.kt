package com.example.soundboard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.soundboard.audio.dsp.fx.Fx
import com.example.soundboard.audio.dsp.fx.NO_FX_ID

/**
 * Button metadata. The sound itself lives in `filesDir/sounds/<id>.wav` (see [SoundFileStore]),
 * so the "sound file" field of the spec is derived from [id] rather than stored twice.
 */
@Entity(tableName = "buttons")
data class ButtonEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Id of a swatch in `ButtonPalette`. */
    val colorId: String,
    /** Creation time in epoch millis; buttons are ordered by it (no reordering yet). */
    val sortOrder: Long,
    /** Id of the button's effect (see [Fx]). The WAV file stays dry; the effect is applied on load. */
    @ColumnInfo(defaultValue = NO_FX_ID)
    val fxId: String = NO_FX_ID,
    /** The melody played by the Melody effect; null for every other effect. */
    val melodyId: String? = null,
)
