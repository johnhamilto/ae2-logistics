# Roadmap

Status as of v0.5.1 (2026-07-26). DESIGN.md holds the full rationale; this file tracks
what exists, what is queued, and what is known debt. The gametest suite (45 tests, run
by CI and `make test`) is the source of truth for behavioral claims.

## Done

| Area | Feature | Since |
|---|---|---|
| F1 | Signal key type, ME Register Bank, Signal Card, grid-service store | 0.1.0-0.2.0 |
| F2 | Ten logic parts on a deterministic topological scheduler | 0.2.0 |
| F5 | Stock Sensor, Rate Meter, ME Tracer Terminal (5-min sparklines) | 0.2.0 |
| F9 | Adaptive processing patterns: fuzzy/bands/tag/any-of/catalyst + Pattern Workbench | 0.2.0-0.3.0 |
| F11.1 | P2P Frequency Terminal (list, named frequencies, retune) | 0.3.0 |
| F11.2-4 | Universal Mesh Endpoints: 5 transports, mesh + universal P2P + true provider P2P | 0.4.0-0.5.0 |
| F11.3-ME | Mesh-ME grid bridging (virtual quantum-bridge star, 32ch/spoke) | 0.5.0 |
| Infra | Gametest harness + CI, in-game guide + tablet, generated art pipeline | 0.1.0+ |

Cut by decision: adaptive smithing/stonecutting patterns (exact-identity recipes have no
fuzziness need). Evaluated and skipped: EMI/REI stack converters (signals have no viewer
representation).

## Next session

1. **Playtest feedback first** - several UI surfaces (part GUIs, workbench, tracer,
   P2P terminal, mesh config) are machine-verified but have never been touched by
   human hands; friction fixes take priority over new systems.
2. **Mesh polish**: mesh frequencies listed in the P2P Frequency Terminal, per-endpoint
   item/fluid filters, and loop/ambiguity diagnostics for mesh-ME (the DESIGN collision
   model - AE2 tolerates loops but half-a-base-offline is its worst debugging story).
3. **Memory card support** for our parts (exportSettings/importSettings currently do
   not carry our custom fields).

## Longer term

- **F5 completion**: job telemetry (craft start/finish/stall as signals, using the
  AE2CC failure taxonomy) feeding the Tracer Terminal.
- **F3 guarded patterns**: signals gating pattern availability. Needs either our own
  provider block or the upstream `ICraftingProvider` extra-requirements hook (AE2
  issue #1761); design before code.
- **F6 query language** and **F7 config terminal**, in that order (F7 wants F6).
- **F4 job scheduler/policy**: admission control is feasible today for jobs we
  originate (submitJob takes an explicit CPU); steering foreign jobs is the open part.
- **F11.5 wireless machine connectivity** via WAP coverage + Dense WAP.
- **F11.6 extra tunnel types** (experience; entity/player/chunk-load are balance
  decisions first).
- **GUI framework migration** to AE2's menu system (clone ExtendedAE as reference) if
  playtesting says the vanilla-plumbing screens fall short.
- **Upstream PRs worth writing**: public grid-connection API for 1.21.1-era AE2 (we
  use internal `GridConnection.create`), and dynamic per-node channel demand (would
  unlock per-capability mesh costing and true pooled accounting).

## Known debt and constraints

- `appeng.me.GridConnection.create` and a handful of other internals (AEBasePart,
  PartItem, PatternProviderBlockEntity in tests) are version-sensitive; the 26.x port
  will mostly be the client/model layer plus these.
- Catalyst inputs use AE2's container-credit flow: correct for machines that hold or
  return the tool, dupe-prone if a pattern ALSO lists the tool as machine output.
  Documented in the guide; a damage-changing round-trip needs CPU-side fuzzy output
  tracking (upstream).
- Provider blocking through the mesh is always per-machine ("smart"); the provider's
  own blocking toggle is effectively bypassed.
- P2P frequency names persist per-terminal, not per-network.
- Mesh registry is server-global and rebuilt live; nothing persists beyond part NBT.
- Localization is en_us only; art is programmatic 16x16 (see scripts/gen_textures.py -
  run from the repo root).
- Untested by automation: fluid/energy mesh forwarding E2E (no reliable vanilla
  fixture; needs a test fixture block), GUI interactions, world save/load cycles,
  catalyst execution phase (planner phase is covered).
