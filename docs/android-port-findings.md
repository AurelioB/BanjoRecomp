# BanjoRecomp Android port findings

This is the durable repo-local handoff for the Android port. Keep it updated when findings change, and prefer this root document over scattering notes across submodules.

## Current branch and dependency stack

- Main Android branch: `android`.
- Android applicationId/package: `com.aure.banjorecomp`; Java/JNI namespace remains `io.github.banjorecomp`.
- Shared RT64 submodule target: `lib/rt64` commit `2647d781a0c03486c7e373eb7b0729258292b36c` on `audit/android-sdl-vulkan`.
- Shared RecompFrontend submodule target: `lib/RecompFrontend` commit `c4fdf39a74f23d92acc8a29a6c55fcc209a77bfe` on `audit/android-frontend-support`.
- Shared N64ModernRuntime submodule target: `lib/N64ModernRuntime` commit `27b20c8c80aa817be24c2a2af3d42030225bf8d7`.
- `BanjoRecompSyms` is a data/symbol dependency only; do not place Android app scaffolding, JNI, renderer, runtime, or ROM-loading logic there.
- `lib/bk-decomp` contains Banjo-Kazooie game/decomp data and code. Keep Android platform scaffolding out unless the change is truly game/decomp-specific.

## Build environment

Use system `gradle`; do not use `./gradlew` in these checkouts unless a wrapper is later added intentionally.

Hermes/local build setup:

```sh
source /home/hermes/.config/android-build-env.sh
export PATH=$(printf '%s' "$PATH" | tr ':' '\n' | grep -v 'toolchains/llvm/prebuilt/linux-x86_64/bin' | paste -sd: -)
export PATH="/usr/lib/llvm-19/bin:$PATH"
ln -sf "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/ld.lld" /home/hermes/.local/bin/ld.lld
gradle -p android --no-daemon :app:assembleDebug --rerun-tasks
```

Reason: Banjo patch compilation needs host LLVM clang (`/usr/lib/llvm-19/bin/clang`), not the Android NDK clang. The host link step also needs `ld.lld`; the local symlink points to the NDK copy when the host package does not provide one.

For distributable GitHub Actions APKs, see `docs/android-apk-distribution.md`.

## Runtime APK and ROM loading

- Runtime APKs must not bundle dev ROMs or ROM-derived private assets unless explicitly built with a dev-only flag.
- The shared Android Load ROM path is:
  1. RecompFrontend `open_file_dialog` detects Android.
  2. It calls the current SDL Android activity method `openRomFilePicker()` through `SDL_AndroidGetActivity()`.
  3. The Java activity launches Android DocumentsUI using `ACTION_OPEN_DOCUMENT`.
  4. Java copies the selected URI into app-private `files/data/roms/<displayName-or-selected.rom>`.
  5. App-owned JNI calls `recompui::file::complete_android_file_dialog(success, path)`.
- Banjo owns package-specific Java/JNI symbols. Keep them out of shared submodules.

Relevant files:
- `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`
- `src/main/main.cpp`
- `lib/RecompFrontend/recompui/src/util/file.cpp`
- `lib/RecompFrontend/recompui/src/util/file.h`

Shared RecompFrontend rules:
- No `io.github.banjorecomp` or `io.github.bmherorecomp` symbols in the submodule.
- No game-specific ROM names/hashes there.
- Keep desktop/macOS file-dialog behavior unchanged.
- Android DocumentsUI may destroy/recreate the SDL surface; renderer code must tolerate that at the app/RT64 layer.

## Fullscreen and Android activity behavior

SDLActivity fullscreen needs Java-side immersive mode reapplied from lifecycle callbacks, not just manifest flags. The Banjo fullscreen fix is in `BanjoSDLActivity.java` and should be preserved when rebasing Android activity changes.

## Save/data safety

Do not use `pm clear`, uninstall with data removal, or delete app-private files before backup.

Known save path:

```sh
/data/user/0/io.github.banjorecomp/files/data/saves/bk.n64.us.1.0.bin
```

Safe backup pattern:

```sh
adb exec-out "run-as io.github.banjorecomp cat files/data/saves/bk.n64.us.1.0.bin" > backup.bin
```

