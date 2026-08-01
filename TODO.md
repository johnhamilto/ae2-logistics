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
- **Sticky Card** (from AE2UEL/GTNH): upgrade card for storage cells and storage buses
  (Subnet Link included - it IS a bus). Keys covered by a sticky device's partition are
  CLAIMED: they may only be stored on sticky-carded storages; when those are full the
  network REFUSES the key instead of spilling into general storage - reserved storage
  that never leaks. Key-type agnostic (items, fluids, companion-mod chemicals).
  Decisions and edge cases:
  - Claims are the union of every MOUNTED sticky partition, per network. Claimed keys
    route to sticky storages regardless of priority order (everything else vetoes
    them), so priority only orders sticky storages among themselves.
  - Full sticky storages = refused inserts BY DESIGN: crafting jobs and provider
    returns for that key stall visibly (Job Monitor's stall detection shows it). The
    trash-excess variant is sticky + Void Card on the cell, which composes cleanly.
  - Empty partition + sticky = inert card (accidentally claiming everything is worse
    than doing nothing); Inverter Card + sticky = rejected combination (a blacklist
    claim means "claim the universe"). Fuzzy Card widens the claim exactly as it
    widens the partition.
  - Claim lifecycle follows MOUNTED state (drive online, bus powered, chunk loaded):
    a power flicker lapses the claim and inserts land in general storage until
    re-imported. Pre-existing scattered stock never migrates by itself - document
    the manual migration (filtered IO / export-into-sticky), maybe a Config
    Terminal action later.
  - REFUSAL IS BLOCKED ON UPSTREAM. Examined and rejected: a max-priority
    interceptor storage sorts first and can capture + deliver claimed keys into
    sticky storages (a grid-service mount plus our own sticky registry, no mixins),
    but NetworkStorage.insert treats partial acceptance as fall-through - the
    remainder continues to general storage and there is no event to cancel. The
    only lies available are simulate/modulate divergence (AE2 calls that broken
    storage), void (item deletion; vanilla partition+Void Card already covers
    reserved-or-trash), or a buffer (kills the backpressure refusal exists for).
    The "always report more space" variant plugs the fall-through but must then
    put overflow SOMEWHERE: interceptor-held = infinite cell-less storage
    (exploit), voided = deletion, bounded = the refusal problem returns at the
    buffer edge. A mounted storage can say "I took N" but never "nobody may take
    the rest". True refusal needs the storage-service insert-filter hook (ROADMAP
    upstream list); no partial our-devices-only version.
  - Viable TODAY as an interim: the JANITOR variant - the claim registry
    periodically sweeps claimed keys out of non-sticky storages into sticky ones.
    Convergent placement without lies, and it solves the scattered-stock migration
    edge for free; what it lacks is backpressure (sustained overflow fills general
    storage as a buffer instead of refusing). Could ship as the sweep half of the
    card, upgraded to true refusal when the upstream hook lands.
- Generated backgrounds everywhere: adopt `generatedBackground` for the remaining
  screens as each is touched (queries, Job Monitor, Pattern Workbench, Job
  Scheduler, Guarded Provider, Tracer/Query/Config terminals). Slot insets over a
  generated panel are solved: Icon.SLOT_BACKGROUND per active slot (see
  LogicPartScreen).
- ME Job Monitor board onto ScrollingRowList + Palette.
- Pre-release modpack soak against the standard suite (see CLAUDE.md).
