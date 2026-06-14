# BMHero companion framework fit audit

Status: reference-only audit for BanjoRecomp publish-polish work. No BMHero implementation was done for this note.

## Scope

This compares the current BanjoRecomp companion-display API direction against the local BMHeroRecomp Android port so the Banjo API does not become a fake one-game abstraction.

Inputs inspected:

- BanjoRecomp repo: `/home/hermes/Projects/personal/BanjoRecomp`
- BMHeroRecomp repo: `/home/hermes/Projects/personal/BMHeroRecomp`
- Banjo API draft: `docs/android-dualscreen-framework-api.md`
- BMHero context: `/home/hermes/Projects/personal/BMHeroRecomp/.hermes.md`
- BMHero findings: `/home/hermes/Projects/personal/BMHeroRecomp/docs/android-port-findings.md`
- BMHero Android files:
  - `android/app/build.gradle`
  - `android/app/src/main/AndroidManifest.xml`
  - `android/app/src/main/java/io/github/bmherorecomp/BMHeroSDLActivity.java`
  - `android/app/src/main/java/io/github/bmherorecomp/MainActivity.java`
  - `android/app/src/main/java/org/libsdl/app/SDLSurface.java`
  - `src/main/main.cpp`
  - `src/android/sdl_lifecycle_probe.cpp`
  - `src/game/recomp_api.cpp`
  - `patches/required_patches.c`

BMHeroRecomp exists at `/home/hermes/Projects/personal/BMHeroRecomp` on branch `android-port`.

## Current layout comparison

| Area | BanjoRecomp current state | BMHeroRecomp current state | Framework implication |
|---|---|---|---|
| Android package | `io.github.banjorecomp` | `io.github.bmherorecomp` | Package names, manifest activity names, and JNI symbols are app-owned and must not move into shared dependencies. |
| Main Android Activity | `BanjoSDLActivity extends SDLActivity` | `BMHeroSDLActivity extends SDLActivity` | The Activity bridge pattern is reusable; concrete classes and native method names stay app-local. |
| Runtime APK mode | Gradle/SDLActivity runtime APK with app-private program/data paths and optional gated dev ROM packaging | Same shape adapted for BMHero (`BMHERO_ANDROID_*` flags, program/data extraction, gated `bmheroBundleDevRoms`) | App-private path setup and gated dev-ROM packaging checks are reusable process/patterns, not shared Java API yet. |
| ROM/mod picker | Banjo Activity owns SAF callbacks and app-specific JNI completion | BMHero Activity owns SAF callbacks and `Java_io_github_bmherorecomp_BMHeroSDLActivity_nativeOnRomSelected` | Generic file-dialog seams belong in RecompFrontend; Activity/JNI completion symbols stay app-local. |
| Surface/lifecycle hooks | SDL Java glue calls `BanjoSDLActivity.nativeSetAndroidSurfaceReady`; Activity gates app audio and dual-screen foreground | SDL Java glue calls `BMHeroSDLActivity.nativeSetAndroidSurfaceReady`; Activity gates app audio only | Surface readiness and audio/focus gating are reusable platform mechanics, but the current SDL glue has app-specific hard-coded class names. A shared solution must remove those names first. |
| Shared dependency stack | Uses N64ModernRuntime, RecompFrontend, RT64/Plume Android branches | Uses the same Android branches/commits for N64ModernRuntime, RecompFrontend, RT64/Plume | This supports extracting generic Android runtime/frontend/renderer fixes by ownership. It does not justify moving Banjo display UI into dependencies. |
| Companion display implementation | `DualScreenStatsManager`, `DualScreenStatsPresentation`, `DualScreenStatsView`, `DualScreenStats`, `BanjoSpriteTheme*`, `DualScreenDebugAreas` | No companion/dual-screen Java implementation found | Banjo remains the only implementation. Keep framework contracts app-local until BMHero actually consumes the shape. |
| Native game snapshot source | `src/game/recomp_api.cpp` + Banjo patch helpers publish display mode/stats/transition state | No companion snapshot bridge found; BMHero native work is runtime, file loading, audio/surface, and visual-effect debugging | Snapshot/event envelope can be planned, but BMHero-specific payload fields are unknown until a BMHero gameplay-state probe is designed. |
| ROM-derived companion resources | Banjo has ROM-derived sprite/theme extraction and map/background-specific rendering | BMHero has `assets/BMHeroLogo.svg` and normal program assets, but no companion resource extraction layer | Resource-provider concept is reusable. Banjo sprite IDs, title art reconstruction, palette/glyph metrics, and map backgrounds are not. BMHero may start with logo/static assets before ROM-derived resources. |
| Renderer/layout | Banjo Canvas renderer is tailored to BK stats, map art, global progress, iris transitions, debug previews | No BMHero secondary renderer | Renderer interface and transition orchestration are reusable candidates. Banjo Canvas layout is not reusable. |

