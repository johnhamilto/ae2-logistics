# AE2 Logistics

A control plane for Applied Energistics 2: signals, logic parts, job policy, and
observability, built in AE2's own idiom. The full design rationale lives in
[DESIGN.md](DESIGN.md).

**Status: v0.9.x — the control plane is real, it steers crafting, and it speaks.** Signals are
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
Sensor, and exported by a Query Export Bus — the generalized tag-bus.
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
everything. Verified by a 65-test in-game gametest suite on every push.

## Trying it

`make client`, create a world, then:

```
/give @p ae2logistics:register_bank
/ae2logistics signal set factory:iron_rate 500     (looking at a placed bank)
/ae2logistics signal card factory:iron_rate        (get a bound Signal Card)
```

Wire the bank into a powered ME network and the channel appears in the terminal
with its value as the amount. The Signal Card fills config slots — set a Level
Emitter's filter with it and the emitter thresholds on a computed value instead
of an item count.

Commands (`/ae2logistics signal ...`, permission level 2): `set <channel> <value>`,
`get <channel>`, `list` (all target the bank you are looking at), and
`card <channel>`.

Both items are craftable in survival: the bank from iron, certus quartz, redstone,
and a logic processor; the Signal Card from AE2's basic card plus a redstone torch.

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
