#!/usr/bin/env python3
"""Generate 16x16 RGBA PNG textures for ae2logistics from ASCII pixel grids."""
import colorsys
import math
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


def write_png_pixels(path, rows):
    """Write an RGBA PNG from rows of (r, g, b, a) pixels."""
    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(bytes(px) for px in row)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
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
write_panel(OUT / "gui" / "workbench_panel.png", 176, 190)

GUARDED_PATTERN_ITEM = """
................
.OOOOOOOOOOOOOO.
.OFFFFFFFFFFFSO.
.OFrrFFFFFFFFSO.
.OFrrFF.FF.FFSO.
.OFrrFFFFFFFFSO.
.OFrrFF.rr.FFSO.
.OFrrFFFFFFFFSO.
.OFrrFF.FF.FFSO.
.OFrrFFFFFFFFSO.
.OFrrFFrFFrFFSO.
.OFrrFrFrFFFFSO.
.OFrrFFrFrFFFSO.
.OSSSSSSSSSSSSO.
.OOOOOOOOOOOOOO.
................
"""

write_png(OUT / "item" / "guarded_pattern.png", GUARDED_PATTERN_ITEM, PART | CARD)

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


# Mesh faces: the typed endpoints and the Provider P2P Tunnel borrow AE2's own
# faces in their models (p2p_tunnel_item/fluid/energy/redstone/me,
# pattern_provider), and signal is a load-time hue swap of the light P2P face
# (paletted_permutations in assets/minecraft/atlases/blocks.json). The key strip
# must stay byte-exact with ae2's light-face ramp - unmatched pixels pass
# through unrecolored, so an AE2 palette change shows up as a gold face.
P2P_FACE_KEY = [(255, 207, 64, 255), (244, 255, 128, 255), (255, 227, 89, 255)]
SIGNAL_FACE = [(134, 44, 158, 255), (238, 116, 255, 255), (186, 78, 206, 255)]
write_png_pixels(OUT / "color_palette" / "p2p_face_key.png", [P2P_FACE_KEY])
write_png_pixels(OUT / "color_palette" / "signal_face.png", [SIGNAL_FACE])

# P2P chassis, house edition: ae2's tunnel chassis grays pulled to a light cool
# gray so our parts read as ours next to vanilla tunnels (the faces stay ae2's,
# and the ME-purple connector nub on back2 stays purple on purpose - it really
# does plug into ME, so its colors are simply absent from the key). The curve
# is the one lever for how dark.
P2P_CHASSIS_KEY = [
    (242, 242, 242, 255), (232, 232, 234, 255), (213, 214, 220, 255),
    (204, 204, 204, 255), (193, 195, 208, 255), (168, 168, 168, 255),
    (166, 166, 166, 255), (135, 143, 165, 255), (120, 126, 151, 255),
    (102, 102, 102, 255), (89, 89, 89, 255), (77, 77, 103, 255),
    (66, 66, 66, 255), (65, 65, 65, 255), (64, 64, 64, 255), (65, 63, 84, 255),
]


def chassis_gray(r, g, b):
    """House chassis curve - the one lever for how dark our p2p family reads."""
    return (int(r * 0.78), int(g * 0.80), min(255, int(b * 0.85) + 5), 255)


CHASSIS_GRAY = [chassis_gray(r, g, b) for r, g, b, _ in P2P_CHASSIS_KEY]
write_png_pixels(OUT / "color_palette" / "p2p_chassis_key.png", [P2P_CHASSIS_KEY])
write_png_pixels(OUT / "color_palette" / "chassis_gray.png", [CHASSIS_GRAY])

# ae2's back2 is transparent at the four frequency windows BY DESIGN: real P2P
# tunnels composite the frequency-glow layer behind them (P2PModels does this
# for our provider tunnel too, so it keeps the permuted ae2 back2). The mesh
# endpoints and Subnet Link are not tunnels - nothing fills the windows and you
# see into the part - so they wear this opaque connector instead: chassis tones
# through the same curve, ae2's ME-purple nub kept, windows lit dim cyan. Only
# texels 5..11 are visible (the nub cube's uv region).
MESH_BACK2 = """
################
################
################
################
################
#####LLggLL#####
#####LmmmmL#####
#####gmBPmg#####
#####gmPdmg#####
#####LmmmmL#####
#####LLggLL#####
################
################
################
################
################
"""

write_png(OUT / "part" / "mesh_back2.png", MESH_BACK2, {
    "#": chassis_gray(65, 65, 65),
    "L": chassis_gray(204, 204, 204),
    "m": chassis_gray(102, 102, 102),
    "g": (0, 90, 120, 255),      # dim cyan link light
    "B": (226, 163, 227, 255),   # ae2 ME nub, bright
    "P": (145, 93, 205, 255),    # ae2 ME nub, mid
    "d": (90, 71, 158, 255),     # ae2 ME nub, dark
})


