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
---

# Typed Mesh Endpoints

<ItemGrid>
  <ItemIcon id="mesh_endpoint_redstone" />
  <ItemIcon id="mesh_endpoint_item" />
  <ItemIcon id="mesh_endpoint_fluid" />
  <ItemIcon id="mesh_endpoint_energy" />
  <ItemIcon id="mesh_endpoint_signal" />
  <ItemIcon id="mesh_endpoint_me" />
</ItemGrid>

Single-transport [mesh](mesh.md) endpoints: one each for redstone, items, fluids,
energy, signals, and ME. Each behaves exactly like a
[Universal Mesh Endpoint](mesh-endpoint.md) attuned to that one transport - same
frequencies, roles, priorities, filters, and diagnostics - but the attunement is
**fixed**: the GUI shows what the part is instead of capability toggles.

Typed endpoints mix freely with universal ones on a frequency; the frequency does
not care which part item joined it. Item and fluid endpoints keep the full
provider-P2P behavior (point a pattern provider at an input face) described in
[the Mesh](mesh.md).

Each is crafted like the classic endpoint - an ME P2P Tunnel and a logic processor -
plus one item matching the transport:

<RecipeFor id="mesh_endpoint_redstone" />
<RecipeFor id="mesh_endpoint_item" />
<RecipeFor id="mesh_endpoint_fluid" />
<RecipeFor id="mesh_endpoint_energy" />
<RecipeFor id="mesh_endpoint_signal" />
<RecipeFor id="mesh_endpoint_me" />

Combine all six to fuse their attunements into a
[Universal Mesh Endpoint](mesh-endpoint.md):

<RecipeFor id="mesh_endpoint" />
