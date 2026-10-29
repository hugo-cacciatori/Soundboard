package com.example.soundboard.ui

import com.example.soundboard.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun releaseShowsTheVersionAlone() {
        assertEquals("1.1", appVersion(versionName = "1.1", commit = ""))
    }

    @Test
    fun debugAddsTheCommit() {
        assertEquals("1.0 · 5990701", appVersion(versionName = "1.0", commit = "5990701"))
        assertEquals("1.0 · 5990701+", appVersion(versionName = "1.0", commit = "5990701+"))
    }

    @Test
    fun thisDebugBuildKnowsItsCommit() {
        // Built from a git checkout, so the commit is filled in: 7 or more hex digits, maybe "+".
        assertTrue(BuildConfig.GIT_COMMIT, Regex("[0-9a-f]{7,}\\+?").matches(BuildConfig.GIT_COMMIT))
    }
}
