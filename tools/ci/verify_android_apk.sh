#!/usr/bin/env bash
set -euo pipefail

APK="${1:?usage: verify_android_apk.sh path/to.apk [runtime|probe]}"
MODE="${2:-runtime}"

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 2
fi

python3 - "$APK" "$MODE" <<'PY'
import hashlib
import sys
import zipfile

apk, mode = sys.argv[1:3]
forbidden = (
    'baserom',
    'decompressed',
    '.z64',
    '.n64',
    '.v64',
    'banjo.us',
    'dev-roms/',
    'RecompiledFuncs',
    'RecompiledPatches',
)
with zipfile.ZipFile(apk) as zf:
    names = zf.namelist()
    libs = sorted(n for n in names if n.startswith('lib/'))
    bad = [n for n in names if any(token in n for token in forbidden)]

if bad:
    print('Forbidden ROM/generated artifact entries found in APK:', file=sys.stderr)
    for name in bad:
        print(f'  {name}', file=sys.stderr)
    sys.exit(1)

required_lib = 'lib/arm64-v8a/libmain.so'
if required_lib not in libs:
    print(f'Missing required native library: {required_lib}', file=sys.stderr)
    sys.exit(1)

if 'lib/arm64-v8a/libSDL2.so' not in libs:
    print('Missing required native library: lib/arm64-v8a/libSDL2.so', file=sys.stderr)
    sys.exit(1)

with open(apk, 'rb') as f:
    digest = hashlib.sha256(f.read()).hexdigest()

print(f'APK: {apk}')
print(f'Mode: {mode}')
print(f'SHA256: {digest}')
print('Native libs:')
for lib in libs:
    print(f'  {lib}')
print('ROM/generated artifact scan: clean')
PY
