# Android Dual-Screen Stats Display Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Add optional Android dual-screen support that shows Banjo-Kazooie pause-menu-style stats on a secondary display, initially focused on AYN Thor-class devices, only while gameplay is active.

**Architecture:** Keep the actual game renderer on SDL/RT64's existing primary display. Add a separate Android-native secondary-display UI using `DisplayManager` + `Presentation`, fed by a small JNI bridge from native/game state. Do not try to make RT64 render to two Android surfaces in the first version; that is higher risk and unnecessary for a text/icon stats panel.

**Tech Stack:** Android Java, `DisplayManager`, `Presentation`, JNI, SDLActivity lifecycle hooks, BanjoRecomp patches/native exports, Gradle/NDK.

---

## Product behavior

### MVP behavior

- Detect whether Android exposes at least one valid non-default presentation display.
- If no secondary display exists, do nothing and keep current behavior unchanged.
- If a secondary display exists, show a simple stats dashboard on that display only when gameplay is active.
- Hide or blank the secondary display during:
  - launcher/menu/front-end UI
  - ROM picker/mod picker/DocumentsUI
  - pause menu
  - app background/recents/focus loss
  - loading screens where stats are stale or game state is not active
- Show pause-menu-style stats, not a second gameplay view.

### Initial stats set

Start with low-risk counters that already exist in current patches/decomp symbols:

- Health / honeycombs
- Lives
- Musical notes
- Jiggies
- Mumbo tokens
- Jinjos for current level, if available
- Current level/map name, if mapping is cheap

Do not add interactive second-screen controls in MVP.

### Non-goals for MVP

- No second RT64/Vulkan surface.
- No touch controls on the second display.
- No DS/3DS-style gameplay layout.
- No hard dependency on AYN Thor model strings.
- No support matrix for every foldable/external-display Android device yet.

---

## Key technical decisions

### 1. Use Android-native secondary UI, not RT64 dual rendering

Reason: Android `Presentation` is the lowest-risk path for an informational panel. It avoids RT64 swapchain/surface duplication, avoids SDL multi-window assumptions on Android, and keeps renderer lifecycle bugs away from gameplay.

### 2. Detect feature by display capability, not device model

Reason: AYN Thor is the target, but model-string checks are brittle. The first gate should be `DisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)` and a valid display whose ID is not the primary display. Add logging for model/display details so Thor-specific quirks can be added later if needed.

### 3. Feed stats from native through JNI as a snapshot

Reason: The Java secondary UI should not know ROM memory addresses. Native/game patch code should produce a compact `DualScreenStats` snapshot and push it to Java at a throttled rate.

### 4. Gate on gameplay-active state separately from app-focus state

Reason: `activityResumed && windowFocused` only means the app is foregrounded. It does not mean gameplay is active. The native side should decide whether game state is active and whether the pause menu is closed.

---

## Discovery tasks before implementation

### Task 1: Capture AYN Thor display behavior

**Objective:** Learn how Thor exposes the secondary display to Android.

**Status:** Completed on paired wireless ADB device `AYN Thor` / Android 13 API 33.

**Findings:**
- Primary display: id `0`, name `Built-in Screen`, app size `1920 x 1080` in current landscape orientation, internal, no `FLAG_PRESENTATION`.
- Secondary display: id `4`, name `Screen-2`, app size `1240 x 1080` in current landscape orientation, internal, has `FLAG_PRESENTATION`.
- `cmd display get-displays` exposes `Screen-2` through the standard Android presentation-display path, so the planned `DisplayManager` + `Presentation` approach is viable for AYN Thor.

**Commands used:**

```bash
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell dumpsys display
adb shell cmd display get-displays
adb shell wm size
adb shell wm density
```

**Acceptance:**
- We know whether Thor exposes the lower/secondary panel as a presentation display.
- We know display IDs, sizes, density, rotation, and whether it survives app pause/resume.

### Task 2: Confirm Android app can create a secondary `Presentation`

**Objective:** Add a temporary Java-only probe before involving game stats.

**Files:**
- Modify: `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`
- Create: `android/app/src/main/java/io/github/banjorecomp/DualScreenStatsPresentation.java`

**Implementation sketch:**
- In `BanjoSDLActivity.onCreate`, get `DisplayManager`.
- Register a `DisplayListener`.
- Pick the first display from `DISPLAY_CATEGORY_PRESENTATION` that is not the current default display.
- Show a `Presentation` with a black background and text: `BanjoRecomp dual-screen probe`.
- Hide it on `onPause`; show again on `onResume` if display still exists.

**Verification:**
- Build default debug APK.
- Install/launch on Thor.
- Main gameplay/launcher remains on primary screen.
- Secondary screen shows the probe text.
- No crash on close, recents, sleep/wake, fold/hinge/display changes.

---

## Implementation tasks

