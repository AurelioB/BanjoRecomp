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

Push a tag matching `android-v*` or `v*`:

```bash
git tag android-v0.1.0
git push origin android-v0.1.0
```

The workflow builds the runtime release APK, uploads it as an Actions artifact, and attaches it plus the SHA256 file to the GitHub Release for that tag.

You can also run the workflow manually from GitHub Actions and choose `runtime` or `probe`.

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
