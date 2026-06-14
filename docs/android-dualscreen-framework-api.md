# Android dual-screen companion display API (experimental)

Status: experimental/internal app-local contract.

This document defines the intended BanjoRecomp Android companion-display API before code is moved or interface classes are implemented. It is not a stable public API, not a published shared library contract, and not a promise that downstream ports can depend on these names unchanged.

For this cycle the API should live inside the BanjoRecomp app repo. Extraction to a shared Java/runtime module is allowed only after BMHeroRecomp consumes the same shape in a later pass and proves that the shared pieces contain no Banjo- or BMHero-specific symbols.

## Goals

- Separate the reusable envelope for Android secondary-display lifecycle, snapshots, resources, rendering, and events from Banjo-specific game adapters.
- Keep Android `Presentation` lifecycle and update routing decoupled from Banjo stat fields, map IDs, ROM asset IDs, and renderer layout.
- Make display modes and game transition phases explicit across native, JNI, Java, and Canvas rendering.
- Prefer typed game DTOs inside a shared envelope for now. Avoid generic key/value soup and avoid schema/code generation until at least two ports need it.

## Non-goals

- No code movement is required by this document.
- No shared Maven/module/submodule extraction is approved by this document.
- No public API stability guarantee is implied.
- No Banjo map IDs, item IDs, ROM asset IDs, Java Activity names, package names, or JNI symbols should move into shared dependencies.

## High-level shape

```text
Android Activity / SDLActivity hooks
        |
        v
CompanionDisplayHost
        |
        +-- CompanionDisplayProvider  (game adapter)
        |       +-- CompanionResourceProvider
        |       +-- CompanionRenderer
        |
        +-- CompanionSnapshot stream  (native/JNI -> Java)
        +-- CompanionEvent stream     (user/display/resource events)
```

The host owns the Android display lifecycle. The provider supplies game-specific policy and components. Snapshots describe current game/display state. Events describe discrete actions or lifecycle/resource changes.

## Shared envelope concepts

### CompanionDisplayHost

Owns secondary-display lifecycle and routes snapshots/events to the current provider.

Candidate responsibilities:

- `start(context)` / `stop()`
- `setAppForeground(boolean foreground)`
- discover eligible secondary displays
- `attachDisplay(Display display)` / `detachDisplay(Display display)`
- create, update, and dismiss the Android `Presentation`
- `setProvider(CompanionDisplayProvider provider)`
- `publishSnapshot(CompanionSnapshot snapshot)`
- `publishEvent(CompanionEvent event)`
- suppress rendering while the app is backgrounded or external activities own the surface

Shared boundary:

- DisplayManager listening, Presentation attach/detach, foreground gating, and one-update-path routing are reusable candidates.
- Package-specific Activity calls and `BanjoSDLActivity` behavior are not shared.

### CompanionDisplayProvider

Game adapter registered with the host. It provides renderer/resource implementations and translates game lifecycle hooks into companion-display behavior.

Candidate responsibilities:

- `getGameId(): String`
- `getInitialMode(): CompanionDisplayMode`
- `getRenderer(): CompanionRenderer`
- `getResourceProvider(): CompanionResourceProvider`
- `onHostStarted(CompanionDisplayHost host)` / `onHostStopped()`
- `onRomImported(File romFile)`
- `onSnapshot(CompanionSnapshot snapshot)`
- `onEvent(CompanionEvent event)`

Shared boundary:

- The interface shape is a reusable candidate.
- Implementations such as `BanjoCompanionProvider` are app-local.

### CompanionResourceProvider

Provides game/app resources needed by the renderer without making the host know ROM formats or asset IDs.

Candidate responsibilities:

- `getCachedResource(ResourceKey key): Optional<ResourceHandle>`
- `requestResource(ResourceKey key, ResourceCallback callback)`
- `warmResourcesForSnapshot(CompanionSnapshot snapshot)`
- `clear()`
- report resource availability through `CompanionEvent` such as `RESOURCE_READY` or `RESOURCE_FAILED`

