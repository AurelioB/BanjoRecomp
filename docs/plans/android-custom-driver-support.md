# Android Custom Graphics Driver Support Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Add Android-only support for selecting a rootless custom Vulkan graphics driver such as Turnip, resetting to the system/default driver, and showing which driver is loaded at runtime.

**Architecture:** Keep the feature Android-only and optional. Import driver packages into app-private storage from Android DocumentsUI, persist only driver metadata/path selection in config, initialize Vulkan through AdrenoTools before RT64/Plume creates its Vulkan instance, and fall back safely to the system driver if custom loading fails. Surface creation should stay on the current SDL Vulkan path unless testing proves SDL binds the wrong Vulkan loader; only then add an Android-custom-driver surface bypass.

**Tech Stack:** Android Java `BanjoSDLActivity`, JNI, C++17/20, Gradle/NDK, SDL2, Vulkan/volk, RT64/Plume, libadrenotools, existing recompui config UI.

---

## Current repo facts this plan depends on

- Android package: `com.aure.banjorecomp`.
- Activity: `io.github.banjorecomp.BanjoSDLActivity`.
- Native runtime library: `libmain.so` launched through SDLActivity `SDL_main`.
- Android build already uses `packaging { jniLibs { useLegacyPackaging true } }` in `android/app/build.gradle`, which AdrenoTools requires because `hookLibDir` must match `ApplicationInfo.nativeLibraryDir`.
- Java already imports documents for ROMs/mods in `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`.
- Android app-private paths are already established by Java env vars:
  - `APP_PROGRAM_PATH=<files>/program`
  - `APP_FOLDER_PATH=<files>/data`
- RT64/Plume Vulkan initialization is centralized in `lib/rt64/src/contrib/plume/plume_vulkan.cpp`, currently calling `volkInitialize()` before `vkCreateInstance()`.
- Vendored volk supports `volkInitializeCustom(PFN_vkGetInstanceProcAddr)` in `lib/rt64/src/contrib/plume/contrib/volk/volk.h`.
- Existing Android Vulkan surface path uses SDL helpers:
  - `SDL_Vulkan_GetInstanceExtensions()`
  - `SDL_Vulkan_CreateSurface()`

---

## Non-goals / constraints

- Do not bundle Turnip/custom driver binaries in normal release APKs.
- Do not store selected driver libraries on `/sdcard` or shared storage. They must be copied into app-private storage before `dlopen()`.
- Do not make custom driver the default. Default remains Android system Vulkan driver.
- Do not rewrite RT64 renderer logic to chase custom-driver bugs before proving the loader path with a smoke probe.
- Do not treat this as cross-vendor GPU support. This is primarily Qualcomm/Adreno/Turnip.

---

## UX requirements

Add a Graphics settings section visible only on Android:

1. **Graphics Driver** status row / selector:
   - `System Default` when no custom driver is selected or when fallback is active.
   - Driver display name when a custom driver is selected and loaded, e.g. `Turnip Mesa 25.x` if metadata exists, otherwise sanitized folder/file name.
   - Failure state when selection exists but runtime fell back, e.g. `System Default (custom driver failed: missing vkGetInstanceProcAddr)`.

2. **Select Custom Driver** action:
   - Opens Android file picker.
   - Accepts a `.zip` first. Optionally accept raw `.so` later for dev convenience.
   - Copies/extracts into app-private storage.
   - Validates required files.
   - Saves selection.
   - Shows a message that restart is required.

3. **Reset to System Driver** action:
   - Clears selected custom driver config.
   - Leaves imported driver files on disk initially, or deletes only the active driver directory if a confirmation path exists.
   - Shows a message that restart is required.

4. **Loaded Driver indication**:
   - Runtime must report the actually loaded path, not just the selected config.
   - At minimum expose:
     - selected mode: system/custom
     - load result: not attempted/success/fallback/error
     - Vulkan physical device name/vendor/driverVersion from `vkGetPhysicalDeviceProperties`
   - Show this in Graphics settings and logcat.

---

## Storage layout

Use app-private files directory:

