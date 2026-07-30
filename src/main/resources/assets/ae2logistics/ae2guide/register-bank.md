---
navigation:
  parent: devices-index.md
  title: ME Register Bank
  position: 10
  icon: ae2logistics:register_bank
item_ids:
- ae2logistics:register_bank
---

# ME Register Bank

<BlockImage id="register_bank" scale="4" />

The network's memory for [signals](signals.md): it stores manually-set channels
persistently, and it is the block the `/ae2logistics signal` commands target (they
act on the network of the bank you are looking at).

- **Right-click**: lists every signal on the network in chat.
- **Right-click holding a bound [Signal Card](signal-card.md)**: reads that
  channel to the action bar.
- Stored values survive restarts. A channel a logic part is actively driving shows
  the driven value; when the part stops driving, the stored value returns.
- Values of zero or less delete a stored channel.

<RecipeFor id="register_bank" />

See [Signals](signals.md) for the channel model and the multi-writer sum rule.
