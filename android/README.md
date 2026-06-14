# BanjoRecomp Android build

This Gradle project packages the Android port around `BanjoSDLActivity` (SDLActivity) and native `libmain.so`.

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

Diagnostic code kept for bring-up/reference:

- `src/android/sdl_lifecycle_probe.cpp`: opt-in SDLActivity lifecycle probe (`-PbanjoProbe=true`) that still builds `libmain.so`, not the real game runtime.
- `src/android/vulkan_smoke_probe.cpp`: raw Vulkan probe, compiled only when `BANJO_ANDROID_VULKAN_SMOKE_PROBE=ON`.

The older text-view `MainActivity` / `BanjoAndroidShell` diagnostic path has been retired. It predated the SDLActivity integration and is no longer part of any supported Gradle build mode.

## Prerequisites

Use the system `gradle` installed in the build environment, not `./gradlew`. This checkout intentionally does not ship a wrapper yet; the local and CI Android jobs pin the toolchain through the host environment and `android/app/build.gradle` instead of a project-local Gradle distribution.

```sh
source /home/hermes/.config/android-build-env.sh
export PATH=$(printf '%s' "$PATH" | tr ':' '\n' | grep -v 'toolchains/llvm/prebuilt/linux-x86_64/bin' | paste -sd: -)
export PATH="/usr/lib/llvm-19/bin:$PATH"
ln -sf "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/ld.lld" "$HOME/.local/bin/ld.lld"
```

Expected inputs after sourcing the environment:

- `ANDROID_HOME` points at an Android SDK with platform/build-tools 36.
- `ANDROID_NDK_HOME` points at NDK `28.2.13676358`.
- `BANJO_ANDROID_SDL2_PREFIX` points at an arm64 Android SDL2 prefix containing `lib/cmake/SDL2` and `lib/libSDL2.so`.
- `BANJO_ANDROID_FREETYPE_PREFIX` points at an arm64 Android Freetype prefix containing `include/freetype2` and `lib/libfreetype.a`.
- Runtime APK builds have generated sources in `RecompiledFuncs/`, `RecompiledPatches/`, and `rsp/n_aspMain.cpp`. Run `tools/ci/prepare_android_generated_sources.sh runtime` if they are missing.
- Host `clang` should come from `/usr/lib/llvm-19/bin`, not the Android NDK toolchain. Banjo patch compilation targets MIPS and needs the host LLVM toolchain.
- `ld.lld` must be visible on `PATH`. If the host package does not provide one, the local setup symlinks the NDK `ld.lld` into `$HOME/.local/bin`.

Gradle runs `:app:validateAndroidBuildEnvironment` before native packaging so missing SDK/NDK prefixes, generated sources, and dev-ROM inputs fail with a targeted error instead of a later CMake/compile failure.

## Android app-private path policy

Android runtime paths are intentionally app-private:

- Java extracts packaged runtime assets from APK `assets/program/` to `files/program/` and sets `APP_PROGRAM_PATH` to that directory before native startup.
- Java sets `APP_FOLDER_PATH` to `files/data/`; RecompFrontend uses that as the root for config, mods, imported ROMs, and saves instead of desktop `$HOME/.config` paths.
- ROM picker imports are copied under `files/data/roms/`.
- Known saves live under `files/data/saves/`.
- RecompFrontend `get_program_path()` uses `APP_PROGRAM_PATH`, falling back to SDL internal storage only as an Android safety net.
- RT64 Android user paths use `SDL_AndroidGetInternalStoragePath()` and must stay inside the app sandbox; do not route Android through desktop Linux `$HOME`/`getpwuid()` branches.

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

By default the dev-ROM task reads:

- `lib/bk-decomp/baserom.us.v10.z64`
- `banjo.us.v10.decompressed.z64`

To keep private ROM inputs outside the checkout, point Gradle at them without copying into the repo:

```sh
gradle -p android :app:assembleDebug -PbanjoBundleDevRoms=true \
  -PbanjoDevRomDir=/home/hermes/Projects/backups/BanjoRecomp-android-port-20260525-132738
```

You can also set exact files with `BANJO_ANDROID_BASEROM` and `BANJO_ANDROID_DECOMPRESSED_ROM`.

## Build SDL lifecycle probe

```sh
gradle -p android :app:assembleDebug -PbanjoProbe=true
```

## Install on a connected device

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity
```

## Cleanup notes

Generated files should stay untracked:

- `android/.gradle/`
- `android/build/`
- `android/app/build/`
- `android/app/.cxx/`

If lifecycle, audio, or Vulkan behavior regresses, check `docs/plans/android-port-review-findings.md` and the native-platform-porting skill references before making broad changes.
