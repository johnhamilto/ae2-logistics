# AE2 Logistics

The operations layer for Applied Energistics 2: signals and logic on your
cables, crafting that follows policy, storage that keeps itself sorted, and
wireless everything, built in AE2's own idiom as parts, terminals, and cards.
No computer mod required.

Where things live, so this file never goes stale:

- [ROADMAP.md](ROADMAP.md) - every shipped feature with its version, plus the
  current test count. The header is the status line.
- [TODO.md](TODO.md) - the working backlog: polish debt and wanted features.
- [DESIGN.md](DESIGN.md) - the full design rationale, feature by feature.
- [docs/publishing/modrinth.md](docs/publishing/modrinth.md) - the player-facing
  pitch (also the CurseForge body).
- The in-game guide documents every device: craft the AE2 Logistics Guide
  tablet, or hold G over any of our items.

## Trying it

`make client`, create a creative world, then `/ae2logistics testworld` builds
interactive demo plots for every major system in a signposted row. Or start
from the guide and build up: signals and logic parts, the Job Scheduler and
Monitor, adaptive patterns, queries, the Config Terminal, mesh endpoints and
the wireless family, and the Gated Storage Bus with its input cards.

Commands (permission 2): `/ae2logistics signal ...`, `/ae2logistics mesh ...`,
`/ae2logistics query ...`, `/ae2logistics wireless status`,
`/ae2logistics janitor ...`, `/ae2logistics testworld`.

## Building

Requires `make` and JDK 21 (`brew install openjdk@21`; the Makefile finds
Homebrew's JDK on its own, and any JDK 21 on `JAVA_HOME` works). `make help`
lists every target; the daily ones:

| Command | Does |
|---|---|
| `make check` | Compile and run verification tasks |
| `make test` | Full in-game gametest suite on a headless server |
| `make client` | Dev client with AE2, JEI, Jade, and the compat suite |
| `make guide` | Dev client with hot-reloading guide pages |
| `make data` | Datagen into `src/generated/resources` |
| `make textures` | Regenerate sprites from `scripts/gen_textures.py` |
| `make build` | Assemble the jar into `build/libs/` |

The first build downloads Gradle, NeoForge, and all dependencies. CI runs the
build and the full gametest suite on every push.

## Version targets and branches

- `main`: Minecraft 1.21.1 / NeoForge / AE2 19.2.x. Version pins live in
  `gradle.properties`.
- `mc-26.1`: the forward port to Minecraft 26.1 (NeoForge 26.1, AE2 26.1
  betas). It compiles and passes the full suite; `docs/PORT-26.1.md` on that
  branch tracks what remains before release. Features land on `main` first and
  merge forward.
- Jars are named `ae2logistics-<minecraft>-<modversion>.jar` so builds from any
  branch self-identify.

## Layout

`src/main/java/.../ae2logistics/` holds the mod sources, grouped by system
(`signal/`, `mesh/`, `query/`, `parts/`, `crafting/`, `menu/`, `client/`,
`gametest/`, `testplots/`, ...). Assets are largely generated pixel art; see
`scripts/gen_textures.py` and DESIGN.md for the visual language. `CLAUDE.md`
describes the testing layers (gametests, test plots, structure templates) and
the compat suite. Read-only reference checkouts of AE2 and friends live outside
the repo in `../ae2-reference/`.

## License

LGPL-3.0, matching upstream AE2.
