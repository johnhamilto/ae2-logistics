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

A P2P tunnel that REPLICATES a pattern provider. Point a provider at the input tunnel
and an invisible copy of it logically exists at every output face - the provider
behaves as if it were physically adjacent to every machine behind the outputs:

- **The real provider stays the single source of truth.** The replicas mirror its
  patterns, priority, and blocking mode live; they hold no inventory, save nothing,
  and appear in no UI. Edit patterns in one place, every machine sees them.
- **Each batch lands complete on one machine.** With blocking mode on, replicas whose
  machine still holds pattern ingredients report busy and are skipped - per-machine
  blocking, at range, over one face. AE2's own crafting scheduler distributes work
  across the replicas.
- **Crafting machines work through the tunnel**: a replica pushes to molecular
  assemblers and other crafting machines on its face first, so assembler patterns
  cross the tunnel too - just like a real adjacent provider.
- **Machines return results through their own face**: pushing into the output tunnel
  delivers into whatever the input tunnel faces, as if the machine pushed into the
  provider directly.
- **Key-type agnostic, like a provider itself**: items, fluids, and any key type a
  companion mod registers (Mekanism chemicals via Applied Mekanistics, flux, ...)
  push through the same way. With Applied Mekanistics installed, output faces also
  speak Mekanism's own chemical capability, so Mekanism machines auto-eject their
  results straight into the tunnel.
- **Same network only**: like a physical provider, patterns serve the network the
  provider is on - the tunnel does not bridge patterns across networks.

This is a standard AE2 P2P in every other way: it carries a frequency on its network,
links with a memory card (shift-click the input to store, click outputs to bind), shows
up in the <ItemLink id="p2p_frequency_terminal" />, and attunes in place - right-click
any P2P tunnel while holding a pattern provider to convert it.

There is no crafting recipe: attune one in place by right-clicking any P2P tunnel
while holding a pattern provider.

Where an Item P2P moves stacks as a dumb pipe, this tunnel replicates the provider
itself: batches stay whole, assembler patterns cross, and the provider's blocking
mode keeps meaning per machine. The
mesh-frequency version of the same behavior is the provider transport of the
[mesh endpoints](typed-mesh-endpoints.md) - see [the Mesh](mesh.md).
