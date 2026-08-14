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

- Configure by right-click: frequency name and role (input/output/both); the cog
  opens the transport toggles - any subset of redstone, items, fluids, energy,
  signals, ME, and provider. Priority sits behind AE2's usual wrench tab in the
  top-right corner, same as every storage bus and interface.
- A live roster lists every endpoint of the frequency on this network: role,
  priority, what the face touches, and status with ME lane state - streamed live,
  so status flips and edits by other players show up while the GUI is open.
  Hovering marks the clickable rows; clicking one closes the GUI, flashes a thick
  locator box around that endpoint's cable (visible through walls), and prints how
  far away it is. An endpoint in another dimension prints where it is instead.
- Costs one channel and idles at 1 AE/t. Frequencies never cross networks - the
  network the endpoints sit on is the carrier.

Crafted from any P2P tunnel, a Regulus Crystal - the "every transport" token - and
an engineering processor; the [Typed Mesh Endpoints](typed-mesh-endpoints.md) are
the cheaper single-transport versions you'll build first:

<RecipeFor id="mesh_endpoint" />

Transport semantics, ME lane bundling, and the diagnostics are all in
[the Mesh](mesh.md).
