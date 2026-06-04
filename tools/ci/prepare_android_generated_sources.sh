#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-runtime}"
ARCHIVE="${BANJO_ANDROID_GENERATED_SOURCES_ARCHIVE:-}"
URL="${BANJO_ANDROID_GENERATED_SOURCES_URL:-}"
TOKEN="${BANJO_ANDROID_GENERATED_SOURCES_TOKEN:-}"
TMP_ARCHIVE="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/banjo-generated-sources.tar"

have_runtime_sources() {
  (compgen -G 'RecompiledFuncs/*.c' >/dev/null || compgen -G 'RecompiledFuncs/*.cpp' >/dev/null) && \
  [[ -f rsp/n_aspMain.cpp ]] && \
  [[ -f RecompiledPatches/patches.c ]]
}

if [[ "$MODE" == "probe" ]]; then
  echo "Probe build selected; generated game sources are not required."
  exit 0
fi

if have_runtime_sources; then
  echo "Generated runtime sources are already present."
  exit 0
fi

if [[ -n "$ARCHIVE" ]]; then
  if [[ ! -f "$ARCHIVE" ]]; then
    echo "BANJO_ANDROID_GENERATED_SOURCES_ARCHIVE does not exist: $ARCHIVE" >&2
    exit 2
  fi
  TMP_ARCHIVE="$ARCHIVE"
elif [[ -n "$URL" ]]; then
  echo "Downloading generated source archive."
  if [[ -n "$TOKEN" ]]; then
    curl -fsSL --retry 3 --retry-delay 5 -H "Authorization: Bearer $TOKEN" -o "$TMP_ARCHIVE" "$URL"
  else
    curl -fsSL --retry 3 --retry-delay 5 -o "$TMP_ARCHIVE" "$URL"
  fi
else
  cat >&2 <<'MSG'
Runtime APK build needs generated BanjoRecomp sources, but they are missing.

Expected at minimum:
  RecompiledFuncs/*.c or *.cpp
  RecompiledPatches/patches.c
  rsp/n_aspMain.cpp

Do not commit ROMs. For GitHub Actions, provide a private tar archive containing
those generated source directories/files via repository secrets:
  BANJO_ANDROID_GENERATED_SOURCES_URL
  optional BANJO_ANDROID_GENERATED_SOURCES_TOKEN

The archive may be .tar, .tar.gz/.tgz, .tar.xz, or .tar.zst and should extract at
repo root. Probe builds can run without this by setting build_mode=probe.
MSG
  exit 2
fi

case "$TMP_ARCHIVE" in
  *.tar) tar -xf "$TMP_ARCHIVE" ;;
  *.tar.gz|*.tgz) tar -xzf "$TMP_ARCHIVE" ;;
  *.tar.xz|*.txz) tar -xJf "$TMP_ARCHIVE" ;;
  *.tar.zst|*.tzst) tar --zstd -xf "$TMP_ARCHIVE" ;;
  *)
    if tar -tf "$TMP_ARCHIVE" >/dev/null 2>&1; then
      tar -xf "$TMP_ARCHIVE"
    else
      echo "Unsupported generated source archive format: $TMP_ARCHIVE" >&2
      exit 2
    fi
    ;;
esac

if ! have_runtime_sources; then
  echo "Generated source archive extracted, but required runtime files are still missing." >&2
  exit 2
fi

echo "Generated runtime sources are ready."
