# BanjoRecomp Android publish-polish audit baseline

Captured: 2026-06-13T18:57:25-06:00
Repo: `/home/hermes/Projects/personal/BanjoRecomp`
Branch: `android`
HEAD: `4f59ba85bba3607e7d39421e79bb56aed48afdb2` (`Use model ground texture for dual-screen background`)
Remote: `origin git@github.com:AurelioB/BanjoRecomp.git`

Purpose: freeze the current known working Android/dual-screen state before cleanup, ownership classification, or shared-framework refactors. This file is intentionally an audit note only; do not refactor code in this phase.

## Full diff references

A full local snapshot was written under:

`/home/hermes/Projects/personal/BanjoRecomp/.hermes/audits/publish-polish-baseline/`

Files:

| File | Lines | SHA-256 |
|---|---:|---|
| `captured-at.txt` | 1 | `dc4029d731c84cac13b949a89ab02ddc9bfd1186a581843d1a206dbe9f7f7b6f` |
| `root-status.txt` | 32 | `ef8bc17fb1247b45d88e0bad5ca4343412051ce1c35370b651ffced4cfa84c95` |
| `submodule-status-recursive.txt` | 45 | `4f31fa79f46b67ee33cdcced9e082f35afb7358d4f8a0cff6b3f07fd885405f7` |
| `root-diff-stat.txt` | 23 | `e1def41400dd211c9bfdf5f3949e0cc16c2dedfe955c91f5a6f811ba4ad7be78` |
| `root-diff-name-status.txt` | 22 | `a01b01a0225f02db15a872f8bc3d64089ca1af09ff02f7aca6acc0f60d195686` |
| `root-full.diff` | 2224 | `10bdd3c6e48f48e217857f13735a0d4a997efbbb1eb6f6f95c72255acadf27f7` |
| `submodule-working-status.txt` | 135 | `b4f0794d6db44f54300a97744ea0c7b3b0169dbd0e0430cf2dafec918d981f2d` |

Regenerate the same class of snapshot with:

```sh
cd /home/hermes/Projects/personal/BanjoRecomp
AUDIT_DIR=.hermes/audits/publish-polish-baseline
mkdir -p "$AUDIT_DIR"
date -Iseconds > "$AUDIT_DIR/captured-at.txt"
git status --short --branch > "$AUDIT_DIR/root-status.txt"
git submodule status --recursive > "$AUDIT_DIR/submodule-status-recursive.txt"
git diff --stat > "$AUDIT_DIR/root-diff-stat.txt"
git diff --name-status > "$AUDIT_DIR/root-diff-name-status.txt"
git diff > "$AUDIT_DIR/root-full.diff"
git submodule foreach --recursive 'echo "### $sm_path"; git status --short --branch; if ! git diff --quiet; then git diff --stat; fi' > "$AUDIT_DIR/submodule-working-status.txt"
sha256sum "$AUDIT_DIR"/* | sort > "$AUDIT_DIR/SHA256SUMS"
wc -l "$AUDIT_DIR"/* > "$AUDIT_DIR/line-counts.txt"
```

## Root status

`git status --short --branch`:

```text
## android...origin/android [ahead 12]
 M android/app/build.gradle
 M android/app/src/main/AndroidManifest.xml
 M android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java
 M android/app/src/main/java/io/github/banjorecomp/BanjoSpriteThemeExtractor.java
 M android/app/src/main/java/io/github/banjorecomp/DualScreenStats.java
 M android/app/src/main/java/io/github/banjorecomp/DualScreenStatsManager.java
 M android/app/src/main/java/io/github/banjorecomp/DualScreenStatsPresentation.java
 M android/app/src/main/java/io/github/banjorecomp/DualScreenStatsView.java
 M android/app/src/main/java/org/libsdl/app/SDLActivity.java
 M android/app/src/main/res/drawable/ic_launcher_background.xml
 M android/app/src/main/res/drawable/ic_launcher_foreground.png
 M android/app/src/main/res/mipmap-hdpi/ic_launcher.png
 M android/app/src/main/res/mipmap-mdpi/ic_launcher.png
 M android/app/src/main/res/mipmap-xhdpi/ic_launcher.png
 M android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png
 M android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
 M docs/android-port-findings.md
 M icons/app.png
 M patches/graphics_patches.c
 M src/android/sdl_lifecycle_probe.cpp
 M src/game/recomp_api.cpp
 M tools/generate_android_icons.py
?? .hermes/
?? android/app/src/main/java/io/github/banjorecomp/DualScreenDebugAreas.java
?? android/app/src/main/res/drawable/splash_transparent.xml
?? android/app/src/main/res/values-v31/
?? android/app/src/main/res/values/
?? docs/android-dual-screen-transition-findings.md
?? icons/app-adaptive-foreground.svg
?? icons/app.svg
?? tools/preview_dual_screen_background.py
```