## App-specific boundary findings

BMHero has the same app-local package/JNI boundary problem as Banjo, just with different names:

- `BMHeroSDLActivity` owns app identity, asset extraction, SAF import, focus/audio gating, and native methods.
- Native symbols in `src/main/main.cpp` and `src/android/sdl_lifecycle_probe.cpp` use `Java_io_github_bmherorecomp_BMHeroSDLActivity_*`.
- `SDLSurface.java` directly references `io.github.bmherorecomp.BMHeroSDLActivity.nativeSetAndroidSurfaceReady(...)`.
- Banjo has the equivalent `BanjoSDLActivity` / `Java_io_github_banjorecomp_*` / `io.github.banjorecomp.*` shape, plus dual-screen calls.

Current shared dependency leak check found no Banjo/BMHero package, Activity, JNI, `DualScreenStats`, or sprite-theme symbols in:

- `lib/N64ModernRuntime`
- `lib/RecompFrontend`
- `lib/rt64`
- `lib/rt64/src/contrib/plume`

That is the right boundary. Preserve it.

## Banjo API concepts that look reusable for BMHero

Likely reusable after BMHero has an actual consumer:

1. `CompanionDisplayHost`
   - DisplayManager listener lifecycle.
   - Presentation attach/detach/dismiss.
   - Foreground/focus gating.
   - Single routing path for updates regardless of display mode.

2. `CompanionDisplayProvider`
   - A game adapter registered with the host.
   - Supplies renderer/resource provider and interprets snapshots/events.
   - Must remain typed per game, not global key/value soup.

3. `CompanionSnapshot` envelope
   - `schemaVersion`, `gameId`, `sequence`, `timestampNanos`, `displayMode`, `transitionPhase`, and typed payload.
   - Useful for BMHero even if its first payload is small, such as level/area, health/lives, collectible counters, boss/goal state, or a logo/status screen.

4. `CompanionEvent` envelope
   - `ROM_IMPORTED`, `RESOURCE_READY`, `DISPLAY_ATTACHED`, `DISPLAY_DETACHED`, `APP_FOREGROUND_CHANGED`, `DISPLAY_MODE_CHANGED`, and future user/debug events are game-agnostic enough.

5. Display modes
   - `LOGO`, `STATS`, and `BLACK` map cleanly to a second N64 port.
   - `CUSTOM` should stay reserved until BMHero needs a distinct non-stats renderer mode.

6. Transition phases
   - `NONE`, `FADE_OUT_LOADING`, and `REVEAL` are still reasonable as generic renderer state.
   - BMHero may not use Banjo-style iris semantics; the shared concept should be transition phase, not `iris` terminology.

7. Resource-provider abstraction
   - Cache/request/warm/clear and resource-ready events are reusable.
   - BMHero may initially use packaged SVG/logo art or simple app assets, then later ROM-derived resources if useful.

8. Guard checks
   - Grep shared dependency repos for app package names, Activity class names, JNI symbols, map/item/asset IDs, and renderer assumptions before extraction.

## Banjo concepts that are not reusable as-is

