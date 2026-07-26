#!/usr/bin/env python3
"""Generate the empty gametest structure template as a vanilla structure NBT file."""
import gzip
import struct
import sys
from pathlib import Path

OUT = Path(sys.argv[1])

DATA_VERSION = 3955  # Minecraft 1.21.1


def tag_string(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(tag_type, name, payload):
    return struct.pack(">B", tag_type) + tag_string(name) + payload


def int_payload(value):
    return struct.pack(">i", value)


def int_list(values):
    return struct.pack(">Bi", 3, len(values)) + b"".join(int_payload(v) for v in values)


def empty_list():
    return struct.pack(">Bi", 0, 0)


def compound(entries):
    return b"".join(entries) + struct.pack(">B", 0)


air_palette_entry = compound([named(8, "Name", tag_string("minecraft:air"))])
palette_list = struct.pack(">Bi", 10, 1) + air_palette_entry

root = named(10, "", compound([
    named(9, "size", int_list([5, 5, 5])),
    named(9, "entities", empty_list()),
    named(9, "blocks", empty_list()),
    named(9, "palette", palette_list),
    named(3, "DataVersion", int_payload(DATA_VERSION)),
]))

OUT.parent.mkdir(parents=True, exist_ok=True)
with gzip.open(OUT, "wb") as f:
    f.write(root)
print(f"wrote {OUT}")
