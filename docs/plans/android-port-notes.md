# BanjoRecomp Android Port Notes

## RT64 workaround decision

We should not fully port RT64 before proving the Android app shell and native runtime. The practical path is:

1. Add Android build/app scaffolding.
2. Add an Android-only renderer-bypass or lazy-renderer mode so startup, storage, ROM import, SDL lifecycle, audio, and input can be validated before renderer work.
3. Bring up the smallest RT64 Android Vulkan path needed for first frame.

## Minimal RT64 path to try first

Prefer the existing SDL Vulkan path instead of a raw `ANativeWindow` port:

- Android uses SDL2 already for window/input/audio.
- BanjoRecomp currently passes `SDL_Window*` through the Linux/Android renderer path.
- RT64/Plume already has `PLUME_SDL_VULKAN_ENABLED` support using `SDL_Vulkan_CreateSurface`.
- Plume also has raw Android `vkCreateAndroidSurfaceKHR` code, but using SDL avoids threading `ANativeWindow*` through more layers at the start.

Concrete first RT64 changes to evaluate:

- In `lib/rt64/CMakeLists.txt`, enable `PLUME_SDL_VULKAN_ENABLED` and `RT64_SDL_WINDOW_VULKAN` for `CMAKE_SYSTEM_NAME MATCHES "Android"`, not just Linux.
- In `lib/rt64/src/hle/rt64_application_window.cpp`, remove Android `static_assert(false)` only for the `RT64_SDL_WINDOW_VULKAN` case.
- Avoid `SDL_SysWMinfo` on Android when the window handle is just `SDL_Window*`.
- Make fullscreen/window placement behavior no-op or SDL-only on Android.

## Known RT64 Android blockers

- `lib/rt64/src/hle/rt64_application_window.cpp` has explicit `static_assert(false && "Android unimplemented")` branches.
- RT64 CMake currently only enables the SDL Vulkan integration for Linux.
- RT64 shader generation uses DXC and emits SPIR-V at build time. During Android cross-compilation, CMake may incorrectly choose target-arch/tool paths instead of host tools. If that happens, add a host DXC override rather than trying to run DXC on-device.
- Vulkan device/swapchain creation still needs testing on a physical Android device; Vulkan support alone does not guarantee RT64 desktop assumptions hold.

## Current recommendation

Proceed in two tracks:

- Track A: Android shell + native startup with renderer bypass/lazy renderer.
- Track B: minimal SDL Vulkan RT64 path for first frame.

Do not block Track A on full RT64 correctness.

## Initial implementation notes

Implemented the first code-side slice of the plan:

- `src/main/main.cpp` now excludes desktop `nfd.h`, `SDL_syswm.h`, `SDL_GetWindowWMInfo()`, desktop icon handling, `NFD_Init()`, and `NFD_Quit()` from Android builds.
- Top-level CMake now exposes `BANJO_ANDROID_RENDERER_STUB`, defaulting ON only for Android, and defines `BANJO_ANDROID_RENDERER_STUB=1` when enabled.
- `recompui` has `NullRendererContext`, selected by `BANJO_ANDROID_RENDERER_STUB`, so Android can get through early native startup without constructing RT64.
- `lib/rt64/CMakeLists.txt` enables `PLUME_SDL_VULKAN_ENABLED` and `RT64_SDL_WINDOW_VULKAN` for Android as well as Linux.
- `rt64_application_window.cpp` keeps Android unsupported in non-SDL-window paths but permits the SDL Vulkan path and skips `SDL_SysWMinfo` on Android.
- `nativefiledialog-extended` now treats Android as its own platform and builds a no-op `nfd_null.cpp` backend instead of trying to use the Linux GTK/portal backend.

Verification added:

- `tools/check_android_port_guards.py` checks the Android guards and stub-renderer path without requiring an Android NDK.
- `nfd_null.cpp` compiles standalone with host `g++` against the NFD header.

Current environment/tooling status:

- User-local Android build tooling is installed and sourceable via `~/.config/android-build-env.sh`.
- Android arm64 SDL2 and Freetype prefixes are available under `~/Android/prefixes`.
- `tools/build_android_shell.sh` configures and builds the first Android native shell slice.
- Verified output: `build/hermes-android-shell/libBanjoAndroidShell.so` is an Android arm64 shared object for API 24.
- `android/` now contains a minimal Gradle/AGP Android app that builds an APK with:
  - `MainActivity.java`
  - `libBanjoAndroidShell.so`
  - `libSDL2.so`
