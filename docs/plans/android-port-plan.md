# BanjoRecomp Android Port Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Build and package Banjo: Recompiled as an Android app, initially for arm64-v8a devices with Vulkan support, using the user's legally owned Banjo-Kazooie ROM supplied at runtime.

**Architecture:** Keep the core C/C++ game, N64ModernRuntime, RT64 renderer, RecompFrontend UI/input, generated recompiled code, and patch pipeline as native code. Add an Android app shell that owns lifecycle, storage permissions/file import, asset packaging, and JNI/native entry into the existing runtime. Prefer minimal platform-specific forks: add Android branches in CMake and small platform shims rather than rewriting engine code.

**Tech Stack:** Android Studio / Gradle, Android NDK, CMake, Ninja, C++20, SDL2 for Android, Vulkan, RT64, N64ModernRuntime, RecompFrontend, nativefiledialog replacement/shim, app assets packaged under `app/src/main/assets` or copied to app-private storage.

---

## 1. Current Architecture Findings

### Repository layout and build flow

- `CMakeLists.txt` defines one native desktop executable target: `BanjoRecompiled`.
- Main desktop entrypoint is `src/main/main.cpp`.
- Core project sources are listed in `CMakeLists.txt:142-157`:
  - `src/main/launcher_animation.cpp`
  - `src/main/main.cpp`
  - `src/main/register_overlays.cpp`
  - `src/main/register_patches.cpp`
  - `src/main/theme.cpp`
  - `src/game/config.cpp`
  - `src/game/debug.cpp`
  - `src/game/recomp_api.cpp`
  - `src/game/recomp_extension_api.cpp`
  - `src/game/recomp_data_api.cpp`
  - `src/game/rom_decompression.cpp`
  - `rsp/n_aspMain.cpp`
- Generated/recompiled code is expected in:
  - `RecompiledFuncs/*.c`
  - `RecompiledFuncs/*.cpp`
  - `RecompiledPatches/patches.c`
  - `RecompiledPatches/patches_bin.c`
  - `RecompiledPatches/recomp_overlays.inl`
  - `RecompiledPatches/funcs.h`
- Build generation steps are documented in `BUILDING.md`:
  - Decompress the NTSC-U 1.0 ROM externally.
  - Run `./N64Recomp banjo.us.rev0.toml`.
  - Run `./RSPRecomp n_aspMain.us.rev0.toml`.
  - Build with CMake/Ninja.
- Submodules are required and include:
  - `lib/N64ModernRuntime`
  - `lib/RecompFrontend`
  - `lib/rt64`
  - `lib/bk-decomp`
  - `BanjoRecompSyms`

### Platform and dependency assumptions in current code

- `CMakeLists.txt` has platform branches for Windows, Apple, and Linux, but not Android.
- Linux build links SDL2, X11, Freetype, Threads, `-latomic`, `-static-libstdc++`, and dynamic loader libs.
- RT64 is added as a static subdirectory and configured with:
  - `RT64_STATIC TRUE`
  - `RT64_SDL_WINDOW_VULKAN TRUE`
  - `HLSL_CPU`
- `src/main/main.cpp` currently defines `SDL_MAIN_HANDLED`, calls `SDL_Init`, creates an SDL window, opens SDL audio, uses SDL game controller APIs, registers UI/fonts/config paths, and finally calls `recomp::start(...)`.
- The main window path is already partially Android-aware:
  - `create_window()` returns `ultramodern::renderer::WindowHandle{ window }` for `__linux__ || __ANDROID__`.
  - `lib/N64ModernRuntime/ultramodern/include/ultramodern/renderer_context.hpp` includes `android/native_window.h` for `__ANDROID__`, but currently still aliases Android/Linux `WindowHandle` to `SDL_Window*`.
  - `lib/RecompFrontend/recompui/src/renderer/rt64_render_context.cpp` handles `__linux__ || __ANDROID__` together for `appCore.window = window_handle`.