# The universal face is the one still drawn here: a rainbow swirl - every
# transport at once.
def mesh_universal_swirl():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                row.append(PART["#"])
            elif x in (1, 14) or y in (1, 14):
                row.append(PART["D"])
            else:
                r = math.hypot(x - 7.5, y - 7.5)
                hue = (math.atan2(y - 7.5, x - 7.5) / math.tau + r / 9.0) % 1.0
                red, green, blue = colorsys.hsv_to_rgb(hue, 0.8, 0.95 - 0.045 * r)
                row.append((int(red * 255), int(green * 255), int(blue * 255), 255))
        rows.append(row)
    return rows


write_png_pixels(OUT / "part" / "mesh_endpoint.png", mesh_universal_swirl())

# Subnet link: a split face - main network left, subnet right, bridged in the middle.
PART_SUBNET_LINK = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.CC......cc.D#
#D.CC......cc.D#
#D....C..c....D#
#D..CCCccccc..D#
#D.CCrrrrrrcc.D#
#D.CCrrrrrrcc.D#
#D..CCCccccc..D#
#D....C..c....D#
#D.CC......cc.D#
#D.CC......cc.D#
#D............D#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "part" / "subnet_link.png", PART_SUBNET_LINK, PART)

write_png(OUT / "part" / "p2p_frequency_terminal.png", PART_P2P_TERMINAL, PART)

write_png(OUT / "part" / "tracer_terminal.png", PART_TRACER, PART)
write_panel(OUT / "gui" / "tracer_panel.png", 236, 190)

PART_JOB_MONITOR = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.##########.D#
#D.#........#.D#
#D.#.CCCCCC.#.D#
#D.#........#.D#
#D.#.cccC...#.D#
#D.#........#.D#
#D.#.ccC....#.D#
#D.#........#.D#
#D.##########.D#
#D....rr......D#
#D...LLLLLL...D#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "part" / "job_monitor.png", PART_JOB_MONITOR, PART)

PART_QUERY_TERMINAL = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.##########.D#
#D.#........#.D#
#D.#.CCC....#.D#
#D.#C...C...#.D#
#D.#C...C...#.D#
#D.#C...C...#.D#
#D.#.CCC.C..#.D#
#D.#......C.#.D#
#D.##########.D#
#D............D#
#D...LLLLLL...D#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "part" / "query_terminal.png", PART_QUERY_TERMINAL, PART)

PART_QUERY_SENSOR = """
################
#DDDDDDDDDDDDDD#
#DMMMMMMMMMMMMD#
#DM..........MD#
#DM...CCCC...MD#
#DM..C....C..MD#
#DM.......C..MD#
#DM......C...MD#
#DM.....C....MD#
#DM.....C....MD#
#DM..........MD#
#DM.....r....MD#
#DM..........MD#
#DMMMMMMMMMMMMD#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "part" / "query_sensor.png", PART_QUERY_SENSOR, PART)

PART_QUERY_EXPORT_BUS = """
################
#DDDDDDDDDDDDDD#
#DMMMMMMMMMMMMD#
#DM..........MD#
#DM...CCC....MD#
#DM..C...C...MD#
#DM......C...MD#
#DM.....C....MD#
#DM.....C....MD#
#DM..........MD#
#DM.....c....MD#
#DM....ccc...MD#
#DM...ccccc..MD#
#DMMMMMMMMMMMMD#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "part" / "query_export_bus.png", PART_QUERY_EXPORT_BUS, PART)

PART_CONFIG_TERMINAL = """
################
#DDDDDDDDDDDDDD#
#D............D#
#D.##########.D#
#D.#........#.D#
#D.#.CC.ccc.#.D#
#D.#.CC.....#.D#
#D.#........#.D#
#D.#.cc.CCC.#.D#
#D.#.cc.....#.D#
#D.#........#.D#
#D.##########.D#
#D....rr......D#
#D...LLLLLL...D#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "part" / "config_terminal.png", PART_CONFIG_TERMINAL, PART)

BLUEPRINT = dict(CARD)
BLUEPRINT.update({
    "B": (58, 108, 200, 255),   # blueprint blue
    "W": (196, 214, 240, 255),  # draft lines
})

ITEM_CONFIG_BLUEPRINT = """
................
.OOOOOOOOOOOOOO.
.OBBBBBBBBBBBSO.
.OBWWWWWBBWWBSO.
.OBBBBBBBBBBBSO.
.OBWWBBWWWWWBSO.
.OBWWBBBBBBBBSO.
.OBBBBBWWWBBBSO.
.OBWWWWWBBBBBSO.
.OBBBBBBBWWBBSO.
.OBWWBBWWWWWBSO.
.OBBBBBBBBBBBSO.
.OBrrBBBBBBBBSO.
.OSSSSSSSSSSSSO.
.OOOOOOOOOOOOOO.
................
"""

write_png(OUT / "item" / "config_blueprint.png", ITEM_CONFIG_BLUEPRINT, BLUEPRINT)

SCHEDULER_SIDE = """
################
#DDDDDDDDDDDDDD#
#DLMMMMMMMMMMLD#
#DM..........MD#
#DM.CC.......MD#
#DM.CCCCCC...MD#
#DM..........MD#
#DM.cc.......MD#
#DM.cccccccc.MD#
#DM..........MD#
#DM.rr.......MD#
#DM.rrcccc...MD#
#DM..........MD#
#DLMMMMMMMMMMLD#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "block" / "job_scheduler.png", SCHEDULER_SIDE, PART)

