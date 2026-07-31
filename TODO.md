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
- Transport family (mesh endpoints, P2P Frequency Terminal, Provider P2P Tunnel):
  better sprite art ONLY - needs a new texture approach, deferred; GUI, models,
  guide, recipes, and tests closed out 0.24.x-0.26.0
- ME Subnet Link

## Features

- **Pattern Import Card**: a card item that goes into AE2's Pattern Encoding Terminal
  (and ExtendedAE's Extended Pattern Terminal when present) and auto-imports patterns
  into that GUI's pattern slot. Must work in both the cable-part terminals and the
  wireless forms.
- Generated backgrounds everywhere: adopt `generatedBackground` for the remaining
  screens as each is touched; verify slot-grid screens (Stock Sensor, Pattern
  Workbench) draw slot insets correctly without a baked texture.
- ME Job Monitor board onto ScrollingRowList + Palette.
- Logic part screens onto Palette + the padding rhythm when next touched.
- Pre-release modpack soak against the standard suite (see CLAUDE.md).
