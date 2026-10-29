package com.example.soundboard.ui.theme

import androidx.compose.ui.graphics.Color

// "Pocket keys" (SPEC §1): a deep violet-black ground the bright pads stand out on.
val Background = Color(0xFF141019)
val OnBackground = Color(0xFFEEE8F5)
val SurfaceContainerLowest = Color(0xFF0F0C13)
val SurfaceContainerLow = Color(0xFF1A1521)
val SurfaceContainer = Color(0xFF1E1926)
val SurfaceContainerHigh = Color(0xFF2A2433)
val SurfaceContainerHighest = Color(0xFF332C3D)
val SurfaceVariant = Color(0xFF2A2433)
val OnSurfaceVariant = Color(0xFFA9A1B8)
val Outline = Color(0xFF6E6680)
val OutlineVariant = Color(0xFF3A3345)

// The accent is the violet pad: primary actions are violet keys.
val Primary = Color(0xFFC79BFF)
val OnPrimary = Color(0xFF14121A)
val PrimaryLip = Color(0xFF8A57D6)
val PrimaryContainer = Color(0xFF2A2433)
val OnPrimaryContainer = Color(0xFFEEE8F5)
val Secondary = Color(0xFFD9D1E6)
val OnSecondary = Color(0xFF14121A)

// Red pad: Delete and the record key.
val Error = Color(0xFFFF8A80)
val OnError = Color(0xFF14121A)
val ErrorLip = Color(0xFFC2453F)
val ErrorContainer = Color(0xFF5A1F1C)
val OnErrorContainer = Color(0xFFFFDAD6)

/** ARGB of [Background], kept as an Int for the palette contrast check. */
const val BACKGROUND_ARGB = 0xFF141019.toInt()