### Task 3: Add a production secondary-display manager

**Objective:** Encapsulate display detection/lifecycle out of `BanjoSDLActivity`.

**Files:**
- Create: `android/app/src/main/java/io/github/banjorecomp/DualScreenStatsManager.java`
- Create: `android/app/src/main/java/io/github/banjorecomp/DualScreenStatsPresentation.java`
- Modify: `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`

**Design:**

`DualScreenStatsManager` owns:
- `DisplayManager`
- `DisplayListener`
- current secondary `Display`
- current `DualScreenStatsPresentation`
- app foreground state
- native gameplay-active state
- latest stats snapshot

Public methods:
- `start()`
- `stop()`
- `setAppForeground(boolean foreground)`
- `setGameplayActive(boolean active)`
- `updateStats(DualScreenStats stats)`
- `refreshPresentation()`

**Acceptance:**
- All secondary display lifecycle logic is isolated from ROM picker/mod picker code.
- If no secondary display exists, manager logs once and stays inert.

### Task 4: Add Java-side stats model and JNI entry points

**Objective:** Let native code publish gameplay state and stats to Java.

**Files:**
- Modify: `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`
- Create or modify: `android/app/src/main/java/io/github/banjorecomp/DualScreenStats.java`
- Modify: `src/main/main.cpp`

**Java static methods:**

```java
public static void nativeSetDualScreenGameplayActive(boolean active)
public static void nativeUpdateDualScreenStats(
    int health,
    int maxHealth,
    int lives,
    int notes,
    int jiggies,
    int mumboTokens,
    int levelId,
    int jinjosMask
)
```

Better final naming may be:

```java
public static void setDualScreenGameplayActiveFromNative(boolean active)
public static void updateDualScreenStatsFromNative(...)
```

**Native helper:**

Add an Android-only helper function in `src/main/main.cpp` or a new `src/android/dual_screen_stats_bridge.cpp`:

```cpp
#if defined(__ANDROID__)
void banjo_android_set_dual_screen_gameplay_active(bool active);
void banjo_android_update_dual_screen_stats(const DualScreenStatsSnapshot& stats);
#endif
```

**Acceptance:**
- Probe build still links.
- Calling the JNI methods updates Java logs/UI without touching game stats yet.

### Task 5: Identify gameplay-active and pause-menu signals

**Objective:** Find reliable game-side signals for “gameplay is active and pause menu is not open.”

**Likely files:**
- `patches/hud_transform_tagging.c`
- `patches/init_patches.c`
- New: `patches/dual_screen_stats.c`
- New: `patches/dual_screen_stats.h`
- `patches/Makefile` if new patch file needs inclusion
- `patches/misc_funcs.h` if adding host callbacks

**Starting clues already found:**
- `patches/hud_transform_tagging.c` declares `getGameMode(void)` and has a pause-menu struct `D_80383010` with fields including `state`, `menu`, `page`, `exit_pause`.
- The same file already references `level_get()`, `itemPrint_getValue()`, and `itemscore_noteScores_getTotal()`.

**Approach:**
- Add a patch-side function that runs once per frame or from an existing stable update hook.
- Compute:
  - `gameplay_active = getGameMode() == expected gameplay mode && D_80383010.state == pause-closed-state`
- Initially log state transitions only; do not show stats until verified.

**Acceptance:**
- Device logs show active=false in launcher, pause menu, ROM picker, app background.
- Device logs show active=true during controllable gameplay.

### Task 6: Export or callback stats snapshot from patches to host

**Objective:** Send current stats to native host at a throttled rate.

**Files:**
- Modify/create patch files from Task 5.
- Modify: `src/main/main.cpp` or `src/android/dual_screen_stats_bridge.cpp`.
- Modify: `patches/misc_funcs.h` if using `DECLARE_FUNC` host callbacks.

**Preferred pattern:**
- Patch code collects values from existing game functions/globals.
- Patch code calls a host callback such as:

```c
DECLARE_FUNC(void, recomp_android_update_dual_screen_stats,
    s32 active,
    s32 health,
    s32 max_health,
    s32 lives,
    s32 notes,
    s32 jiggies,
    s32 mumbo_tokens,
    s32 level_id,
    s32 jinjos_mask
);
```

- Host callback is Android-only functional; desktop implementation is a no-op.

**Throttle:**
- Only publish when values change, or at 4 Hz max.
- Avoid per-frame JNI calls if nothing changed.

**Acceptance:**
- No measurable gameplay hitch from JNI updates.
- Logs show sane values while collecting items and entering/exiting pause.

### Task 7: Build the secondary stats UI

**Objective:** Replace probe text with a real dashboard.

**Files:**
- Modify: `DualScreenStatsPresentation.java`
- Optional assets: `android/app/src/main/res/drawable/*` if using simple icons

