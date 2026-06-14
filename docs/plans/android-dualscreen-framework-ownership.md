# Android dual-screen framework ownership

This document classifies the current BanjoRecomp Android dual-screen and release-polish work before any code is moved into shared forks. The rule for this phase is conservative: keep app-local interfaces in BanjoRecomp first, then promote only proven generic seams after BMHeroRecomp consumes the same shape in a later pass.

## Constraints

- Release target is GitHub Releases only.
- The public/default APK must not package ROMs, decompressed ROMs, generated recomp artifacts, private screenshots, save backups, or ROM-derived private assets.
- The installed package/applicationId is app identity and belongs to BanjoRecomp. The Java/JNI namespace can intentionally remain `io.github.banjorecomp` while the installed package is `com.aure.banjorecomp`.
- Dual-screen API is experimental/internal. Do not present it as a stable cross-port API yet.
- BMHeroRecomp is reference-only for this cycle. Do not move shared Java framework code only because Banjo has a working implementation.
- Do not put app package names, Activity names, Banjo map IDs, Banjo asset IDs, or Banjo-specific JNI symbols into shared dependencies.

## App-local-first interface strategy

BanjoRecomp should first introduce an app-local companion-display seam under the app repo, with names that make the split obvious:

- Generic-ish app-local contracts: `CompanionDisplayHost`, `CompanionDisplayProvider`, `CompanionRenderer`, `CompanionResourceProvider`, `CompanionSnapshot`, `CompanionEvent`, `CompanionDisplayMode`, and `CompanionTransitionPhase`.
- Banjo adapters and payloads: `BanjoCompanionProvider`, `BanjoCompanionRenderer`, `BanjoCompanionStats`, `BanjoResourceKeys`, Banjo display-mode classification, Banjo map/area labels, and Banjo ROM-derived sprite extraction.

The first implementation can live under `android/app/src/main/java/io/github/banjorecomp` or a local `.../companion` package. Promotion to a shared Android module/repo should wait until BMHeroRecomp maps cleanly onto the same API and the shared files contain no Banjo or BMHero symbols.

Recommended envelope shape for now:

- `displayMode`: `LOGO`, `STATS`, `BLACK`, optional future `CUSTOM`.
- `transitionPhase`: `NONE`, `FADE_OUT_LOADING`, `REVEAL`.
- `schemaVersion`, `gameStateKey`, timestamp, and a typed game-specific payload.

Use typed game DTOs first. A generic key/value bundle or schema generator is premature until at least two ports prove that the field set varies in ways worth abstracting.

## Ownership matrix

