---
navigation:
  parent: devices-index.md
  title: Signal Logic Gate
  position: 24
  icon: ae2logistics:logic_gate
item_ids:
- ae2logistics:logic_gate
---

# Signal Logic Gate

<ItemImage id="logic_gate" scale="2" />

Boolean logic over zero/nonzero: AND, OR, XOR, NOT. Write 1/0 to the output channel.

- NOT ignores operand B.
- Chain gates freely - the whole graph settles in one tick (see [Logic Parts](logic-parts.md)).

<RecipeFor id="logic_gate" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