```text
/data/user/0/com.aure.banjorecomp/files/gpu-drivers/
  active.json
  imports/
    <driver-id>/
      driver.json              # generated metadata
      libvulkan_freedreno.so   # common Turnip soname
      ...optional support files
  tmp/
```

`active.json` should contain only durable selection metadata, for example:

```json
{
  "mode": "custom",
  "driver_id": "turnip-2026-07-03-abcdef",
  "display_name": "Turnip 25.x",
  "driver_dir": "/data/user/0/com.aure.banjorecomp/files/gpu-drivers/imports/turnip-2026-07-03-abcdef",
  "driver_soname": "libvulkan_freedreno.so"
}
```

Do not persist broad source URI permissions unless needed later. Copy the selected package into app-private storage and operate on that copy.

---

## Task 1: Add Android driver path/env discovery

**Objective:** Make native code aware of app-private driver directories and Android native library directory before Vulkan initializes.

**Files:**
- Modify: `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`
- Modify: `src/main/main.cpp` or new Android helper called during `banjo_recomp_main()` startup

**Steps:**

1. In `BanjoSDLActivity.onCreate()`, compute:
   - `File gpuDriverRoot = new File(getFilesDir(), "gpu-drivers")`
   - `File gpuDriverTmp = new File(gpuDriverRoot, "tmp")`
   - `String nativeLibraryDir = getApplicationInfo().nativeLibraryDir`
2. Create directories if missing.
3. Set native environment variables before `SDL_main` runs far enough to initialize RT64:

```java
nativeSetenv("BANJO_GPU_DRIVER_ROOT", gpuDriverRoot.getAbsolutePath());
nativeSetenv("BANJO_GPU_DRIVER_TMP", gpuDriverTmp.getAbsolutePath());
nativeSetenv("BANJO_NATIVE_LIBRARY_DIR", getApplicationInfo().nativeLibraryDir);
```

4. Log these values except avoid dumping arbitrary imported package contents.
5. Add guard logging in native startup to confirm these env vars are present on Android.

**Verification:**

```sh
source ~/.config/android-build-env.sh
python3 tools/check_android_port_guards.py
gradle --no-daemon -p android :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity
adb logcat -d | grep -E 'BANJO_GPU_DRIVER_ROOT|BANJO_NATIVE_LIBRARY_DIR|SDL_main'
```

Expected: env-var log lines are present and app still launches with the system driver.

---

## Task 2: Add a small Android custom-driver metadata module

**Objective:** Centralize custom-driver selection, status, and JSON persistence outside the renderer.

**Files:**
- Create: `src/android/custom_driver_manager.hpp`
- Create: `src/android/custom_driver_manager.cpp`
- Modify: `CMakeLists.txt`

**API sketch:**

```cpp
namespace banjo::android::custom_driver {
    enum class Mode { System, Custom };
    enum class LoadState { NotAttempted, Disabled, LoadedCustom, FallbackSystem, Failed };

    struct Selection {
        Mode mode = Mode::System;
        std::string driver_id;
        std::string display_name;
        std::string driver_dir;
        std::string driver_soname;
    };

    struct RuntimeStatus {
        Selection selection;
        LoadState load_state = LoadState::NotAttempted;
        std::string message;
        std::string loaded_driver_label;
        std::string physical_device_name;
        uint32_t vendor_id = 0;
        uint32_t device_id = 0;
        uint32_t driver_version = 0;
    };

    std::filesystem::path root_dir();
    std::filesystem::path active_json_path();
    Selection load_selection();
    bool save_selection(const Selection& selection, std::string* error);
    bool reset_to_system(std::string* error);
    void set_runtime_status(RuntimeStatus status);
    RuntimeStatus get_runtime_status();
}
```

**Implementation notes:**

- Use `BANJO_GPU_DRIVER_ROOT` env var on Android.
- Use `nlohmann::json`, already present in the tree.
- Keep this module Android-only; compile it only when `CMAKE_SYSTEM_NAME MATCHES "Android"`.
- For non-Android builds, either do not compile it or provide stubs behind `#if defined(__ANDROID__)`.