Keep these Banjo-local:

- `BanjoSDLActivity`, `io.github.banjorecomp`, installed package, manifest identity, and `Java_io_github_banjorecomp_*` symbols.
- `DualScreenStats` field names and semantics: health/honeycomb, notes, eggs, feathers, Jiggies, Mumbo tokens, Jinjo masks, save/global progress, reached-Lair flags.
- Banjo display-mode classifier based on BK game mode, map/level IDs, save select, intro/demo/cutscene maps, and Grunty's Lair/global-progress contexts.
- `BanjoSpriteTheme`, `BanjoSpriteThemeExtractor`, BK byte-order/asset-table parsing, title-logo reconstruction, glyph metrics, palette ramps, and map backgrounds.
- `DualScreenStatsView` Canvas art direction, row layout, BK labels/colors, debug area names, preview controls, and completion/global-progress presentation.
- Banjo patch helpers and native queries in `patches/graphics_patches.c` / `src/game/recomp_api.cpp` that read BK-specific state.
- Banjo-specific transition visuals such as iris duration/timing unless BMHero independently proves the same transition behavior fits.

## BMHero-specific concepts expected if/when it consumes the API

Do not design these in Banjo now. BMHero needs its own inspection pass first:

- A typed `BMHeroCompanionSnapshot` payload, probably starting smaller than Banjo's.
- BMHero game-state classifier: menu/logo/gameplay/cutscene/loading/goal/boss states.
- BMHero resource keys: logo/status art, level/area names, icons, health/life/score/collectible semantics, and any ROM-derived assets if chosen.
- `BMHeroCompanionRenderer` layout and theme.
- BMHero JNI bridge names under `io.github.bmherorecomp`.
- Any BMHero-specific patch-layer state exports in `patches/required_patches.c` or another BMHero-owned patch file.

## Updated extraction criteria

This audit strengthens the existing rule: no shared Java companion framework should be extracted during the Banjo publish-polish cycle.

Before extraction, require all of the following:

1. Banjo renames/separates app-local generic interfaces from Banjo adapters, but keeps them in the Banjo repo.
2. BMHero implements a minimal consumer against the same conceptual API in a later pass, with BMHero-owned typed DTOs and renderer/resource classes.
3. The same host/provider/resource/renderer/snapshot/event shape works for both without forcing BMHero to use Banjo field names or Banjo transition art assumptions.
4. Shared candidates pass package leak checks for both repos:
   - no `io.github.banjorecomp`
   - no `io.github.bmherorecomp`
   - no `BanjoSDLActivity` / `BMHeroSDLActivity`
   - no `Java_io_github_banjorecomp_*` / `Java_io_github_bmherorecomp_*`
   - no Banjo or BMHero map IDs, item IDs, object IDs, sprite IDs, ROM filenames, or renderer layout names
5. SDL surface/focus/audio hooks are package-neutral before moving into shared SDL/Android glue. The current `SDLSurface.java` hard-coded Activity calls are not extractable as-is.
6. Only generic ownership moves outward:
   - Presentation/display lifecycle: small Android companion module, if two consumers prove it.
   - Runtime/JNI event transport: N64ModernRuntime only if package-free and game-free.
   - File picker seams: RecompFrontend only for generic dialog plumbing.
   - Renderer/platform fixes: RT64/Plume only for game-agnostic Android fixes.

## Recommendation

Proceed with Banjo app-local cleanup only:

- Rename current `DualScreen*` classes to make Banjo ownership explicit before any interface extraction.
- Introduce app-local experimental interfaces only where they make the current Banjo code clearer.
- Keep the Banjo Canvas renderer and ROM-derived sprite/theme extractor in Banjo.
- Treat BMHero as the second-consumer gate later, not as a reason to extract now.

BMHero's current Android port is a good reference for package/JNI and shared-dependency boundaries, but it is not yet a companion-display consumer. Extracting a shared framework before BMHero has a real typed payload and renderer would be premature.
