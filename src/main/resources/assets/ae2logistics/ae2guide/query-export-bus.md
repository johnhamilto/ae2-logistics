---
navigation:
  parent: devices-index.md
  title: Query Export Bus
  position: 52
  icon: ae2logistics:query_export_bus
item_ids:
- ae2logistics:query_export_bus
---

# Query Export Bus

<ItemImage id="query_export_bus" scale="2" />

An export bus whose filter is a [query](queries.md): it exports everything
matching the expression into the inventory it faces.

- Eight items per operation, scanning up to 32 matching kinds; ticks every 10-40
  ticks, speeding up while it finds work.
- The classic trick: `damage > 50` feeds a repair setup; `tag:c:ores AND stored`
  empties ore overflow into a furnace bank.

<RecipeFor id="query_export_bus" />
