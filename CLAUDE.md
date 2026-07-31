# AE2 Logistics

NeoForge 1.21.1 addon for Applied Energistics 2 (versions in `gradle.properties`).
`make help` lists all dev commands; the Makefile wraps the Gradle wrapper and finds
Homebrew's JDK 21 on its own.

TODO.md is the working backlog (polish checklist + wanted features) - delete entries
as they finish; ROADMAP.md records what shipped.

- `make check` - compile + verification; run after every code change
- `make test` - full in-game gametest suite on a headless server
- `make client` / `make server` - dev instance with the mod, AE2, JEI, Jade
- `make guide` - client with hot-reloading GuideME pages
- `make data` - datagen into `src/generated/resources`
- `make textures` - regenerate sprites from `scripts/gen_textures.py`

## Testing

Three layers. Every new system or behavior change updates the matching layer; a
feature is not done until layer 1 covers it.

**1. Gametests** (`gametest/*GameTests.java`) - automated per-system and
AE2-interaction coverage, run headless by `make test`. Networks are built
programmatically (creative energy cell + `PartHelper.setPart` on cable buses)
inside the `empty5`/`empty12` templates. This is the regression suite; scenarios,
edge cases, and compat behavior all belong here.

**2. Test plots** (`testplots/LogisticsTestPlots.java`) - interactive scenes.
`make client`, creative flat world, then `/ae2logistics testworld` builds ONLY our
plots in a signposted row in front of the player (AE2's `/ae2 setuptestworld` also
spawns them, but hash-scattered among ~40 AE2 plots; its single-plot-id form
tab-completes ours as `ae2:logistics_*`). AE2 scans every mod for
`@TestPlotClass`, so plots register automatically. Constraints:

- `@TestPlot` values must be plain paths - AE2 force-namespaces them via
  `fromNamespaceAndPath("ae2", value)`, so a colon crashes the scan for every mod.
  Prefix ours `logistics_`; they surface as `ae2:logistics_*`.
- `PlotBuilder.part(...)` wants AE2's `ItemDefinition`; wrap our `DeferredItem`
  part items with the local `def(...)` helper.
- Part configs go in the part-customizer lambda (same `applyConfig`/
  `applyMeshConfig` calls the gametests use).
- The plot API lives in `appeng.server.*` (internals, not API) - expect breakage
  on AE2 major updates, and keep plots mirroring known-good gametest scenes.

Add a plot whenever a subsystem gains an interactive surface worth poking; keep
scenes small and legible rather than exhaustive - exhaustive belongs to layer 1.

**3. Structure templates** (`data/ae2logistics/structure/*.nbt`) - hand-built
scenes. Author in-world, then either save via structure block (writes
`run/saves/<world>/generated/ae2logistics/structures/*.nbt`; copy into resources)
or `/test export <name>` (writes `run/gameteststructures/<name>.snbt`, picked up
by name in dev without a build step). Cable-bus NBT carries parts plus all their
GUI settings, so captured scenes keep their configuration. Committed templates
work as `@GameTest(template = ...)` scenes and can be pasted into plots with
`PlotStructures.structure(plot, "x y z", id)`.

`TestPlotGameTests` guards layers 2 and 3: it fails `make test` if the AE2
annotation scan stops finding our plots or template loading breaks.

**Compat suite**: the standard AE2 companion mods (ExtendedAE, MEGA, Applied
Mekanistics + Mekanism, ME Requester, AE2WTLib, Network Analyser, AE2 JEI
Integration) ride in the dev runtime as runtime-only Gradle deps - never real
dependencies. Mod ids live in `compat/CompatMods`. Integration gametests
(`CompatGameTests`) and compat plots gate on `CompatMods.loaded(...)` and treat
absence as skip/sign-pedestal, so everything stays green in a bare environment.
`make test` fails if the gametest server crashes before running tests (a mod
dependency error would otherwise exit 0).

Before releases: drop `build/libs/*.jar` into an established NeoForge 1.21.1
modpack and soak in a real base for cross-mod behavior; rapid iteration stays on
plots and gametests.
