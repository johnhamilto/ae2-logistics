---
navigation:
  parent: devices-index.md
  title: Signal Rate Meter
  position: 27
  icon: ae2logistics:rate
item_ids:
- ae2logistics:rate
---

# Signal Rate Meter

<ItemImage id="rate" scale="2" />

Measures how fast channel A GROWS: units per second over a sliding window.

- Window: value A seconds, clamped 1-60. Reads 0 until the window fills.
- Decreasing values read 0 - it meters production, not consumption.
- Feed it a [Stock Sensor](stock-sensor.md) channel to see items/second of production.

<RecipeFor id="rate" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