- Verified APK output: `android/app/build/outputs/apk/debug/app-debug.apk`.
- Verified the APK does not package ROM/generation artifacts matching `z64`, `rom`, `RecompiledFuncs`, `n_aspMain`, or `RecompiledPatches`.

Current project-content blocker:

- Full `BANJO_ANDROID_SHELL_ONLY=OFF` configure still requires generated copyrighted-game-derived sources:
  - `RecompiledFuncs/*.c` / `*.cpp`
  - `rsp/n_aspMain.cpp`
  - `RecompiledPatches/*`
- Host `N64Recomp` and `RSPRecomp` have been built and copied to the repo root, but generation cannot run until a legally owned decompressed `banjo.us.v10.decompressed.z64` is present.
- Keep those generated files/ROM artifacts out of commits unless the project/legal policy explicitly allows them.

## Device smoke test

The debug APK was installed and launched on an AYN Thor connected over ADB (`arm64-v8a`, Android API 33).

Verification:

- `gradle --no-daemon :app:assembleDebug` succeeds from `android/`.
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` succeeds.
- `adb shell am start -n io.github.banjorecomp/.MainActivity` launches the app.
- Logcat shows:
  - `native shell library loaded; SDL compiled version 2.32.10`
  - `native probe OK; SDL linked version 2.32.10`
- Screenshot confirms the activity displays: `Banjo Android native shell loaded. Probe result: 0`.

## Full native Android build smoke test

After receiving the legally owned ROM dump, the uploaded file was verified as a byte-swapped `.v64`-style image despite its `.z64` name. Swapping every 16-bit word produced the expected big-endian `baserom.us.v10.z64` input, then `bk_rom_decompress` produced `banjo.us.v10.decompressed.z64`.

Generation/build verification:

- Built `bk_rom_decompress` with Cargo and generated the decompressed ROM locally.
- Ran `./N64Recomp banjo.us.rev0.toml` and `./RSPRecomp n_aspMain.us.rev0.toml` to regenerate the game/RSP sources.
- Configured Android arm64 with `BANJO_ANDROID_SHELL_ONLY=OFF` and `BANJO_ANDROID_RENDERER_STUB=ON`.
- Built `build/hermes-android-full/BanjoRecompiled` successfully as an Android API 24 arm64 PIE executable.
- Pushed `BanjoRecompiled` plus `libSDL2.so` to `/data/local/tmp/banjo-full` on the AYN Thor and ran it from `adb shell`.

Observed device result:

- The first run crashed because `recompui::message_box()` called SDL's Android message-box path without Java `Activity` state; the tombstone pointed at `Android_JNI_ShowMessageBox`.
- After changing Android `message_box()` behavior to log instead of opening an SDL dialog, the executable starts far enough to report the next blocker cleanly:
  - `[ERROR] No audio device could be found. Please make sure an audio device is available.`
  - `Error opening audio device: Audio subsystem is not initialized`
- Logcat also reports SDL warnings about environment access before JNI is ready, which is expected for this standalone `adb shell` executable path.

Current interpretation:

- The generated full native Android binary now cross-compiles and links.
- A standalone shell execution is useful for linker/startup triage but is not a valid final app lifecycle test; SDL audio/video need proper Android Activity/JNI lifecycle integration.
- The Gradle APK is still the safe shell-only APK and still contains only `libBanjoAndroidShell.so` and `libSDL2.so`; it does not package ROM/generated game artifacts.

ROM/generated artifacts remain local-only and must stay out of commits/packages unless project/legal policy explicitly allows them.

## SDLActivity lifecycle probe

Next milestone was to prove the proper Android SDL lifecycle, without packaging ROM/generated game artifacts:

- Vendored SDL 2.32.10 Android Java glue under `android/app/src/main/java/org/libsdl/app/`.
- Added `io.github.banjorecomp.BanjoSDLActivity`, derived from `org.libsdl.app.SDLActivity`.
- Added `src/android/sdl_lifecycle_probe.cpp`, exporting `SDL_main` from `libmain.so`.
- Added `BANJO_ANDROID_SDL_LIFECYCLE_PROBE` so shell-only Android builds can build a lifecycle probe instead of the old JNI text-view probe.
- Updated the debug Android app to launch `.BanjoSDLActivity` and package only:
  - `lib/arm64-v8a/libSDL2.so`
  - `lib/arm64-v8a/libmain.so`

Verification on AYN Thor over ADB:

- `gradle --no-daemon :app:assembleDebug` succeeds.
- `python3 tools/check_android_port_guards.py` succeeds.
- APK artifact scan finds no `baserom`, `decompressed`, `.z64`, `banjo.us`, `RecompiledFuncs`, or `RecompiledPatches` entries.
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` succeeds.
- `adb shell am start -n io.github.banjorecomp/.BanjoSDLActivity` succeeds.
- Logcat confirms the lifecycle path:
  - `nativeSetupJNI()`
  - `surfaceCreated()` / `surfaceChanged()`
  - `Running main function SDL_main from library .../libmain.so`
  - `SDL_main entered`
  - `SDL linked version 2.32.10`
  - `SDL_Init succeeded; video=Android audio=openslES`
  - `SDL window created`
  - `SDL lifecycle probe completed`

