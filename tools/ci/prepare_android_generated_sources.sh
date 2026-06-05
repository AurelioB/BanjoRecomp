#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-runtime}"
PRIVATE_INPUTS_DIR="${BANJO_ANDROID_PRIVATE_INPUTS_DIR:-extra/private-inputs}"
DECOMPRESSED_ROM="${BANJO_ANDROID_DECOMPRESSED_ROM:-${PRIVATE_INPUTS_DIR}/banjo.us.v10.decompressed.z64}"
N64RECOMP_SOURCE_DIR="${BANJO_ANDROID_N64RECOMP_SOURCE_DIR:-lib/N64ModernRuntime/N64Recomp}"
N64RECOMP_BUILD_DIR="${BANJO_ANDROID_N64RECOMP_BUILD_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/banjo-n64recomp-build}"

have_runtime_sources() {
  (compgen -G 'RecompiledFuncs/*.c' >/dev/null || compgen -G 'RecompiledFuncs/*.cpp' >/dev/null) && \
  [[ -f rsp/n_aspMain.cpp ]] && \
  [[ -f RecompiledPatches/patches.c ]]
}

build_recomp_tools() {
  if [[ -x ./N64Recomp && -x ./RSPRecomp ]]; then
    echo "N64Recomp and RSPRecomp are already present."
    return
  fi

  if [[ ! -d "$N64RECOMP_SOURCE_DIR" ]]; then
    echo "N64Recomp source directory is missing: $N64RECOMP_SOURCE_DIR" >&2
    exit 2
  fi

  echo "Building N64Recomp/RSPRecomp from $N64RECOMP_SOURCE_DIR."
  cmake \
    -S "$N64RECOMP_SOURCE_DIR" \
    -B "$N64RECOMP_BUILD_DIR" \
    -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_MAKE_PROGRAM=ninja
  cmake --build "$N64RECOMP_BUILD_DIR" --config Release --target N64Recomp RSPRecomp -j "$(nproc)"
  cp "$N64RECOMP_BUILD_DIR/N64Recomp" ./N64Recomp
  cp "$N64RECOMP_BUILD_DIR/RSPRecomp" ./RSPRecomp
  chmod +x ./N64Recomp ./RSPRecomp
}

if [[ "$MODE" == "probe" ]]; then
  echo "Probe build selected; generated game sources are not required."
  exit 0
fi

if have_runtime_sources; then
  echo "Generated runtime sources are already present."
  exit 0
fi

if [[ ! -f "$DECOMPRESSED_ROM" ]]; then
  cat >&2 <<MSG
Runtime APK build needs generated BanjoRecomp sources, but they are missing.

Expected generated files at minimum:
  RecompiledFuncs/*.c or *.cpp
  RecompiledPatches/patches.c
  rsp/n_aspMain.cpp

This workflow now follows the upstream-style generation path. Provide the private
input repository checkout containing:
  banjo.us.v10.decompressed.z64

Expected ROM path for this run:
  $DECOMPRESSED_ROM

Probe builds can run without this by setting build_mode=probe.
MSG
  exit 2
fi

cp "$DECOMPRESSED_ROM" banjo.us.v10.decompressed.z64
build_recomp_tools

./N64Recomp banjo.us.rev0.toml
./RSPRecomp n_aspMain.us.rev0.toml
CC="${PATCHES_C_COMPILER:-clang}" LD="${PATCHES_LD:-ld.lld}" make -C patches
./N64Recomp patches.toml

if ! have_runtime_sources; then
  echo "Runtime source generation completed, but required generated files are still missing." >&2
  exit 2
fi

# The decompressed ROM is needed only while generating sources. Remove it before
# Gradle packaging so it cannot accidentally be bundled as an APK asset.
rm -f banjo.us.v10.decompressed.z64

echo "Generated runtime sources are ready."
