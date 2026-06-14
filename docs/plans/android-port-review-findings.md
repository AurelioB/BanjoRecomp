# Android Port Review Findings

**Review date:** 2026-05-25

**Backup made before review:** `/home/hermes/Projects/backups/BanjoRecomp-android-port-20260525-132738`

**Scope reviewed:** current working tree for the BanjoRecomp Android port, including top-level repo changes and dirty submodules:

- `CMakeLists.txt`
- `src/main/main.cpp`
- `android/`
- `src/android/`
- `tools/`
- `docs/plans/`
- `lib/N64ModernRuntime`
- `lib/RecompFrontend`
- `lib/rt64`

**Current known-good behavior:** debug full APK builds and installs; task switcher/recents pauses gameplay/audio; resume is clean; Android in-game audio crackle improved after Android-specific queue pacing.

---

## P0 — Fix before trusting cleanup/build checks

### 1. Default SDL lifecycle probe JNI symbols — fixed

**Files:**

- `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`
- `android/app/src/main/java/org/libsdl/app/SDLSurface.java`
- `CMakeLists.txt`
- `src/android/sdl_lifecycle_probe.cpp`

**Issue:**

Earlier bring-up builds used the SDL lifecycle probe as the default non-dev Gradle path, setting:

- `BANJO_ANDROID_SHELL_ONLY=ON`
- `BANJO_ANDROID_SDL_LIFECYCLE_PROBE=ON`

That built `libmain.so` from `src/android/sdl_lifecycle_probe.cpp`. The vendored SDL Java glue calls:

- `BanjoSDLActivity.nativeSetAndroidSurfaceReady(false)`
- `BanjoSDLActivity.nativeSetAndroidSurfaceReady(true)`

Those native functions were initially implemented only for the full renderer path. Probe APKs could therefore hit `UnsatisfiedLinkError` during surface lifecycle. The current default Gradle build is no longer this probe path; it is the real runtime APK unless `-PbanjoProbe=true` is passed.

**Fix:**

`src/android/sdl_lifecycle_probe.cpp` now exports no-op/logging JNI stubs for `nativeSetAndroidSurfaceReady()` and `nativeSetAppAudioActive()`.

**Verification:**

- `gradle -p android :app:assembleDebug -PbanjoProbe=true --stacktrace`

---

### 2. Android guard script — fixed

**File:**

- `tools/check_android_port_guards.py`

**Observed command:**

```bash
python3 tools/check_android_port_guards.py
```

**Old result:**

```text
FAIL: Android full build branch must link SDL2 plus android/log
```

**Issue:**

The script expected older literal CMake wiring. CMake now uses `${BANJO_RECOMP_TARGET}` and links additional Android dependencies such as Vulkan. The script is useful, but was producing a false failure.

**Fix:**

The guard script was updated for the current CMake/Gradle structure and now passes.

**Verification:**

- `python3 tools/check_android_port_guards.py`

---

## P1 — Important cleanup before commit/PR

### 3. Generated artifacts are untracked and should be ignored/cleaned

**Examples:**

- `android/.gradle/...`
- `android/app/build/...`
- `android/app/.cxx/...`
- `tools/__pycache__/check_android_port_guards.cpython-313.pyc`

**Issue:**

These are build/cache outputs and should not be part of source review or commits.

**Recommended fix:**

Add Android/Gradle/CMake cache patterns to `.gitignore`, remove local generated files from the working tree, then re-run `git status --short`.

---

### 4. Legacy probe/shell code needs deletion or explicit documentation — fixed

**Files:**

- `src/android/sdl_lifecycle_probe.cpp`
- `android/README.md`
- `CMakeLists.txt`

**Issue:**

The current app launches `BanjoSDLActivity` and the working path is the full `SDL_main`/`libmain.so` path. The older native text-view shell and SDL lifecycle probe are now bring-up artifacts unless deliberately preserved.

**Fix:**

The old text-view `MainActivity` / `BanjoAndroidShell` path was retired. Android probe mode now only means the SDLActivity lifecycle probe: Gradle `-PbanjoProbe=true` passes `BANJO_ANDROID_SHELL_ONLY=ON` and `BANJO_ANDROID_SDL_LIFECYCLE_PROBE=ON`, CMake builds `src/android/sdl_lifecycle_probe.cpp` as `libmain.so`, and CMake fails fast if `BANJO_ANDROID_SHELL_ONLY` is requested without the SDL lifecycle probe. The default and dev-ROM modes remain the real `BanjoSDLActivity` / `libmain.so` runtime path.

`android/README.md` now documents the active package/activity (`com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity`), the default runtime APK, the gated bundled-dev-ROM runtime APK, and the opt-in SDL lifecycle probe. It no longer presents `MainActivity` or `libBanjoAndroidShell` as a supported path.