| Dirty/current area | Current files | Owner now | Later shared candidate | Reason / next action |
|---|---|---|---|---|
| Android application identity, Gradle flags, versioning, signing inputs, release metadata | `android/app/build.gradle`, `android/app/src/main/AndroidManifest.xml`, Android resource values | Banjo app repo | None, except generic guard/check patterns | Package ID, release metadata, debug flags, signing env names, and version names are app/release policy. Keep local. |
| Java package and Activity bridge | `android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java`, `src/main/main.cpp` JNI bridge symbols | Banjo app repo | Generic bridge concepts only | `BanjoSDLActivity`, `io.github.banjorecomp`, `Java_io_github_banjorecomp_*`, ROM/mod picker callbacks, and component names are app-specific. Do not promote these symbols. |
| SDLActivity local debug hook | `android/app/src/main/java/org/libsdl/app/SDLActivity.java` | Banjo app repo for this cycle, but should be minimized | Shared SDL/Android glue only if it can be generic and package-free | Current diff directly calls `io.github.banjorecomp.BanjoSDLActivity.handleDualScreenDebugKeyEvent(...)`, which is app-specific. Prefer routing debug input through Banjo-local Activity code or a generic callback before considering shared SDL glue. |
| Secondary display lifecycle and Presentation attach/detach | `DualScreenStatsManager.java`, `DualScreenStatsPresentation.java` | Banjo app repo, behind app-local companion interfaces first | Future small shared Android companion module | Display discovery, `Presentation` ownership, foreground gating, and attach/detach semantics are reusable, but the current classes are coupled to Banjo stat payloads, debug previews, and renderer names. Extract after local interfaces stabilize. |
| Display modes and transition state machine | `DualScreenStats.java`, `DualScreenStatsManager.java`, `DualScreenStatsView.java`, `patches/graphics_patches.c`, `src/game/recomp_api.cpp` | Split: generic enum names app-local; Banjo classification in Banjo patch/app layer | Possible shared enum/event contract | The idea of `LOGO`/`STATS`/`BLACK` and transition phases is reusable. The logic mapping Banjo game modes, map IDs, cutscene levels, file select, Grunty's Lair, and intro maps is Banjo-specific. |
| Banjo stat payload and dedupe | `DualScreenStats.java`, `BanjoSDLActivity.java`, `src/game/recomp_api.cpp`, `patches/graphics_patches.c` | Banjo app repo + Banjo patch layer | Generic snapshot transport only | Health, notes, feathers, Jiggies, Mumbo tokens, Jinjo bitmasks, selected save file, and global progress counts are Banjo payload fields. Keep in Banjo DTO/patch bridge. |
| Banjo gameplay/state hooks | `patches/graphics_patches.c` | Banjo patch layer in main repo | None unless a hook is a true decomp/runtime fix | The patch calls Banjo game helpers (`map_get`, `level_get`, `jiggyscore_total`, `itemscore_noteScores_getTotal`, `fileProgressFlag_get`, etc.) and uses Banjo map/level enums. This belongs in Banjo's patch layer, not shared dependencies or dirty `bk-decomp` edits. |
| Native-to-Java event/stat transport | `src/game/recomp_api.cpp` and future local companion bridge files | Banjo app repo now | N64ModernRuntime only for package-free generic transport helpers | The pattern of publishing snapshots/events is reusable. The current JNI target method names and payload shape are Banjo-specific. If promoted later, expose generic runtime callbacks/functions and keep package-specific JNI in each app repo. |
| ROM import and Android file picker Activity bridge | `BanjoSDLActivity.java`, `src/main/main.cpp`, RecompFrontend branches/pointers | Split: Activity bridge in Banjo app repo; generic frontend seams in RecompFrontend | RecompFrontend for game-agnostic picker/frontend seams | The file-picker concept is shared, but callback names, request codes, component names, and ROM/mod handling policy are app-specific. |
| ROM-derived theme/resource extraction | `BanjoSpriteThemeExtractor.java`, `DualScreenStatsView.java`, generated/cache behavior | Banjo app repo | Generic `CompanionResourceProvider` interface only | Asset IDs, texture indices, glyph handling, level portrait keys, and Banjo-specific palette/layout choices belong to Banjo. Share only cache/provider interfaces after BMHero validates them. |
| Canvas renderer/layout and debug previews | `DualScreenStatsView.java`, `DualScreenDebugAreas.java`, `tools/preview_dual_screen_background.py` | Banjo app repo | `CompanionRenderer` interface only | The large Canvas renderer is Banjo menu art, stat rows, map labels, completion row, debug area list, and asset IDs. Do not move it wholesale into a framework. |
| Launcher icons/adaptive icon generation | `icons/app.svg`, `icons/app-adaptive-foreground.svg`, `icons/app.png`, `android/app/src/main/res/**/ic_launcher*`, `tools/generate_android_icons.py` | Banjo app repo | Icon generator technique can remain app-local or become a template later | App icon source art and generated PNG/XML resources are release identity. Keep generated outputs reproducible but app-owned. |
| Android splash/fullscreen/visual resources | `android/app/src/main/res/drawable/splash_transparent.xml`, `android/app/src/main/res/values*/` | Banjo app repo | Generic documentation/checklist only | Splash/launch theme choices are app polish and package-specific resource wiring. |
| RT64/Plume Android surface, Vulkan, cache/config path fixes | `lib/rt64`, `lib/rt64/src/contrib/plume` submodule branches/pointers | Shared dependency if the code is package-free | RT64/Plume shared forks | These are renderer/platform mechanics. Keep only generic Android surface/swapchain/cache/path fixes there; app-specific JNI must stay in Banjo app code. Current submodule working trees are clean in this phase. |
| N64ModernRuntime lifecycle/event helpers | `lib/N64ModernRuntime` submodule branch/pointer | Shared dependency if generic | N64ModernRuntime shared fork | Runtime pause/foreground contracts and generic snapshot/event helpers can be shared. Banjo payloads and Java package names cannot. Current submodule working tree is clean in this phase. |
| RecompFrontend Android frontend seams | `lib/RecompFrontend` submodule branch/pointer | Shared dependency if generic | RecompFrontend shared fork | File picker/frontend support can be shared if no game package names, ROM filenames, or Banjo activity assumptions leak in. Current submodule working tree is clean in this phase. |
| bk-decomp | `lib/bk-decomp` submodule pointer | No current dirty source; future true decomp fixes belong in bk-decomp, Android UI hooks should live in Banjo patch layer | bk-decomp only for decomp correctness | Current `git -C lib/bk-decomp status` is clean. Keep Android companion-state queries in `patches/*.c` unless a real decomp correctness fix is discovered. |
| Static guard/check tooling | `tools/check_android_port_guards.py`, future `tools/check_android_port_guards.py` updates | Banjo app repo now | Reusable check snippets later | Existing checks intentionally assert Banjo-specific JNI in app code. Add shared-dependency leak checks here later, but do not make dependency repos depend on Banjo names. |
| Publish/audit docs | `docs/android-port-findings.md`, `docs/android-dual-screen-transition-findings.md`, `docs/plans/*.md` | Banjo app repo | Process pattern only | These are project-local status and release notes. Keep in Banjo. |