This proves the earlier standalone `adb shell` audio failure was lifecycle-related, not a basic SDL/NDK/audio-device absence. The right next step is to connect the real Banjo runtime to `SDLActivity`/`SDL_main`, while keeping ROM/generated artifacts out of normal APK packaging until policy is explicit.

## Dev-only artifact packaging approval

Aure explicitly approved including the ROM and derived/generated files only for development purposes. Treat this as permission for local debug APKs installed to the development device, not as permission for release/distribution artifacts. Keep this path gated behind an explicit development-only build option/flavor and keep release packaging clean by default.

Implemented gated dev path:

- Gradle property `-PbanjoDevFull=true` switches the debug APK from shell/probe mode to the full `SDLActivity` runtime path.
- CMake option `BANJO_ANDROID_DEV_FULL_APK=ON` builds the real Banjo runtime as Android `libmain.so` and exports `SDL_main`.
- `android/app/build.gradle` passes Android SDL2/Freetype prefixes, disables unneeded Zstd programs/tests/dictbuilder for Android, packages `libSDL2.so`, and copies dev-full files under `assets/program/` only for the dev-full build.
- Dev-full packaging now includes non-ROM program resources (`assets/` and `recompcontrollerdb.txt`) plus the dev-only ROM files under `assets/program/dev-roms/`.
- `BanjoSDLActivity` copies `assets/program/` into app-private storage on launch and sets `APP_PROGRAM_PATH` to that filesystem directory after SDL initializes JNI.
- `recompui::file::get_program_path()` uses `APP_PROGRAM_PATH` on Android, with `SDL_AndroidGetInternalStoragePath()/program` as fallback, so existing desktop resource lookups like `get_program_path()/assets/...` resolve to normal files.
- `recompui::file::get_app_folder_path()` now uses `SDL_AndroidGetInternalStoragePath()` on Android, avoiding the invalid `/data/.config/...` desktop-Linux fallback.

Dev-full APK verification on AYN Thor over ADB:

