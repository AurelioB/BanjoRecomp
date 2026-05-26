# BanjoRecomp Android build

This Gradle project packages the Android port around SDLActivity and native `libmain.so`.

There are two useful local build modes:

1. SDL lifecycle probe, default debug build
   - Builds `libmain.so` from `src/android/sdl_lifecycle_probe.cpp`.
   - Proves SDLActivity/native lifecycle without requiring ROM-derived generated sources.
   - Not playable.

2. Dev-full APK, opt-in
   - Builds the real native runtime as `libmain.so`.
   - Enabled with `-PbanjoDevFull=true`.
   - Packages local dev assets and ROM-derived files for smoke testing only.
   - Not a release/distribution configuration.

Legacy diagnostic code still exists for bring-up/reference:

- `src/android/android_shell.cpp` / `BanjoAndroidShell`: old minimal Java/native shell path.
- `src/android/vulkan_smoke_probe.cpp`: raw Vulkan probe, compiled only when `BANJO_ANDROID_VULKAN_SMOKE_PROBE=ON`.

## Prerequisites

```sh
. ~/.config/android-build-env.sh
```

The current local environment expects Android SDK/NDK, SDL2, Freetype, and Zstd prefixes to be available as configured by `android/app/build.gradle`.

## Build lifecycle probe

```sh
gradle -p android :app:assembleDebug
```

Output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Build playable dev-full APK

```sh
gradle -p android :app:assembleDebug -PbanjoDevFull=true
```

The dev-full APK uses `BANJO_ANDROID_DEV_FULL_APK=1` and may package local ROM-derived files under app assets for local testing. Keep that path gated; do not use it for release packaging.

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
