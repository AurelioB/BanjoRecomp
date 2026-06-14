# Android port notes

This document holds the technical Android-port details that do not need to live in the main marketing README.

For the high-level Android overview and screenshots, see [../README.md](../README.md). For the original upstream desktop README, see [../README.original.md](../README.original.md).

## Android package and runtime

- Application ID: `com.aure.banjorecomp`
- Launch activity: `io.github.banjorecomp.BanjoSDLActivity`
- Runtime: SDLActivity-based Android APK wrapping the Banjo: Recompiled native runtime.
- Storage: app-private Android storage for saves, config, mods, texture packs, imported ROM data, and runtime caches.
- Iteration install command: `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`

Use `adb install -r` for normal iteration so app-private saves and imported data are preserved. Do not clear app data or uninstall for routine testing unless saves have been backed up first.

## Dual-screen companion display

The Android port can detect a secondary display and present a companion UI while the game runs on the primary screen. The current Banjo-specific implementation is also being shaped as an experimental pattern for future Android ports of other Nintendo 64 recompilation projects.

The companion display is not a mirror of the game screen. It has its own mode and transition state:

- `LOGO` / idle mode for startup, menus, or states where gameplay stats are not useful.
- `STATS` mode for gameplay and save/state contexts where live game data is useful.
- `BLACK` mode for cutscenes, transitions, or states where the secondary screen should stay out of the way.
- Iris-style transitions between visible content and black/active modes to avoid abrupt flashes.

The current stats presentation is fed by native game-state snapshots routed through JNI into the Android view layer. Banjo-specific fields include health, lives, notes, eggs, feathers, Jiggies, Mumbo tokens, Jinjo state, level/map context, save selection, and global progress-style values.

Related documents:

- [Android dual-screen companion display API](android-dualscreen-framework-api.md)
- [Dual-screen ownership boundaries](plans/android-dualscreen-framework-ownership.md)
- [Dual-screen transition findings](android-dual-screen-transition-findings.md)

## Screenshot assets

README screenshot captures live under:

```text
docs/assets/android-dual-screen/
```

Primary and secondary display captures should be committed together so each README example shows what the player sees on both screens at the same moment.

Capture naming convention:

```text
docs/assets/android-dual-screen/<label>-primary.png
docs/assets/android-dual-screen/<label>-secondary.png
```

Use short, lowercase, hyphenated labels such as `spiral-mountain-stats`, `title-logo`, or `cutscene-black`.

## Building a debug APK

Set up the Android build environment first. On the current Hermes build host this is available through:

```sh
source ~/.config/android-build-env.sh
```

Then build from the repository root:

```sh
python3 tools/check_android_port_guards.py
gradle --no-daemon -p android :app:assembleDebug
```

The debug APK is written to:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected Android device:

```sh
adb devices -l
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Running on device

Launch from the Android launcher, or through ADB:

```sh
adb shell monkey -p com.aure.banjorecomp -c android.intent.category.LAUNCHER 1
```

Useful verification commands:

```sh
adb shell pidof com.aure.banjorecomp
adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'
adb logcat -d -t 200 | grep -E 'BanjoRecomp|SDL|AndroidRuntime|FATAL EXCEPTION'
```

## Android artifact hygiene

Do not commit or package private game inputs or generated private artifacts, including:

- ROMs or decompressed ROMs.
- Generated recompilation sources derived from private inputs.
- APKs, AABs, Gradle/CMake output directories, `.cxx`, or `.gradle` caches.
- App-private saves or imported user mods unless they are explicitly public test fixtures.

The Android guard script should pass before committing Android changes:

```sh
python3 tools/check_android_port_guards.py
```