- Desktop-only/problematic areas for Android:
  - `#include <filesystem>` and app-relative paths must be reconciled with Android app-private storage and asset packaging.
  - `nfd.h` / Native File Dialog is desktop-focused and needs an Android file picker bridge or an app-specific ROM import flow.
  - `SDL2/SDL_syswm.h` and `SDL_GetWindowWMInfo()` are used in `src/main/main.cpp`; Android may not need or support the desktop window manager logic.
  - Linux-specific icon setup under `#if defined(__gnu_linux__)` should not run on Android.
  - `preload_executable()` currently treats only Windows, Linux, and Apple as successful; Android falls into the false branch and would print a preload failure.
  - `CMakeLists.txt` assumes bundled DXC binaries for Linux/macOS/Windows, not Android-host shader compilation/runtime.
  - Android packaging must include fonts/assets/config/controller DB, and runtime ROM import must never bundle copyrighted game assets.

---

## 2. Android Target Architecture

### Initial target

- ABI: `arm64-v8a` only for the first milestone.
- Min SDK: start at API 26 or higher; raise if Vulkan/NDK dependencies require it.
- Rendering: Vulkan through SDL2 + RT64.
- Input: physical controllers first through SDL GameController APIs; touch overlay later.
- Audio: SDL audio first.
- ROM: user imports a legally owned NTSC-U 1.0 ROM at runtime. Do not package ROM or decompressed ROM in the APK.
- Mods/texture packs: defer full mod manager support until the base game boots; use app-private storage for later support.

### App shape

Create a new Android Gradle project under `android/` that builds a native library from the existing CMake project:

- `android/settings.gradle`
- `android/build.gradle`
- `android/app/build.gradle`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/.../MainActivity.kt` or Java
- `android/app/src/main/cpp/android_entry.cpp` if a JNI/native entry shim is needed
- `android/app/src/main/assets/` for non-copyrighted bundled assets only

Preferred native model:

1. Use SDL2's Android activity/native integration if compatible with the current SDL2 window/audio/input path.
2. Build existing C++ as either:
   - a native executable launched via SDL's main bridge, or
   - a shared library loaded by an Android activity that calls into a refactored `banjo_main(int argc, char** argv)`.
3. Refactor `src/main/main.cpp` so the platform-independent runtime startup is not tied directly to a desktop `main()`:
   - Extract the body into `int banjo_recomp_main(int argc, char** argv)`.
   - Keep desktop `int main(...)` as a small wrapper.
   - Add Android entry wrapper if SDL/Gradle requires `SDL_main` or JNI entry.

---

## 3. Dependencies / Toolchain

### Required local tools

- Android Studio or command-line Android SDK.
- Android NDK r26+ or newer.
- CMake 3.22+ from the Android SDK.
- Ninja.
- Java 17+ for modern Android Gradle Plugin.
- Git submodules initialized:
  - `git submodule update --init --recursive`

### Third-party native dependencies to verify

- SDL2 for Android:
  - Use a vendored SDL2 source submodule or Android prefab package.
  - Current desktop CMake finds system SDL2 on Linux and fetches Windows SDL2; Android needs a new branch.
- Vulkan:
  - Link Android Vulkan loader: `vulkan` / NDK Vulkan headers and library.
  - Ensure `PLUME_SDL_VULKAN_ENABLED` and `RT64_SDL_WINDOW_VULKAN` definitions are active for Android.
- Freetype:
  - Desktop Linux uses `find_package(Freetype REQUIRED)`; Android needs a vendored/static build or dependency from RT64/RecompFrontend if already bundled.
- nativefiledialog-extended (`nfd`):
  - Replace or compile out for Android; use Android Storage Access Framework via Java/Kotlin instead.
- DXC / shader pipeline:
  - Audit RT64 shader compilation expectations. If shaders compile at build time, add host-tool paths. If runtime DXC is required on device, this is a major risk and needs a Vulkan/SPIR-V strategy for Android.

---

## 4. Bite-Sized Implementation Tasks

### Task 1: Record the baseline desktop build state

**Objective:** Confirm the repository is initialized and document current desktop build requirements before Android changes.

**Files:**
- Read: `BUILDING.md`
- Read: `CMakeLists.txt`
- Read: `src/main/main.cpp`
- Create: `docs/plans/android-port-notes.md`

**Steps:**
1. Run: `git submodule update --init --recursive`
2. Verify submodules: `git submodule status --recursive`
3. If generated files are absent, note that `N64Recomp` and `RSPRecomp` must be run before any native build.
4. Write the baseline notes, including required ROM hash from `BUILDING.md`.
5. Commit: `docs: record Android port baseline`

### Task 2: Add an Android CMake option without changing desktop behavior

**Objective:** Introduce Android-specific build switches safely.

**Files:**
- Modify: `CMakeLists.txt`

**Steps:**
1. Add an option near the existing Linux/Flatpak option:
   - `option(BANJO_ANDROID "Configure BanjoRecomp for Android." OFF)` only when `ANDROID` is true, or rely on CMake's `ANDROID` variable.
2. Add Android compile definitions:
   - `PLUME_SDL_VULKAN_ENABLED`
   - `RT64_SDL_WINDOW_VULKAN`
   - possibly `BANJO_ANDROID`
3. Do not change Windows/Linux/macOS branches.
4. Configure desktop build to ensure no regression:
   - `cmake -S . -B build-cmake -G Ninja -DCMAKE_BUILD_TYPE=Release`
5. Commit: `build: add Android build switch scaffolding`

### Task 3: Split platform-independent startup from desktop main

**Objective:** Make the runtime callable from Android without duplicating 800 lines of startup logic.

**Files:**
- Modify: `src/main/main.cpp`
- Create: `include/banjo_android_entry.h` or `include/banjo_main.h`

**Steps:**
1. Extract most of `int main(int argc, char** argv)` into:
   - `int banjo_recomp_main(int argc, char** argv)`
2. Keep desktop entrypoint:
   - `int main(int argc, char** argv) { return banjo_recomp_main(argc, argv); }`
3. Guard the desktop-only console and icon code with existing platform macros.
4. Build desktop to verify no behavior change.
5. Commit: `refactor: split runtime startup from desktop main`

### Task 4: Add Android app skeleton

**Objective:** Create an Android app module that can build and load native code.

**Files:**
- Create: `android/settings.gradle`
- Create: `android/build.gradle`
- Create: `android/app/build.gradle`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/io/github/banjorecomp/MainActivity.kt`
- Create: `android/app/src/main/res/values/strings.xml`

**Steps:**
1. Add Gradle Android application module.
2. Configure `externalNativeBuild.cmake.path` to point to repository `CMakeLists.txt` or a wrapper CMake file.
3. Restrict first build to `abiFilters "arm64-v8a"`.
4. Add Vulkan feature declaration:
   - `<uses-feature android:name="android.hardware.vulkan.version" android:version="0x00400000" android:required="true" />` or choose a conservative optional declaration after testing.
5. Add a minimal activity that loads the native library or extends SDL activity if using SDL's Android bridge.
6. Run: `./gradlew :app:tasks`
7. Commit: `build: add Android Gradle app skeleton`

### Task 5: Decide SDL2 Android integration path

**Objective:** Make SDL window/audio/input available on Android.

**Files:**
- Modify: `android/app/build.gradle`
- Modify: `CMakeLists.txt`
- Possibly create: `android/app/src/main/java/.../SDLActivity` integration files if vendoring SDL.

**Steps:**
1. Evaluate whether SDL2 is already available through a CMake package in the Android build environment.
2. If not, vendor SDL2 source as a submodule or documented external dependency.
3. Add Android CMake branch:
   - include SDL2 headers
   - link SDL2 library / shared object
   - avoid Linux X11 and desktop Freetype discovery in Android branch