**MVP UI:**
- Black/dark background.
- Large readable text/cards.
- Grid layout optimized for Thor’s secondary screen aspect ratio.
- No copyrighted asset extraction from the ROM.
- Use text labels and simple vector icons first.

**Example layout:**

- Top: `Banjo-Kazooie` / current level
- Row 1: Health and lives
- Row 2: Notes and jiggies
- Row 3: Mumbo tokens and Jinjos
- Footer: hidden or dimmed when inactive

**Acceptance:**
- Readable from normal handheld distance.
- Looks acceptable on emulator/external presentation display too.
- Does not mirror private ROM art into Android resources.

### Task 8: Add settings and developer override

**Objective:** Let the feature be disabled and tested without Thor.

**Files:**
- Modify config/UI files after inspecting current RecompFrontend settings patterns.
- Likely: `include/banjo_config.h`, `src/main/banjo_config.cpp`, launcher/options UI files.

**Settings:**
- `Dual-screen stats`: Auto / Off / Force probe

**Reason:**
- Auto is default.
- Off avoids weird behavior on docks/external displays.
- Force probe helps development on non-Thor Android devices with HDMI/virtual displays.

**Acceptance:**
- Default Auto does nothing on single-screen phones.
- Off suppresses Presentation even if Android reports a secondary display.

### Task 9: Lifecycle hardening

**Objective:** Make display changes safe.

**Files:**
- `DualScreenStatsManager.java`
- `BanjoSDLActivity.java`

**Cases to test:**
- App launch with secondary already present.
- Display added after app launch, if possible.
- Display removed while app running.
- Recents/task switcher.
- Sleep/wake.
- DocumentsUI ROM picker and mod picker.
- Device rotation/config changes if Thor exposes them.

**Acceptance:**
- No leaked window exceptions.
- No crash on `InvalidDisplayException`.
- No stats shown while gameplay should be hidden.

### Task 10: Verification build matrix

**Objective:** Prove no regressions.

**Commands:**

```bash
python3 tools/check_android_port_guards.py
bash -n tools/ci/*.sh
gradle -p android --no-daemon :app:assembleDebug --stacktrace
gradle -p android --no-daemon :app:assembleDebug -PbanjoProbe=true --stacktrace
gradle -p android --no-daemon :app:assembleRelease --stacktrace
```

**APK checks:**

```bash
tools/ci/verify_android_apk.sh android/app/build/outputs/apk/debug/app-debug.apk runtime
```

**Device checks:**

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell monkey -p io.github.banjorecomp 1
adb logcat | grep -E 'BanjoSDLActivity|DualScreenStats|DisplayManager'
```

**Acceptance:**
- Single-screen devices behave exactly as before.
- Thor shows secondary stats only during gameplay.
- Pause menu hides/blanks stats.
- App background hides/blanks stats.

---

## Risk register

### Risk: Thor does not expose the lower screen as a `Presentation` display

Mitigation:
- First task is a display probe.
- If `Presentation` is unavailable, fallback options are device-specific SDK/API, Android windowing mode, or SDL multi-window experiments. Do not assume.

### Risk: Pause-menu stats are not cleanly available outside pause

Mitigation:
- Use existing item/global functions already referenced by `hud_transform_tagging.c` first.
- Avoid reusing pause-menu rendering code directly.
- If a value is hard to get, omit it from MVP rather than destabilizing gameplay.

### Risk: Per-frame JNI updates cause stutter

Mitigation:
- Change detection plus 4 Hz throttle.
- Java UI updates only on UI thread and only when visible.

### Risk: Secondary UI leaks windows during lifecycle changes

Mitigation:
- Keep manager lifecycle explicit.
- Dismiss Presentation in `onPause`, `onDestroy`, display removal, and before launching picker intents.

### Risk: Desktop or non-Android builds regress

Mitigation:
- Android-only Java/JNI code.
- Host callbacks compile to no-op on non-Android.
- Guard checks and desktop compile if available.

---

## Recommended commit sequence

1. `docs: plan Android dual-screen stats display`
2. `android: add secondary display probe`
3. `android: manage dual-screen stats presentation lifecycle`
4. `android: bridge dual-screen stats updates from native`
5. `game: expose gameplay-active state for dual-screen stats`
6. `game: publish dual-screen stats snapshot`
7. `android: render dual-screen stats dashboard`
8. `settings: add dual-screen stats option`
9. `test: document Thor dual-screen verification`

---

## Open questions

- Does AYN Thor expose the second screen as a standard Android presentation display?
- What are the exact dimensions/density/rotation of the secondary screen?
- Should pause-menu-open state blank the display entirely, or show “Paused”?
- Should stats include current-level-only totals or global totals first?
- Should the dashboard use only text/simple vector icons, or later recreate pause-menu visual styling with legally safe assets?
