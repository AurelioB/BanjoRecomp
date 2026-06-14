# Android dual-screen transition findings and follow-up plan

## Context

This note captures the current findings around the Android secondary-display jiggy transition, especially the timing/easing question and the remaining work we should revisit later.

## Source-of-truth game behavior

From `lib/bk-decomp/src/core2/gc/transition.c`:

- Regular jiggy transition asset: `ASSET_7D0_MODEL_TRANSITION_JIGGY`.
- Default map transition fallback uses:
  - `TRANSITION_ID_5_JIGGY_IN`
  - `TRANSITION_ID_6_JIGGY_OUT`
- Game timings:
  - `JIGGY_IN`: `2.5f` seconds
  - `JIGGY_OUT`: `0.9f` seconds
- Progress is linear for the normal jiggy transition:
  - `percentage = timer / duration`
- Rotation:
  - `vp_rotation[2] = s_current_transition.rotation - 90.0f * percentage`
  - after fade in/out completes, base rotation advances by `-90.0f`
- Scale:
  - in: `percentage * 3.5f + 0.1f`
  - out: `(1.0f - percentage) * 3.5f + 0.1f`
- Important: the normal jiggy transition does not use an additional ease-in/ease-out curve. The special witch-head out case uses `func_80257618(percentage)`, but the regular jiggy transition does not.

## What was wrong with the first Android implementation

- The Android transition timing was too fast:
  - previous in/open timing: `520 ms`
  - previous out/close timing: `180 ms`
- Those values did not match game timing.
- There was also a visual completion bug:
  - the final transition frame could still draw the jiggy mask before clearing the iris state
  - the jiggy mask size was not large enough to guarantee that the visible cutout grew beyond the physical secondary display dimensions
  - result: after easing/opening, the black jiggy mask could remain partially covering the screen

## Current Android changes made

In `android/app/src/main/java/io/github/banjorecomp/DualScreenStatsView.java`:

- Updated transition durations to match the game:
  - `IRIS_DURATION_IN_SLOW_MS = 2500L`
  - `IRIS_DURATION_OUT_FAST_MS = 900L`
- Kept progress linear, matching the game’s `timer / duration` behavior.
- Kept rotation at `-90° * progress`, matching game behavior.
- Increased maximum jiggy mask size:
  - from `Math.max(width, height) * 2.0f`
  - to `(float) Math.hypot(width, height) * 4.0f`
- Added a `finishIris(...)` path so once progress reaches `1.0f`, the view stops drawing the mask and draws the final content/black state directly.

## Verification performed

Commands run from repository root:

```sh
source ~/.config/android-build-env.sh
gradle -p android :app:assembleDebug
python3 tools/check_android_port_guards.py
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.aure.banjorecomp
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity
```

Observed results:

- Gradle build succeeded.
- Android port guard checks passed.
- APK installed successfully.
- App launched successfully.
- Logcat showed secondary display presentation:
  - `Showing dual-screen stats surface on display 4 / Screen-2`
- No `AndroidRuntime` / `FATAL EXCEPTION` lines were observed in the checked logs.
- Physical secondary display screenshots were captured during transition testing, then removed after verification to avoid preserving temporary/ROM-derived captures.

## Follow-up plan

1. Compare Android jiggy shape against the in-game `ASSET_7D0_MODEL_TRANSITION_JIGGY` more closely.
   - Current Android path is an approximation.
   - The game uses a model asset, not a simple ready-to-use 2D grayscale mask.

2. Verify transition direction semantics against gameplay captures.
   - Confirm whether Android’s visible/open fraction maps exactly to game fade-in/fade-out naming and visual behavior.
   - The game code names can be counterintuitive because the model is drawn as a transition overlay/aperture.

3. Test real map/content transitions, not only double-tap hide/show.
   - Startup/logo -> stats
   - stats -> black/cutscene
   - black -> visible
   - stats map/background change -> close, swap, reopen

4. Capture frame timings if needed.
   - Use 60 fps frame capture or timestamped screenshots to compare Android progression against game expectations at roughly:
     - out: 0 ms, 450 ms, 900 ms
     - in: 0 ms, 1250 ms, 2500 ms

5. Decide whether the secondary-display transition should mirror the exact game transition every time or use game-style timing only for certain state changes.
   - Exact game timing is slower, especially 2.5s for opening.
   - That is authentic, but may feel sluggish for streamer/user-controlled hide/show.
   - If we later want quicker manual hide/show, keep that as a deliberate product choice, not an accidental mismatch.

## Current status

The implementation now follows the game’s regular jiggy transition timing and linear progress model, and the partial-mask-at-completion issue was addressed. More visual comparison work remains if we want a closer asset/shape match to the original N64 model transition.