- `gradle --no-daemon :app:assembleDebug -PbanjoDevFull=true` succeeds.
- APK scan confirms expected packaged entries:
  - `lib/arm64-v8a/libSDL2.so`
  - `lib/arm64-v8a/libmain.so`
  - `assets/program/assets/InterVariable.ttf`
  - `assets/program/assets/promptfont/promptfont.ttf`
  - `assets/program/assets/recomp.rcss`
  - `assets/program/recompcontrollerdb.txt`
  - `assets/program/dev-roms/banjo.us.v10.decompressed.z64`
  - `assets/program/dev-roms/baserom.us.v10.z64`
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` succeeds.
- `adb shell am start -n io.github.banjorecomp/.BanjoSDLActivity` succeeds.
- Logcat confirms SDL lifecycle, program asset extraction, and real runtime entry:
  - `Copied program assets to /data/user/0/io.github.banjorecomp/files/program`
  - `nativeSetupJNI()`
  - `surfaceCreated()` / `surfaceChanged()`
  - `Running main function SDL_main from library .../libmain.so`
  - `APP_PROGRAM_PATH=/data/user/0/io.github.banjorecomp/files/program`
- `run-as io.github.banjorecomp ls -l files/program/...` confirms copied files exist on-device, including `recompcontrollerdb.txt`, fonts, `recomp.rcss`, and both dev ROM files.
- The process stayed alive after launch (`pidof io.github.banjorecomp` returned PID `16468`) with no `Fatal signal`, `SIGABRT`, or `AndroidRuntime` crash in the captured log window.
- Screenshot `/tmp/banjo-android-devfull-20260524-222506.png` shows the app launched under `Banjo: Recompiled`; current visible output is still a mostly black window/header, so this remains a startup/lifecycle/resource-path smoke pass, not a playable/rendering pass.
- Generated game/RSP code is compiled into `libmain.so`, not packaged as separate asset files.

Next practical milestone: investigate why the full UI/launcher is still not visibly rendering after filesystem-backed resources are available. Start with filtered logcat around RmlUi/resource loading and renderer initialization, then trace whether the Android renderer path is drawing frames, blocked on missing documents/styles/images, or intentionally idling before the launcher state.

## Dev-full RT64 renderer smoke test

The previous dev-full APK was still using the Android `NullRendererContext` because Gradle did not pass `BANJO_ANDROID_RENDERER_STUB=OFF` for `-PbanjoDevFull=true`. That made the black-window result expected rather than evidence about RT64. The Gradle CMake args now explicitly set:

- `BANJO_ANDROID_RENDERER_STUB=OFF` for `-PbanjoDevFull=true`
- `BANJO_ANDROID_RENDERER_STUB=ON` for the default shell/probe build

First real-renderer launch result:

- `gradle --no-daemon :app:assembleDebug -PbanjoDevFull=true --rerun-tasks` succeeded and produced a dev APK containing `libmain.so`, `libSDL2.so`, program assets, and the gated dev ROMs.
- Installing and launching that APK initially crashed in RT64 startup with:
  - `filesystem error: in create_directories: Permission denied ["/data/.rt64"]`
- Root cause: RT64's `UserPaths::detectDataPath()` treated Android as Linux, fell back through `$HOME`/`getpwuid()`, and selected `/data/.rt64`, which is outside app-owned storage.
- Fix: `lib/rt64/src/common/rt64_user_paths.cpp` now handles `__ANDROID__` before `__linux__` and uses `SDL_AndroidGetInternalStoragePath() / ".<appId>"`, matching the app-private storage rule already used by `recompui`.

Post-fix verification on AYN Thor over ADB:

- `gradle --no-daemon :app:assembleDebug -PbanjoDevFull=true` succeeds.
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` succeeds.
- `adb shell am start -n io.github.banjorecomp/.BanjoSDLActivity` succeeds.
- The process remains alive after launch (`pidof io.github.banjorecomp` returned PID `20012`).
- Filtered logcat shows SDLActivity lifecycle, Vulkan layer discovery, and Adreno Vulkan driver startup:
  - `Running main function SDL_main from library .../libmain.so`
  - `APP_PROGRAM_PATH=/data/user/0/io.github.banjorecomp/files/program`
  - `D vulkan: searching for layers ...`
  - `AdrenoVK-0: Application Name : plume`
- The prior `/data/.rt64` permission crash, `filesystem_error`, `Fatal signal`, and `SIGABRT` no longer appear in the captured log window.
- Screenshot `/tmp/banjo-android-real-renderer-after-storage-fix-20260524-224455.png` still shows only the `Banjo: Recompiled` title/header and a black content area. This is progress: the real RT64/Vulkan path now starts without the storage crash, but first visible UI/game rendering is not solved yet.

Next practical milestone: keep the real renderer enabled and instrument RT64/RmlUi frame progression. Specifically verify whether Rml documents/styles are loaded, whether the launcher document is attached to a context, whether `RT64Context::update_screen()` is being called, and whether Vulkan swapchain presentation is occurring after the initial Plume/Adreno startup.

## First visible Android RT64/RmlUi output

The dev-full real-renderer path now reaches visible launcher UI on device.

Confirmed blockers and fixes:

