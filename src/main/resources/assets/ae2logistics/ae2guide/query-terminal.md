---
navigation:
  parent: devices-index.md
  title: ME Query Terminal
  position: 50
  icon: ae2logistics:query_terminal
item_ids:
- ae2logistics:query_terminal
---

# ME Query Terminal

<ItemImage id="query_terminal" scale="2" />

The workbench for the [query language](queries.md): write expressions against the
network's storage with a live preview, and save them to a shared library.

- The preview updates about once a second: matching kinds, total amount, up to
  six sample stacks; parse errors show as you type.
- **Save** stores the expression under a name; `@name` references it from any
  query device. The library replicates across every Query Terminal on the
  network, and editing a saved query updates everything referencing it.
- Click a saved entry to load it for editing.

<RecipeFor id="query_terminal" />
