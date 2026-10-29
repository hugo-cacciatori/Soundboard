package com.example.soundboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An update must open every older database without losing data. A gap in the migrations would
 * crash the app at start after an update, so it fails here instead.
 */
class MigrationChainTest {
    @Test
    fun migrationsLeadFromTheFirstVersionToTheCurrentOneStepByStep() {
        val steps = SoundboardDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        val expected = (1 until SoundboardDatabase.VERSION).map { it to it + 1 }
        assertEquals(expected, steps)
    }
}
