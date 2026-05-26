#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

source "$HOME/.config/android-build-env.sh"

cmake -S "$REPO_ROOT" -B "$REPO_ROOT/build/hermes-android-shell" \
  -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Debug \
  -DCMAKE_MAKE_PROGRAM="$ANDROID_HOME/cmake/3.22.1/bin/ninja" \
  -DSDL2_DIR="$BANJO_ANDROID_SDL2_PREFIX/lib/cmake/SDL2" \
  -DBANJO_ANDROID_SHELL_ONLY=ON

cmake --build "$REPO_ROOT/build/hermes-android-shell" --target BanjoAndroidShell -j"$(nproc)"
file "$REPO_ROOT/build/hermes-android-shell/libBanjoAndroidShell.so"
