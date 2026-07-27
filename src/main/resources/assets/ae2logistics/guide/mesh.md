---
navigation:
  title: Universal Mesh Endpoints
  position: 50
---

# Universal Mesh Endpoints

AE2's P2P tunnels are one-input, many-output, one transport type each. The Universal
Mesh Endpoint generalizes all three limits: any number of endpoints share a **named
frequency**, each with a role (input, output, or both), a priority, and any subset of
transport capabilities - redstone, items, fluids, energy, and signals - on one part,
for one channel.

**Two endpoints on a frequency are a universal point-to-point tunnel. More are a mesh.**

Right-click to configure: type a frequency name, pick a role, toggle capabilities.

# Per-type behavior

- **Redstone** - a wired-OR bus: outputs emit the highest level present at any input.
- **Items and fluids** - anything pushed into an input endpoint is delivered into the
  inventory an output endpoint faces, by priority then round-robin, with batches kept
  whole on one destination.
- **Provider P2P** - point a pattern provider at an input endpoint and it behaves as if
  it were adjacent to every machine on the frequency: each batch goes complete to the
  first machine that has finished its previous batch, and when all machines are still
  working, nothing is pushed until one frees up - true per-machine blocking, at range,
  over one face.
- **Energy** - FE pushed into an input spreads across all outputs by priority.
- **Signals** - the only transport that crosses networks: input endpoints publish their
  network's channels onto the frequency and outputs inject them into their own network,
  so logic graphs can finally span subnets. Bridged values sum; loops are impossible by
  construction (a mesh never re-publishes what a mesh delivered).

Every transfer has a hop budget of one: a mesh delivery can never enter another mesh,
which makes item loops structurally impossible rather than merely discouraged.

- **ME Link** - endpoints with the ME Link capability fuse their networks like a
  multi-point quantum bridge: one elected hub, a virtual star of dense connections, and
  AE2's own pather carrying channels (up to 32 per spoke), power, and grid membership
  through the mesh. Renaming or un-attuning an endpoint cleanly tears its links down.

The mesh never creates channel capacity from nothing - every endpoint costs a channel
and ME links route through AE2's normal pathing rules.

Each endpoint costs one channel and idles at one AE per tick.
