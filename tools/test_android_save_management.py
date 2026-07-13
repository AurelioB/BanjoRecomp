#!/usr/bin/env python3
"""Focused source-contract tests for Android save management.

These tests run without an Android SDK/NDK. They protect the ordering and
synchronization properties that are easy to accidentally weaken during UI or
runtime refactors.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text()


def require(condition: bool, message: str) -> None:
    if not condition:
        print(f"FAIL: {message}", file=sys.stderr)
        raise SystemExit(1)


def require_regex(text: str, pattern: str, message: str) -> None:
    require(re.search(pattern, text, re.S) is not None, message)


def main() -> int:
    activity = read("android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java")
    bridge = read("src/android/save_storage_manager.cpp")
    runtime = read("lib/N64ModernRuntime/librecomp/src/pi.cpp")
    runtime_api = read("lib/N64ModernRuntime/ultramodern/include/ultramodern/ultramodern.hpp")
    main_cpp = read("src/main/main.cpp")
    cmake = read("CMakeLists.txt")

    # Picker contracts: documents for import/export, a persistable tree for the
    # synchronized folder, and no attempt to turn content:// into a filesystem path.
    require("Intent.ACTION_OPEN_DOCUMENT" in activity, "save import must use ACTION_OPEN_DOCUMENT")
    require("Intent.ACTION_CREATE_DOCUMENT" in activity, "save export must use ACTION_CREATE_DOCUMENT")
    require("Intent.ACTION_OPEN_DOCUMENT_TREE" in activity, "save folder selection must use ACTION_OPEN_DOCUMENT_TREE")
    for flag in ("FLAG_GRANT_READ_URI_PERMISSION", "FLAG_GRANT_WRITE_URI_PERMISSION",
                 "FLAG_GRANT_PERSISTABLE_URI_PERMISSION", "FLAG_GRANT_PREFIX_URI_PERMISSION"):
        require(flag in activity, f"folder picker must request {flag}")
    require("takePersistableUriPermission(uri, flags)" in activity,
            "selected tree permission must be persisted")
    require("DocumentsContract.buildChildDocumentsUriUsingTree" in activity,
            "external folder access must remain SAF/ContentResolver based")

    # The external document is authoritative. It must hydrate the runtime mirror
    # before SDLActivity starts native code and must be activated atomically.
    on_create = re.search(r"protected void onCreate\(Bundle savedInstanceState\) \{(.*?)\n    \}", activity, re.S)
    require(on_create is not None, "onCreate body must be discoverable")
    body = on_create.group(1)
    require(body.index("hydrateInternalSaveFromSelectedFolder(appDataDir)") < body.index("super.onCreate(savedInstanceState)"),
            "authoritative external save must hydrate before native SDL startup")
    require_regex(activity, r"copyUriToFile\(document, temporary\).*?temporary\.length\(\) != BANJO_SAVE_SIZE.*?"
                            r"destination\.renameTo\(backup\).*?temporary\.renameTo\(destination\)",
                  "startup hydration must validate then backup and atomically activate the mirror")
    require("private static final long BANJO_SAVE_SIZE = 0x800L;" in activity,
            "Banjo external hydration must enforce the exact EEP16K size")

    # A folder with any known app/runtime save artifact is a collision. Selection
    # must return before creating or persisting anything, preventing silent overwrite.
    init_folder = re.search(r"private void initializeSaveFolder\(Uri treeUri\) \{(.*?)\n    \}", activity, re.S)
    require(init_folder is not None, "folder initialization body must be discoverable")
    folder_body = init_folder.group(1)
    require(folder_body.index("if (collision != null)") < folder_body.index("DocumentsContract.createDocument"),
            "collision detection must happen before destination creation")
    require(folder_body.index("return;") < folder_body.index("putString(SAVE_FOLDER_URI"),
            "collision path must return before persisting the selected folder")
    require("SAVE_DOCUMENT_NAME.equals(name)" in activity and "RUNTIME_SAVE_NAME.equals(name)" in activity,
            "collision detection must recognize both public and runtime save names")
    require('name.equals(SAVE_DOCUMENT_NAME + ".bak")' in activity and
            'name.equals(RUNTIME_SAVE_NAME + ".bak")' in activity,
            "collision detection must recognize save backups")

    # Provider I/O and native transactions may block; Activity result handling must
    # dispatch them to a serialized background executor.
    require("Executors.newSingleThreadExecutor()" in activity,
            "save I/O must use a serialized background executor")
    require_regex(activity, r"saveIoExecutor\.execute\(\(\) -> processSaveDocument\(requestCode, uri\)\)",
                  "document import/export must run off the UI thread")
    require_regex(activity, r"saveIoExecutor\.execute\(\(\) -> initializeSaveFolder\(uri\)\)",
                  "folder initialization must run off the UI thread")

    # JNI must validate the runtime's exact active save size and delegate to the
    # transactional snapshot/import APIs rather than reading/writing the live file.
    require("ultramodern::snapshot_save_file(snapshot)" in bridge,
            "export bridge must use a consistent runtime snapshot")
    require("const size_t expected = ultramodern::get_save_file_size();" in bridge,
            "import bridge must query the exact active runtime save size")
    require_regex(bridge, r"static_cast<size_t>\(size\) == expected.*?ultramodern::import_save_file\(data\)",
                  "import must reject non-exact sizes before runtime replacement")
    for api in ("get_save_file_size", "flush_save_file", "snapshot_save_file", "import_save_file",
                "reload_save_file", "set_save_root_path"):
        require(api in runtime_api, f"runtime public API must declare {api}")
    require("src/android/save_storage_manager.cpp" in cmake,
            "Android target must compile the save-management JNI bridge")
    require("save_storage::register_frontend_provider();" in main_cpp,
            "Android startup must register the save settings provider")

    # The worker handshake is generation-specific: a transaction requests and waits
    # for its generation; the worker acknowledges that generation and cannot resume
    # until the same generation is released. The operation lock also excludes game
    # reads/writes for the duration of the transaction.
    require("std::shared_mutex operation_mutex" in runtime,
            "runtime needs an operation gate around live save access")
    require_regex(runtime, r"SaveControlTransaction\(\).*?operation_lock\{ save_context\.operation_mutex \}.*?"
                           r"generation = \+\+save_context\.control_requested_generation.*?"
                           r"control_acknowledged_generation >= generation", 
                  "transaction must exclusively gate operations and wait for its requested generation")
    require_regex(runtime, r"const uint64_t generation = save_context\.control_requested_generation;.*?"
                           r"control_acknowledged_generation = generation.*?"
                           r"control_released_generation >= generation", 
                  "worker must acknowledge and await release of the same generation")
    require_regex(runtime, r"~SaveControlTransaction\(\).*?control_released_generation = generation.*?notify_all",
                  "transaction destruction must release its generation")
    for operation in ("save_write_ptr", "save_write", "save_read", "save_clear"):
        require_regex(runtime, rf"void {operation}\(.*?std::shared_lock operation_lock\{{ save_context\.operation_mutex \}}",
                      f"{operation} must participate in the transaction operation gate")

    print("Android save-management source-contract tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
