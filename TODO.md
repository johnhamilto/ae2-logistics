# TODO

Working backlog. Delete entries as they finish - ROADMAP.md records what shipped,
this file tracks what is rough or wanted. A "polish pass" on an entry means: item and
in-world model, GUI (generated background, consistent padding rhythm, Palette /
ScrollingRowList where they fit), guide page accuracy, recipe sanity, tooltip.

## Polish

- Deferred sprite art ONLY (needs a new texture approach) - every device family is
  otherwise closed out (GUI, models, guides, recipes, tests): transport
  (0.24.x-0.26.0), signal (0.28.0), telemetry boards (0.29.0), queries + config
  (0.30.0), crafting blocks + scheduler + wireless pair + remaining items + Subnet
  Link (0.31.0).

## Features

- **Pattern Import Card**: a card item that goes into AE2's Pattern Encoding Terminal
  (and ExtendedAE's Extended Pattern Terminal when present) and auto-imports patterns
  into that GUI's pattern slot. Must work in both the cable-part terminals and the
  wireless forms.
- **Trace Panels** (in-world dashboards): multiblock monitor blocks that merge by
  placement into one screen - 1x1 up to a sane cap (2x2, 2x3, ...) via coplanar
  same-facing rectangle detection, Create-display-board style - and render signal
  sparklines on the merged face. Notes:
  - Bind channels by clicking the panel with a bound [Signal Card] (very much the
    house item for "a channel in item form"); a small GUI lists/removes bound
    traces; memory cards copy the set. Layout adapts to panel size (1 big trace or
    a grid of small ones).
  - Formation picks a master block that owns the ONE grid node and channel (the
    rest are slaves, controller-style); breaking any block splits the panel back.
  - Rendering is a BER over the merged face reusing the shared Sparkline helper
    (extracted 0.29.0); history is recorded server-side in the master (own ring
    buffers via the signal service, independent of any Tracer Terminal) and
    synced to watching clients at ~1/s, throttled and cached.
  - Guide page + plot from day one; sprite/model art joins the deferred-art line.
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
  - The ME Storage Janitor (shipped 0.32.0) already covers the migration half:
    a run re-settles claimed keys into their partitioned homes. What sticky still
    needs from upstream is refusal/backpressure only.
- Pre-release modpack soak against the standard suite (see CLAUDE.md).