- Plume's Android/SDL Vulkan path was aborting swapchain format selection when RT64 requested a desktop-preferred format not present in Android's `vkGetPhysicalDeviceSurfaceFormatsKHR()` list. Because the constructor returned before choosing an Android surface format, later resize code created the swapchain with `VK_FORMAT_UNDEFINED` (`format=0`) and image views with `viewFormat=0`. This produced black/no visible swapchain output even though command buffers submitted and present returned success.
- Fix: for `__ANDROID__`, if surface formats exist, pick `surfaceFormats[0]` instead of failing on desktop requested-format incompatibility.
- Follow-up fix: after swapchain image retrieval, update Plume's swapchain `desc.format` to the RenderFormat matching the chosen native surface format (`R8G8B8A8_UNORM` for Vulkan format 37; handle `B8G8R8A8_UNORM` too if the surface exposes that). Without this, framebuffer/render-pass/pipeline metadata can still disagree with the swapchain image view format.
- Keep `createInfo.preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR`; using Android `currentTransform` made the launcher render visibly but rotated relative to the device/status bar.
- Keep Android resize using `surfaceCapabilities.currentExtent` when available; the tested device reported `1920x1025`, matching the content area below the status bar.
- Another self-inflicted blocker was an Android diagnostic guard in `recompui/src/base/ui_state.cpp` that skipped `ui_state->context->Render()` and only cleared purple. Removing that probe restored RmlUi geometry submission.

Verification on AYN Thor over ADB:

- `gradle --no-daemon :app:assembleDebug -PbanjoDevFull=true` succeeds.
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` succeeds.
- `adb shell am start -n io.github.banjorecomp/.BanjoSDLActivity` succeeds.
- Screenshot `/home/hermes/tmp/banjo-identity-transform-6s.png` shows the Banjo-Recompiled launcher, correctly oriented in landscape, with menu entries `Load ROM`, `Controls`, `Settings`, `Mods`, and `Exit`.
- Filtered logcat shows the expected RmlUi lifecycle and repeated rendering:
  - `Rml::Initialise complete`
  - `Rml context created: 1920x1025`
  - fonts load successfully
  - `create_menus done`
  - `showing launcher from draw_hook`
  - `render UI #... framebuffer=1920x1025`

Current status: first visible RT64/RmlUi presentation is solved for the dev-full Android APK. Next milestone is interactive smoke testing: input/touch/controller behavior, ROM-loading flow, audio, and actual game frame rendering after selecting/loading the dev ROM.

## Android dev-full Load ROM and first game rendering

The dev-full Android APK now gets through the launcher ROM flow and into game rendering on device.

Confirmed blockers and fixes:

- `Load ROM` was still routed through `recompui::file::open_file_dialog()`, which calls NFD. Android is currently built with the null NFD backend, so tapping `Load ROM` returned `false` and silently stayed on the launcher.
- Fix: on Android, `GameOptionsMenu::select_rom()` first tries the dev-full packaged ROM path `get_program_path()/dev-roms/baserom.us.v10.z64` and feeds that to `recomp::select_rom()` before falling back to the native file dialog path. This is appropriate for gated `-PbanjoDevFull=true` smoke testing only.
- After the ROM fallback, first game startup initially crashed in the Adreno Vulkan driver inside `vkUpdateDescriptorSets`, called via Plume `VulkanDescriptorSet::setTexture()` from RT64 texture/framebuffer rendering.
- Descriptor-boundary diagnostics isolated the crash to sampled-image descriptor updates for storage/unordered texture-cache outputs later sampled by draw passes. Null image views, descriptor type mismatch, unsupported format features, descriptor-update threading, and “descriptor set still in use” were ruled out with targeted probes.
- A real fix was the bindless/boundless descriptor layout mismatch in Plume: the variable descriptor allocation used `boundlessRangeSize`, but the layout binding still used the source range `count`. The layout binding now uses `std::max(descriptorSetDesc.boundlessRangeSize, 1U)` for the final boundless range, matching allocation and descriptor indexing.

Verification on AYN Thor over ADB:

