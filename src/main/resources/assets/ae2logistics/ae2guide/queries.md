---
navigation:
  parent: ae2logistics-index.md
  title: Query Language
  position: 45
  icon: ae2logistics:query_terminal
---

# Query Language

AE2's terminal search is a flat string you retype everywhere. Queries are expressions
over your storage that can be **named, saved per-network, and bound to machines**:

```
mod:ae2
tag:c:ingots AND count >= 10k
name:"iron ingot" OR name:raw_iron
tag:c:ores AND NOT craftable
damage > 75
stored AND signal(factory:cleanup) > 0
@metals AND count < 1000
```

- `mod:` / `tag:` / `name:` match identity (name is a case-insensitive substring).
- `count OP N` compares the stored amount (k/m/b suffixes work); `stored` is
  shorthand for any amount; `craftable` asks the crafting service.
- `damage OP N` is percent damaged, for tools.
- `signal(channel) OP N` reads a live signal - queries can be mode-switched from the
  logic graph.
- `@name` includes a saved query. `AND` is implicit between adjacent terms;
  `OR`, `NOT`, and parentheses do what they say.

Queries range over items and fluids; signal keys in storage are deliberately excluded
(a query counting signals would feed back into itself).

# ME Query Terminal

The editor. Type an expression and the terminal shows **live results** - matching
kinds, total amount, and a sample of stacks - with syntax errors called out as you
type. Name it and press Save and it joins the network's library, shown as `@name` in
the saved list. **Every Query Terminal on the network carries the full library**, so
any one surviving terminal preserves it, and editing a saved query updates every
machine that references it by name.

<RecipeFor id="query_terminal" />

# Signal Query Sensor

Writes the **total stored amount matching a query** to a signal channel, every tick,
in dependency order with the rest of the logic graph. This is the bridge from
questions to control: `tag:c:ores AND stored` into a threshold into a guarded
provider gates smelting on ore backlog - three parts, no code.

<RecipeFor id="query_sensor" />

# Query Export Bus

Exports items matching its query (inline or `@named`) into the inventory it faces,
eight items per operation, speeding up while it finds work. The generalized tag-bus:
one part, any predicate, retargetable by editing one saved query.

<RecipeFor id="query_export_bus" />

## Component data rules

`data:"minecraft:stored_enchantments"` matches items that carry the component;
`data:"minecraft:stored_enchantments~*mending*"` walks to that node in the item's
serialized data tree and glob-matches there - so a "mending" in some item's NAME
never trips an enchantment rule. `data:"~*glob*"` matches against the whole tree
when you do want that. Paths use dots (`*` wildcards a level); modded attributes
and classic custom NBT all live in this tree.

## Query Card

Bind a card to a named query with `/ae2logistics query card <name>` and install
it in a [Gated Storage Bus](gated-storage-bus.md) or
[Subnet Link](subnet-link.md): the bus's partition becomes live membership in
that query - data rules, tags, mod filters, craftability, even signal terms.
Queries live in Query Terminals, so the network needs one for the card to
resolve, same as @refs.

## Devices

<ItemLink id="query_terminal" /> (write and save), <ItemLink id="query_sensor" /> (count to a signal), <ItemLink id="query_export_bus" /> (export by query).