**Verification:**

- Add a tiny unit-style CLI path is probably not worth it here.
- Verify by building Android APK and grepping symbols/strings:

```sh
gradle --no-daemon -p android :app:assembleDebug
strings android/app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libmain.so | grep -E 'gpu-drivers|LoadedCustom|FallbackSystem'
```

Expected: strings are present; desktop build should not pick up Android-only source.

---

## Task 3: Add Java driver import picker and app-private copy/extract

**Objective:** Allow the UI/native layer to request a driver package import using Android DocumentsUI.

**Files:**
- Modify: `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`

**Steps:**

1. Add request code:

```java
private static final int REQUEST_SELECT_GPU_DRIVER = 1003;
```

2. Add public method callable from JNI:

```java
public void openGpuDriverFilePicker() { ... }
```

3. Open `ACTION_OPEN_DOCUMENT` with MIME filters:
   - `application/zip`
   - `application/octet-stream`
   - `application/x-zip-compressed`
4. In `onActivityResult`, handle `REQUEST_SELECT_GPU_DRIVER`.
5. Copy selected file into `getCacheDir()/driver-imports/` first.
6. If `.zip`, extract into `getFilesDir()/gpu-drivers/imports/<sanitized-name>-<timestamp>/`.
7. Validate at least one supported Vulkan driver soname exists. Initial soname search order:
   - `libvulkan_freedreno.so`
   - `vulkan.freedreno.so`
   - `libvulkan.so`
8. Generate `driver.json` with display name, soname, import time, and original sanitized filename.
9. Call native callback:

```java
private static native void nativeOnGpuDriverImported(
    String driverId,
    String displayName,
    String driverDir,
    String driverSoname,
    String error);
```

If validation fails, pass `error` and null/empty path values.

**Security notes:**

- Reject zip entries containing `..`, absolute paths, or empty names.
- Do not extract outside the selected import directory.
- Do not execute or `dlopen()` from cache/shared storage.

**Verification:**

- Build APK.
- Import a deliberately invalid zip and verify native callback/log reports validation failure.
- Import a zip containing dummy `libvulkan_freedreno.so` and verify metadata is written, but do not try to load it until Task 6.

---

## Task 4: Add JNI bridge for driver import/reset/status

**Objective:** Connect Java import/reset operations to native config and expose native status back to Java/UI.

**Files:**
- Modify or create near existing Android JNI code. Search current JNI functions first:
  - `nativeOnModsSelected`
  - `nativeOnRomSelected`
  - `nativeSetAndroidSurfaceReady`
  - `nativeSetAppAudioActive`
- Likely modify one of:
  - `src/main/main.cpp`
  - Android-specific source under `src/android/`

**Native callbacks:**

```cpp
extern "C" JNIEXPORT void JNICALL
Java_io_github_banjorecomp_BanjoSDLActivity_nativeOnGpuDriverImported(
    JNIEnv* env,
    jclass,
    jstring driver_id,
    jstring display_name,
    jstring driver_dir,
    jstring driver_soname,
    jstring error);
```

**Native functions callable from UI:**

Add functions in a small C++ interface used by recompui:

```cpp
namespace banjo::android::custom_driver {
    void request_driver_picker();
    bool reset_to_system_from_ui(std::string* error);
    RuntimeStatus get_runtime_status();
}
```

`request_driver_picker()` should use existing safe JNI pattern:

- `SDL_AndroidGetJNIEnv()`
- `SDL_AndroidGetActivity()`
- `env->GetObjectClass(activity)`
- call Java instance method `openGpuDriverFilePicker()`
- delete local refs

Avoid `FindClass()` from SDL/game threads.

**Verification:**

- Add log lines for callbacks.
- Build and launch.
- Invoke picker from a temporary debug hook if UI is not wired yet.

---

## Task 5: Add Graphics UI options

**Objective:** Add Android-only Graphics settings controls for selecting/resetting the custom driver and showing loaded status.

