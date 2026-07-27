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
  inventory an output endpoint faces, by priority then round-robin. Transfers made in
  the same tick stick to one destination, so **a pattern provider pushing a batch
  through the mesh lands the whole batch on one machine**, and blocking mode reads the
  destination's inventory through the mesh. Point a provider at an input endpoint and
  scatter output endpoints across your machines - that is provider P2P.
- **Energy** - FE pushed into an input spreads across all outputs by priority.
- **Signals** - the only transport that crosses networks: input endpoints publish their
  network's channels onto the frequency and outputs inject them into their own network,
  so logic graphs can finally span subnets. Bridged values sum; loops are impossible by
  construction (a mesh never re-publishes what a mesh delivered).

Every transfer has a hop budget of one: a mesh delivery can never enter another mesh,
which makes item loops structurally impossible rather than merely discouraged.

ME channels do not travel through the mesh - channel capacity is the one thing this
addon never inflates.

Each endpoint costs one channel and idles at one AE per tick.
