---
navigation:
  parent: devices-index.md
  title: Provider P2P Tunnel
  position: 62
  icon: ae2logistics:provider_p2p_tunnel
item_ids:
- ae2logistics:provider_p2p_tunnel
---

# Provider P2P Tunnel

<ItemImage id="provider_p2p_tunnel" scale="2" />

A P2P tunnel for pattern-provider pushes. Point a provider at the input tunnel and it
behaves as if it were adjacent to every machine behind the output tunnels:

- Each batch lands **complete on one machine** - the first one that has finished its
  previous batch (its last batch is no longer in its inventory).
- When every machine is still working, nothing is pushed until one frees up - true
  per-machine blocking, at range, over one face.
- **Key-type agnostic, like a provider itself**: items, fluids, and any key type a
  companion mod registers (Mekanism chemicals via Applied Mekanistics, flux, ...)
  push through the same way.

This is a standard AE2 P2P in every other way: it carries a frequency on its network,
links with a memory card (shift-click the input to store, click outputs to bind), shows
up in the <ItemLink id="p2p_frequency_terminal" />, and attunes in place - right-click
any P2P tunnel while holding a pattern provider to convert it.

<RecipeFor id="provider_p2p_tunnel" />

Where an Item P2P moves stacks as a dumb pipe, this tunnel preserves provider
semantics: batches stay whole and blocking mode keeps meaning per machine. The
mesh-frequency version of the same behavior is the provider transport of the
[mesh endpoints](typed-mesh-endpoints.md) - see [the Mesh](mesh.md).