**Files:**
- Modify: `lib/RecompFrontend/recompui/include/recompui/config.h`
- Modify: `lib/RecompFrontend/recompui/src/config/ui_config_tab_graphics.cpp`
- Possibly modify: `lib/RecompFrontend/recompui/src/config/ui_config_option.*` if a button/action option does not already exist
- Possibly create Android-specific UI helper in app repo if keeping RecompFrontend generic is cleaner

**Recommended UI model:**

The current config system supports enum/number/string/bool, but not obvious action buttons. Do not abuse a string text field as a button. Prefer one of these approaches:

1. **Best:** add a reusable `ConfigOptionAction` / button option to RecompFrontend.
2. **Fallback:** create a custom Android-only tab/section via `recompui::config::create_tab()` if the config system makes button rows awkward.

**Options/labels:**

Under Graphics, Android-only:

- `Graphics Driver` enum or status row:
  - `System Default`
  - `<selected driver display name>` if selected
- `Select Custom Driver...` action button
- `Reset to System Driver` action button
- Status/details text:
  - `Loaded: System Default / Turnip ...`
  - `Device: <VkPhysicalDeviceProperties.deviceName>`
  - `Vendor: 0x... Driver: 0x...`
  - `Restart required` after changing selection

**Important behavior:**

- The selected driver cannot safely switch live after RT64 has initialized Vulkan. Mark changes as requiring app restart.
- Reset should immediately update config selection but actual loaded driver remains unchanged until restart. UI must say that.
- On non-Android builds, none of this UI appears.

**Verification:**

- Android build succeeds.
- Non-Android configure/build still succeeds or at least compiles UI without Android-only symbols.
- On device, Graphics tab shows driver state.
- Select opens DocumentsUI.
- Reset clears selection and shows restart-required status.

---

## Task 6: Add AdrenoTools dependency behind Android build option

**Objective:** Link libadrenotools only on Android and only when custom-driver support is enabled.

**Files:**
- Modify: `CMakeLists.txt`
- Possibly add submodule/vendor directory:
  - `lib/adrenotools/` or `third_party/adrenotools/`
- Modify: `.gitmodules` if using submodule
- Modify: `android/app/build.gradle` only if the library must be copied/packaged specially

**Build option:**

```cmake
option(BANJO_ANDROID_CUSTOM_VULKAN_DRIVER "Enable Android custom Vulkan driver support through AdrenoTools." ON)
```

**Dependency requirement:**

Use `libadrenotools` headers:

```cpp
#include <adrenotools/driver.h>
```

The APK must package the AdrenoTools hook library in `nativeLibraryDir`. Keep `useLegacyPackaging true`.

**Verification:**

```sh
gradle --no-daemon -p android :app:assembleDebug
unzip -l android/app/build/outputs/apk/debug/app-debug.apk | grep -E 'adreno|libmain|libSDL2'
```

Expected: required hook/native library is packaged if AdrenoTools uses one; no Turnip/custom driver blob is packaged by default.

---

## Task 7: Teach Plume/Vulkan init to use custom `vkGetInstanceProcAddr`

**Objective:** Load the selected custom Vulkan driver before Plume calls `vkCreateInstance`, with safe fallback to system driver.

**Files:**
- Modify: `lib/rt64/src/contrib/plume/plume_vulkan.cpp`
- Possibly modify: `lib/rt64/src/contrib/plume/plume_vulkan.h`
- Modify: `src/android/custom_driver_manager.*`

**Implementation shape:**

In `VulkanInterface::VulkanInterface(...)`, replace unconditional Android `volkInitialize()` with a helper:

```cpp
#if defined(__ANDROID__) && defined(BANJO_ANDROID_CUSTOM_VULKAN_DRIVER)
VkResult res = banjo::android::custom_driver::initialize_vulkan_loader_for_volk();
#else
VkResult res = volkInitialize();
#endif
```

`initialize_vulkan_loader_for_volk()` should:

1. Load active selection from `active.json`.
2. If mode is system/default, call `volkInitialize()` and set status `Disabled`.
3. If custom selected, call:

```cpp
void* lib_vulkan = adrenotools_open_libvulkan(
    RTLD_NOW | RTLD_LOCAL,
    ADRENOTOOLS_DRIVER_CUSTOM,
    tmp_lib_dir,
    native_library_dir,
    custom_driver_dir,
    custom_driver_soname,
    nullptr,
    nullptr);
```

4. Resolve:

```cpp
auto gipa = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
    dlsym(lib_vulkan, "vkGetInstanceProcAddr"));
```

5. If `gipa` exists:

```cpp
volkInitializeCustom(gipa);
```

6. If anything fails, log the reason, set status `FallbackSystem`, and call `volkInitialize()`.

**Do not crash on bad drivers.** A bad imported driver should be recoverable through Reset/System Default.

**Verification:**

- With no driver selected, app logs `custom driver disabled; using system Vulkan`.
- With invalid selected driver, app logs fallback and still launches.
- With real Turnip driver selected, app logs custom load success or a clear failure reason.

---

## Task 8: Record actual loaded driver information

**Objective:** Make the UI indication reflect the actual Vulkan device/driver, not stale config.

**Files:**
- Modify: `lib/rt64/src/contrib/plume/plume_vulkan.cpp`
- Modify: `src/android/custom_driver_manager.*`
- Modify: Graphics UI from Task 5

**Implementation:**

After physical device selection / device description is available, populate runtime status from `VkPhysicalDeviceProperties`:

```cpp
VkPhysicalDeviceProperties props;
vkGetPhysicalDeviceProperties(physicalDevice, &props);
status.physical_device_name = props.deviceName;
status.vendor_id = props.vendorID;
status.device_id = props.deviceID;
status.driver_version = props.driverVersion;
```

If custom load succeeded, label should be:

```text
Loaded: <selection.display_name> (<props.deviceName>, driver 0x...)
```

If fallback happened:

```text
Loaded: System Default — custom driver failed: <reason>
```

**Verification:**

```sh
adb logcat -d | grep -E 'BanjoGpuDriver|Device Name|Driver Version|Loaded:'
```

Expected: log lines identify selected/fallback state and physical device.

---

## Task 9: Add same-APK Vulkan smoke probe for system vs custom driver

**Objective:** Prove the custom loader path independently before blaming RT64.

**Files:**
- Modify: `src/android/vulkan_smoke_probe.cpp`
- Modify: `CMakeLists.txt`
- Possibly expose trigger through debug build property or intent extra

**Current status:** `src/android/vulkan_smoke_probe.cpp` already creates an SDL Vulkan window and swapchain using volk.

**Changes:**

- Add mode parameter: system/custom.
- Run loader initialization through the same custom-driver helper as Plume.
- Log:
  - selected driver
  - whether custom/system loader was used
  - instance extensions
  - physical device name/vendor/driver version
  - surface creation success
  - first clear/present result

**Acceptance:**

- System mode still passes on known device.
- Custom mode either passes with Turnip or fails with a specific loader/surface error.
- If custom loader passes smoke probe but RT64 fails, debug RT64/Plume separately.

---

## Task 10: Decide whether SDL Vulkan surface path is compatible

**Objective:** Verify whether `SDL_Vulkan_CreateSurface()` is safe with the custom-loaded Vulkan handle.

**Procedure:**

1. Run smoke probe with system driver.
2. Run smoke probe with Turnip/custom driver.
3. Compare:
   - `vkEnumeratePhysicalDevices` count
   - physical device name/vendor/driver version
   - `SDL_Vulkan_CreateSurface` result
   - swapchain creation result
   - present result

**Decision:**

- If SDL path works: keep it. Do not add raw `ANativeWindow` plumbing.
- If SDL path fails or binds system loader: add a narrow Android-custom-driver-only surface path in Plume:
  - enable `VK_KHR_android_surface`
  - obtain the current native window safely
  - call `vkCreateAndroidSurfaceKHR` through the custom dispatch path
  - keep normal SDL path for system driver and all desktop platforms

**Do not do this until smoke-probe evidence says it is necessary.**

---

## Task 11: Add user-facing reset/safe fallback behavior

