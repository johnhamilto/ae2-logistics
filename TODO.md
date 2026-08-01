# TODO

Working backlog. Delete entries as they finish - ROADMAP.md records what shipped,
this file tracks what is rough or wanted. A "polish pass" on an entry means: item and
in-world model, GUI (generated background, consistent padding rhythm, Palette /
ScrollingRowList where they fit), guide page accuracy, recipe sanity, tooltip.

## Polish pass - blocks

- Pattern Workbench
- Guarded Pattern Provider
- ME Job Scheduler
- Dense Wireless Access Point
- ME Wireless Bridge

## Polish pass - items

- Adaptive Processing Pattern
- Guarded Pattern
- Config Blueprint
- Regulus Crystal

## Polish pass - cable parts

- ME Tracer Terminal
- ME Job Monitor
- Query Terminal
- Query Sensor
- Query Export Bus
- ME Config Terminal
- Deferred sprite art (needs a new texture approach): transport family (mesh
  endpoints, P2P Frequency Terminal, Provider P2P Tunnel; closed out 0.24.x-0.26.0)
  and signal family (ten logic parts, Register Bank, Logic Core, Signal Card;
  closed out 0.28.0) - GUI, models, guide, recipes, tests all done
- ME Subnet Link

## Features

- **Pattern Import Card**: a card item that goes into AE2's Pattern Encoding Terminal
  (and ExtendedAE's Extended Pattern Terminal when present) and auto-imports patterns
  into that GUI's pattern slot. Must work in both the cable-part terminals and the
  wireless forms.
- Generated backgrounds everywhere: adopt `generatedBackground` for the remaining
  screens as each is touched (queries, Job Monitor, Pattern Workbench, Job
  Scheduler, Guarded Provider, Tracer/Query/Config terminals). Slot insets over a
  generated panel are solved: Icon.SLOT_BACKGROUND per active slot (see
  LogicPartScreen).
- ME Job Monitor board onto ScrollingRowList + Palette.
- Pre-release modpack soak against the standard suite (see CLAUDE.md).
