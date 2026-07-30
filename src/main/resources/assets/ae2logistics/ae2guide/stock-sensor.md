---
navigation:
  parent: devices-index.md
  title: Signal Stock Sensor
  position: 26
  icon: ae2logistics:stock_sensor
item_ids:
- ae2logistics:stock_sensor
---

# Signal Stock Sensor

<ItemImage id="stock_sensor" scale="2" />

Writes the network-stored amount of one watched item to a channel, every tick. The eyes of every keep-stocked graph.

- Set the watched item by clicking the GUI's ghost slot with a held item.
- For counting by query instead of one exact item, see the [Query Sensor](query-sensor.md).

<RecipeFor id="stock_sensor" />

Like every logic part it evaluates once per tick in dependency order, costs no AE2 channel, and idles at 0.5 AE/t - the shared rules live in [Logic Parts](logic-parts.md).
