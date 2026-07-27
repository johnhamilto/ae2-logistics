# Roadmap

Status as of v0.7.0 (2026-07-27). DESIGN.md holds the full rationale; this file tracks
what exists, what is queued, and what is known debt. The gametest suite (55 tests, run
by CI and `make test`) is the source of truth for behavioral claims.

## Done

| Area | Feature | Since |
|---|---|---|
| F1 | Signal key type, ME Register Bank, Signal Card, grid-service store | 0.1.0-0.2.0 |
| F2 | Ten logic parts on a deterministic topological scheduler | 0.2.0 |
| F5 | Stock Sensor, Rate Meter, ME Tracer Terminal (5-min sparklines) | 0.2.0 |
| F5 | ME Job Monitor: CPU/job telemetry as signals (active/idle/stalled/pending + per-named-CPU) - F5 complete | 0.7.0 |
| QoL | Memory cards carry all part settings; stock sensor GUI gained a player inventory | 0.7.0 |
| F9 | Adaptive processing patterns: fuzzy/bands/tag/any-of/catalyst + Pattern Workbench | 0.2.0-0.3.0 |
| F11.1 | P2P Frequency Terminal (list, named frequencies, retune) | 0.3.0 |
| F11.2-4 | Universal Mesh Endpoints: 5 transports, mesh + universal P2P + true provider P2P | 0.4.0-0.5.0 |
| F11.3-ME | Mesh-ME grid bridging (virtual quantum-bridge star, 32ch/spoke) | 0.5.0 |
| F11 polish | Stateless P2P names (on-tunnel attachments), mesh rows + rename-all in the terminal, 9-slot endpoint filters (batch-aware), status + cabled-loop diagnostics, /ae2logistics mesh | 0.6.0 |
| Infra | Gametest harness + CI, in-game guide + tablet, generated art pipeline | 0.1.0+ |

Cut by decision: adaptive smithing/stonecutting patterns (exact-identity recipes have no
fuzziness need). Evaluated and skipped: EMI/REI stack converters (signals have no viewer
representation).

## Next session

1. **Playtest feedback first** - every UI surface (part GUIs, workbench, tracer, P2P
   terminal, mesh config, job monitor) is machine-verified but has never been touched
   by human hands; friction fixes take priority over new systems.
2. **F3 guarded patterns, design-first** - the next feature tier. Work out the
   provider hook question (own provider block vs upstream ICraftingProvider
   extra-requirements) before writing code.

## Longer term

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
- Mesh rename-all retags loaded endpoints only; endpoints in unloaded chunks keep the
  old frequency until they load. Cabled-loop detection runs when a frequency's ME star
  (re)builds, not continuously - `/ae2logistics mesh relink` forces a re-check.
- Endpoint filters match exactly (components included); no fuzzy/tag cards yet.
- Mesh registry is server-global and rebuilt live; nothing persists beyond part NBT
  (P2P names persist as data attachments on the tunnels' cable-bus block entities).
- Localization is en_us only; art is programmatic 16x16 (see scripts/gen_textures.py -
  run from the repo root).
- One Job Monitor per network per prefix; two on the same prefix double every count
  (multi-writer channels sum by design). Stall detection is progress-freeze polling;
  19.2.x has no job event API for start/finish attribution.
- Untested by automation: fluid/energy mesh forwarding E2E (no reliable vanilla
  fixture; needs a test fixture block), GUI interactions, world save/load cycles,
  catalyst execution phase (planner phase is covered), per-named-CPU monitor channels
  (no programmatic path to name a crafting cluster in a gametest).
