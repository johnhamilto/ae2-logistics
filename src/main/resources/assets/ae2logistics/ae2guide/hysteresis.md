---
navigation:
  parent: devices-index.md
  title: Signal Hysteresis
  position: 22
  icon: ae2logistics:hysteresis
item_ids:
- ae2logistics:hysteresis
---

# Signal Hysteresis

<ItemImage id="hysteresis" scale="2" />

A latch with two thresholds - the no-flicker switch. Latches to 1 when input A drops below the LOW value, back to 0 only when A rises above the HIGH value; anywhere between, it holds its last state.

- Config: input A, low (value A), high (value B).
- Latch state persists across save/load.
- The classic use: keep-stocked without machine flutter (start below 1k, stop above 2k).

<RecipeFor id="hysteresis" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
