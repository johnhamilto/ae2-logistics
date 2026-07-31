---
navigation:
  parent: ae2logistics-index.md
  title: The Mesh
  position: 50
  icon: ae2logistics:mesh_endpoint
---

# Universal Mesh Endpoints

AE2's P2P tunnels are one-input, many-output, one transport type each. The Universal
Mesh Endpoint generalizes all three limits: any number of endpoints **on one network**
share a **named frequency**, each with a role (input, output, or both), a priority, and
any subset of transport capabilities - redstone, items, fluids, energy, signals, ME,
and provider pushes - on one part, for one channel.

**Two endpoints on a frequency are a universal point-to-point tunnel. More are a mesh.**

Like AE2's own P2P, **frequencies never cross networks**: the network the endpoints sit
on is the carrier, and the same frequency name on another network is a different,
unrelated frequency. To span distance, extend the carrier network (cables, quantum
bridges, the ME Wireless Bridge) - not the frequency.

Endpoints come in seven single-transport flavors - the
[Typed Mesh Endpoints](typed-mesh-endpoints.md) - and the universal part that
carries any mix. Craft a typed endpoint from any P2P tunnel, a logic processor,
and an item matching the transport; the universal swaps the transport item for a
Regulus Crystal and an engineering processor:

<RecipeFor id="mesh_endpoint" />

Right-click to configure: type a frequency name, pick a role, toggle capabilities
(typed endpoints show their fixed attunement instead). Typed and universal endpoints
mix freely on a frequency.

# Per-type behavior

- **Redstone** - a wired-OR bus: outputs emit the highest level present at any input.
- **Items and fluids** - anything pushed into an input endpoint is delivered into the
  inventory an output endpoint faces, by priority then round-robin, with batches kept
  whole on one destination.
- **Provider** - its own transport: point a pattern provider at a provider-attuned
  input endpoint and it behaves as if it were adjacent to every machine behind the
  frequency's provider outputs: each batch goes complete to one machine, rotating
  round-robin, and the provider's own blocking mode maps through - with it on, busy
  machines are skipped and pushes wait until one frees up; with it off, the rotation
  never waits. Like
  a provider itself it is **key-type agnostic**: items, fluids, and any companion-mod
  key type (Mekanism chemicals, flux) push through. A provider facing an item-only
  input endpoint still pushes, but as a plain pipe - batches split and blocking loses
  meaning; use the provider transport for provider semantics. The P2P-frequency
  version of the same behavior is the
  [Provider P2P Tunnel](provider-p2p-tunnel.md).
- **Energy** - FE pushed into an input spreads across all outputs by priority.
- **Signals** - bridge subnets THROUGH the mesh: an input endpoint reads the signal
  channels of the network **touching its face**, and an output endpoint injects them
  into the network touching its own face - so logic graphs span subnets, carried by
  your backbone. Bridged values sum; loops are impossible by construction (a mesh
  never re-publishes what a mesh delivered).

Every transfer has a hop budget of one: a mesh delivery can never enter another mesh,
which makes item loops structurally impossible rather than merely discouraged.

- **ME Link** - true ME P2P with quantum-bridge mechanics underneath. An ME-attuned
  endpoint exposes a connection point on its **face**: the network touching that face
  is **carried** through the mesh - fed in on one endpoint, it comes out at every
  other endpoint on the frequency as one grid (channels, power, membership) - while
  the host network the endpoint sits on is **never fused**. Endpoints whose fed
  networks already touch count as one **side**, and the mesh builds parallel
  **lanes** between sides - as many as the smaller side has endpoints. Each lane
  carries up to 32 channels, so channel capacity **bundles**: feed a controller
  network in on two endpoints and pull it out on two elsewhere for a 64-channel
  trunk. Renaming or un-attuning an endpoint cleanly tears its lanes down and
  destroys its carried connection point.

The mesh never creates channel capacity from nothing - every endpoint costs a host
channel, each lane tops out at AE2's 32-channel dense ceiling, and channels route by
AE2's normal pathing. Pathing assigns each device the nearest lane, so spread far-side
cabling between exit endpoints rather than funneling everything through one.

# Diagnostics

The endpoint's own screen shows a live readout: how many endpoints share the frequency,
whether this one owns an ME lane (or sits on standby as a spare on a larger side), and
a status. The status also appears per endpoint in the P2P Frequency Terminal's mesh
rows.

If every fed network on the frequency is already one cabled network, the mesh has
nothing to bridge and quietly builds no ME lane - links would only add redundant
parallel paths, the classic half-a-base-offline debugging trap. Everything keeps
working through the existing cables; `/ae2logistics mesh status <frequency>` notes
"cabled (no ME lane needed)" per endpoint. Sides and lanes are computed when a
frequency's links (re)build; after recabling, `/ae2logistics mesh relink <frequency>`
forces a rebuild.

For servers and bug reports: `/ae2logistics mesh list` prints every frequency with
endpoint counts and capabilities, and `/ae2logistics mesh status <frequency>` prints
each endpoint's position, dimension, role, and status.

Each endpoint costs one channel and idles at one AE per tick.

## Devices

<ItemLink id="mesh_endpoint" />, the seven [Typed Mesh Endpoints](typed-mesh-endpoints.md),
the <ItemLink id="provider_p2p_tunnel" /> (provider semantics on a classic P2P
frequency), and the <ItemLink id="p2p_frequency_terminal" /> that lists and renames
frequencies.