**Objective:** Make sure a bad driver cannot brick the app into a crash loop.

**Files:**
- Modify: `BanjoSDLActivity.java`
- Modify: `src/android/custom_driver_manager.*`
- Modify: Graphics UI

**Minimum fallback:**

- Any custom load failure falls back to system driver automatically.
- UI shows the failure reason after launch.
- Reset button clears selection.

**Better fallback:**

- Add an intent extra or debug property:

```sh
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity --ez banjo_force_system_driver true
```

- Java sets `BANJO_FORCE_SYSTEM_DRIVER=1` before native startup.
- Native ignores active custom selection when this env var is set.

**Verification:**

- Select invalid driver.
- Relaunch.
- App does not crash.
- UI says fallback occurred.
- Reset returns status to `System Default` after restart.

---

## Task 12: Documentation and release hygiene

**Objective:** Document how users and testers should use the feature without implying bundled driver support.

**Files:**
- Modify: `docs/android-port.md`
- Modify: `docs/android-port-findings.md`
- Modify: `android/README.md`
- Possibly modify: `.hermes.md` if new canonical commands are added

**Docs must cover:**

- Custom driver support is Android/Adreno-focused.
- Drivers are imported by the user and copied to app-private storage.
- Normal builds do not bundle Turnip/custom driver blobs.
- Switching/resetting drivers requires app restart.
- Reset/safe-mode command.
- How to capture logs:

```sh
adb logcat -c
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity
adb logcat -d | grep -E 'BanjoGpuDriver|Adreno|Turnip|Vulkan|Device Name|Driver Version'
```

---

## Final verification checklist

Run before considering the feature done:

```sh
source ~/.config/android-build-env.sh
python3 tools/check_android_port_guards.py
gradle --no-daemon -p android :app:assembleDebug
sha256sum android/app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/build-tools/36.0.0/aapt dump badging android/app/build/outputs/apk/debug/app-debug.apk | sed -n '1,14p'
unzip -l android/app/build/outputs/apk/debug/app-debug.apk | grep -E 'lib/(arm64-v8a/)?(libmain|libSDL2|adreno)|gpu-drivers|turnip|freedreno|\.z64|RecompiledFuncs|n_aspMain'
```

Expected:

- Guard checks pass.
- APK builds.
- Package/activity remain:
  - `com.aure.banjorecomp`
  - `io.github.banjorecomp.BanjoSDLActivity`
- Normal APK does not contain ROM/generated artifacts.
- Normal APK does not contain bundled Turnip/custom driver blobs.
- AdrenoTools support library is present only if required by the integration.

Device tests:

1. Fresh install, no selected driver → app launches with system driver, UI says `System Default`.
2. Select invalid zip → validation error, no crash.
3. Select invalid `.so`/dummy driver → import may succeed only if validation allows dev mode; launch falls back to system driver with clear reason.
4. Select real Turnip package → smoke probe succeeds or gives specific failure.
5. Launch full game with custom driver selected → device/driver status visible in UI/logcat.
6. Reset to default → after restart, system driver is used.
7. Recents/surface recreation still works; no regression in existing SDL/Vulkan lifecycle behavior.

---

## Suggested commit sequence

1. `docs: plan Android custom graphics driver support`
2. `android: expose app-private GPU driver paths to native runtime`
3. `android: add custom driver metadata persistence`
4. `android: import GPU driver packages into app-private storage`
5. `android: add graphics driver selection UI`
6. `android: add AdrenoTools-backed Vulkan loader path`
7. `android: report loaded Vulkan driver status`
8. `android: extend Vulkan smoke probe for custom drivers`
9. `docs: document Android custom driver support`

---

## Risk assessment

- **Low risk:** UI/status/import metadata if kept Android-only.
- **Medium risk:** AdrenoTools dependency packaging and `volkInitializeCustom` path.
- **High risk:** SDL Vulkan surface compatibility with custom-loaded Vulkan. Prove with smoke probe before modifying RT64 surface creation.
- **Operational risk:** Bad drivers can crash or fail enumeration. Fallback and reset are required, not polish.
