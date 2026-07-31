# TODO

Working backlog. Delete entries as they finish - ROADMAP.md records what shipped,
this file tracks what is rough or wanted. A "polish pass" on an entry means: item and
in-world model, GUI (generated background, consistent padding rhythm, Palette /
ScrollingRowList where they fit), guide page accuracy, recipe sanity, tooltip.

## Polish pass - blocks

- ME Register Bank
- Pattern Workbench
- Guarded Pattern Provider
- ME Job Scheduler
- ME Logic Core
- Dense Wireless Access Point
- ME Wireless Bridge

## Polish pass - items

- Signal Card
- Adaptive Processing Pattern
- Guarded Pattern
- Config Blueprint
- Regulus Crystal

## Polish pass - cable parts

- Signal Constant
- Signal Threshold
- Signal Hysteresis
- Signal Arithmetic
- Logic Gate
- Redstone Signal Port
- Stock Sensor
- Rate Meter
- Signal Counter
- Signal Timer
- ME Tracer Terminal
- ME Job Monitor
- Query Terminal
- Query Sensor
- Query Export Bus
- ME Config Terminal
- P2P Frequency Terminal
- Mesh endpoints (universal + 7 typed): better sprite art ONLY - needs a new texture
  approach, deferred; GUI, 3D item models, guide, recipes, and tests closed out 0.24.x
- Provider P2P Tunnel
- ME Subnet Link

## Features

- **Pattern Import Card**: a card item that goes into AE2's Pattern Encoding Terminal
  (and ExtendedAE's Extended Pattern Terminal when present) and auto-imports patterns
  into that GUI's pattern slot. Must work in both the cable-part terminals and the
  wireless forms.
- Generated backgrounds everywhere: adopt `generatedBackground` for the remaining
  screens as each is touched; verify slot-grid screens (Stock Sensor, Pattern
  Workbench) draw slot insets correctly without a baked texture.
- Provider P2P Tunnel: expose `ICraftingMachine` on the input face so assembler
  patterns cross and machines chain (public-API version of MAE2's trick).
- Roster follow-ups: click a row to highlight the endpoint in world; stream status
  changes live (today only config edits refresh); self-row emphasis if the color
  distinction stays too subtle.
- ME Job Monitor board onto ScrollingRowList + Palette.
- Logic part screens onto Palette + the padding rhythm when next touched.
- Applied Mekanistics native chemical capability on our tunnel faces (one
  registration on either side; AE2-aware paths already work).
- Pre-release modpack soak against the standard suite (see CLAUDE.md).
