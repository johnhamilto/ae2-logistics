---
navigation:
  parent: devices-index.md
  title: Signal Threshold
  position: 21
  icon: ae2logistics:threshold
item_ids:
- ae2logistics:threshold
---

# Signal Threshold

<ItemImage id="threshold" scale="2" />

Compares input A against operand B and writes 1 or 0.

- Operators: `<` `<=` `==` `>=` `>`.
- Operand B is a literal value or channel B (toggle in the GUI).
- Pair with a [Redstone Signal Port](redstone-port.md) to turn a comparison into redstone.

<RecipeFor id="threshold" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
