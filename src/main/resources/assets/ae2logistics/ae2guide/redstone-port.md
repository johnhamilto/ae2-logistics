---
navigation:
  parent: devices-index.md
  title: Redstone Signal Port
  position: 25
  icon: ae2logistics:redstone_port
item_ids:
- ae2logistics:redstone_port
---

# Redstone Signal Port

<ItemImage id="redstone_port" scale="2" />

The bridge between signals and redstone, in both directions.

- **Output mode**: emits the channel's value as redstone 0-15 on its face. The
  Strong/Weak toggle picks the emission style: **Strong** (default) also powers the
  block on its face like a lever would, **Weak** only feeds directly adjacent
  receivers and never conducts through blocks.
- **Input mode**: writes the neighbor's redstone level into the channel.
- The only logic part whose job cannot move into an [ME Logic Core](logic-core.md) - it needs a physical face.

<RecipeFor id="redstone_port" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