---

### 5. Vulkan smoke probe is explicitly optional — fixed

**Files:**

- `src/android/vulkan_smoke_probe.cpp`
- `CMakeLists.txt`

**Issue:**

The smoke probe was useful during renderer bring-up, but `android_vulkan_smoke_main()` does not appear to be used by the current app path. If compiled into the full app unconditionally, it would be dead diagnostic code and unnecessary binary/link surface.

**Fix:**

`src/android/vulkan_smoke_probe.cpp` is now behind the explicit `BANJO_ANDROID_VULKAN_SMOKE_PROBE` CMake option, defaulting off.

---

### 6. Android audio lifecycle locking should be consolidated

**File:**

- `src/main/main.cpp`

**Issue:**

`nativeSetAppAudioActive()` opens/closes `audio_device` under `android_audio_device_mutex`; `queue_samples()` and `get_frames_remaining()` also lock. But `reset_audio()` and `set_frequency()`/converter updates are not fully under the same lock. A lifecycle callback can race audio reset/frequency changes.

**Recommended fix:**

Centralize Android audio open/close/reset in helper functions that use the same mutex, or route lifecycle audio changes onto the main/audio thread. Keep the current behavior but make ownership obvious.

---

### 7. Runtime pause semantics are partial: VI pauses, timers/time do not

**Files:**

- `lib/N64ModernRuntime/ultramodern/src/events.cpp`
- `lib/N64ModernRuntime/ultramodern/src/timer.cpp`
- `lib/N64ModernRuntime/ultramodern/include/ultramodern/ultramodern.hpp`

**Issue:**

The new pause gate stops VI/event progression, which solved visible gameplay progression in recents. But timer/time code can still advance while paused. That means the name `set_app_paused()` implies broader suspension than the current implementation provides.

**Recommended fix:**

Either:

- document/rename it as a VI pause gate, or
- extend pause semantics to runtime time/timers so paused wall-clock does not leak into game time.

For Android user-visible behavior, the current fix is good enough. For engine cleanliness, it needs a clearer contract.

---

### 8. Existing timer scheduling has data-race risk

**File:**

- `lib/N64ModernRuntime/ultramodern/src/timer.cpp`

**Issue:**

`osSetTimer()` mutates `OSTimer` fields while `timer_thread` can read them. The current diff improves set cleanup but does not make timer ownership/synchronization airtight.

**Recommended fix:**

Have the timer thread own timer scheduling state, or protect scheduling fields with synchronization. This is not necessarily Android-specific, but the port touched nearby code and should not deepen the risk.

---

### 9. Android UI render logging remains in hot path

**File:**

- `lib/RecompFrontend/recompui/src/renderer/ui_renderer.cpp`

**Observed references:**

- `BANJO_ANDROID_RENDER_LOG` in `RenderGeometry()`
- `BANJO_ANDROID_RENDER_LOG` in `LoadTexture()`
- `BANJO_ANDROID_RENDER_LOG` in `GenerateTexture()`

**Issue:**

Even throttled, render-path Android logging is cleanup/debug noise and can add overhead on-device.

**Recommended fix:**

Remove it or gate behind an explicit debug build flag.

---

### 10. Android dev ROM auto-selection should be explicitly dev-only

**File:**

- `lib/RecompFrontend/recompui/src/base/ui_launcher.cpp`

**Issue:**

`select_rom()` checks `get_program_path()/dev-roms/baserom.us.v10.z64` and auto-selects it on Android. That is useful for the gated dev-full APK but should never look like production behavior.

**Recommended fix:**

Guard it behind `BANJO_ANDROID_DEV_FULL_APK` or an equivalent compile definition, and document it as local smoke-test behavior only.

---

### 11. Android swapchain format propagation — fixed

**Files:**

- `lib/rt64/src/contrib/plume/plume_vulkan.cpp`
- `lib/RecompFrontend/recompui/src/renderer/ui_renderer.cpp`

**Issue:**

Plume can accept Android-native surface formats that are not BGRA. If a device picks `R8G8B8A8_UNORM`, the UI pipeline/render target format can mismatch the actual swapchain format.

**Fix:**

The Android Vulkan swapchain path now maps native `VK_FORMAT_B8G8R8A8_UNORM` and `VK_FORMAT_R8G8B8A8_UNORM` back into `desc.format`, and swapchain textures inherit that actual render format before creating image views.

---

### 12. Android surface recreation failure path — fixed

**File:**

- `lib/rt64/src/contrib/plume/plume_vulkan.cpp`

**Issue:**

During Android surface recreation, the current surface could be destroyed before a replacement was successfully created. If `SDL_Vulkan_CreateSurface()` failed, later resize/recovery paths could be stuck with `VK_NULL_HANDLE`.

**Fix:**

