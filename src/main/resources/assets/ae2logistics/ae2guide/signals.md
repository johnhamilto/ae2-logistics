---
navigation:
  parent: ae2logistics-index.md
  title: Signals and the Register Bank
  position: 10
  icon: ae2logistics:register_bank
item_ids:
- ae2logistics:register_bank
- ae2logistics:signal_card
- ae2logistics:tracer_terminal
---

# Signals

A **signal** is a named channel — like `factory:iron_target` — whose value is a number.
Signals are real AE2 keys: they appear in the ME Terminal (with a red wave icon), can be
shown on Storage Monitors, and can drive **Level Emitters**, which means any computed
number can become redstone.

There is exactly one value per channel per network. Every device sees the same number.

# ME Register Bank

The Register Bank stores manually-set signals so they survive restarts, and lets you
inspect the network's signals:

- **Right-click** the bank to list every signal on the network in chat.
- **Click with a bound Signal Card** to read that card's channel on the action bar.

Craft it from iron, certus quartz, redstone, and a logic processor.

Set values by hand while looking at a bank:

```
/ae2logistics signal set factory:iron_target 10000
/ae2logistics signal get factory:iron_target
/ae2logistics signal list
```

Channels driven by logic parts recompute every tick and override manual values.

# ME Tracer Terminal

The Tracer Terminal is the observability surface: a wall-mounted part listing every
signal on the network with its live value. Click a channel to open its **five-minute
sparkline** — values are sampled once a second on the server, so you can watch rates
settle, spot oscillations, and confirm a hysteresis loop is actually latching.

Craft it from an AE2 Terminal, a logic processor, and certus quartz.

# Signal Card

Level Emitters and Storage Monitors are configured by clicking them with an item — and
signals are not items. The **Signal Card** bridges that: bind it to a channel and use it
to fill any config slot.

- Craft: an AE2 Basic Card plus a redstone torch.
- Bind: `/ae2logistics signal card factory:iron_target`.

Put the bound card in a Level Emitter's config slot and the emitter thresholds on your
computed value instead of an item count.
