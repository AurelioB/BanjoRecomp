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
- Runtime APK builds have generated sources in `RecompiledFuncs/`, `RecompiledPatches/`, and `rsp/n_aspMain.cpp`. Run `tools/ci/prepare_android_generated_sources.sh runtime` before the first runtime build or after intentionally deleting those ignored generated-source directories.
- Host `clang` should come from `/usr/lib/llvm-19/bin`, not the Android NDK toolchain. Banjo patch compilation targets MIPS and needs the host LLVM toolchain.
- `ld.lld` must be visible on `PATH`. If the host package does not provide one, the local setup symlinks the NDK `ld.lld` into `$HOME/.local/bin`.

Gradle runs `:app:validateAndroidBuildEnvironment` before native packaging so missing SDK/NDK prefixes, generated sources, and dev-ROM inputs fail with a targeted error instead of a later CMake/compile failure. Android CMake treats `RecompiledPatches/` as prepared input, not generated output, so `:app:clean` should not remove those source-tree files; if they are missing, run the prepare script above and rebuild.

## Android path policy

Android runtime paths are intentionally app-private:

- Java extracts packaged runtime assets from APK `assets/program/` to `files/program/` and sets `APP_PROGRAM_PATH` to that directory before native startup.
- Java sets `APP_FOLDER_PATH` to `files/data/`; RecompFrontend uses that as the root for config, mods, imported ROMs, and saves instead of desktop `$HOME/.config` paths.
- ROM picker imports are copied under `files/data/roms/`.
- The native runtime always reads and writes its active save mirror under `files/data/saves/`.
- Save Management can synchronize that mirror with a user-selected DocumentsUI folder. This uses a persisted `content://` tree permission, not a raw shared-storage filesystem path.
- RecompFrontend `get_program_path()` uses `APP_PROGRAM_PATH`, falling back to SDL internal storage only as an Android safety net.
- RT64 Android user paths use `SDL_AndroidGetInternalStoragePath()` and must stay inside the app sandbox; do not route Android through desktop Linux `$HOME`/`getpwuid()` branches.

## Build default runtime APK

```sh
tools/ci/prepare_android_generated_sources.sh runtime
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

## Custom Vulkan driver safe mode

Android custom graphics drivers are optional and user-imported into app-private storage under `files/gpu-drivers/`; normal APKs must not bundle Turnip/custom driver blobs. Selecting or resetting a driver requires an app restart because Vulkan cannot safely hot-swap loaders after startup.

User flow:

1. Open the in-game Graphics settings.
2. Use `Select Custom Driver...` to launch Android DocumentsUI and choose a driver ZIP. The import path copies and extracts the ZIP into app-private storage, validates an `arm64-v8a` Vulkan driver soname such as `libvulkan_freedreno.so`, writes `driver.json`, and marks the selection as restart-required.
3. Restart the app. The native Vulkan loader reads `files/gpu-drivers/active.json`, tries AdrenoTools/`volkInitializeCustom`, and automatically falls back to Android's system Vulkan loader on failure.
4. Check `Graphics Driver` and `Loaded Driver Details` in Graphics settings. These rows report the actual loaded state, fallback reason, and Vulkan device/vendor/driver version, not just the saved selection.
5. Use `Reset to System Driver` to clear the active selection. Imported driver files are preserved, but the app still needs a restart before Vulkan uses the reset state.

If a bad active custom driver prevents normal startup, launch once with the system-driver bypass:

```sh
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity --ez banjo_force_system_driver true
```

That sets `BANJO_FORCE_SYSTEM_DRIVER=1` before native Vulkan initialization. The native loader ignores `files/gpu-drivers/active.json`, uses Android's system Vulkan driver, and keeps the fallback reason visible in the Graphics driver details/logcat. Use the Graphics settings `Reset to System Driver` action to clear the active selection; it preserves imported driver files but removes the active selection and marks restart required.

Useful logcat filter for driver import/load diagnostics:

```sh
adb logcat -d | grep -E 'BanjoGpuDriver|BanjoVkSmoke|Adreno|Turnip|Vulkan|Device Name|Driver Version|Loaded:'
```

Default/release APKs should contain app support libraries only; they must not contain user-provided Turnip/custom-driver packages. Verify with the APK scan in the cleanup notes before distributing a build.

The custom-loader directory is normalized before it is passed to AdrenoTools; this fixes the earlier missing-separator failure that produced a concatenated `arm64-v8alibvulkan...` path. Source tests and an offline arm64 APK build verify the integration, but a successful launch, swapchain presentation, and gameplay session with Turnip on a real arm64 Adreno device are still required. There is currently no **Remove Installed Driver** action: **Reset to System Driver** only clears the active selection and preserves imports.

## Save Management

Open **Settings → Save Management** to:

- import a raw Banjo-Kazooie save (`0x800` bytes) through DocumentsUI;
- export a consistent snapshot as `banjo-kazooie.bin`;
- choose a DocumentsUI folder for cross-launch synchronized storage; or
- reset to app storage without deleting either copy.

The selected SAF document is authoritative across launches. Before native startup, Java validates it and atomically refreshes `files/data/saves/bk.n64.us.1.0.bin`; the native runtime continues using that app-private mirror. On pause, a quiesced runtime snapshot is written back to the selected document. Import also updates the selected folder after the app copy succeeds.

When a selected folder contains no recognized Banjo save artifacts, the current save is copied into a newly created `banjo-kazooie.bin`. If it already contains `banjo-kazooie.bin`, `bk.n64.us.1.0.bin`, or their `.bak` forms, setup stops without overwriting anything. Use **Import Save** explicitly to replace the active save. If the provider disappears, revokes access, returns an invalid-size file, or fails a write, the app retains its previous internal copy and reports the failure in Save Management/logcat.

Back up before device testing:

```sh
adb exec-out "run-as com.aure.banjorecomp cat files/data/saves/bk.n64.us.1.0.bin" > backup.bin
```

Offline verification:

```sh
python3 tools/check_android_port_guards.py
python3 tools/test_android_gpu_driver_import.py
gradle --no-daemon -p android :app:assembleDebug
git diff --check
```

See [the save-storage design and test matrix](../docs/plans/android-save-storage.md) for device-required checks.

## Cleanup notes

Generated files should stay untracked:

- `android/.gradle/`
- `android/build/`
- `android/app/build/`
- `android/app/.cxx/`

If lifecycle, audio, or Vulkan behavior regresses, check `docs/plans/android-port-review-findings.md` and the native-platform-porting skill references before making broad changes.
