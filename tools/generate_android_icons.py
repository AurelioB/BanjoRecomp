#!/usr/bin/env python3
"""Generate Android launcher icon resources from Banjo/Kazooie SVG assets.

Outputs:
- icons/app.svg source composed from assets/Banjo.svg and assets/Kazooie.svg
- icons/app.png preview/legacy source image
- legacy mipmap PNGs for pre-adaptive launchers
- adaptive icon foreground/background resources for API 26+

Requires ffmpeg with SVG input support.
"""

from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


BACKGROUND_XML_COLOR = "#0071bc"
ADAPTIVE_FOREGROUND_SCALE = 0.74
ADAPTIVE_FOREGROUND_OFFSET_Y = 18


def run(cmd: list[str]) -> None:
    subprocess.run(cmd, check=True)


def ffmpeg(*args: str) -> None:
    run(["ffmpeg", "-y", "-loglevel", "error", *args])


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def read_svg_inner(path: Path, prefix: str) -> str:
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"<\?xml[^>]*>\s*", "", text)
    text = re.sub(r"<!DOCTYPE[^>]*(?:\[[\s\S]*?\]\s*)?>\s*", "", text)
    match = re.search(r"<svg\b[^>]*>([\s\S]*)</svg>\s*$", text)
    if not match:
        raise ValueError(f"Could not find root <svg> body in {path}")

    inner = match.group(1).strip()
    ids = re.findall(r'\bid="([^"]+)"', inner)
    for old_id in ids:
        new_id = f"{prefix}{old_id}"
        inner = re.sub(rf'\bid="{re.escape(old_id)}"', f'id="{new_id}"', inner)
        inner = inner.replace(f"url(#{old_id})", f"url(#{new_id})")
        inner = inner.replace(f"href=\"#{old_id}\"", f"href=\"#{new_id}\"")
        inner = inner.replace(f"xlink:href=\"#{old_id}\"", f"xlink:href=\"#{new_id}\"")
    return inner


def write_composed_svg(
    repo: Path,
    output: Path,
    include_background: bool,
    content_scale: float = 1.0,
    content_offset_y: float = 0.0,
) -> Path:
    banjo = read_svg_inner(repo / "assets" / "Banjo.svg", "banjo_")
    kazooie = read_svg_inner(repo / "assets" / "Kazooie.svg", "kazooie_")
    background = '  <circle cx="256" cy="256" r="255" fill="#0071bc"/>\n' if include_background else ""
    content_transform = ""
    if content_scale != 1.0 or content_offset_y != 0.0:
        content_transform = (
            f' transform="translate(256 {256 + content_offset_y:.3f}) '
            f'scale({content_scale:.3f}) translate(-256 -256)"'
        )
    write_text(
        output,
        f'''<?xml version="1.0" encoding="UTF-8"?>
<svg width="512" height="512" viewBox="0 0 512 512" version="1.1"
     xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
     xml:space="preserve" style="fill-rule:evenodd;clip-rule:evenodd;stroke-linejoin:round;stroke-miterlimit:2;">
  <title>BanjoRecomp app icon</title>
  <desc>Composed from assets/Banjo.svg and assets/Kazooie.svg.</desc>
{background}  <g id="character-layers"{content_transform}>
  <g id="banjo-layer" transform="translate(18 78) scale(0.50)">
{banjo}
  </g>
  <g id="kazooie-layer" transform="translate(226 52) rotate(8 181 224) scale(0.46)">
{kazooie}
  </g>
  </g>
</svg>
''',
    )
    return output


def render_svg(source_svg: Path, output: Path, size: int) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    ffmpeg(
        "-i", str(source_svg),
        "-vf", f"scale={size}:{size}:flags=lanczos",
        "-frames:v", "1", "-update", "1",
        str(output),
    )


def generate(repo: Path) -> None:
    banjo_svg = repo / "assets" / "Banjo.svg"
    kazooie_svg = repo / "assets" / "Kazooie.svg"
    icons = repo / "icons"
    res = repo / "android" / "app" / "src" / "main" / "res"

    if not banjo_svg.exists() or not kazooie_svg.exists():
        raise SystemExit("Missing assets/Banjo.svg or assets/Kazooie.svg")

    for directory in [icons, res / "drawable", res / "mipmap-anydpi-v26", *(res / f"mipmap-{d}" for d in DENSITIES)]:
        directory.mkdir(parents=True, exist_ok=True)

    app_svg = write_composed_svg(repo, icons / "app.svg", include_background=True)
    adaptive_foreground_svg = write_composed_svg(
        repo,
        icons / "app-adaptive-foreground.svg",
        include_background=False,
        content_scale=ADAPTIVE_FOREGROUND_SCALE,
        content_offset_y=ADAPTIVE_FOREGROUND_OFFSET_Y,
    )
    render_svg(app_svg, icons / "app.png", 512)
    render_svg(adaptive_foreground_svg, res / "drawable" / "ic_launcher_foreground.png", 432)

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
        render_svg(app_svg, res / f"mipmap-{density}" / "ic_launcher.png", size)

    print(f"Generated SVG launcher icon: {app_svg}")
    print(f"Generated Android launcher icons under {res}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    generate(args.repo.resolve())


if __name__ == "__main__":
    main()
