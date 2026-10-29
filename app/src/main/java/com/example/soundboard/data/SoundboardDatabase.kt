package com.example.soundboard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ButtonEntity::class, MelodyEntity::class], version = 3, exportSchema = false)
abstract class SoundboardDatabase : RoomDatabase() {
    abstract fun buttonDao(): ButtonDao
    abstract fun melodyDao(): MelodyDao

    companion object {
        fun create(context: Context): SoundboardDatabase =
            Room.databaseBuilder(context.applicationContext, SoundboardDatabase::class.java, "soundboard.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** 2 -> 3: saved melodies, and the melody a button plays. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `melodies` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`timeSignature` TEXT NOT NULL, `bpm` INTEGER NOT NULL, `notes` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("ALTER TABLE buttons ADD COLUMN melodyId TEXT")
            }
        }

        /** 1 -> 2: per-button effect. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE buttons ADD COLUMN fxId TEXT NOT NULL DEFAULT 'none'")
            }
        }
    }
}
