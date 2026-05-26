# BanjoRecomp Android build

This Gradle project packages the Android port around SDLActivity and native `libmain.so`.

The default debug build is the full Android runtime APK without bundled ROMs. That is the intended auditable/default configuration: it packages native code and runtime assets, but users must provide their own ROM through the app's ROM picker/import path.

There are three local build modes:

1. Runtime APK, default
   - Builds the real native runtime as `libmain.so`.
   - Packages UI/runtime assets and `recompcontrollerdb.txt`.
   - Does not package ROMs or ROM-derived local test files.
   - Intended as the default buildable fork configuration.

2. Runtime APK with bundled dev ROMs, opt-in
   - Enabled with `-PbanjoBundleDevRoms=true`.
   - Backwards-compatible alias: `-PbanjoDevFull=true`.
   - Packages local dev ROM files under `assets/program/dev-roms/` for smoke testing only.
   - Not a release/distribution configuration.

3. SDL lifecycle probe, opt-in
   - Enabled with `-PbanjoProbe=true`.
   - Builds `libmain.so` from `src/android/sdl_lifecycle_probe.cpp`.
   - Proves SDLActivity/native lifecycle without requiring ROM-derived generated sources.
   - Not playable.

Legacy diagnostic code still exists for bring-up/reference:

- `src/android/android_shell.cpp` / `BanjoAndroidShell`: old minimal Java/native shell path.
- `src/android/vulkan_smoke_probe.cpp`: raw Vulkan probe, compiled only when `BANJO_ANDROID_VULKAN_SMOKE_PROBE=ON`.

## Prerequisites

```sh
. ~/.config/android-build-env.sh
```

The current local environment expects Android SDK/NDK, SDL2, Freetype, and Zstd prefixes to be available as configured by `android/app/build.gradle`.

## Build default runtime APK

```sh
gradle -p android :app:assembleDebug
```

Output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Build runtime APK with bundled local dev ROMs

```sh
gradle -p android :app:assembleDebug -PbanjoBundleDevRoms=true
```

The dev-ROM APK may package local ROM-derived files under app assets for local testing. Keep that path gated; do not use it for release packaging.

## Build SDL lifecycle probe

```sh
gradle -p android :app:assembleDebug -PbanjoProbe=true
```

## Install on a connected device

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.banjorecomp/.BanjoSDLActivity
```

## Cleanup notes

Generated files should stay untracked:

- `android/.gradle/`
- `android/build/`
- `android/app/build/`
- `android/app/.cxx/`

If lifecycle, audio, or Vulkan behavior regresses, check `docs/plans/android-port-review-findings.md` and the native-platform-porting skill references before making broad changes.