LOGIC_CORE_SIDE = """
################
#DDDDDDDDDDDDDD#
#DLMMMMMMMMMMLD#
#DM..........MD#
#DM.CC.cc.CC.MD#
#DM..........MD#
#DM.cc.CC.cc.MD#
#DM..........MD#
#DM.CC.cc.cc.MD#
#DM..........MD#
#DM.cc.cc.CC.MD#
#DM..........MD#
#DM.rr.......MD#
#DLMMMMMMMMMMLD#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "block" / "logic_core.png", LOGIC_CORE_SIDE, PART)

DENSE_WAP_SIDE = """
################
#DDDDDDDDDDDDDD#
#DLMMMMMMMMMMLD#
#DM..c....c..MD#
#DM.c......c.MD#
#DM.c.CCCC.c.MD#
#DM..CCCCCC..MD#
#DM..CCLLCC..MD#
#DM..CCLLCC..MD#
#DM..CCCCCC..MD#
#DM.c.CCCC.c.MD#
#DM.c......c.MD#
#DM..c....c..MD#
#DLMMMMMMMMMMLD#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "block" / "dense_wireless_access_point.png", DENSE_WAP_SIDE, PART)

WIRELESS_BRIDGE_SIDE = """
################
#DDDDDDDDDDDDDD#
#DLMMMMMMMMMMLD#
#DM..........MD#
#DM....cc....MD#
#DM..cc..cc..MD#
#DM.c..CC..c.MD#
#DM...CCCC...MD#
#DM...CCCC...MD#
#DM.c..CC..c.MD#
#DM..cc..cc..MD#
#DM....cc....MD#
#DM.rr.......MD#
#DLMMMMMMMMMMLD#
#DDDDDDDDDDDDDD#
################
"""

write_png(OUT / "block" / "wireless_bridge.png", WIRELESS_BRIDGE_SIDE, PART)


REGULUS = dict(PART)
REGULUS.update({
    "q": (255, 138, 122, 255),  # regulus glow
    "W": (255, 236, 230, 255),  # facet highlight
})

ITEM_REGULUS_CRYSTAL = """
................
.......W........
......Wq........
.....Wqr........
....Wqrr...W....
....qrrr..Wq....
...qrrrr.Wqr....
..Wqrrr..qrr....
..qrrr..Wqrr....
.Wqrr...qrrr....
.qrrr..Wqrr.....
.rrr...qrrr.....
..rr...rrr......
...r....r.......
................
................
"""

write_png(OUT / "item" / "regulus_crystal.png", ITEM_REGULUS_CRYSTAL, REGULUS)

write_png(OUT / "part" / "stock_sensor.png", PART_STOCK_SENSOR, PART)
write_png(OUT / "part" / "rate.png", PART_RATE, PART)
write_png(OUT / "part" / "counter.png", PART_COUNTER, PART)
write_png(OUT / "part" / "timer.png", PART_TIMER, PART)

write_png(OUT / "block" / "register_bank_side.png", REGISTER_BANK_SIDE, STEEL)
write_png(OUT / "block" / "register_bank_top.png", REGISTER_BANK_TOP, STEEL)

TRACE_PANEL_FRONT = """
################
#..............#
#..............#
#...C..........#
#..cC..C.......#
#..cCc.Cc..C...#
#.ccCccCcc.Cc..#
#.ccCccCcccCc..#
#..............#
#......C.......#
#...c..Cc......#
#..cCc.Ccc.C...#
#.ccCccCcccCc..#
#..............#
#..............#
################
"""

write_png(OUT / "block" / "trace_panel_front.png", TRACE_PANEL_FRONT, STEEL)

GUARDED_PROVIDER_SIDE = """
################
#DDDDDDDDDDDDDD#
#DLMMMMMMMMMMLD#
#DM..........MD#
#DM.########.MD#
#DM.#MMMMMM#.MD#
#DM.#MrrrrM#.MD#
#DM.#MrMMrM#.MD#
#DM.#MrMMrM#.MD#
#DM.#MrrrrM#.MD#
#DM.#MMMMMM#.MD#
#DM.########.MD#
#DLMMMMMMMMMMLD#
#DDDDDDDDDDDDDD#
################
################
"""

write_png(OUT / "block" / "guarded_pattern_provider.png", GUARDED_PROVIDER_SIDE, PART)
write_png(OUT / "item" / "signal_card.png", SIGNAL_CARD, CARD)
write_png(OUT / "gui" / "signal.png", SIGNAL_ICON, GUI)
write_png(OUT.parent.parent.parent / "logo.png", SIGNAL_ICON, GUI, scale=4)
