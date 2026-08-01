---
navigation:
  parent: devices-index.md
  title: ME Storage Janitor
  position: 11
  icon: ae2logistics:storage_janitor
item_ids:
- ae2logistics:storage_janitor
---

# ME Storage Janitor

<BlockImage id="storage_janitor" scale="4" />

An in-place IO Port for the whole network, external storages included. Press
**Rejigger** and stored stock re-settles to wherever CURRENT filters, partitions,
and priorities say it belongs - set up a new drawer wall, filter its bus, run the
janitor, done. No bus-breaking, no manual re-import.

- Works by moving each stored kind through a transient buffer and re-inserting it:
  AE2's own insert ordering is the placement policy, so misplaced stock lands in
  its new best home and correctly-placed stock returns exactly where it was.
- A run is two passes (the second catches space freed by the first), chunked over
  ticks. It is a DEFRAG: pathological full-storage swap webs need free space
  somewhere and may take another run.
- Nothing is ever lost: a re-insert shortfall parks in the janitor and retries
  every tick before any new work ("holding" in the GUI, normally never seen).
- Costs AE power per kind processed and pauses while the network is unpowered.
- `/ae2logistics janitor` toggles the janitor you are looking at.

<RecipeFor id="storage_janitor" />
