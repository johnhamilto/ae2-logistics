---
navigation:
  parent: devices-index.md
  title: Adaptive Processing Pattern
  position: 41
  icon: ae2logistics:adaptive_processing_pattern
item_ids:
- ae2logistics:adaptive_processing_pattern
---

# Adaptive Processing Pattern

<ItemImage id="adaptive_processing_pattern" scale="2" />

A processing pattern whose ingredients match a RANGE of inputs instead of one
exact item. Made at the [Pattern Workbench](pattern-workbench.md); used in ANY
pattern provider.

- Per-ingredient modes: **exact**, **fuzzy** (same item ignoring components, with
  damage bands 99/75/50/25 for damageables), **tag**, **any-of** (an explicit
  list, up to 8).
- The **catalyst flag** marks an ingredient that must be present and ships with
  the batch but is credited back - one pickaxe serves many queued crafts.
- The planner consumes substitutes from storage at every level of a crafting
  tree, but only the canonical encoded item is ever autocrafted.
- Do not list a catalyst tool as a machine OUTPUT too - that would dupe it.

The full matching semantics live in [Adaptive Patterns](adaptive-patterns.md).
