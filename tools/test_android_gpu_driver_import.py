#!/usr/bin/env python3
"""Offline fixtures for Android custom GPU-driver ZIP import rules.

This mirrors the Java-side BanjoSDLActivity validation contract closely enough to
exercise the security/shape cases without a device or DocumentsUI interaction.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import tempfile
import time
import zipfile
from pathlib import Path

SONAME_ORDER = ["libvulkan_freedreno.so", "vulkan.freedreno.so", "libvulkan.so"]
REQUIRED_ABI = "arm64-v8a"
ZIP_MAX_ENTRIES = 512
ZIP_MAX_ENTRY_BYTES = 256 * 1024 * 1024
ZIP_MAX_TOTAL_BYTES = 512 * 1024 * 1024
REPO_ROOT = Path(__file__).resolve().parents[1]


class ImportErrorForTest(Exception):
    pass


def validate_zip_entry_name(name: str | None) -> str:
    if name is None:
        raise ImportErrorForTest("Rejected ZIP entry with empty name")
    normalized = name.replace("\\", "/").strip()
    if not normalized:
        raise ImportErrorForTest("Rejected ZIP entry with empty name")
    if normalized.startswith("/") or re.match(r"^[A-Za-z]:.*", normalized):
        raise ImportErrorForTest(f"Rejected absolute ZIP entry: {name}")
    components_name = normalized[:-1] if normalized.endswith("/") else normalized
    if not components_name:
        raise ImportErrorForTest("Rejected ZIP entry with empty name")
    for component in components_name.split("/"):
        if component in ("", ".", ".."):
            raise ImportErrorForTest(f"Rejected unsafe ZIP entry: {name}")
    return components_name


def extract_zip_safely(
    zip_path: Path,
    destination_dir: Path,
    *,
    max_entries: int = ZIP_MAX_ENTRIES,
    max_entry_bytes: int = ZIP_MAX_ENTRY_BYTES,
    max_total_bytes: int = ZIP_MAX_TOTAL_BYTES,
) -> None:
    destination_root = destination_dir.resolve()
    saw_entry = False
    total_extracted_bytes = 0
    with zipfile.ZipFile(zip_path) as archive:
        for entry_count, info in enumerate(archive.infolist(), start=1):
            saw_entry = True
            if entry_count > max_entries:
                raise ImportErrorForTest("GPU driver package has too many ZIP entries")

            safe_name = validate_zip_entry_name(info.filename)
            destination = destination_dir / safe_name
            resolved = destination.resolve()
            if not (resolved == destination_root or destination_root in resolved.parents):
                raise ImportErrorForTest(f"Rejected ZIP entry outside import directory: {info.filename}")
            if not info.is_dir() and info.file_size > max_entry_bytes:
                raise ImportErrorForTest(f"GPU driver ZIP entry is too large: {info.filename}")
            if info.is_dir():
                destination.mkdir(parents=True, exist_ok=True)
                continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(info) as source, destination.open("wb") as output:
                entry_extracted_bytes = 0
                while True:
                    chunk = source.read(64 * 1024)
                    if not chunk:
                        break
                    entry_extracted_bytes += len(chunk)
                    total_extracted_bytes += len(chunk)
                    if entry_extracted_bytes > max_entry_bytes:
                        raise ImportErrorForTest(f"GPU driver ZIP entry is too large: {info.filename}")
                    if total_extracted_bytes > max_total_bytes:
                        raise ImportErrorForTest("GPU driver package is too large after extraction")
                    output.write(chunk)
    if not saw_entry:
        raise ImportErrorForTest("GPU driver package ZIP is empty or invalid")


def find_file_named(directory: Path, filename: str) -> Path | None:
    for root, _, files in os.walk(directory):
        if filename in files:
            return Path(root) / filename
    return None


def find_abi_driver_file(directory: Path, filename: str, abi: str = REQUIRED_ABI) -> Path | None:
    for root, _, files in os.walk(directory):
        if filename in files and Path(root).name == abi:
            return Path(root) / filename
    return None


def import_fixture(zip_path: Path, imports_root: Path, display_name: str = "Turnip Test.zip") -> dict[str, object]:
    driver_id = f"turnip-test-{time.time_ns()}"
    import_dir = imports_root / driver_id
    if import_dir.exists():
        shutil.rmtree(import_dir)
    import_dir.mkdir(parents=True)
    try:
        extract_zip_safely(zip_path, import_dir)
        found = None
        soname = None
        for candidate in SONAME_ORDER:
            found = find_abi_driver_file(import_dir, candidate)
            if found is not None:
                soname = candidate
                break
        if found is None or soname is None:
            for candidate in SONAME_ORDER:
                if find_file_named(import_dir, candidate) is not None:
                    raise ImportErrorForTest(
                        "GPU driver package contains a supported Vulkan driver soname, "
                        f"but not for required ABI {REQUIRED_ABI}"
                    )
            raise ImportErrorForTest("GPU driver package does not contain a supported Vulkan driver soname")
        metadata = {
            "display_name": display_name,
            "id": driver_id,
            "dir": str(found.parent),
            "soname": soname,
            "import_time_ms": int(time.time() * 1000),
            "source_filename": display_name,
        }
        (import_dir / "driver.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        return metadata
    except Exception:
        shutil.rmtree(import_dir, ignore_errors=True)
        raise


def make_zip(path: Path, entries: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        for name, data in entries.items():
            archive.writestr(name, data)


def expect_rejected(zip_path: Path, imports_root: Path, label: str) -> None:
    before = set(imports_root.iterdir()) if imports_root.exists() else set()
    try:
        import_fixture(zip_path, imports_root)
    except Exception:
        after = set(imports_root.iterdir()) if imports_root.exists() else set()
        assert after == before, f"{label}: rejected import left files behind"
        return
    raise AssertionError(f"{label}: expected rejection")


def assert_safe_mode_source_contract() -> None:
    java_source = (REPO_ROOT / "android/app/src/main/java/io/github/banjorecomp/BanjoSDLActivity.java").read_text(encoding="utf-8")
    native_source = (REPO_ROOT / "src/android/custom_driver_manager.cpp").read_text(encoding="utf-8")
    app_source = (REPO_ROOT / "src/main/main.cpp").read_text(encoding="utf-8")
    graphics_header = (REPO_ROOT / "lib/RecompFrontend/recompui/include/recompui/config.h").read_text(encoding="utf-8")
    graphics_source = (REPO_ROOT / "lib/RecompFrontend/recompui/src/config/ui_config_tab_graphics.cpp").read_text(encoding="utf-8")
    plume_header = (REPO_ROOT / "lib/rt64/src/contrib/plume/plume_vulkan.h").read_text(encoding="utf-8")
    android_notes = (REPO_ROOT / "docs/android-port.md").read_text(encoding="utf-8")
    android_readme = (REPO_ROOT / "android/README.md").read_text(encoding="utf-8")

    assert 'EXTRA_FORCE_SYSTEM_DRIVER = "banjo_force_system_driver"' in java_source
    assert 'nativeSetenv("BANJO_FORCE_SYSTEM_DRIVER", forceSystemDriver ? "1" : "0")' in java_source
    assert "BANJO_FORCE_SYSTEM_DRIVER" in native_source
    assert "driver_dir.push_back('/')" in native_source
    assert "Custom driver directory is outside the managed imports directory" in native_source
    assert "Custom driver soname must be a filename without path components" in native_source
    assert "dlclose(lib_vulkan)" in native_source

    initialize = native_source[native_source.index("int initialize_vulkan_loader_for_volk()") : native_source.index("int initialize_vulkan_loader_for_volk_with_selection")]
    assert initialize.index("force_system_driver_requested()") < initialize.index("load_selection()")
    assert "ignoring active custom Vulkan driver selection" in initialize

    assert "DriverSettingsProvider" in graphics_header
    assert "driver_settings_provider->select" in graphics_source
    assert "reset_to_system_from_ui" in app_source
    assert "set_driver_settings_provider" in app_source
    assert "refresh_driver_status" in app_source
    assert "SetVulkanLoaderInitializeCallback" in plume_header
    assert "SetVulkanLoaderInitializeCallback(initialize_android_vulkan_loader)" in app_source
    assert "SetVulkanPhysicalDeviceObserver(observe_android_vulkan_device)" in app_source
    assert "Custom driver selected; restart required" in app_source

    safe_mode_command = "adb shell am start -n com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity --ez banjo_force_system_driver true"
    assert safe_mode_command in android_notes
    assert safe_mode_command in android_readme


def main() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        imports_root = root / "gpu-drivers" / "imports"
        imports_root.mkdir(parents=True)

        valid = root / "valid.zip"
        make_zip(valid, {f"nested/{REQUIRED_ABI}/": b"", f"nested/{REQUIRED_ABI}/libvulkan.so": b"dummy"})
        metadata = import_fixture(valid, imports_root)
        assert metadata["soname"] == "libvulkan.so"
        assert Path(str(metadata["dir"])).name == REQUIRED_ABI
        assert (Path(str(metadata["dir"])).parents[1] / "driver.json").is_file()

        ordered = root / "ordered.zip"
        make_zip(ordered, {
            f"lib/{REQUIRED_ABI}/libvulkan.so": b"fallback",
            f"lib/{REQUIRED_ABI}/libvulkan_freedreno.so": b"preferred",
        })
        metadata = import_fixture(ordered, imports_root)
        assert metadata["soname"] == "libvulkan_freedreno.so"

        multi_abi = root / "multi-abi.zip"
        make_zip(multi_abi, {
            "lib/x86_64/libvulkan_freedreno.so": b"wrong abi first",
            f"lib/{REQUIRED_ABI}/libvulkan.so": b"right abi",
        })
        metadata = import_fixture(multi_abi, imports_root)
        assert metadata["soname"] == "libvulkan.so"
        assert Path(str(metadata["dir"])).name == REQUIRED_ABI

        wrong_abi = root / "wrong-abi.zip"
        make_zip(wrong_abi, {"lib/x86_64/libvulkan_freedreno.so": b"wrong abi"})
        expect_rejected(wrong_abi, imports_root, "missing arm64-v8a driver")

        no_driver = root / "no-driver.zip"
        make_zip(no_driver, {"README.txt": b"not a driver"})
        expect_rejected(no_driver, imports_root, "missing soname")

        for idx, bad_name in enumerate(["../escape.txt", "/absolute.so", "foo/../bar.so", "C:/bad.so", "dir//bad.so"]):
            bad = root / f"bad-{idx}.zip"
            make_zip(bad, {bad_name: b"bad"})
            expect_rejected(bad, imports_root, bad_name)

        invalid = root / "invalid.zip"
        invalid.write_bytes(b"not a zip")
        expect_rejected(invalid, imports_root, "invalid zip")

        too_many_entries = root / "too-many-entries.zip"
        make_zip(too_many_entries, {f"entry-{i}.txt": b"x" for i in range(ZIP_MAX_ENTRIES + 1)})
        expect_rejected(too_many_entries, imports_root, "too many entries")

        too_large_entry = root / "too-large-entry.zip"
        make_zip(too_large_entry, {f"lib/{REQUIRED_ABI}/libvulkan.so": b"x" * 4})
        oversized_dir = imports_root / "oversized-entry-direct"
        oversized_dir.mkdir()
        try:
            extract_zip_safely(too_large_entry, oversized_dir, max_entry_bytes=3)
        except ImportErrorForTest:
            shutil.rmtree(oversized_dir, ignore_errors=True)
        else:
            raise AssertionError("too large entry: expected rejection")

        too_large_total = root / "too-large-total.zip"
        make_zip(too_large_total, {
            f"lib/{REQUIRED_ABI}/libvulkan.so": b"xx",
            f"lib/{REQUIRED_ABI}/helper.so": b"xx",
        })
        oversized_total_dir = imports_root / "oversized-total-direct"
        oversized_total_dir.mkdir()
        try:
            extract_zip_safely(too_large_total, oversized_total_dir, max_total_bytes=3)
        except ImportErrorForTest:
            shutil.rmtree(oversized_total_dir, ignore_errors=True)
        else:
            raise AssertionError("too large total: expected rejection")

    assert_safe_mode_source_contract()
    print("Android GPU driver ZIP import fixture tests passed")


if __name__ == "__main__":
    main()
