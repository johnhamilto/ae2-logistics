#!/usr/bin/env python3
"""Compose GUI backgrounds in AE2's own chrome.

Nine-slices AE2's standard dialog and stamps AE2's slot sprites at our layouts, so
screens migrated to AEBaseScreen use pixel-identical chrome. Sources pixels from an
AE2 resources checkout (LGPL-3.0, same license as this mod); the generated PNGs are
committed, so builds never need the sources.

Usage: gen_ae2_chrome.py <path-to-ae2-src-main-resources>
"""
import sys
from pathlib import Path

from PIL import Image

AE2_RES = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "/Users/jackmacstudio/Projects/ae2-reference/Applied-Energistics-2/src/main/resources")
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/ae2/textures/guis/ae2logistics"

SOURCE = AE2_RES / "assets/ae2/textures/guis/me_chest.png"
# The texture is a 256x256 sheet; the dialog art is this srcRect (from qnb.json).
ART_RECT = (0, 0, 176, 168)
CORNER = 8
# A clean body patch well away from me_chest's baked-in storage slot.
BODY_PATCH = (100, 50, 132, 66)
# The first player-inventory slot frame in me_chest (style: left 8, bottom 84).
SLOT_FRAME = (7, 83, 25, 101)


def dialog(source: Image.Image, width: int, height: int) -> Image.Image:
    src_w, src_h = source.size
    out = Image.new("RGBA", (width, height))

    body = source.crop(BODY_PATCH)
    bw, bh = body.size
    for y in range(0, height, bh):
        for x in range(0, width, bw):
            out.paste(body, (x, y))

    top = source.crop((CORNER, 0, src_w - CORNER, CORNER))
    bottom = source.crop((CORNER, src_h - CORNER, src_w - CORNER, src_h))
    for x in range(CORNER, width - CORNER, top.width):
        w = min(top.width, width - CORNER - x)
        out.paste(top.crop((0, 0, w, CORNER)), (x, 0))
        out.paste(bottom.crop((0, 0, w, CORNER)), (x, height - CORNER))

    # Clean vertical band only - lower rows of me_chest's edges carry its baked-in
    # player-inventory frame pixels.
    left = source.crop((0, CORNER, CORNER, 72))
    right = source.crop((src_w - CORNER, CORNER, src_w, 72))
    for y in range(CORNER, height - CORNER, left.height):
        h = min(left.height, height - CORNER - y)
        out.paste(left.crop((0, 0, CORNER, h)), (0, y))
        out.paste(right.crop((0, 0, CORNER, h)), (width - CORNER, y))

    out.paste(source.crop((0, 0, CORNER, CORNER)), (0, 0))
    out.paste(source.crop((src_w - CORNER, 0, src_w, CORNER)), (width - CORNER, 0))
    out.paste(source.crop((0, src_h - CORNER, CORNER, src_h)), (0, height - CORNER))
    out.paste(source.crop((src_w - CORNER, src_h - CORNER, src_w, src_h)),
              (width - CORNER, height - CORNER))
    return out


def stamp_slots(out: Image.Image, slot: Image.Image, positions) -> None:
    for x, y in positions:
        out.paste(slot, (x, y))


def grid(x: int, y: int, cols: int, rows: int, step: int = 18):
    return [(x + c * step, y + r * step) for r in range(rows) for c in range(cols)]


def player_inventory(x: int = 7, inv_y: int = 107, hotbar_y: int = 165):
    return grid(x, inv_y, 9, 3) + grid(x, hotbar_y, 9, 1)


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA").crop(ART_RECT)
    slot = source.crop(SLOT_FRAME)
    OUT.mkdir(parents=True, exist_ok=True)

    # Pattern Workbench: 176x190 - pattern slot, 3x3 display grid, output, guard strip.
    out = dialog(source, 176, 190)
    stamp_slots(out, slot, [(150, 20)])
    stamp_slots(out, slot, grid(25, 16, 3, 3))
    stamp_slots(out, slot, [(115, 34)])
    stamp_slots(out, slot, player_inventory())
    out.save(OUT / "pattern_workbench.png")
    print("wrote", OUT / "pattern_workbench.png")


if __name__ == "__main__":
    main()
