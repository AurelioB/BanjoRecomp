# Android APK distribution

This repo builds Android APKs with GitHub Actions in `.github/workflows/android-apk.yml`.
GitHub Releases is the only publishing channel for Android APKs in this polish pass. Do not publish Android builds to Google Play, third-party stores, or ad-hoc public file shares from this repository unless release policy changes in a later documented decision.

## What the workflow does

- Checks out submodules recursively.
- For runtime builds, checks out the private inputs repository into `extra` using a read-only deploy key.
- Copies the private input repo contents from `extra/` into the repository root, matching upstream's `cp extra/* .` pattern.
- Builds `N64Recomp` and `RSPRecomp` from the pinned submodule under `lib/N64ModernRuntime/N64Recomp`, then runs them to generate runtime sources.
- Installs JDK 17, Android SDK platform 36, build-tools 36.0.0, NDK 28.2.13676358, and CMake 3.22.1.
- Builds Android arm64 SDL2 2.32.10 and Freetype 2.13.3 into `~/Android/prefixes` and caches them.
- Builds the Gradle APK from `android/`.
- Verifies the APK contains `libmain.so` and `libSDL2.so` and does not contain ROM/generated-game artifacts.
- Uploads the APK and a `.sha256` file as workflow artifacts for CI traceability. Workflow artifacts are not the public distribution channel.
- On matching tag pushes, attaches the APK and checksum to the GitHub Release.
- On manual dispatch with `release_version`, creates `android-v<version>`, writes release notes from commits since the previous Android tag, and lets the tag-triggered build attach the signed APK to the GitHub Release.

## Build modes

`runtime` is the real APK mode. It requires generated BanjoRecomp sources to exist before the native build:

- `RecompiledFuncs/*.c` or `RecompiledFuncs/*.cpp`
- `RecompiledPatches/patches.c`
- `rsp/n_aspMain.cpp`

Those files are generated in CI from a legally owned decompressed ROM stored in the private inputs repository. They should not be committed unless the project/legal policy explicitly changes.

`probe` builds the SDL lifecycle smoke APK with `-PbanjoProbe=true`. It does not require generated game sources and is used for pull requests.

## Required repository secrets for distributable runtime releases

For a signed runtime release APK, configure these GitHub repository secrets:

- `BANJO_ANDROID_PRIVATE_INPUTS_SSH_KEY`: private half of the read-only deploy key that can clone the private inputs repository.
- `BANJO_ANDROID_KEYSTORE_BASE64`: base64 encoded Android release keystore.
- `BANJO_ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `BANJO_ANDROID_KEY_ALIAS`: key alias.
- `BANJO_ANDROID_KEY_PASSWORD`: key password. Optional if it matches the keystore password.

The configured GitHub secret names have been checked with `gh secret list --repo AurelioB/BanjoRecomp`; only the secret names were inspected, never their values. The workflow fails fast before decoding or building a runtime `Release` APK if any required signing secret name is absent or empty. `BANJO_ANDROID_KEY_PASSWORD` may be omitted only when the key password is the same as `BANJO_ANDROID_KEYSTORE_PASSWORD`.

The workflow also reads repository variable `BANJO_ANDROID_PRIVATE_INPUTS_REPO`, currently expected to be `AurelioB/BanjoRecomp-private-inputs`. That private repository should contain the private files expected by the public TOML configuration at its root.

A runtime `Release` build fails fast if signing secrets are missing. That is intentional: unsigned release APKs are not useful for GitHub Releases distribution. Local non-distributable smoke builds may pass `-PbanjoAllowUnsignedRelease=true`, but that must not be used for published artifacts.

## Creating a release

### Recommended local helper

Use the helper script to avoid fat-fingering GitHub Actions inputs:

```bash
./scripts/android-release.sh 0.1.0
```

Dry-run first if you want to verify the derived tag and Android `versionCode`:

```bash
./scripts/android-release.sh --dry-run 0.1.0
```

The helper validates the version, checks that `android` matches `origin/android`, verifies the tag does not already exist, and dispatches the `Android APK` workflow with `release_version` set.

Release tag builds derive Android app metadata from the tag:

- `android-v0.1.0` -> `versionName=0.1.0`, `versionCode=100`
- `android-v0.1.1` -> `versionName=0.1.1`, `versionCode=101`
- `android-v1.2.3` -> `versionName=1.2.3`, `versionCode=10203`

This keeps release APKs installable as updates as long as each new release has a higher semantic version.

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

When `release_version` is set, the dispatch run:

1. Creates or reuses `android-v<version>` at the selected branch commit.
2. Finds the previous merged `android-v*` tag, falling back to `v*` if needed.
3. Creates or updates a GitHub Release whose notes list every non-merge commit between the previous tag and the new tag.
4. Builds the signed runtime Release APK and attaches the APK plus `.sha256` to that release. Release APK assets use the upstream-style filename form, for example `BanjoRecompiled-v0.1.0-Android-ARM64.apk`.

Leave `release_version` empty only for internal CI validation; those workflow artifacts are not a publishing channel.

## Local equivalents

Probe APK:

```bash
source ~/.config/android-build-env.sh
tools/ci/prepare_android_generated_sources.sh probe
gradle -p android --no-daemon :app:assembleDebug -PbanjoProbe=true --stacktrace
tools/ci/verify_android_apk.sh android/app/build/outputs/apk/debug/app-debug.apk probe
```

Runtime APK after generated sources are present, using release signing environment variables:

```bash
source ~/.config/android-build-env.sh
export BANJO_ANDROID_KEYSTORE_FILE=/path/to/release.jks
export BANJO_ANDROID_KEYSTORE_PASSWORD=...
export BANJO_ANDROID_KEY_ALIAS=...
# Optional when the key password differs from the keystore password:
# export BANJO_ANDROID_KEY_PASSWORD=...
tools/ci/prepare_android_generated_sources.sh runtime
gradle -p android --no-daemon :app:assembleRelease --stacktrace
tools/ci/verify_android_apk.sh android/app/build/outputs/apk/release/app-release.apk runtime
```

For a local, non-distributable runtime smoke build without signing, add `-PbanjoAllowUnsignedRelease=true`. Do not upload that APK to GitHub Releases.

Runtime APK using the private-input layout locally:

```bash
git clone git@github.com:AurelioB/BanjoRecomp-private-inputs.git extra
source ~/.config/android-build-env.sh
tools/ci/prepare_android_generated_sources.sh runtime
gradle -p android --no-daemon :app:assembleRelease --stacktrace
```

If this is only a local smoke build and no release keystore is available, add `-PbanjoAllowUnsignedRelease=true` to the Gradle command and keep the APK private.
