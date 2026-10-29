package com.example.soundboard.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.soundboard.audio.dsp.fx.NO_FX_ID
import kotlinx.coroutines.flow.Flow

@Dao
interface MelodyDao {
    @Query("SELECT * FROM melodies ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<MelodyEntity>>

    @Query("SELECT * FROM melodies WHERE id = :id")
    fun observe(id: String): Flow<MelodyEntity?>

    @Query("SELECT * FROM melodies WHERE id = :id")
    suspend fun getById(id: String): MelodyEntity?

    @Upsert
    suspend fun upsert(melody: MelodyEntity)

    @Query("SELECT id FROM buttons WHERE melodyId = :melodyId")
    suspend fun buttonIdsUsing(melodyId: String): List<String>

    @Query("SELECT COUNT(*) FROM buttons WHERE melodyId = :melodyId")
    fun observeUsageCount(melodyId: String): Flow<Int>

    @Query("UPDATE buttons SET fxId = '$NO_FX_ID', melodyId = NULL WHERE melodyId = :melodyId")
    suspend fun resetButtonsUsing(melodyId: String)

    @Query("DELETE FROM melodies WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Deletes the melody; the buttons that used it fall back to no effect. */
    @Transaction
    suspend fun deleteAndResetButtons(id: String) {
        resetButtonsUsing(id)
        deleteById(id)
    }
}
