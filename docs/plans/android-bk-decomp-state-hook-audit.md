# Android bk-decomp state hook audit

Date: 2026-06-14

## Scope

This audit covers `lib/bk-decomp` for Android dual-screen/companion-display state hooks during the publish-polish pass. The goal is to keep Android/Banjo port behavior in the BanjoRecomp patch layer unless a change is a true decomp correctness fix.

## Commands run

```sh
git -C lib/bk-decomp status --short --branch
git -C lib/bk-decomp status --porcelain=v1
git -C lib/bk-decomp diff --stat
git -C lib/bk-decomp diff --name-status
git -C lib/bk-decomp diff
git -C lib/bk-decomp grep -n -E 'Android|DualScreen|dual_screen|Companion|DISPLAY_LOGO|DISPLAY_STATS|DISPLAY_BLACK|updateDualScreen|banjo_android' -- .
```

## Result

`lib/bk-decomp` is clean at `351ca158`:

- `status --porcelain=v1`: 0 entries
- `diff --stat`: 0 lines
- Android/dual-screen symbol search: no matches

## Classification

| Area | Classification | Owner/rationale |
|---|---|---|
| `lib/bk-decomp` working tree | No-op / clean | No decompiled source changes are present, so there is nothing to move or revert. |
| Android companion state query hooks | Already patch-owned | Current hooks live in `patches/graphics_patches.c` and call Banjo game helpers such as `map_get`, `level_get`, `jiggyscore_total`, `itemscore_noteScores_getTotal`, and `fileProgressFlag_get`. This is Banjo-specific port behavior and belongs in the BanjoRecomp patch layer. |
| Native Android/JNI transport | Banjo app repo | `src/game/recomp_api.cpp` owns the current `recomp_android_update_dual_screen_stats` bridge and Java method target. The payload and JNI target are Banjo-specific; only a future package-free transport seam should move to shared runtime code. |

## Decision

No decompiled-source edits need to be moved. Keep companion display state classification and stat collection in `patches/graphics_patches.c`, with the Android bridge in `src/game/recomp_api.cpp`. Do not modify `lib/bk-decomp` unless a future change is a true upstream decomp correctness fix independent of Android publishing or companion-display behavior.

## Verification

Patch generation/build checks for this audit:

```sh
make -C patches
./N64Recomp patches.toml
git diff --check -- docs/plans/android-bk-decomp-state-hook-audit.md patches/graphics_patches.c src/game/recomp_api.cpp
```
