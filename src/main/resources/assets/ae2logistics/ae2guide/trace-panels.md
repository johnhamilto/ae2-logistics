---
navigation:
  parent: devices-index.md
  title: Trace Panels
  position: 37
  icon: ae2logistics:trace_panel
item_ids:
- ae2logistics:trace_panel
---

# Trace Panels

<BlockImage id="trace_panel" scale="4" />

In-world dashboards: place panels of the same facing in a rectangle (up to 4x4)
and they merge into one screen that renders live signal sparklines on its face -
the [Tracer Terminal](tracer-terminal.md)'s charts, on a wall.

- **Bind a channel** by clicking any panel of the wall with a bound
  [Signal Card](signal-card.md); sneak-click with the card removes that trace,
  sneak with an empty hand clears the panel. Up to six traces stack per panel.
- **Manage the panel** by clicking with an empty hand: the window lists every
  bound trace with a remove button, plus clear-all. Clicking any member of the
  wall manages the whole panel.
- The wall records its own two-minute history (one sample per second) - it does
  not need a Tracer Terminal anywhere.
- The min-corner panel is the wall's master: it holds the bindings, so breaking
  it loses them (breaking any other member just re-forms smaller panels).
- Every panel connects to the grid on its own and idles at 0.5 AE/t; a wall only
  needs cable contact somewhere along its members.

<RecipeFor id="trace_panel" />
