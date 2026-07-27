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

# Filters

Each endpoint has nine whitelist slots: click one with an item (or a bucket, to filter
the contained fluid) to set it; click with an empty hand to clear. An empty filter
allows everything. An **input** endpoint refuses non-matching insertions outright; an
**output** endpoint is skipped as a destination for stacks its filter rejects - so one
frequency can sort, with each machine declaring what it takes. Provider batches respect
filters as a whole: a batch only lands on a machine whose filter accepts **every**
ingredient in it, and moves complete to another machine otherwise. Matching is exact
(components included); filters do not apply to redstone, energy, signals, or ME.

# Diagnostics

The endpoint's own screen shows a live readout: how many endpoints share the frequency,
this one's ME star role (hub or spoke), and a status. The status also appears per
endpoint in the P2P Frequency Terminal's mesh rows.

**CABLED LOOP** means the mesh link runs parallel to a physical cable path between the
same networks. AE2 tolerates the loop, but redundant paths are the classic
half-a-base-offline debugging trap - remove the cable or the extra endpoint. Loops are
detected when a frequency's links (re)build; `/ae2logistics mesh relink <frequency>`
forces a rebuild and a fresh diagnosis.

For servers and bug reports: `/ae2logistics mesh list` prints every frequency with
endpoint counts and capabilities, and `/ae2logistics mesh status <frequency>` prints
each endpoint's position, dimension, role, and status.

Each endpoint costs one channel and idles at one AE per tick.