Shared boundary:

- Cache lookup/request/warm/clear concepts are reusable.
- Banjo ROM parsing, byte-order handling, sprite IDs, texture indices, palette ramps, glyph metrics, map background choices, and `BanjoSpriteThemeExtractor` stay Banjo-specific.

### CompanionRenderer

Draws the current companion-display content for the active mode/snapshot/resources.

Candidate responsibilities:

- `render(Canvas canvas, CompanionRenderState state)` or equivalent View callback
- measure/layout for the current display size
- draw one frame for `LOGO`, `STATS`, `BLACK`, or future `CUSTOM` content
- render transition masks/phases when the host/view state machine asks it to
- keep static modes from running unnecessary redraw loops

Shared boundary:

- The renderer interface and transition orchestration hooks are reusable candidates.
- Banjo's Canvas layout, stat rows, labels, colors, title/logo reconstruction, debug area previews, and ROM-derived art choices stay Banjo-specific.

### CompanionSnapshot

Immutable state update from game/native code to Java. It is the normal path for changing what the companion display shows.

Recommended envelope fields:

```text
schemaVersion: int
gameId: String
sequence: long
timestampNanos: long
displayMode: CompanionDisplayMode
transitionPhase: CompanionTransitionPhase
gameStateKey: String or int
payload: typed game DTO
```

Rules:

- `displayMode` and `transitionPhase` must be part of dedupe comparisons. Otherwise mode-only changes can be dropped when stat values do not change.
- `payload` should be a typed game DTO for the current port, not a loose global key/value map.
- Snapshots should be immutable once published.
- The host should route every snapshot through one update path, including logo/black modes, so transition handling cannot diverge by mode.

Banjo's current payload maps to a future `BanjoCompanionStats`-style DTO: health, max health, lives, notes, eggs, feathers, Jiggies, Mumbo tokens, Jinjo mask, current level/map key, global totals, selected save, and Banjo transition classification.

### CompanionEvent

Discrete event separate from continuous snapshot state.

Recommended envelope fields:

```text
schemaVersion: int
gameId: String
type: CompanionEventType
timestampNanos: long
payload: typed event DTO or narrow typed fields
```

Candidate event types:

- `ROM_IMPORTED`
- `RESOURCE_READY`
- `RESOURCE_FAILED`
- `DISPLAY_ATTACHED`
- `DISPLAY_DETACHED`
- `DISPLAY_MODE_CHANGED`
- `MAP_CHANGED`
- `USER_TOGGLE`
- `APP_FOREGROUND_CHANGED`
- `GAME_TRANSITION_CHANGED`
- `DEBUG_PREVIEW_CHANGED`

Events should not replace snapshots for render state. Use events for lifecycle/resource/user actions; use snapshots for authoritative game display state.

## Display modes

Initial enum candidates:

| Mode | Meaning | Shared? | Banjo-specific policy examples |
|---|---|---|---|
| `LOGO` | Attract/menu/startup state where stats are not useful. | Yes | Banjo title/logo art and save-select policy. |
| `STATS` | Gameplay or save-select state where companion data is useful. | Yes | Banjo health, collectibles, global progress, map labels, background art. |
| `BLACK` | Intentionally blank secondary display. | Yes | Banjo cutscene/map classification and new-save hold behavior. |
| `CUSTOM` | Future app-defined renderer mode. | Maybe later | Any port-specific special screen. Do not add until a second port needs it. |

`gameplayActive` may exist as a convenience derived from `displayMode == STATS`, but it must not be the authoritative display state.

## Transition phases

Initial enum candidates:

| Phase | Meaning | Expected rendering behavior |
|---|---|---|
| `NONE` | No game transition is active. | Render current mode normally. |
| `FADE_OUT_LOADING` | The game is closing/fading/loading away from current content. | Close/hold the secondary transition mask and avoid flashing stale content. |
| `REVEAL` | The game is revealing/fading into the new content. | Apply pending content and open the transition mask. |

