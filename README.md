# Soundboard

> 🎉 **Just for fun.** Soundboard is an entertainment app. Record silly sounds with your own voice, put them on colorful buttons, and tap away. It is not a professional audio tool, a voice recorder for important recordings, or an accessibility/communication aid.

An Android app that turns your voice into a personal soundboard. Create a button, give it a name and a color, record a short sound (up to 5 seconds), and tap it to play it back. Each tap stops whatever is playing and starts the new sound right away.

## Features

- **Your voice, your buttons.** Every button plays a clip you recorded yourself.
- **Instant replay.** Tap to play. Any tap, on the same button or another, cuts the sound that was playing and starts the new one from the beginning.
- **Loud but never distorted.** Every clip is leveled automatically, and a built-in limiter keeps the switch from one sound to the next from crackling.
- **Share.** Send a button's sound, effect included, to a friend with the **Share sound** key in the editor. It goes out as a small MP3 (about 6 KB per second) that chat apps, email and messages play right in the conversation.
- **Trim.** Drag the edges of the waveform to keep just the good part.
- **Effects.** Give a button an effect to change how it sounds: the **Mouseyfier** turns your voice into a tiny cartoon mouse 🐭, **Robot** holds your whole sentence on one in-tune note with a metallic buzz 🤖, and **Melody** makes your sound sing a tune you arrange yourself on a small four-bar grid 🎵. Melodies are saved, so you can reuse one on other buttons.
- **Colors.** Pick from a palette made to look good together and keep the button labels readable.

## Install

Soundboard needs a phone running **Android 7.0 or later**. Get the latest APK from the [Releases page](https://github.com/hugo-cacciatori/Soundboard/releases): download it on your phone, open it, and allow the install when Android asks. Newer versions install over the old one and keep your buttons.

## How to use it

1. Open the app and tap **Create your first button**.
2. Type a name, tap the colored square to pick a color, then tap record and say something (up to 5 seconds). Play it back, record again if you like, and save.
3. Tap the button to play it. Long-press it to edit it. Use **+** above the grid to add more.
4. To send a sound to someone, long-press its button and tap **Share sound** under the play and record controls, then pick the app to send it with.

## Privacy

Everything stays on your phone unless you share a sound yourself: **Share sound** hands that one MP3 to the app you pick, and nothing else ever leaves. The app has no internet permission, no analytics and no ads. Your recordings are stored in the app's private storage and are deleted when you delete the button (or uninstall the app).

The microphone permission is only requested the first time you tap record.

## Building

Requirements: the Android SDK with the NDK (the version in `app/build.gradle.kts`), JDK 17 or later, and an Android phone (7.0 or later) with USB or wireless debugging enabled.

```powershell
.\gradlew.bat installDebug        # build and install on the connected phone
.\gradlew.bat testDebugUnitTest   # run the unit tests
```

On macOS/Linux use `./gradlew` instead of `.\gradlew.bat`.

## Under the hood

Kotlin, Jetpack Compose (Material 3), Room, and coroutines. Playback uses a custom real-time mixer (a single float `AudioTrack` stream with a look-ahead limiter) instead of `SoundPool`, so cutting from one sound to the next never clicks or hard-clips. Effects are pure-Kotlin DSP rendered once per clip, so playback stays fast. Shared sounds are encoded with LAME, built from source with the NDK, since Android has no MP3 encoder of its own.

## Credits

The app ships the Bricolage Grotesque typeface, under the SIL Open Font License 1.1 (its licence is bundled in the app at `assets/licenses/BricolageGrotesque-OFL.txt`).

MP3 encoding uses [LAME](https://lame.sourceforge.io/) 3.100, under the GNU Library General Public License 2 (source in `app/src/main/cpp/lame/`, licence bundled at `assets/licenses/LAME-LGPL.txt`). You can rebuild the app with a modified LAME from this repository.
