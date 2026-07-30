# Roadmap

Status as of v0.17.0 (2026-07-29). DESIGN.md holds the full rationale; this file tracks
what exists, what is queued, and what is known debt. The gametest suite (94 tests, run
by CI and `make test`) is the source of truth for behavioral claims.

## Done

| Area | Feature | Since |
|---|---|---|
| F1 | Signal key type, ME Register Bank, Signal Card, grid-service store | 0.1.0-0.2.0 |
| F2 | Ten logic parts on a deterministic topological scheduler | 0.2.0 |
| F5 | Stock Sensor, Rate Meter, ME Tracer Terminal (5-min sparklines) | 0.2.0 |
| F5 | ME Job Monitor: CPU/job telemetry as signals (active/idle/stalled/pending + per-named-CPU) - F5 complete | 0.7.0 |
| QoL | Memory cards carry all part settings; stock sensor GUI gained a player inventory | 0.7.0 |
| F3 | Guarded Pattern Provider (plan-time hiding + toggleable push gate) + Guarded Pattern wrappers + priority channels - F3 complete | 0.8.0 |
| Resource | Regulus Crystal: first themed resource, in-world transform (charged certus + redstone + glowstone in water) | 0.8.0 |
| F6 | Query language (mod/tag/name/count/craftable/stored/damage/signal + @refs), replicated library, Query Terminal + Query Sensor + Query Export Bus - F6 complete | 0.9.0 |
| F7 | ME Config Terminal: audit + in-place edit of every configurable grid device, generic setting cycling, priorities, copy/paste-to-all-same-type (first slice) | 0.10.0 |
| F7 | Persistent config snapshots with changed/new/gone diff filter; Config Blueprint region capture/apply item - F7 complete | 0.11.0 |
| F4 | ME Job Scheduler: stock rules with admission control (plan-complete + free class-pool CPU), named-CPU pools respecting Player-Only, guards, rate limiting | 0.11.0 |
| F9 | Adaptive processing patterns: fuzzy/bands/tag/any-of/catalyst + Pattern Workbench | 0.2.0-0.3.0 |
| F11.1 | P2P Frequency Terminal (list, named frequencies, retune) | 0.3.0 |
| F11.2-4 | Universal Mesh Endpoints: 5 transports, mesh + universal P2P + true provider P2P | 0.4.0-0.5.0 |
| F11.3-ME | Mesh-ME grid bridging (virtual quantum-bridge star, 32ch/spoke) | 0.5.0 |
| F11 polish | Stateless P2P names (on-tunnel attachments), mesh rows + rename-all in the terminal, 9-slot endpoint filters (batch-aware), status + cabled-loop diagnostics, /ae2logistics mesh | 0.6.0 |
| Infra | Gametest harness + CI, in-game guide + tablet, generated art pipeline | 0.1.0+ |
| Infra | Hardening: fluid/energy mesh E2E, scheduler completion loop, catalyst execution phase all gametested; /ae2logistics query command; upstream PR drafts in docs/upstream/ | 0.12.0 |
| F8 | ME Logic Core: eight virtual logic entries on the host grid's scheduler, per-entry channel cost, TransferableSettings - F8 complete (virtual storage devices deferred) | 0.13.0 |
| F11.5 | ME Wireless Bridge (coverage-gated grid joining, nearest-AP association, handover) + Dense Wireless Access Point; AE2 WAPs serve bridges - F11.5 complete | 0.14.0 |
| F4 | Scheduler stretch: wall-clock deadlines with eviction, within-pool priority preemption, rules ride TransferableSettings | 0.15.0 |
| Infra | Test debt closed: NBT round-trips for all five BEs, named-CPU pools + per-named-CPU monitor channels (custom-name reflection + cluster updateName); advancements; server config (scheduler interval, WAP range, bridge retune); publishing kit in docs/publishing/ | 0.15.0 |
| F8.2 | ME Subnet Core: whole subnet in one block - face storage/import/export entries, uplink/downlink storage proxies with loop-safe reentrancy latches, quartz-fiber-style overlay power sharing - F8 fully complete | 0.16.0 |
| F11 | ME mesh channel bundling: lane pairing replaces the star (disjoint lanes between carried-grid sides, 32 ch each - AE2 pathing never reroutes around a saturated node, so a hub caps everything at 32); workbench GUI on 200-tall chrome with AE2 widgets | 0.16.2 |
| F11 | Mesh frequencies are network-scoped like AE2 P2P (host grid = carrier, must be online; signals bridge subnets via FACES through the carrier); terminal mesh rows and rename scoped to the terminal's network | 0.16.3 |
| Infra | Entire GUI layer on AE2's own framework: all 14 screens are AEBaseMenu/AEBaseScreen with style docs, chrome composed from AE2's me_chest dialog (scripts/gen_ae2_chrome.py), and AE2Button/AETextField widgets; mesh endpoint placement wireframe matches the P2P chassis | 0.17.0 |

Cut by decision: adaptive smithing/stonecutting patterns (exact-identity recipes have no
fuzziness need). Evaluated and skipped: EMI/REI stack converters (signals have no viewer
representation).

## Next session

1. **PLAYTEST.** Jack is testing when home. Fifteen GUI surfaces, two use-item flows
   (blueprint corners, memory cards), and the bridge anchor click-flow are
   machine-verified and human-untouched; the session should start from his notes and
   fix friction before anything new. Everything programmatic is gametested.