- `gradle --no-daemon :app:assembleDebug -PbanjoDevFull=true` succeeds.
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` succeeds.
- Starting `io.github.banjorecomp/.BanjoSDLActivity`, tapping `Load ROM`, then waiting returns to the launcher with `Start Game` shown, proving the packaged ROM was accepted and stored.
- Tapping `Start Game` no longer crashes in `vkUpdateDescriptorSets`.
- Around 40 seconds after `Start Game`, screenshot `/home/hermes/tmp/banjo-after-start-game-boundless-40s.png` shows live game imagery. Later visual review showed rendering itself is acceptable; the earlier “washed out” concern was not the primary issue.
- The intro/cutscene appeared to stop midway and runtime performance felt poor. Logcat showed heavy `AudioTrack: stop(...)` churn; one 5-second sample captured hundreds of stops while the process remained alive and foreground.
- The Android build was still using the desktop-oriented SDL queued-audio buffer size of `0x100` frames. On Android/AudioTrack this is too small for this workload and causes repeated underrun/stop behavior.
- Android-only fix: raise `SDL_AudioSpec.samples` to `0x800` frames in `reset_audio()`, keeping the desktop value at `0x100`.
- Verification after the audio-buffer change: dev-full build/install succeeds, `Load ROM` and `Start Game` reach the Banjo-Kazooie title screen, and the same logcat window reports `AudioTrack stop total 0` with no fatal/native crash entries.

Current status: ROM loading, first game rendering, and intro/title progression are now proven on Android dev-full. The first cutscene stall/perceived-performance issue was Android audio underrun churn, fixed by using a larger Android SDL audio buffer. Remaining polish should focus on real gameplay/input testing and performance profiling with Graphics -> Framerate set to `Original` or `Display`; `Manual 60` is a bad default for judging Android performance.

## Android dev-full freeze and CPU performance follow-up

A later recurring freeze was reproduced after the audio-buffer fix, but the evidence showed it was not audio:

- A long frame-hash watcher detected repeated identical screenshots while the process stayed alive.
- Logcat had no repeated `AudioTrack: stop(...)` churn and no fatal crash.
- Temporary timer instrumentation showed a one-shot `OSTimer` repeatedly firing while already about 30 seconds overdue:
  - `timer=0x802806B0 interval=0 mq=0x8027FB60 msg=0xD`
- Thread CPU during the freeze had `[Game] VIMGR` and `Timer Thread` pegged near one core each.

Root cause and fix:

- `timer_thread()` stored active timers in `std::set<PTR(OSTimer)>` ordered by `OSTimer::timestamp`.
- `osSetTimer()` mutates the pointed-to `OSTimer::timestamp` before the timer thread removes/reinserts that pointer.
- Mutating comparator-visible data while an entry is in `std::set` violates the container ordering invariant. Key-based `active_timers.erase(timer)` can then miss stale entries, leaving duplicate/past-due one-shot timers that fire forever.
- Fix: remove existing timer entries by pointer identity before insert/remove/reload, erase the current timer by iterator, and defensively remove one-shot stale duplicates after firing.

Clean validation:

- Rebuilt and installed the dev-full APK after removing temporary BanjoTimer logging.
- Clean watcher reached 49 consecutive 5-second samples with changing frame hashes before manual interruption; no freeze signature appeared.
- User also reported not seeing the previous cutscene freezes.

Separate CPU-performance finding:

- The Android APK was being built with Gradle `assembleDebug`, which drove CMake with `CMAKE_BUILD_TYPE=Debug`.
- `compile_commands.json` showed `-g` but no `-O*` flags, meaning Clang native code was effectively `-O0`.
- Before optimization, a thread sample during demo/cutscene gameplay showed heavy CPU:
  - `Thread-4` avg ~68.6% of one core
  - `SP Task Thread` avg ~50.7%
  - `RT64 Workload` avg ~20.2%
- `SP Task Thread` is `ultramodern::rsp::run_task()`, so running generated/RSP/RT64-heavy native code at `-O0` is not representative.
- Fix: when `-PbanjoDevFull=true`, Gradle now enables native optimization by default via `banjoNativeOptimized=true`, adding `-O2 -g -DNDEBUG` to both C and C++ compile flags. It can be disabled with `-PbanjoNativeOptimized=false` if true native Debug behavior is needed.
- Verification after a clean CMake rebuild:
  - `compile_commands.json` contains `-O2` and `-DNDEBUG` for 766 native compile commands.
  - Same thread sample dropped to low single-digit CPU averages:
    - `Thread-4` avg ~4.8%
    - `SP Task Thread` avg ~2.2%
    - `RT64 Workload` avg ~3.7%
  - No fatal crash, ANR, Vulkan error, or Choreographer skip spam appeared in the sampled log window.

Current interpretation: the remaining bad cutscene/demo performance was primarily an artifact of testing an unoptimized Android native Debug build, not intrinsic Android overhead. Use the optimized dev-full build for performance judgement.
## Android dev-full Mods menu integration

The Android dev-full Mods menu now has platform-specific behavior instead of silently relying on desktop folder/file-manager assumptions.

Confirmed blockers and fixes:

- `Install Mods` was wired to the desktop/native-file-dialog flow, but Android currently builds NFD with the null backend. Tapping the button therefore did nothing useful.
- Fix: on Android, `ModMenu::open_install_dialog()` calls into `BanjoSDLActivity.openModFilePicker()` via JNI. The activity launches `ACTION_OPEN_DOCUMENT` with multi-select enabled and accepts ZIP/RTZ-ish MIME types. Selected documents are copied into app-private cache files and passed back to native through `nativeOnModsSelected()`.
- Native callback handling pushes SDL drop-file events for the copied cache paths, so the existing mod installation path can continue to process the files without a separate Android-only mod parser.
- `Open Mods Folder` cannot honestly open the app-private mods directory in a normal external file manager on Android. Fix: on Android, the button now creates the mods directory and shows a clear message explaining that the directory is app-private, advising users to use `Install Mods`, and displaying the internal path.
- Launching Android DocumentsUI exposed an RT64/Plume Android surface lifecycle bug: while the picker has focus, Android destroys the SDL `SurfaceView`; the render thread was still touching swapchain/surface resources tied to the abandoned native window, causing Adreno/Vulkan crashes or a black surface on return.
- Fixes:
  - RT64 present queue now creates swapchain framebuffers lazily only after successful swapchain image acquisition, avoiding eager framebuffer creation against stale swapchain images.
  - Plume's Android SDL Vulkan path recreates the `VkSurfaceKHR` when rebuilding the swapchain after Android surface loss/recreation.
  - Java SDL surface callbacks now report Android surface readiness to native; Plume treats the swapchain as unavailable while the Java surface is destroyed, preventing `SDL_Vulkan_CreateSurface()` from running while SDL's native window pointer is invalid.

Verification on AYN Thor over ADB:

- `gradle --no-daemon :app:assembleDebug -PbanjoDevFull=true` succeeds.
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` succeeds.
- Starting `io.github.banjorecomp/.BanjoSDLActivity`, opening Mods, tapping `Install Mods`, and canceling DocumentsUI returns to a rendered Mods screen with no crash-buffer fatal entries and the process still alive.
- Tapping `Open Mods Folder` shows an in-app `Mods Folder` dialog explaining Android app-private storage and the internal mods path instead of silently doing nothing.

