---
navigation:
  parent: devices-index.md
  title: Typed Mesh Endpoints
  position: 59
  icon: ae2logistics:mesh_endpoint_me
item_ids:
- ae2logistics:mesh_endpoint_redstone
- ae2logistics:mesh_endpoint_item
- ae2logistics:mesh_endpoint_fluid
- ae2logistics:mesh_endpoint_energy
- ae2logistics:mesh_endpoint_signal
- ae2logistics:mesh_endpoint_me
- ae2logistics:mesh_endpoint_provider
---

# Typed Mesh Endpoints

<ItemGrid>
  <ItemIcon id="mesh_endpoint_redstone" />
  <ItemIcon id="mesh_endpoint_item" />
  <ItemIcon id="mesh_endpoint_fluid" />
  <ItemIcon id="mesh_endpoint_energy" />
  <ItemIcon id="mesh_endpoint_signal" />
  <ItemIcon id="mesh_endpoint_me" />
  <ItemIcon id="mesh_endpoint_provider" />
</ItemGrid>

Single-transport [mesh](mesh.md) endpoints: one each for redstone, items, fluids,
energy, signals, ME, and provider pushes. Each behaves exactly like a
[Universal Mesh Endpoint](mesh-endpoint.md) attuned to that one transport - same
frequencies, roles, priorities, and diagnostics - but the attunement is
**fixed**: the GUI shows what the part is instead of capability toggles.

Typed endpoints mix freely with universal ones on a frequency; the frequency does
not care which part item joined it. The **Provider Mesh Endpoint** carries
pattern-provider pushes with per-machine batches, honoring the provider's own
blocking mode - and, like a provider,
it is key-type agnostic (companion-mod chemicals and the like ride through), and
machines on Output faces return results back through the mesh to the provider. The
frequency-scoped cousin of the [Provider P2P Tunnel](provider-p2p-tunnel.md); details
in [the Mesh](mesh.md).

Each is crafted from any P2P tunnel and a logic processor, plus one item matching
the transport:

<RecipeFor id="mesh_endpoint_redstone" />
<RecipeFor id="mesh_endpoint_item" />
<RecipeFor id="mesh_endpoint_fluid" />
<RecipeFor id="mesh_endpoint_energy" />
<RecipeFor id="mesh_endpoint_signal" />
<RecipeFor id="mesh_endpoint_me" />
<RecipeFor id="mesh_endpoint_provider" />

When one face should carry several transports at once, the
[Universal Mesh Endpoint](mesh-endpoint.md) is its own recipe - any P2P tunnel, a
Regulus Crystal, and an engineering processor:

<RecipeFor id="mesh_endpoint" />