2. Publishing pass if the playtest holds: gallery screenshots (shot list in
   docs/publishing/modrinth.md), then Modrinth + CurseForge uploads.
3. Later: F11.6 tunnel types, F10 bundles go/no-go, upstream PR implementations.

## Longer term
- **F10 bundles** (DESIGN.md 4.3): the last unbuilt design-doc feature. Preconditions
  met; waits on the ecosystem-novelty check (DESIGN 7.3, folded into the publishing
  pass) and a deliberate go/no-go on the atomic-delivery pitch.
- **F11.6 extra tunnel types**: experience is straightforward; signal is now mostly
  superseded by the mesh; entity/player/chunk-load are balance decisions first;
  tick manipulation is a hard no.
- **Foreign-job steering**: everything scheduler-side shipped in 0.15.0; steering
  jobs we did not originate needs an upstream CPU-selection hook (fourth PR
  candidate below).
- **Upstream PRs worth writing**: public grid-connection API for 1.21.1-era AE2 (we
  use internal `GridConnection.create`), dynamic per-node channel demand (would
  unlock per-capability mesh costing and true pooled accounting), a filter-provider
  API so query expressions could drive AE2's own view cells, bus filters, and cell
  partitions (blocked on internals today; the mod stays mixin-free), and a
  CPU-selection hook for foreign-job steering.

## Known debt and constraints

- Grid connections now use the PUBLIC `GridHelper.createConnection(a, b)` - it
  existed in 19.2 all along (docs/upstream/grid-connection-api.md is superseded).
  Remaining internals: AEBasePart/PartItem/NodeListener, the GUI framework
  (AEBaseMenu/AEBaseScreen/InitScreens/StyleManager), ExternalStorageFacade,
  IEnergyOverlayGridConnection/EnergyService, PatternProviderLogic; the 26.x port is
  mostly the client/model layer plus these.
- Catalyst inputs use AE2's container-credit flow: correct for machines that hold or
  return the tool, dupe-prone if a pattern ALSO lists the tool as machine output.
  Documented in the guide; a damage-changing round-trip needs CPU-side fuzzy output
  tracking (upstream).
- Provider blocking through the mesh is always per-machine ("smart"); the provider's
  own blocking toggle is effectively bypassed.
- Mesh rename-all retags loaded endpoints only; endpoints in unloaded chunks keep the
  old frequency until they load. ME sides/lanes (and the cabled-loop diagnosis) are
  computed when a frequency's membership changes, not when fed-network CABLING
  changes - `/ae2logistics mesh relink` forces a re-check after recabling. One lane
  ceils at AE2's 32-channel dense node cap; capacity scales by adding endpoint pairs.
- Endpoint filters match exactly (components included); no fuzzy/tag cards yet.
- Mesh registry is server-global internally but every operation partitions by live
  host grid (frequencies never cross networks); nothing persists beyond part NBT
  (P2P names persist as data attachments on the tunnels' cable-bus block entities).
- Localization is en_us only; art is programmatic 16x16 (see scripts/gen_textures.py -
  run from the repo root).
- Guard flips re-index patterns on a ten-tick fingerprint: a plan computed while a
  guard was open keeps its patterns (the push gate arbitrates after that), and guard
  evaluation is snapshot-free by design. Provider priority orders pattern CHOICE at
  plan time; AE2 round-robins pushes among providers holding IDENTICAL patterns, so
  dynamic priority moves production between different recipes, not copies of one.
- Guarded Pattern wrappers are inert in vanilla providers (documented + gametested);
  enforcement requires the Guarded Pattern Provider.
- One Job Monitor per network per prefix; two on the same prefix double every count
  (multi-writer channels sum by design). Stall detection is progress-freeze polling;
  19.2.x has no job event API for start/finish attribution.
- Queries range over item/fluid keys only - signal keys in storage are excluded by
  contract (a query counting signals feeds back into its own sensor). Query Terminal
  previews cap at 6 sample rows; export bus scans up to 32 matching kinds per
  operation, 8 items per operation.
- Scheduler rules poll every second and re-plan at most every ten; deferred reasons
  stay visible through the retry window. Only jobs the scheduler originates are
  steered or preempted - foreign jobs (players, other mods) still use AE2's own CPU
  selection and are never canceled. Running jobs are tracked by CPU + expected
  output, NOT links: submitJob with a null requester returns no link
  (CraftingSubmitResult.successful(null)) - if two jobs with the same output run on
  the same CPU back to back, the tracker can briefly conflate them. Deadlines are
  wall-clock from submission because AE2's elapsed tracker pauses while a job is
  stalled (exactly when eviction matters).
- Config Terminal writes gate on mayBuild + mayInteract because AE2 19.2.x has no
  security station or permission API (removed upstream in this line); if AE2 regains
  one, gate on it. Session device list is capped at 256 rows; settings detail shows
  the first four settings.
- Untested by automation: GUI interactions only (a human problem). Save/load is
  covered by NBT round-trip tests (saveWithFullMetadata -> loadWithComponents on a
  fresh BE); named-CPU pools and per-named-CPU monitor channels are covered by
  setting AEBaseBlockEntity.customName reflectively + CraftingCPUCluster.updateName.
  Gametest lore: IEnergyService.getStoredPower caches for 90 ticks; a stalled job's
  ElapsedTimeTracker pauses (deadlines must use wall-clock); submitJob with null
  requester returns a null link (track CPU + expected output instead).
