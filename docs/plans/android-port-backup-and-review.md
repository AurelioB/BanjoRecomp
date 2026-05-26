# Android Port Backup and Review Plan

> **For Hermes:** Review current Android port state before further cleanup. Do not rewrite broad areas until the current working state is backed up.

**Goal:** Preserve the current working Android port state, then review all porting decisions for polish, correctness, optimization, dead/redundant code, and maintainability.

**Current known-good baseline:** The current APK builds, installs, launches, pauses gameplay/audio in Android recents, resumes without static, and has improved in-game audio crackle behavior.

---

## Backup Plan

### 1. Capture repository identity and current state

Run from `/home/hermes/Projects/personal/BanjoRecomp`:

```bash
git status --short --branch
git submodule status --recursive
git diff --stat > /tmp/banjo-android-port-diff-stat.txt
git diff --submodule=diff > /tmp/banjo-android-port-full.diff
```

Also capture untracked files that are part of the Android port:

```bash
git ls-files --others --exclude-standard > /tmp/banjo-android-port-untracked.txt
```

### 2. Make a filesystem backup outside the repo

Preferred destination:

```bash
/home/hermes/Projects/backups/BanjoRecomp-android-port-YYYYMMDD-HHMMSS
```

Use `rsync` to preserve the working tree while excluding bulky/regenerable build caches:

```bash
mkdir -p /home/hermes/Projects/backups
rsync -a \
  --exclude='.git' \
  --exclude='android/.gradle' \
  --exclude='android/build' \
  --exclude='android/app/build' \
  --exclude='android/app/.cxx' \
  --exclude='build' \
  --exclude='build-*' \
  /home/hermes/Projects/personal/BanjoRecomp/ \
  /home/hermes/Projects/backups/BanjoRecomp-android-port-YYYYMMDD-HHMMSS/
```

Then copy diff metadata into the backup folder:

```bash
cp /tmp/banjo-android-port-*.txt /tmp/banjo-android-port-full.diff \
  /home/hermes/Projects/backups/BanjoRecomp-android-port-YYYYMMDD-HHMMSS/
```

### 3. Optional stronger backup: git worktree/bundle

If the intent is long-term preservation, create a temporary branch and git bundle after staging intentional files:

```bash
git checkout -b backup/android-port-working-state
# Review and add intentional files only.
git add <intentional files>
git commit -m "backup: preserve Android port working state"
git bundle create /home/hermes/Projects/backups/BanjoRecomp-android-port.bundle HEAD --all
```

Do not do this until generated files, ROM-derived assets, and accidental build artifacts are excluded.

---

## Review Plan

### 1. Classify every changed file

Group changes into:

- Android project scaffolding and Gradle build
- Java Activity/SDL lifecycle glue
- Native Android entrypoint/platform guards
- Audio lifecycle and queue pacing
- Runtime pause/tick scheduling
- Vulkan/RT64 Android renderer changes
- Filesystem/storage/import handling
- Launcher icon/resources
- Docs/plans/dev notes
- Untracked or generated artifacts that should not be committed

### 2. Review for correctness and maintainability

For each group, check:

- Is the change Android-guarded where needed?
- Does desktop behavior stay unchanged?
- Is there duplicated or dead probe/debug code?
- Are build flags named clearly and used consistently?
- Are lifecycle transitions idempotent and thread-safe?
- Are locks scoped narrowly enough?
- Are runtime pause semantics correct for focus loss, pause, sleep/wake, and return?
- Does audio prefer stable output over destructive catch-up on Android?
- Are device-specific paths, ports, or temporary debug assumptions absent?

### 3. Review performance risks

Check:

- Android native build uses optimization flags even under Debug.
- Audio queue thresholds are conservative but not latency-hostile.
- Runtime pause loop sleeps without busy-waiting.
- Vulkan changes avoid unnecessary per-frame work or synchronization.
- APK packaging avoids unnecessary assets in non-dev builds.

### 4. Review cleanup opportunities

Look for:

- Temporary logs/instrumentation left in Java/C++.
- Probe-only flags/classes that can be deleted or documented as intentional.
- Redundant lifecycle calls between SDLActivity and custom Activity.
- Duplicate Android audio open/reset code that should become a helper.
- Untracked generated files that should remain ignored.
- Docs that need updating with final behavior.

### 5. Produce an action list before editing

Output findings as:

- P0: likely correctness bugs or data-loss/build blockers
- P1: important cleanup before commit/PR
- P2: polish/maintainability improvements
- P3: optional future improvements

Do not start broad refactors until the backup has been made.
