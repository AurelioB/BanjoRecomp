#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./scripts/android-release.sh [--dry-run] [--ref <branch-or-sha>] <version>

Examples:
  ./scripts/android-release.sh 0.1.0
  ./scripts/android-release.sh android-v0.1.0
  ./scripts/android-release.sh --dry-run 0.1.0

This triggers the Android APK GitHub Actions release flow. The workflow creates
android-v<version>, creates a GitHub Release with changelog notes, then the
resulting tag build attaches the signed APK and SHA256 checksum.

Android versionCode for release tags is derived as:
  major * 10000 + minor * 100 + patch
EOF
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dry_run=false
release_ref="android"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      dry_run=true
      shift
      ;;
    --ref)
      if [[ $# -lt 2 ]]; then
        echo "--ref requires a branch, tag, or SHA" >&2
        exit 1
      fi
      release_ref="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      break
      ;;
  esac
done

if [[ $# -ne 1 ]]; then
  usage >&2
  exit 1
fi

input_version="$1"
version_name="$input_version"
version_name="${version_name#android-v}"
version_name="${version_name#v}"

if [[ ! "$version_name" =~ ^[0-9]+[.][0-9]+[.][0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "Version must look like 1.2.3, v1.2.3, or android-v1.2.3" >&2
  exit 1
fi

base_version="${version_name%%-*}"
IFS=. read -r major minor patch <<<"$base_version"

if (( 10#$minor > 99 || 10#$patch > 99 )); then
  echo "Minor and patch versions must be <= 99 because versionCode is major*10000 + minor*100 + patch." >&2
  exit 1
fi

version_code=$((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
tag_name="android-v$version_name"

if (( version_code <= 0 || version_code > 2100000000 )); then
  echo "Derived Android versionCode is out of range: $version_code" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "gh CLI is not authenticated." >&2
  exit 1
fi

if git -C "$repo_root" rev-parse -q --verify "refs/tags/$tag_name" >/dev/null; then
  echo "Tag already exists locally: $tag_name" >&2
  exit 1
fi

if git -C "$repo_root" ls-remote --exit-code --tags origin "refs/tags/$tag_name" >/dev/null 2>&1; then
  echo "Tag already exists on origin: $tag_name" >&2
  exit 1
fi

if [[ "$release_ref" == "android" ]]; then
  git -C "$repo_root" fetch origin android --quiet
  local_android="$(git -C "$repo_root" rev-parse android 2>/dev/null || true)"
  origin_android="$(git -C "$repo_root" rev-parse origin/android)"
  if [[ -n "$local_android" && "$local_android" != "$origin_android" ]]; then
    echo "Local android does not match origin/android. Push or pull before releasing." >&2
    echo "local:  $local_android" >&2
    echo "origin: $origin_android" >&2
    exit 1
  fi
fi

echo "Release version: $version_name"
echo "Android versionCode: $version_code"
echo "Git tag to create in workflow: $tag_name"
echo "Workflow ref: $release_ref"

if [[ "$dry_run" == true ]]; then
  echo "Dry run only; no workflow was triggered."
  exit 0
fi

gh workflow run android-apk.yml \
  --repo AurelioB/BanjoRecomp-Android \
  --ref "$release_ref" \
  -f build_mode=runtime \
  -f build_type=Release \
  -f upload_to_release=false \
  -f release_version="$version_name"

echo "Release workflow dispatched for $tag_name."
echo "Watch runs with: gh run list --repo AurelioB/BanjoRecomp-Android --workflow 'Android APK' --limit 5"
