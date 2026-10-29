package com.example.soundboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.soundboard.BuildConfig
import com.example.soundboard.R

/**
 * "Version 1.1", or "Version 1.0 · 5990701" for a debug build (the git commit it was built from,
 * with "+" when it had uncommitted changes), so the owner always knows which build is installed.
 */
fun appVersion(versionName: String = BuildConfig.VERSION_NAME, commit: String = BuildConfig.GIT_COMMIT): String =
    if (commit.isEmpty()) versionName else "$versionName · $commit"

@Composable
fun appVersionLabel(): String = stringResource(R.string.app_version, appVersion())
