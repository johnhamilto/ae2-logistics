---
navigation:
  parent: devices-index.md
  title: Guarded Pattern
  position: 44
  icon: ae2logistics:guarded_pattern
item_ids:
- ae2logistics:guarded_pattern
---

# Guarded Pattern

<ItemImage id="guarded_pattern" scale="2" />

An encoded pattern wrapped with its own signal condition at the
[Pattern Workbench](pattern-workbench.md): it plans and pushes only while the
condition holds.

- Two opposite-guarded recipes can share ONE provider slot-for-slot - smelt ore
  when stock is low, store it raw when high.
- Enforcement needs a [Guarded Pattern Provider](guarded-pattern-provider.md);
  in a vanilla provider the wrapper is inert and behaves as the inner pattern.
- Unwrap at the workbench to get the original back.

See [Guarded Crafting](guarded-crafting.md) for how pattern guards and provider
guards compose.