For Banjo-Kazooie-style transitions, the native classifier can map game transition state into these phases. That classifier is Banjo-specific; the enum and one-path Java rendering behavior are the reusable part.

The view/renderer state machine should treat visible-to-black, black-to-visible, visible-to-visible, and same-mode background changes explicitly. It should not special-case only stats updates, because logo and black modes may need transitions without a stats payload.

## What remains Banjo-specific

These stay in BanjoRecomp unless later proven otherwise:

- `BanjoSDLActivity`, installed package/applicationId, manifest activity names, JNI symbol names, ROM/mod picker request handling.
- Banjo display-mode classification from game mode, map ID, level ID, cutscene maps, intro/demo/file-select state, and Grunty's Lair/global-progress contexts.
- Banjo stat DTO fields: health, lives, notes, eggs, feathers, Jiggies, Mumbo tokens, Jinjo masks, selected save, global totals, and reached-Lair flags.
- Banjo patch-layer helpers and native game calls such as map/level/item/progress helpers.
- Banjo ROM-derived resource extraction: asset IDs, texture indices, title/logo reconstruction, sprite/glyph palettes, map background selection, and cache keys.
- Banjo renderer/theme/layout: Canvas stat rows, colors, labels, debug area names, preview controls, completion/global-progress presentation, and art direction.
- Banjo release identity: icons, splash resources, signing/release metadata, versioning, GitHub release naming.

## Future extraction criteria

Do not extract shared Java framework code during this cycle just because BanjoRecomp has a working implementation. The BMHero comparison in `docs/plans/bmhero-companion-framework-fit.md` confirms that BMHero currently shares the Android runtime/dependency shape but has no companion-display consumer yet, so extraction still needs a later second-port implementation pass. Extract only after all of the following are true:

1. BMHeroRecomp consumes the same host/provider/resource/renderer/snapshot/event shape in a later pass, with BMHero-owned typed DTOs and renderer/resource classes.
2. Shared candidates contain no Banjo or BMHero package names, Activity names, JNI symbols, ROM filenames, map IDs, item IDs, object IDs, asset IDs, or renderer art/layout assumptions.
3. The API works with typed game DTOs for at least two ports without forcing either port into unnatural field names or Banjo-specific transition art semantics.
4. Package-neutral SDL surface/focus/audio hooks exist before moving lifecycle glue into shared Java/SDL code; current hard-coded Activity calls such as `BanjoSDLActivity`/`BMHeroSDLActivity` are not extractable as-is.
5. The shared home is chosen by ownership, not convenience:
   - Android Presentation/display lifecycle may belong in a small Android companion module.
   - Generic runtime event transport may belong in N64ModernRuntime only if package-free.
   - Generic frontend picker seams may belong in RecompFrontend.
   - Renderer/platform fixes belong in RT64/Plume only when they are game-agnostic.
6. Guard checks exist to grep shared dependencies for app-specific symbols from both ports before publishing.
7. BanjoRecomp remains a consumer/example, not the framework itself.

Until those criteria are met, keep the contracts app-local and mark implementation packages as experimental/internal.

## Suggested first app-local package split

Possible initial Java layout, subject to implementation review:

```text
io.github.banjorecomp.companion
  CompanionDisplayHost
  CompanionDisplayProvider
  CompanionResourceProvider
  CompanionRenderer
  CompanionSnapshot
  CompanionEvent
  CompanionDisplayMode
  CompanionTransitionPhase

io.github.banjorecomp
  BanjoCompanionProvider
  BanjoCompanionRenderer
  BanjoCompanionStats
  BanjoResourceKeys
  BanjoSpriteTheme
  BanjoSpriteThemeExtractor
```

This package split is only an implementation hint. The important boundary is conceptual: generic app-local envelope first, Banjo adapters second, shared extraction later after BMHero proves reuse.