Interpretation:

- Root branch is `android`, ahead of `origin/android` by 12 commits.
- Dirty tracked areas are Android Gradle/manifest, Banjo SDL activity, dual-screen Java UI, vendored SDLActivity, launcher icon assets/generator, Android findings docs, Banjo graphics patch hooks, and native Android/recomp bridge code.
- Untracked areas include Hermes plan/audit files, new Android resources, new dual-screen debug/transition notes, new SVG icon sources, and a dual-screen background preview tool.
- No files were staged at capture time.

## Root diff stat

`git diff --stat`:

```text
 android/app/build.gradle                           |   8 +-
 android/app/src/main/AndroidManifest.xml           |   4 +-
 .../io/github/banjorecomp/BanjoSDLActivity.java    | 138 +++-
 .../banjorecomp/BanjoSpriteThemeExtractor.java     |  82 ++-
 .../io/github/banjorecomp/DualScreenStats.java     |  54 +-
 .../github/banjorecomp/DualScreenStatsManager.java | 135 +++-
 .../banjorecomp/DualScreenStatsPresentation.java   |  31 +-
 .../io/github/banjorecomp/DualScreenStatsView.java | 798 ++++++++++++++++++++-
 .../src/main/java/org/libsdl/app/SDLActivity.java  |   4 +
 .../main/res/drawable/ic_launcher_background.xml   |   2 +-
 .../main/res/drawable/ic_launcher_foreground.png   | Bin 93946 -> 39998 bytes
 .../app/src/main/res/mipmap-hdpi/ic_launcher.png   | Bin 5049 -> 7453 bytes
 .../app/src/main/res/mipmap-mdpi/ic_launcher.png   | Bin 3000 -> 4363 bytes
 .../app/src/main/res/mipmap-xhdpi/ic_launcher.png  | Bin 7418 -> 10829 bytes
 .../app/src/main/res/mipmap-xxhdpi/ic_launcher.png | Bin 13195 -> 18352 bytes
 .../src/main/res/mipmap-xxxhdpi/ic_launcher.png    | Bin 21074 -> 26559 bytes
 docs/android-port-findings.md                      |   2 +-
 icons/app.png                                      | Bin 76013 -> 44148 bytes
 patches/graphics_patches.c                         | 129 +++-
 src/android/sdl_lifecycle_probe.cpp                |   5 +-
 src/game/recomp_api.cpp                            |  25 +-
 tools/generate_android_icons.py                    | 130 ++--
 22 files changed, 1432 insertions(+), 115 deletions(-)
```

Untracked files are intentionally not included in `git diff --stat`; see root status above.

## Root tracked name-status

`git diff --name-status`:

```text
M	android/app/build.gradle
M	android/app/src/main/AndroidManifest.xml
M	android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java
M	android/app/src/main/java/io/github/banjorecomp/BanjoSpriteThemeExtractor.java
M	android/app/src/main/java/io/github/banjorecomp/DualScreenStats.java
M	android/app/src/main/java/io/github/banjorecomp/DualScreenStatsManager.java
M	android/app/src/main/java/io/github/banjorecomp/DualScreenStatsPresentation.java
M	android/app/src/main/java/io/github/banjorecomp/DualScreenStatsView.java
M	android/app/src/main/java/org/libsdl/app/SDLActivity.java
M	android/app/src/main/res/drawable/ic_launcher_background.xml
M	android/app/src/main/res/drawable/ic_launcher_foreground.png
M	android/app/src/main/res/mipmap-hdpi/ic_launcher.png
M	android/app/src/main/res/mipmap-mdpi/ic_launcher.png
M	android/app/src/main/res/mipmap-xhdpi/ic_launcher.png
M	android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png
M	android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
M	docs/android-port-findings.md
M	icons/app.png
M	patches/graphics_patches.c
M	src/android/sdl_lifecycle_probe.cpp
M	src/game/recomp_api.cpp
M	tools/generate_android_icons.py
```

## Recursive submodule status

`git submodule status --recursive`:

