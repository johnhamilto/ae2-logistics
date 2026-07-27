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


def write_panel(path, width, height):
    """AE2-flavored GUI panel: dark steel border, beveled edge, recessed body."""
    border = (22, 26, 33, 255)
    bevel = (88, 103, 122, 255)
    body = (44, 51, 63, 255)
    inset = (34, 40, 50, 255)

    rows = []
    for y in range(height):
        row = []
        for x in range(width):
            if x < 2 or y < 2 or x >= width - 2 or y >= height - 2:
                row.append(border)
            elif x == 2 or y == 2:
                row.append(bevel)
            elif x >= width - 4 or y >= height - 4:
                row.append(inset)
            else:
                row.append(body)
        rows.append(row)

    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(bytes(px) for px in row)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
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

PART = dict(STEEL)
PART.update({
    "r": (232, 58, 28, 255),    # signal red
    "w": (110, 80, 48, 255),    # torch wood
})

LOGIC_HOUSING = """
################
#DDDDDDDDDDDDDD#
#DMMMMMMMMMMMMD#
#DMLMMMMMMMMLMD#
#DMMMMMMMMMMMMD#
#DMMMMMMMMMMMMD#
#DMMMMMMMMMMMMD#
#DMMMMMMMMMMMMD#
#DMMMMMMMMMMMMD#
#DMMMMMMMMMMMMD#
#DMMMMMMMMMMMMD#
#DMMMMMMMMMMMMD#
#DMLMMMMMMMMLMD#
#DMMMMMMMMMMMMD#
#DDDDDDDDDDDDDD#
################
"""

PART_CONSTANT = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.....CC.....D#
#D....CCC.....D#
#D...CC.C.....D#
#D......C.....D#
#D......C.....D#
#D......C.....D#
#D......C.....D#
#D......C.....D#
#D....CCCCC...D#
#D............D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PART_THRESHOLD = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D...CC.......D#
#D....CC......D#
#D.....CC.....D#
#D......CC....D#
#D.......CC...D#
#D......CC....D#
#D.....CC.....D#
#D....CC......D#
#D...CC.......D#
#D............D#
#D...cccccc...D#
#DDDDDDDDDDDDDD#
################
"""

PART_HYSTERESIS = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D............D#
#D......CCCCC.D#
#D......C.....D#
#D......C.....D#
#D......C.....D#
#D......C.....D#
#D......C.....D#
#D.CCCCCC.....D#
#D............D#
#D............D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PART_ARITHMETIC = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D............D#
#D......CC....D#
#D......CC....D#
#D...CCCCCCCC.D#
#D...CCCCCCCC.D#
#D......CC....D#
#D......CC....D#
#D............D#
#D............D#
#D............D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PART_LOGIC_GATE = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D...CCCCCC...D#
#D...CC...CC..D#
#D...CC....CC.D#
#D...CC....CC.D#
#D...CC....CC.D#
#D...CC....CC.D#
#D...CC....CC.D#
#D...CC...CC..D#
#D...CCCCCC...D#
#D............D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PART_REDSTONE_PORT = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.....rrr....D#
#D....rrrrr...D#
#D....rrrrr...D#
#D.....rrr....D#
#D......w.....D#
#D......w.....D#
#D......w.....D#
#D......w.....D#
#D......w.....D#
#D.....www....D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PATTERN_WORKBENCH_TOP = """
################
#DDDDDDDDDDDDDD#
#DMMMMMMMMMMMMD#
#DM..........MD#
#DM.CC.CC.CC.MD#
#DM.CC.CC.CC.MD#
#DM..........MD#
#DM.CC.rr.CC.MD#
#DM.CC.rr.CC.MD#
#DM..........MD#
#DM.CC.CC.CC.MD#
#DM.CC.CC.CC.MD#
#DM..........MD#
#DMMMMMMMMMMMMD#
#DDDDDDDDDDDDDD#
################
"""

ADAPTIVE_PATTERN_ITEM = """
................
.OOOOOOOOOOOOOO.
.OFFFFFFFFFFFSO.
.OFFFFFFFFFFFSO.
.OFF.FF.FF.FFSO.
.OFFFFFFFFFFFSO.
.OFF.FF.rr.FFSO.
.OFFFFFFFFFFFSO.
.OFF.FF.FF.FFSO.
.OFFFFFFFFFFFSO.
.OFFrFFrFFrFFSO.
.OFFFrFrFrFFFSO.
.OFFFFrFrFFFFSO.
.OSSSSSSSSSSSSO.
.OOOOOOOOOOOOOO.
................
"""

write_png(OUT / "block" / "pattern_workbench_top.png", PATTERN_WORKBENCH_TOP, PART)
write_png(OUT / "item" / "adaptive_processing_pattern.png", ADAPTIVE_PATTERN_ITEM, PART | CARD)
write_panel(OUT / "gui" / "workbench_panel.png", 176, 166)

write_png(OUT / "part" / "logic_housing.png", LOGIC_HOUSING, PART)
write_png(OUT / "part" / "constant.png", PART_CONSTANT, PART)
write_png(OUT / "part" / "threshold.png", PART_THRESHOLD, PART)
write_png(OUT / "part" / "hysteresis.png", PART_HYSTERESIS, PART)
write_png(OUT / "part" / "arithmetic.png", PART_ARITHMETIC, PART)
write_png(OUT / "part" / "logic_gate.png", PART_LOGIC_GATE, PART)
write_png(OUT / "part" / "redstone_port.png", PART_REDSTONE_PORT, PART)

PART_STOCK_SENSOR = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D..LLLLLLLL..D#
#D.LL......LL.D#
#D.L..CCCC..L.D#
#D.L.CC..CC.L.D#
#D.L.C.rr.C.L.D#
#D.L.C.rr.C.L.D#
#D.L.CC..CC.L.D#
#D.L..CCCC..L.D#
#D.LL......LL.D#
#D..LLLLLLLL..D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PART_RATE = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.........CC.D#
#D.........CC.D#
#D......CC.CC.D#
#D......CC.CC.D#
#D...CC.CC.CC.D#
#D...CC.CC.CC.D#
#D.CCCC.CC.CC.D#
#D.CCCC.CC.CC.D#
#D............D#
#D.cccccccccc.D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PART_COUNTER = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.C..C..C....D#
#D.C..C..C..r.D#
#D.C..C..C.r..D#
#D.C..C..C.r..D#
#D.C..C..Cr...D#
#D.C..C.rC....D#
#D.C..C.r.C...D#
#D.C..Cr..C...D#
#D.C..r...C...D#
#D.C.r....C...D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PART_TIMER = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D....CCCC....D#
#D...C....C...D#
#D..C......C..D#
#D..C...r..C..D#
#D..C...r..C..D#
#D..C...rrrC..D#
#D..C......C..D#
#D..C......C..D#
#D...C....C...D#
#D....CCCC....D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

PART_TRACER = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.##########.D#
#D.#........#.D#
#D.#..C.....#.D#
#D.#.C.C..C.#.D#
#D.#C...CC.C#.D#
#D.#.......C#.D#
#D.#........#.D#
#D.#cccccccc#.D#
#D.##########.D#
#D............D#
#D...LLLLLL...D#
#DDDDDDDDDDDDDD#
################
"""