4. Create a tiny SDL Android smoke test target if needed.
5. Commit: `build: wire SDL2 for Android`

### Task 6: Replace or stub native file dialog on Android

**Objective:** Remove desktop NFD dependency from Android native startup.

**Files:**
- Modify: `src/main/main.cpp`
- Modify/search in: `lib/RecompFrontend` call sites for file selection APIs
- Create: Android Java/Kotlin file picker bridge if needed

**Steps:**
1. Guard `#include "nfd.h"`, `NFD_Init()`, and `NFD_Quit()` with `#if !defined(__ANDROID__)`.
2. For Android, define no-op initialization and route ROM import through Java/Kotlin Storage Access Framework.
3. Add a native API or config path that points to an already-imported ROM in app-private storage.
4. Commit: `android: replace desktop file dialog dependency`

### Task 7: Fix Android window creation and platform guards

**Objective:** Make `create_window()` compile and run on Android.

**Files:**
- Modify: `src/main/main.cpp`
- Inspect: `lib/N64ModernRuntime/ultramodern/include/ultramodern/renderer_context.hpp`
- Inspect: `lib/RecompFrontend/recompui/src/renderer/rt64_render_context.cpp`

**Steps:**
1. Avoid including or using `SDL_syswm.h` on Android unless SDL documents support for it.
2. Skip `SDL_GetWindowWMInfo()` for Android; it is only used for Windows icon handling and Apple native window/layer extraction.
3. Keep Android path returning `WindowHandle{ window }`.
4. Add Android-specific `SDL_CreateWindow` sizing flags if required:
   - likely fullscreen or desktop fullscreen rather than fixed 1600x900.
5. Verify CMake compile gets past `main.cpp` for Android.
6. Commit: `android: adapt SDL window creation`

### Task 8: Handle Android app-private paths and assets

**Objective:** Ensure config, fonts, controller DB, mods, texture packs, and ROM import work under Android scoped storage.

**Files:**
- Modify: `src/main/main.cpp`
- Inspect: `lib/RecompFrontend/recompui` file utility APIs
- Inspect: `assets/`
- Inspect: `recompcontrollerdb.txt`
- Create/modify Android asset packaging in `android/app/build.gradle`

**Steps:**
1. Package non-copyrighted assets/fonts/controller DB with the APK or copy them into app-private storage on first launch.
2. Replace assumptions that files are next to the executable:
   - `recompcontrollerdb.txt`
   - fonts: `InterVariable.ttf`, `Suplexmentary Comic NC.ttf`
   - `assets/`
3. Register config path to app-private storage, not arbitrary working directory.
4. For ROM import, copy the user's selected ROM URI to app-private storage and pass its path to native code.
5. Commit: `android: route assets and config to app storage`

### Task 9: Make preload and desktop-only OS code Android-safe

**Objective:** Remove expected false/error paths and desktop-only assumptions.

**Files:**
- Modify: `src/main/main.cpp`

**Steps:**
1. Change the Linux/Apple preload branch to include Android:
   - `#elif defined(__linux__) || defined(__APPLE__) || defined(__ANDROID__)`
2. Avoid printing preload failure on Android.
3. Check all `_WIN32`, `__linux__`, `__gnu_linux__`, `APPLE`, and `__APPLE__` guards for accidental Android inclusion/exclusion.
4. Commit: `android: make platform guards explicit`

### Task 10: Add Android CMake target shape

**Objective:** Build native code as Android expects.

**Files:**
- Modify: `CMakeLists.txt`
- Maybe create: `android/app/src/main/cpp/CMakeLists.txt` wrapper

**Steps:**
1. Decide whether Android builds `BanjoRecompiled` as:
   - `add_library(BanjoRecompiled SHARED ...)`, or
   - SDL-managed native executable target.
