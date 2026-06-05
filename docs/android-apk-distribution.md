# Android APK distribution

This repo builds Android APKs with GitHub Actions in `.github/workflows/android-apk.yml`.

## What the workflow does

- Checks out submodules recursively.
- Installs JDK 17, Android SDK platform 36, build-tools 36.0.0, NDK 28.2.13676358, and CMake 3.22.1.
- Builds Android arm64 SDL2 2.32.10 and Freetype 2.13.3 into `~/Android/prefixes` and caches them.
- Builds the Gradle APK from `android/`.
- Verifies the APK contains `libmain.so` and `libSDL2.so` and does not contain ROM/generated-game artifacts.
- Uploads the APK and a `.sha256` file as workflow artifacts.
- On matching tag pushes, attaches the APK and checksum to the GitHub Release.
- On manual dispatch with `release_version`, creates `android-v<version>`, writes release notes from commits since the previous Android tag, and lets the tag-triggered build attach the APK.

## Build modes

`runtime` is the real APK mode. It requires generated BanjoRecomp sources to exist before the native build:

- `RecompiledFuncs/*.c` or `RecompiledFuncs/*.cpp`
- `RecompiledPatches/patches.c`
- `rsp/n_aspMain.cpp`

Those files are generated from a legally owned ROM and should not be committed unless the project/legal policy explicitly changes.

`probe` builds the SDL lifecycle smoke APK with `-PbanjoProbe=true`. It does not require generated game sources and is used for pull requests.

## Required repository secrets for distributable runtime releases

For a signed runtime release APK, configure these GitHub repository secrets:

- `BANJO_ANDROID_GENERATED_SOURCES_URL`: private HTTPS URL for a tar archive of the generated source files. The archive should extract at repo root. Supported formats: `.tar`, `.tar.gz`, `.tgz`, `.tar.xz`, `.tar.zst`.
- `BANJO_ANDROID_GENERATED_SOURCES_TOKEN`: optional bearer token for the generated-source archive URL.
- `BANJO_ANDROID_KEYSTORE_BASE64`: base64 encoded Android release keystore.
- `BANJO_ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `BANJO_ANDROID_KEY_ALIAS`: key alias.
- `BANJO_ANDROID_KEY_PASSWORD`: key password. Optional if it matches the keystore password.

A runtime `Release` build fails fast if signing secrets are missing. That is intentional: unsigned release APKs are not useful for distribution.

## Creating a release

### Local tag flow

Push a tag matching `android-v*` or `v*`:

```bash
git tag android-v0.1.0
git push origin android-v0.1.0
```

The tag-triggered workflow builds the runtime release APK, uploads it as an Actions artifact, and attaches it plus the SHA256 file to the GitHub Release for that tag.

### Manual GitHub Actions release flow

Run the `Android APK` workflow manually from GitHub Actions on the `android` branch and set:

- `release_version`: the new version, for example `0.1.1`, `v0.1.1`, or `android-v0.1.1`.

When `release_version` is set, the dispatch run does not build directly. It:

1. Creates and pushes `android-v<version>` at the selected branch commit.
2. Finds the previous merged `android-v*` tag, falling back to `v*` if needed.
3. Creates a GitHub Release whose notes list every non-merge commit between the previous tag and the new tag.
4. Lets the normal tag-triggered workflow run build the signed runtime Release APK and attach the APK plus `.sha256` to that release.

Leave `release_version` empty if you only want a manually dispatched build artifact without creating a release tag.

## Local equivalents

Probe APK:

```bash
source ~/.config/android-build-env.sh
tools/ci/prepare_android_generated_sources.sh probe
gradle -p android --no-daemon :app:assembleDebug -PbanjoProbe=true --stacktrace
tools/ci/verify_android_apk.sh android/app/build/outputs/apk/debug/app-debug.apk probe
```

Runtime APK after generated sources are present:

```bash
source ~/.config/android-build-env.sh
tools/ci/prepare_android_generated_sources.sh runtime
gradle -p android --no-daemon :app:assembleRelease --stacktrace
tools/ci/verify_android_apk.sh android/app/build/outputs/apk/release/app-release.apk runtime
```
