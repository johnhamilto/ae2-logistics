---
navigation:
  parent: devices-index.md
  title: Signal Arithmetic
  position: 23
  icon: ae2logistics:arithmetic
item_ids:
- ae2logistics:arithmetic
---

# Signal Arithmetic

<ItemImage id="arithmetic" scale="2" />

Combines two operands into one number.

- Operators: `+` `-` `*` `/` `min` `max` `mod`.
- Saturates at 0 and the long limit; divide or mod by zero yields 0.
- Operand B is a literal or channel B.

<RecipeFor id="arithmetic" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