PART_P2P_TERMINAL = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.##########.D#
#D.#........#.D#
#D.#.CC.....#.D#
#D.#.CC..rr.#.D#
#D.#...cc...#.D#
#D.#.rr..CC.#.D#
#D.#.....CC.#.D#
#D.#........#.D#
#D.##########.D#
#D............D#
#D...LLLLLL...D#
#DDDDDDDDDDDDDD#
################
"""

ITEM_GUIDE_TABLET = """
................
..OOOOOOOOOOOO..
..OFFFFFFFFFFO..
..OFSSSSSSSSFO..
..OFS......SFO..
..OFS.rr...SFO..
..OFS.rr.r.SFO..
..OFS..r.r.SFO..
..OFS..rrr.SFO..
..OFS......SFO..
..OFSSSSSSSSFO..
..OFFFFFFFFFFO..
..OFFFgggFFFFO..
..OOOOOOOOOOOO..
................
................
"""

write_png(OUT / "part" / "p2p_frequency_terminal.png", PART_P2P_TERMINAL, PART)
write_png(OUT / "item" / "guide_tablet.png", ITEM_GUIDE_TABLET, PART | CARD)

write_png(OUT / "part" / "tracer_terminal.png", PART_TRACER, PART)
write_panel(OUT / "gui" / "tracer_panel.png", 236, 190)

write_png(OUT / "part" / "stock_sensor.png", PART_STOCK_SENSOR, PART)
write_png(OUT / "part" / "rate.png", PART_RATE, PART)
write_png(OUT / "part" / "counter.png", PART_COUNTER, PART)
write_png(OUT / "part" / "timer.png", PART_TIMER, PART)
write_panel(OUT / "gui" / "logic_panel.png", 200, 166)

write_png(OUT / "block" / "register_bank_side.png", REGISTER_BANK_SIDE, STEEL)
write_png(OUT / "block" / "register_bank_top.png", REGISTER_BANK_TOP, STEEL)
write_png(OUT / "item" / "signal_card.png", SIGNAL_CARD, CARD)
write_png(OUT / "gui" / "signal.png", SIGNAL_ICON, GUI)
write_png(OUT.parent.parent.parent / "logo.png", SIGNAL_ICON, GUI, scale=4)
