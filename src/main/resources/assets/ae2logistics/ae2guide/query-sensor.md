---
navigation:
  parent: devices-index.md
  title: Signal Query Sensor
  position: 51
  icon: ae2logistics:query_sensor
item_ids:
- ae2logistics:query_sensor
---

# Signal Query Sensor

<ItemImage id="query_sensor" scale="2" />

A [Stock Sensor](stock-sensor.md) that counts by [query](queries.md) instead of
one exact item: it writes the total stored amount matching its expression to a
signal channel, every tick.

- `signal()` terms inside the query evaluate in dependency order with the rest of
  the logic graph - query results can feed thresholds that feed other queries.
- Use `@name` to reference expressions saved at a
  [Query Terminal](query-terminal.md).

<RecipeFor id="query_sensor" />
