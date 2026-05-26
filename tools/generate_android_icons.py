#!/usr/bin/env python3
"""Generate Android launcher icon resources from Banjo/Kazooie SVG assets.

Outputs:
- legacy mipmap PNGs for pre-adaptive launchers
- adaptive icon foreground/background resources for API 26+

Requires ffmpeg with SVG input support.
"""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


BACKGROUND_COLOR = "0x2b1608"
BACKGROUND_XML_COLOR = "#2B1608"


def run(cmd: list[str]) -> None:
    subprocess.run(cmd, check=True)


def ffmpeg(*args: str) -> None:
    run(["ffmpeg", "-y", "-loglevel", "error", *args])


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def generate(repo: Path) -> None:
    banjo_svg = repo / "assets" / "Banjo.svg"
    kazooie_svg = repo / "assets" / "Kazooie.svg"
    res = repo / "android" / "app" / "src" / "main" / "res"
    work = repo / "android" / "app" / "build" / "generated" / "launcher-icons"

    if not banjo_svg.exists() or not kazooie_svg.exists():
        raise SystemExit("Missing assets/Banjo.svg or assets/Kazooie.svg")

    work.mkdir(parents=True, exist_ok=True)
    for directory in [res / "drawable", res / "mipmap-anydpi-v26", *(res / f"mipmap-{d}" for d in DENSITIES)]:
        directory.mkdir(parents=True, exist_ok=True)

    banjo_png = work / "banjo.png"
    kazooie_png = work / "kazooie.png"
    foreground_master = work / "ic_launcher_foreground_master.png"
    legacy_master = work / "ic_launcher_master.png"

    # Render source SVGs larger than the final output so downscaling keeps edges clean.
    ffmpeg("-i", str(banjo_svg), "-vf", "scale=720:690:flags=lanczos", "-frames:v", "1", "-update", "1", str(banjo_png))
    ffmpeg("-i", str(kazooie_svg), "-vf", "scale=620:766:flags=lanczos", "-frames:v", "1", "-update", "1", str(kazooie_png))

    # Transparent adaptive foreground. The positions intentionally keep faces readable and avoid
    # clipping under common circle/squircle launcher masks.
    ffmpeg(
        "-f", "lavfi", "-i", "color=c=black@0.0:s=1024x1024,format=rgba",
        "-i", str(kazooie_png),
        "-i", str(banjo_png),
        "-filter_complex", "[0:v][1:v]overlay=x=404:y=128:format=auto[tmp];[tmp][2:v]overlay=x=96:y=172:format=auto",
        "-frames:v", "1", "-update", "1", str(foreground_master),
    )

    # Flatten the foreground on the adaptive background for legacy launcher PNGs.
    ffmpeg(
        "-f", "lavfi", "-i", f"color=c={BACKGROUND_COLOR}:s=1024x1024,format=rgba",
        "-i", str(foreground_master),
        "-filter_complex", "[0:v][1:v]overlay=x=0:y=0:format=auto",
        "-frames:v", "1", "-update", "1", str(legacy_master),
    )

    ffmpeg(
        "-i", str(foreground_master),
        "-vf", "scale=432:432:flags=lanczos",
        "-frames:v", "1", "-update", "1",
        str(res / "drawable" / "ic_launcher_foreground.png"),
    )

    write_text(
        res / "drawable" / "ic_launcher_background.xml",
        f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="{BACKGROUND_XML_COLOR}" />
</shape>
''',
    )

    adaptive_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
    write_text(res / "mipmap-anydpi-v26" / "ic_launcher.xml", adaptive_xml)
    write_text(res / "mipmap-anydpi-v26" / "ic_launcher_round.xml", adaptive_xml)

    for density, size in DENSITIES.items():
        ffmpeg(
            "-i", str(legacy_master),
            "-vf", f"scale={size}:{size}:flags=lanczos",
            "-frames:v", "1", "-update", "1",
            str(res / f"mipmap-{density}" / "ic_launcher.png"),
        )

    print(f"Generated Android launcher icons under {res}")
    print(f"Preview master: {legacy_master}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    generate(args.repo.resolve())


if __name__ == "__main__":
    main()