The resize path now releases the old swapchain and destroys the old `VkSurfaceKHR` before asking SDL for a replacement, because SDL/Android allows only one active Vulkan surface connection to the `SurfaceView`. If replacement creation fails, it leaves both `surface` and `vk` as `VK_NULL_HANDLE`, so later resize attempts can retry instead of becoming permanently unrecoverable.

**Verification:**

- `git diff --check`
- `python3 tools/check_android_port_guards.py`
- `gradle -p android :app:assembleDebug -PbanjoDevFull=true --stacktrace`
- On-device install/launch/background-resume smoke test: process stayed alive and screenshot rendered the Banjo Recompiled main menu.

---

## P2 — Polish and maintainability

### 13. Asset copy behavior — fixed

**Files:**

- `android/app/build.gradle`
- `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`

**Issue:**

The dev-full build copies program assets and ROM-derived files into APK assets, then `BanjoSDLActivity` copied the asset tree to internal storage on every `onCreate()`. This was acceptable for a local dev APK but inefficient.

**Fix:**

`BanjoSDLActivity` now stores a `.program-assets-stamp` based on the installed APK timestamp, skips extraction when the stamp matches, clears stale copied program assets when the APK changes, and quietly skips extraction when non-dev/probe builds have no packaged `program` asset tree.

**Verification:**

- `gradle -p android :app:assembleDebug -PbanjoDevFull=true --stacktrace`
- `gradle -p android :app:assembleDebug --stacktrace`

---

### 14. Gradle project is local-machine oriented

**File:**

- `android/app/build.gradle`

**Issue:**

The Android build assumes local HOME-based SDK/NDK/dependency prefixes. That is fine for this environment but brittle for contributors/CI.

**Recommended fix:**

Document required environment variables clearly, or add fail-fast validation with actionable error messages.

---

### 15. Android paths are spread across frontend and RT64

**Files:**

- `lib/RecompFrontend/recompui/src/util/file.cpp`
- `lib/rt64/src/common/rt64_user_paths.cpp`

**Issue:**

Frontend program/config paths and RT64 paths use different app-private roots. This may be intentional, but it should be documented or centralized.

**Recommended fix:**

Define one Android path policy and use it consistently, or document why program assets, config, mods, and RT64 cache data live in different subfolders.

---

### 16. Android launcher icon is legacy PNG-only

**Files:**

- `android/app/src/main/res/mipmap-*/ic_launcher.png`
- `android/app/src/main/AndroidManifest.xml`

**Issue:**

The icon works, but it is not a modern adaptive icon. `roundIcon` points to the same PNG.

**Recommended fix:**

For release polish, add adaptive icon foreground/background XML resources if source art allows it. Current PNGs are fine for dev builds.

---

### 17. `Rml::LoadFontFace` result variables look unused

**File:**

- `lib/RecompFrontend/recompui/src/base/ui_state.cpp`

**Issue:**

Variables such as `loaded`, `primary_loaded`, and `extra_loaded` are assigned but not used. This is cleanup noise and may warn on stricter builds.

**Recommended fix:**

Either use them for error handling/logging or remove the variables.

---

### 18. Android mod drop event bridge handles `SDL_PushEvent` failure — fixed

**File:**

- `lib/RecompFrontend/recompui/src/composites/ui_mod_menu.cpp`

**Issue:**

The synthetic SDL drop-file bridge allocated `drop_event.drop.file` and assumed `SDL_PushEvent()` succeeded. If it failed, memory could leak and the DROPBEGIN/DROPCOMPLETE sequence could become inconsistent.

**Fix:**

`DROPBEGIN` failure now aborts the synthetic drop sequence. `DROPFILE` failure frees the allocated SDL path before continuing.

**Verification:**

- `git diff --check`
- `python3 tools/check_android_port_guards.py`
- `gradle -p android :app:assembleDebug -PbanjoDevFull=true --stacktrace`

---

## Recommended cleanup order

1. Add `.gitignore` entries and remove generated/cache files from the working tree.
2. Fix the default SDL lifecycle probe missing JNI stub or remove the probe path.
3. Update or delete `tools/check_android_port_guards.py`.
4. Remove/gate hot-path Android render logs.
5. Gate dev ROM auto-select behind the dev-full build flag.
6. Consolidate Android audio device lifecycle helpers and locking.
7. Clean stale Android README/docs so active build modes are clear.
8. Decide whether probe/shell/smoke code stays as documented diagnostics or is removed.
9. Review RT64/Plume surface format and surface recreation fixes before wider testing.
10. Rebuild full dev APK, then run one on-device smoke test.

---

## Verification already run during review

```bash
git diff --check
python3 tools/check_android_port_guards.py
```

Results:

- `git diff --check`: clean.
- `tools/check_android_port_guards.py`: failing/stale as noted above.
