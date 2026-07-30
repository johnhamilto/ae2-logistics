---
navigation:
  parent: devices-index.md
  title: Universal Mesh Endpoint
  position: 60
  icon: ae2logistics:mesh_endpoint
item_ids:
- ae2logistics:mesh_endpoint
---

# Universal Mesh Endpoint

<ItemImage id="mesh_endpoint" scale="2" />

One part, every transport: endpoints on one network sharing a named frequency
form a universal point-to-point tunnel - or, with more of them, a
[mesh](mesh.md).

- Configure by right-click: frequency name, role (input/output/both), priority,
  and any subset of redstone, items, fluids, energy, signals, and ME.
- Nine whitelist filter slots (click with an item, or a bucket for its fluid);
  an empty filter allows everything.
- The GUI's status line shows the same-network endpoint count, this endpoint's ME
  lane state, and diagnostics like CABLED LOOP.
- Costs one channel and idles at 1 AE/t. Frequencies never cross networks - the
  network the endpoints sit on is the carrier.

<RecipeFor id="mesh_endpoint" />

Transport semantics, ME lane bundling, and the diagnostics are all in
[the Mesh](mesh.md).
