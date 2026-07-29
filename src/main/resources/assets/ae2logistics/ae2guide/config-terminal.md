---
navigation:
  parent: ae2logistics-index.md
  title: ME Config Terminal
  position: 48
  icon: ae2logistics:config_terminal
item_ids:
- ae2logistics:config_terminal
- ae2logistics:config_blueprint
---

# ME Config Terminal

Memory cards configure one block at a time; a 200-device network's configuration is
write-only in practice. The Config Terminal lists **every configurable device on the
network** - AE2's buses, providers, interfaces, and emitters alongside every part of
this mod - and edits them in place.

Craft it from an AE2 Terminal, a Regulus Crystal, and an Engineering Processor.

<RecipeFor id="config_terminal" />

- **The list** shows each device's icon, name, position, priority, and a summary of
  its generic settings. The search box filters by name, type, or setting text -
  `redstone` finds every device with a redstone mode configured.
- **Select a device** and its settings appear below as `name = value` lines; the `>`
  button cycles each one through its values (blocking mode, fuzzy mode, redstone
  control, scheduling, ...). Priority is editable next to them.
- **Copy** captures the selected device's full memory-card settings - the same data a
  physical memory card would - including filters and upgrades where the device
  exports them. **Paste** applies to another device of the same type; **All** applies
  to every same-type device on the network. Fix one export bus, propagate to forty.

Remote writes require that you may build and may interact with the terminal's
position, so adventure mode and protection mods are respected. (AE2's security
station no longer exists in this Minecraft line; when AE2 regains one, gating will
follow it.)

# Snapshots and diff

**Snap** records every device's settings and priority into the terminal (persistent).
From then on the list colors devices against that baseline - gold for changed, cyan
for new, red for missing - and the toggle button filters to differences only. "What
did I break last week" becomes a two-click question.

# Config Blueprint

The Config Blueprint item is a multi-block memory card: click two corners to capture
every AE2-based device in the box (positions, part sides, types, full settings), then
sneak-click the minimum corner of a rebuilt region to reapply everything to matching
devices. Sneak-use in the air clears it. Craft it from paper, a Regulus Crystal, and
a certus quartz crystal.

<RecipeFor id="config_blueprint" />