2. Prefer a shared library if using Java/Kotlin `Activity` + JNI.
3. Add target properties and link libraries for Android:
   - `android`
   - `log`
   - `vulkan`
   - SDL2
   - `atomic` if needed
4. Avoid Linux-only `find_package(X11)` and `target_link_libraries(... -static-libstdc++)` on Android.
5. Commit: `build: add Android native target linkage`

### Task 11: Add a renderer-bypass/lazy-renderer startup mode

**Objective:** Let the Android app prove native startup, storage, ROM handling, SDL lifecycle, audio, and input before RT64 first-frame work is solved.

**Files:**
- Modify: `src/main/main.cpp`
- Inspect/modify: `lib/RecompFrontend/recompui/src/renderer/rt64_render_context.cpp`
- Inspect/modify: `lib/N64ModernRuntime/ultramodern/include/ultramodern/renderer_context.hpp`
- Create/modify: Android startup glue from Task 4/10

**Steps:**
1. Add an Android-only build/runtime switch, e.g. `BANJO_ANDROID_RENDERER_STUB` or `BANJO_ANDROID_LAZY_RENDERER`.
2. Ensure Android can start to a controlled "missing ROM" or launcher state without constructing RT64 immediately.
3. Stub or defer calls that require a live RT64 swapchain until after storage and ROM import are confirmed.
4. Keep the stub strictly Android-only; desktop behavior must be unchanged.
5. Log clearly when the renderer is bypassed so it cannot be mistaken for a playable build.
6. Verify desktop configure still works:
   - `cmake -S . -B build-cmake -G Ninja -DCMAKE_BUILD_TYPE=Release`
7. Commit: `android: add renderer-bypass startup mode`

### Task 12: Audit minimal RT64 Android/Vulkan path

**Objective:** Identify the smallest RT64 changes needed for Android first-frame rendering instead of attempting a full renderer port up front.

**Files:**
- Inspect/modify: `lib/rt64/CMakeLists.txt`
- Inspect/modify: `lib/rt64/src/hle/rt64_application_window.cpp`
- Inspect: `lib/rt64/src/contrib/plume/plume_vulkan.cpp:2076-2140`
- Inspect: `lib/rt64/src/contrib/plume/CMakeLists.txt`
- Inspect: `lib/RecompFrontend/recompui/src/renderer/rt64_render_context.cpp`
- Inspect: top-level `CMakeLists.txt` RT64 options and DXC/SPIR-V setup
- Create/modify: `docs/plans/android-port-notes.md`

**Steps:**
1. Prefer the existing SDL Vulkan route for Android: pass `SDL_Window*` and use `SDL_Vulkan_CreateSurface` via `PLUME_SDL_VULKAN_ENABLED`.
2. In RT64 CMake, allow `RT64_SDL_WINDOW_VULKAN` / `PLUME_SDL_VULKAN_ENABLED` when `CMAKE_SYSTEM_NAME` is `Android`, not just Linux.
3. In `rt64_application_window.cpp`, remove Android `static_assert(false)` only when `RT64_SDL_WINDOW_VULKAN` is enabled; keep unsupported native-window paths explicit.
4. Avoid `SDL_SysWMinfo` on Android when the SDL Vulkan path already uses `SDL_Window*`.
5. Make fullscreen/window placement calls no-op or SDL-only on Android.
6. Confirm shader generation is build-time SPIR-V using host DXC, not runtime DXC on Android. If cross-compiling breaks host tool selection, document a host-tool override such as `RT64_DXC_HOST_PATH` before coding it.
7. Document blockers and decisions in `docs/plans/android-port-notes.md`.
8. Commit: `docs: document minimal RT64 Android path`

### Task 13: First native configure attempt

**Objective:** Get the Android build to configure, even if compilation fails.

**Files:**
- Modify as needed from compiler errors.

**Steps:**
1. Run from `android/`:
   - `./gradlew :app:externalNativeBuildDebug --stacktrace`
