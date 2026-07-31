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

- Configure by right-click: frequency name, role (input/output/both), and
  priority; the cog opens the transport toggles - any subset of redstone, items,
  fluids, energy, signals, ME, and provider.
- A live roster lists every endpoint of the frequency on this network: role,
  priority, what the face touches, and status with ME lane state. The server
  re-pushes it as endpoints are reconfigured.
- Costs one channel and idles at 1 AE/t. Frequencies never cross networks - the
  network the endpoints sit on is the carrier.

Crafted from any P2P tunnel, a Regulus Crystal - the "every transport" token - and
an engineering processor; the [Typed Mesh Endpoints](typed-mesh-endpoints.md) are
the cheaper single-transport versions you'll build first:

<RecipeFor id="mesh_endpoint" />

Transport semantics, ME lane bundling, and the diagnostics are all in
[the Mesh](mesh.md).
