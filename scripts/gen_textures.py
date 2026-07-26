#!/usr/bin/env python3
"""Generate 16x16 RGBA PNG textures for ae2logistics from ASCII pixel grids."""
import struct
import sys
import zlib
from pathlib import Path

OUT = Path(sys.argv[1])


def write_png(path, grid, palette, scale=1):
    rows = [line for line in grid.strip("\n").split("\n")]
    assert len(rows) == 16, f"{path}: {len(rows)} rows"
    size = 16 * scale
    raw = b""
    for row in rows:
        assert len(row) == 16, f"{path}: row '{row}' has {len(row)} chars"
        scanline = b"\x00" + b"".join(bytes(palette[ch]) * scale for ch in row)
        raw += scanline * scale

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path}")


STEEL = {
    "#": (22, 26, 33, 255),     # frame edge, near-black steel
    "D": (39, 46, 58, 255),     # dark steel
    "M": (58, 69, 85, 255),     # mid steel
    "L": (88, 103, 122, 255),   # light steel / rivet
    ".": (30, 35, 44, 255),     # recessed inset
    "c": (0, 110, 150, 255),    # dim cyan indicator
    "C": (92, 226, 255, 255),   # bright cyan indicator
}

REGISTER_BANK_SIDE = """
################
#DDDDDDDDDDDDDD#
#DLMMMMMMMMMMLD#
#DM..........MD#
#DM.cc.cc.cc.MD#
#DM.cC.cC.cC.MD#
#DM..........MD#
#DM..........MD#
#DM.cc.cc.cc.MD#
#DM.cC.cC.cC.MD#
#DM..........MD#
#DM..........MD#
#DLMMMMMMMMMMLD#
#DDDDDDDDDDDDDD#
#DDDDDDDDDDDDDD#
################
"""

REGISTER_BANK_TOP = """
################
#DDDDDDDDDDDDDD#
#DMMMMMMMMMMMMD#
#DMLLLLLLLLLLMD#
#DML........LMD#
#DML.MMMMMM.LMD#
#DML.M....M.LMD#
#DML.M.cC.M.LMD#
#DML.M.Cc.M.LMD#
#DML.M....M.LMD#
#DML.MMMMMM.LMD#
#DML........LMD#
#DMLLLLLLLLLLMD#
#DMMMMMMMMMMMMD#
#DDDDDDDDDDDDDD#
################
"""

CARD = {
    ".": (0, 0, 0, 0),          # transparent
    "O": (52, 56, 62, 255),     # outline
    "F": (196, 200, 206, 255),  # face
    "S": (134, 139, 146, 255),  # shaded edge
    "g": (222, 180, 84, 255),   # gold contact
    "r": (232, 58, 28, 255),    # signal red
}

SIGNAL_CARD = """
................
...OOOOOOOOOO...
...OFFFFFFFSO...
...OFFFFFFFSO...
...OrrrFFrrrO...
...OFFrFFrFFO...
...OFFrFFrFFO...
...OFFrrrrFFO...
...OFFFFFFFSO...
...OFFFFFFFSO...
...OFFFFFFFSO...
...OggFggFggO...
...OggFggFggO...
...OOOOOOOOOO...
................
................
"""

GUI = {
    ".": (0, 0, 0, 0),
    "r": (255, 88, 46, 255),    # bright signal wave
    "R": (150, 32, 16, 255),    # wave shadow
}

SIGNAL_ICON = """
................
................
..rrrr....rrrr..
..rrrr....rrrr..
..RRrr....rrRR..
....rr....rr....
....rr....rr....
....rr....rr....
....rr....rr....
....rr....rr....
....rr....rr....
....rrrrrrrr....
....rrrrrrrr....
....RRRRRRRR....
................
................
"""

write_png(OUT / "block" / "register_bank_side.png", REGISTER_BANK_SIDE, STEEL)
write_png(OUT / "block" / "register_bank_top.png", REGISTER_BANK_TOP, STEEL)
write_png(OUT / "item" / "signal_card.png", SIGNAL_CARD, CARD)
write_png(OUT / "gui" / "signal.png", SIGNAL_ICON, GUI)
write_png(OUT.parent.parent.parent / "logo.png", SIGNAL_ICON, GUI, scale=4)