## Shared dependency package/JNI leak check

Checked shared dependencies:

- `lib/N64ModernRuntime`
- `lib/RecompFrontend`
- `lib/rt64`
- `lib/rt64/src/contrib/plume`

Search pattern used by the worker:

```text
io\.github\.banjorecomp|com\.aure\.banjorecomp|BanjoSDLActivity|Java_io_github_banjorecomp|banjorecomp
```

Result summary:

- `search_files` over `/home/hermes/Projects/personal/BanjoRecomp/lib` returned `total_count: 0` for the package/JNI leak pattern above.
- Submodule working trees for `lib/N64ModernRuntime`, `lib/RecompFrontend`, `lib/rt64`, `lib/rt64/src/contrib/plume`, and `lib/bk-decomp` reported no modified/untracked files during this phase.
- App-specific JNI/package hits do exist in the app repo (`src/main/main.cpp`, `src/android/sdl_lifecycle_probe.cpp`, `BanjoSDLActivity.java`, guard tooling). That is expected and acceptable because those files are app-owned.

Before committing future dependency changes, rerun an equivalent check against each shared dependency and include BMHero names too:

```bash
grep -R "io.github.banjorecomp\|com.aure.banjorecomp\|BanjoSDLActivity\|Java_io_github_banjorecomp\|io.github.bmherorecomp\|BMHeroSDLActivity\|Java_io_github_bmherorecomp" -n \
  lib/N64ModernRuntime lib/RecompFrontend lib/rt64 lib/rt64/src/contrib/plume || true
```

Expected result for shared dependencies: no matches.

## Actionable next steps

1. Commit this ownership doc before refactors.
2. Rename Banjo-specific classes/payloads so generic vs Banjo ownership is obvious (`DualScreenStats` should become a Banjo-named DTO unless kept as a short-lived compatibility alias).
3. Remove or replace the app-specific direct call from the local SDLActivity fork before considering any SDL glue promotion.
4. Introduce app-local companion interfaces and route the current Banjo renderer/provider through them.
5. Only after BMHeroRecomp maps to the same interfaces, promote minimal package-free host/interfaces to a shared module/repo.
6. Keep Banjo patch-layer state classification in `patches/graphics_patches.c`; do not move it to `lib/bk-decomp` unless a true decomp correctness fix is involved.
7. Add the package/JNI leak search to `tools/check_android_port_guards.py` in a later guard task so this boundary remains enforced.
