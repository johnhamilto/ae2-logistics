---
navigation:
  parent: devices-index.md
  title: Guarded Pattern Provider
  position: 43
  icon: ae2logistics:guarded_pattern_provider
item_ids:
- ae2logistics:guarded_pattern_provider
---

# Guarded Pattern Provider

<BlockImage id="guarded_pattern_provider" scale="4" />

A full pattern provider (nine slots, pushes to adjacent machines, appears in
pattern access terminals) with a [signal](signals.md)-driven gate.

- **Guard**: channel + operator + value. While the condition fails, every pattern
  in this provider is invisible to the planner - jobs route to other providers.
  An empty channel means no guard. The header shows **PASS/HOLD** live.
- **Gate toggle**: *plan + push* (default; a failing guard also holds pushes of
  already-running jobs until it reopens) or *plan only* (running jobs always
  finish).
- **Priority channel** + base: when set, the provider's priority follows the live
  signal value - moving the planner between DIFFERENT recipes for one output.
- Guard flips take effect within about half a second.

<RecipeFor id="guarded_pattern_provider" />

The mechanics - and what guards can and cannot arbitrate - are in
[Guarded Crafting](guarded-crafting.md).
