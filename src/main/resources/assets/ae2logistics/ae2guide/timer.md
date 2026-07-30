---
navigation:
  parent: devices-index.md
  title: Signal Timer
  position: 29
  icon: ae2logistics:timer
item_ids:
- ae2logistics:timer
---

# Signal Timer

<ItemImage id="timer" scale="2" />

A pulse train: writes 1 for the first PULSE ticks of every PERIOD-tick cycle, 0 for the rest.

- Period: value A ticks (clamped 2-72000). Pulse: value B ticks (1 to period-1).
- Drive a [Counter](counter.md) with it for slow clocks, or gate exports on a duty cycle.

<RecipeFor id="timer" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