2. Fix only configure-time errors:
   - missing package paths
   - platform branches
   - unavailable libraries
3. Do not chase large compile failures yet.
4. Commit: `build: make Android native configure pass`

### Task 14: First compile milestone

**Objective:** Compile all non-renderer/core native code for Android.

**Files:**
- Modify platform guards in source files shown by compiler errors.

**Steps:**
1. Run: `./gradlew :app:externalNativeBuildDebug --stacktrace`
2. Categorize errors:
   - Android unsupported POSIX/desktop API
   - missing headers/libs
   - compiler flags invalid for Android
   - RT64 renderer issues
3. Fix small platform guard issues first.
4. Defer RT64 architectural blockers to separate tasks.
5. Commit: `android: compile core native sources`

### Task 15: Boot to launcher without ROM

**Objective:** Launch Android app and display the launcher/error state without bundled game assets.

**Files:**
- Modify Android activity and native startup.
- Modify storage/asset path handling.

**Steps:**
1. Install debug APK:
   - `./gradlew :app:installDebug`
2. Launch via Android Studio or:
   - `adb shell monkey -p <package.name> 1`
3. Capture logs:
   - `adb logcat | grep -i banjo`
4. Expected result: app opens and prompts for ROM or shows a controlled missing-ROM message, not a crash.
5. Commit: `android: boot launcher without ROM`

### Task 16: Implement ROM import flow

**Objective:** Let the user select a legal ROM and copy it to app-private storage.

**Files:**
- Modify: `MainActivity.kt`
- Add: file picker / Activity Result API code
- Modify: native config/startup bridge as needed

**Steps:**
1. Use Android Storage Access Framework `ACTION_OPEN_DOCUMENT`.
2. Copy selected URI to app-private storage.
3. Validate ROM hash in native or Java/Kotlin before enabling launch.
4. Do not store or ship any ROM in the repo/APK.
5. Commit: `android: add legal ROM import flow`

### Task 17: Input milestone

**Objective:** Support external controllers through SDL first.

**Files:**
- Modify: Android packaging for `recompcontrollerdb.txt`
- Inspect: `lib/RecompFrontend/recompinput`
- Inspect: `src/main/main.cpp:702-706`

**Steps:**
1. Ensure controller DB is accessible from Android storage or assets.
2. Test with Bluetooth/XInput-compatible controller.
3. Verify N64 input callbacks still call `recompinput::profiles::get_n64_input`.
4. Defer touchscreen overlay to later milestone.
5. Commit: `android: enable controller input path`

### Task 18: Audio milestone

**Objective:** Verify SDL audio output works on Android.

**Files:**
- Inspect/modify: `src/main/main.cpp:223-355`

**Steps:**
1. Run on a device with app logs visible.
2. Check `SDL_OpenAudioDevice` success.
3. Tune `spec_desired.samples` if Android latency/popping occurs.
4. Commit: `android: validate SDL audio path`

### Task 19: Vulkan/rendering milestone

**Objective:** Render first frame on a Vulkan-capable Android device.

**Files:**
- Inspect/modify: `lib/rt64`
- Inspect/modify: `lib/RecompFrontend/recompui/src/renderer/rt64_render_context.cpp`
- Modify: Android manifest feature declarations

**Steps:**
1. Verify Vulkan instance/device creation on Android logs.
2. Fix required Android surface extension usage if RT64/plume does not handle it.
3. Confirm swapchain creation and presentation.
4. Disable desktop-only graphics options if necessary for first boot.
5. Commit: `android: render first frame with Vulkan`

### Task 20: Package and smoke-test APK

**Objective:** Produce a debug APK that boots and runs at least the intro/first playable scene on one test device.

**Files:**
- Modify Android Gradle config as needed.
- Update docs.

**Steps:**
1. Build: `./gradlew :app:assembleDebug`
2. Install: `./gradlew :app:installDebug`
3. Import legal ROM.
4. Smoke-test:
   - app launches
   - ROM validation succeeds
   - audio starts
   - controller input works
   - renderer presents frames
   - save/config writes to app-private storage
