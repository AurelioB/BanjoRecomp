#!/usr/bin/env python3
"""Preview BanjoRecomp Android secondary-screen stat backgrounds via adb.

Examples:
  tools/preview_dual_screen_background.py spiral --capture /tmp/spiral.png
  tools/preview_dual_screen_background.py lair --capture /tmp/lair.png
  tools/preview_dual_screen_background.py 0x07 --capture /tmp/ttc.png
  tools/preview_dual_screen_background.py --clear
"""

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

COMPONENT = "com.aure.banjorecomp/io.github.banjorecomp.BanjoSDLActivity"
MAP_ALIASES = {
    "spiral": 0x01,
    "spiral_mountain": 0x01,
    "sm": 0x01,
    "mumbo": 0x02,
    "mumbos_mountain": 0x02,
    "mm": 0x02,
    "treasure_trove": 0x07,
    "treasure_trove_cove": 0x07,
    "ttc": 0x07,
    "gruntys_lair": 0x69,
    "grunty_lair": 0x69,
    "lair": 0x69,
    "gl": 0x69,
}


def run(args: list[str], *, capture: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(args, check=True, text=False if capture else True,
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None)


def parse_map(value: str) -> int:
    key = value.strip().lower().replace("-", "_").replace(" ", "_")
    if key in MAP_ALIASES:
        return MAP_ALIASES[key]
    return int(value, 0)


def secondary_physical_display_id() -> str | None:
    out = run(["adb", "shell", "dumpsys", "display"], capture=True).stdout.decode("utf-8", "replace")
    match = re.search(r"DisplayViewport\{type=EXTERNAL,.*?uniqueId='local:(\d+)'", out, re.S)
    if match:
        return match.group(1)
    match = re.search(r"DisplayDeviceInfo\{\"Screen-2\": uniqueId=\"local:(\d+)\"", out)
    return match.group(1) if match else None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("map", nargs="?", help="level alias or map id, e.g. spiral, lair, ttc, 0x07")
    parser.add_argument("--clear", action="store_true", help="clear preview mode and return to live game updates")
    parser.add_argument("--capture", type=Path, help="write a secondary-screen screenshot after applying the preview")
    parser.add_argument("--wait", type=float, default=3.0, help="seconds to wait before capture; default 3")
    args = parser.parse_args()

    if args.clear:
        run(["adb", "shell", "am", "start", "-n", COMPONENT,
             "--ez", "dualscreen_preview_clear", "true"])
        return 0

    if not args.map:
        parser.error("map is required unless --clear is used")

    map_id = parse_map(args.map)
    run(["adb", "shell", "am", "start", "-n", COMPONENT,
         "--ez", "dualscreen_preview", "true",
         "--es", "dualscreen_preview_map", hex(map_id)])

    if args.capture:
        time.sleep(args.wait)
        display_id = secondary_physical_display_id()
        if not display_id:
            print("Could not find secondary physical display id", file=sys.stderr)
            return 2
        png = run(["adb", "exec-out", "screencap", "-d", display_id, "-p"], capture=True).stdout
        args.capture.parent.mkdir(parents=True, exist_ok=True)
        args.capture.write_bytes(png)
        print(f"captured map=0x{map_id:x} display={display_id} path={args.capture}")
    else:
        print(f"previewing map=0x{map_id:x}; use --clear to return to live updates")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
