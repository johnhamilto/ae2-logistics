# AE2 Logistics

A control plane for Applied Energistics 2: signals, logic parts, job policy, and
observability, built in AE2's own idiom. The full design rationale lives in
[DESIGN.md](DESIGN.md).

**Status: v0.11.x — the control plane is complete enough to run a base.** Signals are
a first-class AE2 key type computed by ten logic parts on a deterministic
per-network scheduler, watched live on the ME Tracer Terminal, and bridged across
networks by mesh endpoints. The ME Job Monitor turns crafting CPU activity, stalls,
and per-named-CPU progress into ordinary signals — and the Guarded Pattern Provider
closes the control loop: signals hide recipes from the planner, hold running jobs,
and move production between recipes via live priorities. The control tier is built
from Regulus Crystal, formed AE2-style by dropping charged certus, redstone, and
glowstone into water. A query language ties it together: named per-network
expressions (mod:/tag:/name:/count/craftable/damage, with live signals as terms)
edited in a Query Terminal with live results, counted into signals by a Query
Sensor, and exported by a Query Export Bus — the generalized tag-bus. The ME
Config Terminal audits and edits every configurable device on the network in
place — settings, priorities, fleet-wide copy/paste, and persistent snapshots
with a changed-only diff; the Config Blueprint captures a region's device
configuration and reapplies it after a rebuild. The ME Job Scheduler adds the
policy layer autocrafting never had: stock rules with admission control (jobs
that would stall never start), class-pooled CPUs that respect AE2's Player-Only
reservation, guards, and rate limits.
Adaptive Processing Patterns match ingredients by tag, fuzzy identity, damage band,
or explicit alternatives, with catalyst inputs credited back. Universal Mesh
Endpoints carry redstone, items, fluids, energy, signals, and ME itself over named
frequencies — two endpoints are a universal P2P tunnel, more are a mesh, each with
a nine-slot whitelist, and a pattern provider pushing into one gains true
per-machine blocking at range. A P2P Frequency Terminal tames vanilla tunnel
linking with names that live on the tunnels themselves, lists every mesh frequency
with live status (including cabled-loop detection), and renames a whole mesh in one
action. Memory cards carry every part's settings. See [ROADMAP.md](ROADMAP.md) for
whats next and the in-game guide (craft the AE2 Logistics Guide) for how to use
everything. Verified by a 74-test in-game gametest suite on every push.

## Trying it

`make client`, create a world, and craft the **AE2 Logistics Guide** — every system
below has an in-game chapter. The quick tour:

- **Signals** — `/give @p ae2logistics:register_bank`, then
  `/ae2logistics signal set factory:x 500` while looking at it; the channel shows in
  terminals, and a Signal Card puts it in any config slot.
- **Logic parts** — ten cable parts (constant, threshold, hysteresis, arithmetic,
  gates, timers, sensors) computing channels once per tick in dependency order.
- **Observability** — the ME Tracer Terminal graphs any channel; the ME Job Monitor
  turns crafting CPU activity and stalls into channels.
- **Adaptive patterns** — the Pattern Workbench makes processing patterns match by
  tag, fuzzy identity, damage band, or alternatives, with catalysts credited back.
- **Guarded crafting** — the Guarded Pattern Provider hides recipes behind signal
  conditions and holds pushes; Regulus Crystal (charged certus + redstone + glowstone
  in water) prices the control tier.
- **Scheduling** — the ME Job Scheduler keeps stock with admission control, class
  pools, guards, and rate limits.
- **Logic Core** — eight virtual logic nodes as list entries in one block, on the
  same deterministic scheduler; each entry costs a channel, so cores want dense
  cable or a controller face.
- **Subnet Core** — an entire ME subnet in one block: face-bound storage/import/
  export entries, an uplink to main storage, and downlinks that expose the subnet
  to the main network (one main channel each). Loop-safe by construction.
- **Queries** — `tag:c:ores AND NOT craftable` in the ME Query Terminal, counted into
  signals by the Query Sensor, exported by the Query Export Bus; test one with
  `/ae2logistics query <expression>` while looking at a network.
- **Mesh** — Universal Mesh Endpoints carry six transports over named frequencies;
  the P2P Frequency Terminal names, lists, and retunes everything.
- **Fleet config** — the ME Config Terminal audits and edits every device, snapshots
  for diffs; the Config Blueprint reapplies a region's configuration.
- **Wireless** — the ME Wireless Bridge joins machines to the network inside
  Wireless Access Point coverage (AE2's or the Dense WAP's), channels drawn through
  the serving access point, with automatic handover.

Commands (permission 2): `/ae2logistics signal set|get|list|card`,
`/ae2logistics mesh list|status|relink`, `/ae2logistics query <expression>`,
`/ae2logistics wireless status`.

## Building

Requires `make` and JDK 21 (`brew install openjdk@21` — the Makefile finds
Homebrew's JDK automatically; any JDK 21 on `JAVA_HOME` also works). Run `make`
alone to list targets:

| Command | Does |
|---|---|
| `make build` | Compile and assemble the jar into `build/libs/` |
| `make client` | Launch a dev Minecraft client with AE2 loaded |
| `make server` | Launch a dev dedicated server |
| `make data` | Run datagen into `src/generated/resources` |
| `make check` | Compile and run verification tasks |
| `make clean` | Delete build outputs |

The first build downloads Gradle, NeoForge, and all dependencies; expect a few
minutes. CI builds every push via GitHub Actions.

## Version targets and branches

- Target: Minecraft 1.21.1 / NeoForge / AE2 19.2.x. Every version pin lives in
  `gradle.properties`.
- Branch model (mirrors AE2 upstream): `main` tracks the newest supported
  Minecraft line. When the mod ports forward, a maintenance branch named for the
  old line (e.g. `1.21.1`) is cut, and `main` moves to the new target. Fixes
  land on `main` and are cherry-picked back.
- Jars are named `ae2logistics-<minecraft>-<modversion>.jar` so builds from any
  branch self-identify.

## Layout

- `src/main/java/io/github/johnhamilto/ae2logistics/` — mod sources: `signal/`
  (key type and storage), `block/`, `item/`, `command/`, `client/`. Planned
  discipline as the mod grows: pure logic separated from AE2 grid services,
  with the version-sensitive client/parts layer quarantined.
- `src/main/resources/` — assets and data; textures are generated pixel art
  (see `DESIGN.md` for the visual language: dark steel, cyan indicators,
  signal-red accents).
- Reference checkouts (AE2 source at the target branch, Applied Mekanistics,
  version-matched javadoc) live outside the repo in `../ae2-reference/`.

## License

LGPL-3.0, matching upstream AE2.
