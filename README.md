# AE2 Logistics

A control plane for Applied Energistics 2: signals, logic parts, job policy, and
observability, built in AE2's own idiom. Pre-alpha; nothing is playable yet. The
full design rationale lives in [DESIGN.md](DESIGN.md).

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
minutes.

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

- `src/main/java/dev/jackhamilton/ae2logistics/` — mod sources. Package
  discipline: `logic/` (pure Java, no Minecraft imports), `grid/` (AE2 grid
  services), `parts/` and `client/` (cable parts, models, UI — the most
  version-sensitive layer), `datagen/`.
- `src/generated/resources/` — datagen output, committed.
- Reference checkouts (AE2 source at the target branch, Applied Mekanistics,
  version-matched javadoc) live outside the repo in `../ae2-reference/`.

## License

LGPL-3.0, matching upstream AE2.