Use `adb install -r` for APK replacement to preserve app-private data.

## RT64 idle/performance findings

- The diagnostic hard-disable of the `RT64 Idle` thread was removed.
- Android now defaults idle/high-performance work inactive while preserving configurability. Desktop behavior should remain unchanged.
- Device CPU frequency pinning was traced to external device/root performance policy, not RT64 app threads alone. Always correlate with process/thread sampling before changing RT64 scheduling.
- Do not treat 3GHz bursts on the current test device as proof of Banjo-side render CPU work without correlating app thread samples and process state.

## Visual/performance diagnostics

- Bubblegloop Swamp XLU/muddy-water FPS concerns were investigated separately from shared dependency cleanup.
- Keep BGS-specific diagnostics isolated on diagnostic branches. Do not commit forced map boot, XLU skipping, or log markers to the main Android branch without explicit decision.
- Do not reapply broad alpha/coverage shader experiments blindly. A previous Android experiment around coverage/alpha made transparent/cutout textures render with opaque backgrounds and did not solve BMHero effects correctly.

For N64 visual-effect bugs:
- Prefer game-side object/display-list proof first.
- Check whether the issue is texture-gen, depth/decal rejection, framebuffer/coverage dependence, or render order.
- Only promote to RT64 if multiple games or a minimal renderer-level repro proves a shared renderer bug.

## Offline verification snapshot — 2026-05-30

No Android device was available over ADB for this pass (`adb devices -l` listed no attached devices), so verification was limited to local build, static guard, and APK artifact hygiene checks.

Completed:
- `git diff --check` succeeded.
- `python3 tools/check_android_port_guards.py` succeeded with `Android port guard checks passed`.
- Default Android APK build succeeded with `gradle -p android --no-daemon :app:assembleDebug --stacktrace`.
- Default APK artifact scan showed no ROM/generated-game artifacts: no `baserom`, `decompressed`, `.z64`, `banjo.us`, `RecompiledFuncs`, or `RecompiledPatches` entries.
- Default APK packaged only `lib/arm64-v8a/libSDL2.so`, `lib/arm64-v8a/libmain.so`, and non-ROM program assets.
- Dev-full local build succeeded with `gradle -p android --no-daemon :app:assembleDebug -PbanjoDevFull=true --stacktrace`.
- Dev-full APK scan confirmed the expected gated local dev ROM entries were present only for that build mode:
  - `assets/program/dev-roms/banjo.us.v10.decompressed.z64`
  - `assets/program/dev-roms/baserom.us.v10.z64`
- Dev-full native compile commands contained `-O2` and `-DNDEBUG` for all 765 BanjoRecomp native compile commands inspected, so the optimized-dev-full performance path was still wired.
- After the dev-full check, the default APK was rebuilt so `android/app/build/outputs/apk/debug/app-debug.apk` returned to the ROM-clean default artifact.

Warnings observed:
- Gradle reported deprecated features and `android.useAndroidX=false` deprecation for future Gradle/AGP compatibility.
- Android C/C++ build still emitted the known `sse2neon.h` optimization warning.

Device-required follow-ups:
- Install the rebuilt default APK and confirm it launches without packaged ROM artifacts.
- Install the dev-full APK, verify launcher/gameplay still renders, and manually confirm task-switcher/sleep/resume no longer produces audio static.
- Test a real ZIP/RTZ mod import end-to-end; the picker launch/cancel path was previously verified, but full mod parsing/install still needs a valid mod file on device.

## Artifact hygiene

Do not commit APKs, Gradle/CMake build outputs, `.cxx`, `.gradle`, ROMs, decompressed ROMs, private save backups, or ROM-derived generated files.

Verify runtime APKs with `unzip -l` when ROM/dev-asset packaging is in question.

Verify native markers with:

```sh
unzip -p android/app/build/outputs/apk/debug/app-debug.apk lib/arm64-v8a/libmain.so | strings | grep '<marker-or-symbol>'
```

Also verify root submodule pointers in both BanjoRecomp and BMHeroRecomp before declaring shared dependency changes present in an APK.
