# Android port notes

This document holds the technical Android-port details that do not need to live in the main marketing README.

For the high-level Android overview and screenshots, see [../README.md](../README.md). For the original upstream desktop README, see [../README.original.md](../README.original.md).

## Android package and runtime

- Application ID: `com.aure.banjorecomp`
- Launch activity: `io.github.banjorecomp.BanjoSDLActivity`
- Runtime: SDLActivity-based Android APK wrapping the Banjo: Recompiled native runtime.
- Storage: app-private Android storage for config, mods, texture packs, imported ROM data, runtime caches, and the active native save mirror. Save Management can synchronize that mirror with a user-selected DocumentsUI folder.
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
tools/ci/prepare_android_generated_sources.sh runtime
python3 tools/check_android_port_guards.py
gradle --no-daemon -p android :app:assembleDebug
```

The prepare step is idempotent when `RecompiledFuncs/`, `RecompiledPatches/`, and `rsp/n_aspMain.cpp` already exist. Android Gradle/CMake clean should not delete those source-tree generated inputs; if they are missing, regenerate them before assembling.

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

## Custom graphics driver recovery

The Android custom Vulkan driver path is Adreno/Turnip-focused and optional. Driver ZIPs are imported by the user through DocumentsUI and copied into app-private `files/gpu-drivers/`; normal APKs must not include custom driver blobs. Switching or resetting the active driver requires an app restart.

User/tester flow:

1. Open Graphics settings and choose `Select Custom Driver...`.
2. Pick a driver ZIP from Android DocumentsUI. The app copies it through cache, extracts it under `files/gpu-drivers/imports/<driver-id>/`, validates an `arm64-v8a` driver soname (`libvulkan_freedreno.so`, `vulkan.freedreno.so`, or `libvulkan.so`), and writes generated metadata.
3. Restart the app. Vulkan loader hot-swapping is not supported after RT64/Plume has initialized Vulkan.
4. Confirm `Graphics Driver` / `Loaded Driver Details`. These rows report the actual runtime state and Vulkan physical-device properties; if custom loading failed, they should show system fallback plus the failure reason.
5. Use `Reset to System Driver` to clear `active.json`. This preserves imported driver files and also requires restart before the reset affects Vulkan startup.

If an invalid active driver causes startup trouble, force the system Vulkan driver for the next launch:

```sh
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity --ez banjo_force_system_driver true
```

This sets `BANJO_FORCE_SYSTEM_DRIVER=1` before native Vulkan initialization, so native code ignores `files/gpu-drivers/active.json` and uses the system driver. The Graphics settings Reset action clears the active selection and marks restart required; custom load failures automatically fall back to the system driver and keep the failure reason visible in Graphics driver details and `BanjoGpuDriver` logcat lines.

Capture custom-driver load/status logs with:

```sh
adb logcat -c
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity
adb logcat -d | grep -E 'BanjoGpuDriver|BanjoVkSmoke|Adreno|Turnip|Vulkan|Device Name|Driver Version|Loaded:'
```

Default/release APKs are expected to include the AdrenoTools support library when custom-driver support is enabled, but they must not bundle Turnip/custom Vulkan driver blobs or imported `gpu-drivers` content.

The loader now supplies AdrenoTools a normalized driver directory ending in `/`. This fixes the previously observed concatenation of the directory and soname. Offline guard/import tests and the arm64 APK build cover packaging and control flow; real arm64 Adreno verification of Turnip instance creation, SDL surface/swapchain presentation, gameplay, suspend/resume, and dual-screen behavior remains pending. Resetting selects the system driver after restart and preserves imported packages; removal of imported packages is not implemented.

The same-APK Vulkan smoke probe is available for loader/surface checks without entering the full game runtime:

```sh
gradle --no-daemon -p android -PbanjoProbe=true -PbanjoCustomVulkanDriver=true -PbanjoVulkanSmokeProbe=true :app:assembleDebug
```

When live device install is blocked by existing app version/signature and app data must be preserved, keep verification non-destructive: run source/fixture checks, build scans, APK string scans, and record the remaining device-only caveat rather than uninstalling or clearing saves.

## Save Management and external folders

Android exposes four actions under **Save Management**: import, export, choose a save folder, and reset to app storage. Import accepts the exact runtime save size (`0x800` bytes for Banjo); export first asks N64ModernRuntime for a synchronized snapshot so it cannot race the asynchronous save writer.

Folder selection uses `ACTION_OPEN_DOCUMENT_TREE` and a persisted URI permission. It deliberately does not translate `content://` URIs into filesystem paths. Because the native runtime needs a filesystem path, the implementation uses an app-private mirror:

1. On launch, the selected folder's `banjo-kazooie.bin` is treated as authoritative, validated, and atomically installed as `files/data/saves/bk.n64.us.1.0.bin`.
2. The displaced internal file is retained as a `.pre-external*.bak` backup.
3. During play the native runtime uses the app-private mirror.
4. On pause, the runtime is quiesced and its snapshot is written back to the SAF document.

An empty destination means it has none of the recognized Banjo artifacts (`banjo-kazooie.bin`, `bk.n64.us.1.0.bin`, or either `.bak`). The current save is copied there before the URI is persisted. A collision aborts selection without overwriting the destination. Reset releases the persisted permission and disables synchronization, but deletes neither the external document nor the internal mirror.

Provider errors are non-destructive: revoked permission, disconnected storage, missing/invalid external files, and write failures leave the previous app-private copy available and surface an operation status. This is a synchronization design, not direct native I/O against shared storage. Detailed invariants and device tests are in [Android save storage](plans/android-save-storage.md).

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
