# Android save storage

## Implemented user flow

The Android-only **Save Management** settings page provides **Import Save**, **Export Save**, **Choose Save Folder**, and **Reset to App Storage**. Banjo saves are raw `0x800`-byte files. Import validates that exact size before atomically replacing runtime state; export uses a quiesced N64ModernRuntime snapshot rather than reading a file while its asynchronous writer may be active.

## SAF mirror model

Android's Storage Access Framework returns `content://` documents, not stable filesystem paths. Native emulation therefore continues to use the app-private `files/data/saves/bk.n64.us.1.0.bin` mirror.

When a folder is selected, `banjo-kazooie.bin` in that folder is authoritative across launches:

1. Folder permission is persisted with `ACTION_OPEN_DOCUMENT_TREE`.
2. If the destination has no recognized save artifact, a consistent snapshot of the existing save is copied into a new `banjo-kazooie.bin` before selection is committed.
3. On later launches the external document is copied to a temporary internal file, checked for the expected size, and atomically activated. The displaced internal save is kept as `.pre-external.bak` (timestamped if needed).
4. During play all native access targets the internal mirror.
5. On pause, and after a successful explicit import, a quiesced snapshot is written back to the external document.

This provides SAF compatibility without exposing a provider stream to latency-sensitive native save operations. It also means the external copy wins at the next launch if it was changed independently between sessions.

## Empty destinations and collisions

A folder is considered empty for this feature when it contains none of:

- `banjo-kazooie.bin`
- `bk.n64.us.1.0.bin`
- `banjo-kazooie.bin.bak`
- `bk.n64.us.1.0.bin.bak`

Unrelated documents do not prevent selection. If a recognized artifact exists, selection stops and neither the destination nor active save is overwritten. The user can inspect the folder and use **Import Save** explicitly when replacement is intended.

**Reset to App Storage** releases the persisted tree permission and disables future hydration/synchronization. It intentionally deletes neither the external document nor the app-private mirror.

## Failure and backup behavior

Provider permission may be revoked, removable storage may disappear, or a cloud provider may fail. Startup hydration validates into a temporary file before replacing anything. If reading, validation, backup, rename, or write-back fails, the previous internal copy remains available and the settings status/logcat reports the error. A failed external write does not erase the app copy.

Before destructive device experiments, make an independent backup:

```sh
adb exec-out "run-as com.aure.banjorecomp cat files/data/saves/bk.n64.us.1.0.bin" > backup.bin
```

Use `adb install -r` for iteration. Do not uninstall, clear app data, or delete app-private files merely to test recovery.

## Verification

Offline checks:

```sh
python3 tools/check_android_port_guards.py
python3 tools/test_android_gpu_driver_import.py
gradle --no-daemon -p android :app:assembleDebug
git diff --check
```

Device acceptance still requires:

- export followed by import produces a byte-identical save;
- wrong-size imports are rejected without changing the active save;
- selecting an empty folder migrates the current save before enabling synchronization;
- every recognized collision is non-destructive;
- external edits are loaded on the next launch;
- pause and explicit import synchronize the external document;
- revoked permission, missing document, invalid external size, disconnected storage, and provider write failure retain the internal copy;
- reset leaves both copies intact and subsequent launches use app storage only;
- lifecycle, suspend/resume, and dual-screen transitions do not race save operations.

The current external-folder layout manages Banjo's primary save document and recognized backups. General recursive mod-save/subfolder migration is not claimed by this implementation.
