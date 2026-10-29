package com.example.soundboard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * One schema upgrade as plain SQL statements, so the JVM tests run exactly what the app runs.
 * Keep data transforms in SQL too (UPDATE ... SET ...).
 */
class MigrationStep(val from: Int, val to: Int, val statements: List<String>) {
    fun toMigration(): Migration = object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) {
            statements.forEach(db::execSQL)
        }
    }
}

/**
 * Updates must keep the user's buttons: every schema change bumps [VERSION] and adds a step to
 * [MIGRATION_STEPS]; unit tests upgrade old databases with them. Never use a destructive fallback.
 */
@Database(entities = [ButtonEntity::class, MelodyEntity::class], version = SoundboardDatabase.VERSION)
abstract class SoundboardDatabase : RoomDatabase() {
    abstract fun buttonDao(): ButtonDao
    abstract fun melodyDao(): MelodyDao

    companion object {
        const val VERSION = 3

        /** Every upgrade, oldest first. */
        internal val MIGRATION_STEPS = listOf(
            // Per-button effect.
            MigrationStep(1, 2, listOf("ALTER TABLE buttons ADD COLUMN fxId TEXT NOT NULL DEFAULT 'none'")),
            // Saved melodies, and the melody a button plays.
            MigrationStep(
                2,
                3,
                listOf(
                    "CREATE TABLE IF NOT EXISTS `melodies` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`timeSignature` TEXT NOT NULL, `bpm` INTEGER NOT NULL, `notes` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    "ALTER TABLE buttons ADD COLUMN melodyId TEXT",
                ),
            ),
        )

        fun create(context: Context): SoundboardDatabase =
            Room.databaseBuilder(context.applicationContext, SoundboardDatabase::class.java, "soundboard.db")
                .addMigrations(*MIGRATION_STEPS.map { it.toMigration() }.toTypedArray())
                .build()
    }
}