5. Commit: `android: produce first smoke-test APK`

### Task 21: Documentation and release hygiene

**Objective:** Document Android build/use steps and legal boundaries.

**Files:**
- Create: `ANDROID_BUILDING.md`
- Modify: `README.md` if desired
- Modify: `.gitignore`

**Steps:**
1. Document SDK/NDK/CMake versions.
2. Document submodule initialization.
3. Document ROM requirements and hash.
4. Document APK build/install commands.
5. Explicitly state that releases do not contain game assets.
6. Commit: `docs: add Android building guide`

---

## 5. Risks / Legal Notes

### Legal / distribution

- Do not commit or package ROM files, decompressed ROMs, extracted copyrighted assets, or generated assets derived from copyrighted ROM data if distribution would be unlawful.
- Keep the existing project policy: users must provide their own legal copy of the game.
- APK releases should contain only original project code, open-source dependencies, and non-copyrighted project assets.

### Technical risks

- RT64 Android support is the largest unknown. Vulkan exists on Android, but desktop renderer assumptions may require upstream RT64/plume work.
- DXC/shader compilation may need a build-time SPIR-V pipeline or Android-compatible runtime shader strategy.
- SDL2 Android lifecycle is different from desktop; pause/resume, surface loss, audio focus, and controller hotplug need testing.
- Native file dialogs do not map directly to Android; use the Storage Access Framework.
- Android scoped storage requires app-private storage and URI import instead of arbitrary filesystem paths.
- Touch controls are not part of the first milestone; controller support should come first.
- Performance and thermal throttling may require graphics defaults tuned down for mobile.

---

## 6. Testing / Verification

### Static/build verification

- Desktop configure still works:
  - `cmake -S . -B build-cmake -G Ninja -DCMAKE_BUILD_TYPE=Release`
- Android Gradle task list works:
  - `cd android && ./gradlew :app:tasks`
- Android native configure works:
  - `cd android && ./gradlew :app:externalNativeBuildDebug --stacktrace`
- Debug APK builds:
  - `cd android && ./gradlew :app:assembleDebug`

### Device verification

- Install:
  - `cd android && ./gradlew :app:installDebug`
- Launch:
  - `adb shell monkey -p <package.name> 1`
- Logs:
  - `adb logcat | grep -i -E 'banjo|recomp|rt64|vulkan|sdl'`

### Runtime acceptance criteria

1. App starts without crashing on a Vulkan-capable arm64 Android device.
2. App does not include copyrighted ROM/assets in the APK.
3. User can import a legal NTSC-U 1.0 ROM via Android file picker.
4. ROM hash validation rejects wrong ROMs with a clear message.
5. Game reaches launcher or first playable scene.
6. SDL audio initializes and outputs sound.
7. External controller input works.
8. App-private config/save paths persist across restart.
9. Android back/home/pause/resume do not corrupt saves or crash the renderer.

---

## 7. Recommended Milestone Order

1. Android build skeleton configures.
2. Native code compiles for arm64-v8a without renderer runtime validation.
3. App launches and reaches a controlled missing-ROM UI/message.
4. ROM import and hash validation work.
5. Audio/input work.
6. Vulkan first frame works.
7. Playable smoke test.
8. Touch controls, mod management, release packaging, and performance tuning.

---

## 8. Notes from attempted Codex run

I attempted to delegate this plan to Codex CLI from the repository root with:

```bash
npx -y @openai/codex exec --full-auto "Create a detailed implementation plan for porting this BanjoRecomp project to Android..."
```

The Codex CLI launched, but the run failed with `401 Unauthorized` against the OpenAI Responses API, meaning the standalone Codex CLI is installed/runnable through `npx` but is not authenticated in this environment. This plan was then prepared from direct repository inspection instead.

