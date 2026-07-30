---
navigation:
  parent: devices-index.md
  title: Signal Counter
  position: 28
  icon: ae2logistics:counter
item_ids:
- ae2logistics:counter
---

# Signal Counter

<ItemImage id="counter" scale="2" />

Counts rising edges of input A (zero to nonzero transitions).

- Value A > 0 wraps the count (modulo); 0 counts forever.
- Channel B, when set, is a reset line: the count holds at 0 while B is nonzero.
- The count persists across save/load.

<RecipeFor id="counter" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
