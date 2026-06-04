#!/usr/bin/env bash
set -euo pipefail

SDL2_VERSION="${SDL2_VERSION:-2.32.10}"
FREETYPE_VERSION="${FREETYPE_VERSION:-2.13.3}"
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-24}"
PREFIX_ROOT="${ANDROID_PREFIX_ROOT:-$HOME/Android/prefixes}"
JOBS="${JOBS:-$(nproc 2>/dev/null || echo 2)}"

: "${ANDROID_HOME:?ANDROID_HOME must point at the Android SDK}"
: "${ANDROID_NDK_HOME:=${ANDROID_HOME}/ndk/28.2.13676358}"

case "$ANDROID_ABI" in
  arm64-v8a) PREFIX_ABI="android-arm64" ;;
  armeabi-v7a) PREFIX_ABI="android-armv7" ;;
  x86_64) PREFIX_ABI="android-x86_64" ;;
  x86) PREFIX_ABI="android-x86" ;;
  *) echo "Unsupported Android ABI: $ANDROID_ABI" >&2; exit 2 ;;
esac

SDL2_PREFIX="${BANJO_ANDROID_SDL2_PREFIX:-${PREFIX_ROOT}/SDL2-${SDL2_VERSION}-${PREFIX_ABI}}"
FREETYPE_PREFIX="${BANJO_ANDROID_FREETYPE_PREFIX:-${PREFIX_ROOT}/freetype-${FREETYPE_VERSION}-${PREFIX_ABI}}"
WORK_DIR="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/banjo-android-deps"
CMAKE_BIN="${ANDROID_HOME}/cmake/3.22.1/bin/cmake"

if [[ ! -x "$CMAKE_BIN" ]]; then
  CMAKE_BIN="$(command -v cmake)"
fi

mkdir -p "$PREFIX_ROOT" "$WORK_DIR"
cd "$WORK_DIR"

fetch() {
  local url="$1"
  local out="$2"
  if [[ ! -f "$out" ]]; then
    curl -fsSL --retry 3 --retry-delay 5 -o "$out" "$url"
  fi
}

if [[ ! -f "$SDL2_PREFIX/lib/libSDL2.so" || ! -f "$SDL2_PREFIX/lib/cmake/SDL2/SDL2Config.cmake" ]]; then
  echo "Building SDL2 ${SDL2_VERSION} for ${ANDROID_ABI} -> ${SDL2_PREFIX}"
  rm -rf "SDL2-${SDL2_VERSION}" "build-sdl2-${ANDROID_ABI}"
  fetch "https://github.com/libsdl-org/SDL/releases/download/release-${SDL2_VERSION}/SDL2-${SDL2_VERSION}.tar.gz" "SDL2-${SDL2_VERSION}.tar.gz"
  tar -xzf "SDL2-${SDL2_VERSION}.tar.gz"
  "$CMAKE_BIN" -S "SDL2-${SDL2_VERSION}" -B "build-sdl2-${ANDROID_ABI}" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ANDROID_ABI" \
    -DANDROID_PLATFORM="android-${ANDROID_PLATFORM}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$SDL2_PREFIX" \
    -DSDL_SHARED=ON \
    -DSDL_STATIC=OFF \
    -DSDL_TEST=OFF
  "$CMAKE_BIN" --build "build-sdl2-${ANDROID_ABI}" --parallel "$JOBS"
  "$CMAKE_BIN" --install "build-sdl2-${ANDROID_ABI}"
else
  echo "Using cached SDL2 prefix: ${SDL2_PREFIX}"
fi

if [[ ! -f "$FREETYPE_PREFIX/lib/libfreetype.a" || ! -d "$FREETYPE_PREFIX/include/freetype2" ]]; then
  echo "Building Freetype ${FREETYPE_VERSION} for ${ANDROID_ABI} -> ${FREETYPE_PREFIX}"
  rm -rf "freetype-${FREETYPE_VERSION}" "build-freetype-${ANDROID_ABI}"
  fetch "https://download.savannah.gnu.org/releases/freetype/freetype-${FREETYPE_VERSION}.tar.xz" "freetype-${FREETYPE_VERSION}.tar.xz"
  tar -xJf "freetype-${FREETYPE_VERSION}.tar.xz"
  "$CMAKE_BIN" -S "freetype-${FREETYPE_VERSION}" -B "build-freetype-${ANDROID_ABI}" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ANDROID_ABI" \
    -DANDROID_PLATFORM="android-${ANDROID_PLATFORM}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$FREETYPE_PREFIX" \
    -DBUILD_SHARED_LIBS=OFF \
    -DFT_DISABLE_ZLIB=TRUE \
    -DFT_DISABLE_BZIP2=TRUE \
    -DFT_DISABLE_PNG=TRUE \
    -DFT_DISABLE_HARFBUZZ=TRUE \
    -DFT_DISABLE_BROTLI=TRUE
  "$CMAKE_BIN" --build "build-freetype-${ANDROID_ABI}" --parallel "$JOBS"
  "$CMAKE_BIN" --install "build-freetype-${ANDROID_ABI}"
else
  echo "Using cached Freetype prefix: ${FREETYPE_PREFIX}"
fi

{
  echo "BANJO_ANDROID_SDL2_PREFIX=${SDL2_PREFIX}"
  echo "BANJO_ANDROID_FREETYPE_PREFIX=${FREETYPE_PREFIX}"
} >> "${GITHUB_ENV:-/dev/null}"

echo "SDL2 prefix: ${SDL2_PREFIX}"
echo "Freetype prefix: ${FREETYPE_PREFIX}"