```text
 6820055ca076e94e30e53d917bd9e5f71c28ca20 BanjoRecompSyms (heads/master)
 27b20c8c80aa817be24c2a2af3d42030225bf8d7 lib/N64ModernRuntime (heads/audit/android-runtime-pause)
 2b6f05688de2abc7d86da5b4a89b84c2c6acbabe lib/N64ModernRuntime/N64Recomp (mod-tool-release-18-g2b6f056)
 ad8b641f9682b6091ba8b9f7c8152255c1a2c803 lib/N64ModernRuntime/N64Recomp/lib/ELFIO (Release_3.7-236-gad8b641)
 0e8aad961d66904cfda8d7cc894f6f6eee2d9f30 lib/N64ModernRuntime/N64Recomp/lib/fmt (11.1.0)
 e0d8003047938e2ec3697eaf8d61a84d11d17b43 lib/N64ModernRuntime/N64Recomp/lib/rabbitizer (1.7.10)
 f6326087b3404efb07c6d3deed97b3c3b8098c0c lib/N64ModernRuntime/N64Recomp/lib/sljit (f632608)
 1f7884e59165e517462f922e7b6de131bd9844f3 lib/N64ModernRuntime/N64Recomp/lib/tomlplusplus (v3.4.0-7-g1f7884e)
 8573fd7cd6f49b262a0ccc447f3c6acfc415e556 lib/N64ModernRuntime/thirdparty/miniz (3.0.2-41-g8573fd7)
 a124b850791db2a33f7354d2b0aa7da821cef6f5 lib/N64ModernRuntime/thirdparty/o1heap (heads/master)
 ac3a25da3d957d9ef3e4114d9f8332d34ce83a46 lib/N64ModernRuntime/thirdparty/xxHash (v0.7.4-748-gac3a25d)
 c4fdf39a74f23d92acc8a29a6c55fcc209a77bfe lib/RecompFrontend (heads/audit/android-frontend-support)
 7a06f27db04fe5d13a5dacc19b2b4544673a4eca lib/RecompFrontend/recompui/lib/RmlUi (6.0)
 9124a2073f3a7055726be46bab1d54bf14f68f63 lib/RecompFrontend/recompui/lib/freetype-windows-binaries (v2.14.1)
 83c58df8103dc7dca423dfd824992af94d49bed6 lib/RecompFrontend/recompui/lib/lunasvg (v3.5.0)
 351ca1580c10e550160ac11c77824fa9a498015e lib/bk-decomp (heads/backup/pre-audit-split)
 ddb2fad1b73d4d7483d483fb833cad4564a5fc01 lib/bk-decomp/tools/asm-differ (ddb2fad)
 42e7ccaf1883279c7fe2f9d338ff3383175aa97f lib/bk-decomp/tools/asm-processor (1.0.0-4-g42e7cca)
 2e759b5716b42649ebb90429c6eb8c593370034f lib/bk-decomp/tools/bk_asset_tool (2e759b5)
 0dc629be232cdcba9d17ceb9ef4ce4ab573d860c lib/bk-decomp/tools/bk_asset_tool/rarezip (0dc629b)
 272180b527b01c0023dc2ab02bdfdfd373670906 lib/bk-decomp/tools/bk_rom_compressor (heads/master)
 4a911ff0f183c19b303fd8587c673d9dd34f423c lib/bk-decomp/tools/bk_rom_compressor/rarezip (4a911ff)
 6f1acba32b6d7b97eabb8387bbe9641b9ebed118 lib/bk-decomp/tools/ido-static-recomp (per-function~10^2)
 785e97b60418f9d56aca2dfd3374433f7bf92228 lib/bk-decomp/tools/n64splat (0.24.1-56-g785e97b)
 2647d781a0c03486c7e373eb7b0729258292b36c lib/rt64 (heads/audit/android-sdl-vulkan)
 2bdf73882b9169ca9d7a307b24d65d6c4e196084 lib/rt64/src/contrib/ddspp (1.1-5-g2bdf738)
 cc15e715ee378a4f675b335bd1071ff105873fc8 lib/rt64/src/contrib/dxc (cc15e71)
 6f5274c66132e8f951c400103d897582b8f21491 lib/rt64/src/contrib/hlslpp (3.6)
 d03941725fd0bd08c78c46e3e5b0265526e9d060 lib/rt64/src/contrib/im3d (d039417)
 277ae93c41314ba5f4c7444f37c4319cdf07e8cf lib/rt64/src/contrib/imgui (v1.62-3369-g277ae93c4)
 f156599faefe316f7dd20fe6c783bf87c8bb6fd9 lib/rt64/src/contrib/implot (v0.16-14-gf156599)
 860fac3fbae94194a392c1d9857e185eda6d083e lib/rt64/src/contrib/mupen64plus-core (2.5.9-484-g860fac3f)
 de8111fdcb89144abc16c85650ce4e21e028bfb5 lib/rt64/src/contrib/mupen64plus-win32-deps (2.5-21-gde8111f)
 f64980da56c0ef76b4f3fa0bc16b9d084d890741 lib/rt64/src/contrib/nativefiledialog-extended (v1.1.1-7-gf64980d)
 df4c7fdff2e28854874bb497f86a15cc0257a60b lib/rt64/src/contrib/plume (heads/audit/android-sdl-vulkan)
 9ef66bc14edd10dee0de3a545b98578363552f66 lib/rt64/src/contrib/plume/contrib/D3D12MemoryAllocator (v3.0.1)
 2fa203425eb4af9dfc6b03f97ef72b0b5bcb8350 lib/rt64/src/contrib/plume/contrib/Vulkan-Headers (v1.4.335)
 29b35ea4232688c0f42cdff0c10848290760a417 lib/rt64/src/contrib/plume/contrib/VulkanMemoryAllocator (v2.1.0-938-g29b35ea)
 be3dbd49bf77052665e96b6c7484af855e7e5f67 lib/rt64/src/contrib/plume/contrib/volk (vulkan-sdk-1.4.321.0-7-gbe3dbd4)
 5d6b756ee62760f71b65d37e41a0b5a3dab90507 lib/rt64/src/contrib/re-spirv (5d6b756)
 f013f08e4455bcc1f0eed8e3dd5e2009682656d9 lib/rt64/src/contrib/re-spirv/external/SPIRV-Headers (1.5.4.raytracing.fixed-367-gf013f08)
 6173e24b31f09a0c3217103a130e74c4ddec14a6 lib/rt64/src/contrib/spirv-cross (vulkan-sdk-1.4.304.0-2-g6173e24b)
 ae721c50eaf761660b4f90cc590453cdb0c2acd0 lib/rt64/src/contrib/stb (ae721c5)
 1864a50c9b5cf8500d8e9e61ed92aa0dd3772750 lib/rt64/src/contrib/xxHash (v0.7.4-707-g1864a50)
 0ff651dd876823b99fa5c5f53292be28381aee9b lib/rt64/src/contrib/zstd (v1.4.7-2211-g0ff651dd)
```

