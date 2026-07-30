---
navigation:
  parent: devices-index.md
  title: Signal Constant
  position: 20
  icon: ae2logistics:constant
item_ids:
- ae2logistics:constant
---

# Signal Constant

<ItemImage id="constant" scale="2" />

Writes its configured value to the output channel, every tick. The starting point of most graphs: set points, thresholds for other parts, feature flags.

- Config: output channel + value.

<RecipeFor id="constant" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
