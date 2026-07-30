---
navigation:
  parent: devices-index.md
  title: Signal Card
  position: 12
  icon: ae2logistics:signal_card
item_ids:
- ae2logistics:signal_card
---

# Signal Card

<ItemImage id="signal_card" scale="2" />

Gives a [signal](signals.md) channel an item form, so AE2 devices with config slots
can threshold and display computed numbers.

- Bind with `/ae2logistics signal card <channel>` while holding it; the tooltip
  shows the bound channel. Stacks to 1.
- In a **Level Emitter's** config slot: the emitter thresholds on the signal's live
  value - computed numbers become redstone.
- In a **Storage Monitor's** slot: the monitor displays the signal's value.
- Read one manually by right-clicking a [Register Bank](register-bank.md) while
  holding the card.

<RecipeFor id="signal_card" />