Submodule working trees were also checked with recursive `git submodule foreach`; no modified/untracked file entries were reported inside submodules beyond branch/detached-HEAD status lines.

Important current branch labels:

- `lib/N64ModernRuntime`: `audit/android-runtime-pause`
- `lib/RecompFrontend`: `audit/android-frontend-support`
- `lib/bk-decomp`: `backup/pre-audit-split`
- `lib/rt64`: `audit/android-sdl-vulkan`
- `lib/rt64/src/contrib/nativefiledialog-extended`: `audit/android-null-backend`
- `lib/rt64/src/contrib/plume`: `audit/android-sdl-vulkan`

## Known-good build/install/launch commands

Use system `gradle`; this checkout currently has no Gradle wrapper requirement.

```sh
cd /home/hermes/Projects/personal/BanjoRecomp
source /home/hermes/.config/android-build-env.sh
export PATH=$(printf '%s' "$PATH" | tr ':' '\n' | grep -v 'toolchains/llvm/prebuilt/linux-x86_64/bin' | paste -sd: -)
export PATH="/usr/lib/llvm-19/bin:$PATH"
ln -sf "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/ld.lld" /home/hermes/.local/bin/ld.lld
gradle -p android --no-daemon :app:assembleDebug --rerun-tasks
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity
```

Dev-only full runtime plus bundled local ROM assets remains gated; do not use it for release artifacts:

```sh
gradle -p android --no-daemon :app:assembleDebug -PbanjoBundleDevRoms=true --stacktrace
```

Observed existing debug APK identity at capture time:

```text
package: name='com.aure.banjorecomp' versionCode='1' versionName='0.1.0-android-runtime'
launchable-activity: name='io.github.banjorecomp.BanjoSDLActivity' label='Banjo Recompiled'
```

## AYN Thor adb note

AYN Thor was visible over adb during this audit. Two adb transports for the same device were listed; use an explicit serial to avoid ambiguous-device failures:

```sh
adb -s 192.168.8.205:39129 install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.8.205:39129 shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity
```

Display snapshot from `adb -s 192.168.8.205:39129 shell cmd display get-displays`:

- Display 0: built-in screen, internal, 1920x1080 app bounds in current rotation.
- Display 4: `Screen-2`, internal presentation display, `FLAG_PRESENTATION`, 1240x1080 app bounds in current rotation.

Do not run `adb uninstall`, `adb shell pm clear`, or app-private data cleanup without backing up saves first. Use `adb install -r` for upgrade smoke tests.

## Artifact hygiene for this phase

- This phase intentionally stages only this audit doc if committed.
- Do not stage `.hermes/`, APKs, Gradle/CMake build directories, ROMs, generated recomp artifacts, screenshots, or device captures.
- If committing this phase, use exactly:

```text
docs(android): record publish polish audit baseline
```