Current status: both Mods buttons now have Android-appropriate behavior in the dev-full APK. The install path is verified through picker launch/cancel and lifecycle recovery; a real ZIP/RTZ import should still be tested with a valid mod file to verify end-to-end mod parsing/install success.


## Android audio/focus lifecycle

Issue fixed:
- Android recents/task switcher caused audio focus loss before Activity pause. Music could keep playing while recents was visible, gameplay continued advancing in the background, and returning to the game could produce loud static.

Implemented behavior:
- `BanjoSDLActivity` tracks `activityResumed && windowFocused` and calls `nativeSetAppAudioActive(active)` whenever that state changes.
- Native Android lifecycle handling now sets an Ultramodern app-paused flag on focus loss/gain.
- The VI thread checks that flag and sleeps while paused, preventing VI/AI/screen update messages from advancing gameplay while recents/task switcher is visible.
- SDL queued-audio calls are ignored while inactive and report 0 frames remaining.
- On Android deactivate, the SDL audio device is paused, queued audio is cleared, then the device is closed.
- On Android reactivate, the SDL audio device is reopened, cleared, and unpaused. This avoids stale AudioTrack/queued-buffer state after resume.

Verification:
- Built dev-full APK successfully with `gradle --no-daemon :app:assembleDebug -PbanjoDevFull=true`.
- Installed over wireless ADB on `192.168.0.66:39385`.
- Opening recents logs `Android app active=false` with SDL focus loss.
- While recents is held, previous draw-hook instrumentation stopped advancing after app inactive, confirming runtime pause at the VI thread gate.
- Returning to the game logs `Android app active=true`; app remains alive and crash buffer has no Banjo fatal entries.
- Final clean build removed temporary `BanjoRecompUI` draw/render diagnostic logging.

Manual user check still recommended:
- Confirm by ear that static is gone after task switcher/sleep/resume. ADB cannot verify audio quality directly.
