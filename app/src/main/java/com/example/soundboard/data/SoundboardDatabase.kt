package com.example.soundboard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ButtonEntity::class], version = 2, exportSchema = false)
abstract class SoundboardDatabase : RoomDatabase() {
    abstract fun buttonDao(): ButtonDao

    companion object {
        fun create(context: Context): SoundboardDatabase =
            Room.databaseBuilder(context.applicationContext, SoundboardDatabase::class.java, "soundboard.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** 1 -> 2: per-button effect. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE buttons ADD COLUMN fxId TEXT NOT NULL DEFAULT 'none'")
            }
        }
    }
}
