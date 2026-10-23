package com.example.soundboard.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ButtonDao {
    @Query("SELECT * FROM buttons ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<ButtonEntity>>

    @Query("SELECT * FROM buttons ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<ButtonEntity>

    @Query("SELECT * FROM buttons WHERE id = :id")
    suspend fun getById(id: String): ButtonEntity?

    @Query("SELECT COUNT(*) FROM buttons")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(button: ButtonEntity)

    @Query("DELETE FROM buttons WHERE id = :id")
    suspend fun deleteById(id: String)
}
