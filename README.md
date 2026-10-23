# Soundboard

> 🎉 **Just for fun.** Soundboard is an entertainment app. Record silly sounds with your own voice, put them on colorful buttons, and tap away. It is not a professional audio tool, a voice recorder for important recordings, or an accessibility/communication aid.

An Android app that turns your voice into a personal soundboard. Create a button, give it a name and a color, record a short sound (up to 5 seconds), and tap it to play it back. Tap fast and the sounds stack on top of each other.

## Features

- **Your voice, your buttons.** Every button plays a clip you recorded yourself.
- **Instant replay.** Tap to play, tap again to layer another copy on top. Nothing gets cut off.
- **Loud but never distorted.** Every clip is leveled automatically, and a built-in limiter keeps stacked sounds from crackling.
- **Trim.** Drag the edges of the waveform to keep just the good part.
- **Effects.** Give a button an effect to change how it sounds. The first one is the **Mouseyfier**, which turns your voice into a tiny cartoon mouse 🐭.
- **Colors.** Pick from a palette made to look good together and keep the button labels readable.

## Privacy

Everything stays on your phone. The app has no internet permission, no analytics and no ads. Your recordings are stored in the app's private storage and are deleted when you delete the button (or uninstall the app).

The microphone permission is only requested the first time you tap record.

## Building

Requirements: Android Studio (recent version) and an Android phone with USB or wireless debugging enabled.

```powershell
.\gradlew.bat installDebug        # build and install on the connected phone
.\gradlew.bat testDebugUnitTest   # run the unit tests
```

On macOS/Linux use `./gradlew` instead of `.\gradlew.bat`.

## Under the hood

Kotlin, Jetpack Compose (Material 3), Room, and coroutines. Playback uses a custom real-time mixer (a single float `AudioTrack` stream with a look-ahead limiter) instead of `SoundPool`, so overlapping sounds never hard-clip. Effects are pure-Kotlin DSP rendered once per clip, so playback stays fast.
