# Roadmap

Status as of v0.43.0 (2026-08-13). DESIGN.md holds the full rationale; this file tracks
what exists, what is queued, and what is known debt. The gametest suite (140 tests, run
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
| F8.2 | Subnet port entries: the core exposes the INTERNAL grid on chosen faces (cable real ME devices onto the subnet); per-entry filter UI scoped to filterable types; uplink/downlink renamed from main/to main | 0.19.0 |
| Design | Subnet Core REMOVED (0.16.0-0.20.0) in favor of the Subnet Link: the link IS a storage bus subclass (AE2's GUI, cards, partition, priority) targeting the subnet; settings AUTO-APPLY everywhere (Apply buttons removed, debounced field sends) | 0.21.0 |
| F8.3 | ME Subnet Link part: quartz fiber + empty interface + storage bus in one - the face carries a REAL subnet (build with normal AE2 devices), power passes through, storage window with mode (subnet sees main / main sees subnet / both, loop-safe), priority + 9-slot filter | 0.20.0 |
| QoL | Guide knowledgebase (page per device + AE2 help button), right-click reverse on cycle buttons, JEI ghost drag onto filter/ghost slots | 0.18.0-0.18.1 |
| Infra | Entire GUI layer on AE2's own framework: all 14 screens are AEBaseMenu/AEBaseScreen with style docs, chrome composed from AE2's me_chest dialog (scripts/gen_ae2_chrome.py), and AE2Button/AETextField widgets; mesh endpoint placement wireframe matches the P2P chassis | 0.17.0 |
| F11 | Typed Mesh Endpoints: six single-transport part items sharing the endpoint class (mask locked by item id, pre-attuned on placement, per-type recolored chassis); fuse all six to craft the universal; provider P2P remains built into item/fluid inputs | 0.22.0 |
| F11.6 | Provider is its own transport, key-type agnostic (provider target resolution = ME_STORAGE cap else external storage strategies - companion-mod chemicals/flux ride through): Provider P2P Tunnel on AE2's own P2P system (attunes with a pattern provider, memory-card linking, terminal rows) + Provider Mesh Endpoint as the seventh typed part; shared batch router with per-machine blocking and a one-hop guard | 0.23.0 |
| F11.6 | Provider P2P Tunnel re-architected as invisible replica providers: each output face registers a stateless ICraftingProvider mirroring the input face's real provider (patterns/priority/blocking read through live), AE2's crafting service schedules across replicas as if the provider sat beside every machine; pushes prefer crafting machines (assembler patterns cross); output faces expose the RETURN path (generic internal inv keeps returns key-type agnostic, plus plain item/fluid caps) so machines return results through the tunnel | 0.24.0 |
| F11 polish | Universal endpoint GUI: transport toggles on an AESubScreen behind the sidebar cog, live endpoint roster streamed into the open menu (MeshRosterPayload); Palette + ScrollingRowList extracted as shared client widgets | 0.24.0 |
| Design | Mesh endpoint 9-slot filters REMOVED (routing is frequency + transport mask only); cabled-loop demoted from alert status to silent skip - an already-cabled frequency doing the right thing is not a diagnosis | 0.24.0 |
| QoL | Part items render on AE2's 3D part/tunnel model bases (was flat sprites); typed-endpoint recipes take any P2P tunnel via the ae2logistics:p2p_tunnels tag, universal endpoint = tunnel + regulus + engineering processor (fuse-all-seven retired), provider tunnel crafted only by attunement; Redstone Port strong/weak emission option (repropagates on flip); Subnet Link window titles as itself; guide tablet item retired (screen help buttons + hold-G reach every page) | 0.24.0 |
| Infra | Interactive test plots (@TestPlotClass scan + /ae2logistics testworld builds our row), 8-mod compat suite as runtime-only deps on NeoForge 21.1.247 (CompatGameTests skip when absent), make test crash guard, TestPlotGameTests guards the plot scan + template loading | 0.24.0 |
| Infra | Mesh endpoint polish closed out (art deferred): roster gametests (carrier scoping, self row, typed rows, live config), open-default + tag-recipe-resolution tests, guide pages match the cog sub-screen / roster / tag recipes / retired fuse recipe | 0.24.1 |
| F11.6 | Mesh provider return path, parity with the tunnel's: strict-Output provider endpoints expose the return surfaces (generic internal inv for any key type, plain item/fluid caps, ME storage) forwarding to the frequency's input faces highest-priority-first with spill; input faces expose the push router only (never the generic inv - a provider standing there must not chain into returns); shared ReturnAdapters behind tunnel + mesh | 0.25.0 |
| F11.6 | Assembler crossing VERIFIED end to end (was claimed, untested): a real vanilla crafting pattern reaches a molecular assembler only through the tunnel and the result returns to networked storage, plus a two-step chain (logs -> planks -> sticks) whose intermediate re-enters through the same tunnel. The MAE2-trick TODO closes via the replica route - no input-face ICraftingMachine needed, replicas push into crafting machines via the public ICraftingMachine.of | 0.25.1 |
| F11 polish | Transport tail closed (art deferred): roster streamed LIVE (menu re-pushes on any change - status flips, other players' edits, face blocks), click a roster row to close and flash a pulsing locator box at that endpoint (EndpointHighlighter), self row gets an accent bar; P2P Frequency Terminal onto generatedBackground + ScrollingRowList + shared Palette (ROW/OUT/REMOTE promoted); guide staleness fixed (LOOP status gone) | 0.26.0 |
| Compat | Applied Mekanistics native chemical returns: provider return faces (tunnel outputs + strict-Output mesh endpoints) expose mekanism:chemical_handler via the guarded AppMekReturns bridge (compileOnly deps, classloaded only when present) - Mekanism machines auto-eject into the faces natively; gametested round trip lands hydrogen in the input-side provider's return slots through AppMek's own generic-inv bridge | 0.26.0 |
| F11 polish | Locator affordances: roster rows wash on hover (clickability), clicking prints a gray chat line naming the endpoint's part item + coords + distance ("... is in the Nether/the End/another dimension" for cross-dimension rows, GUI stays open - grids span dimensions, rosters carry each row's level id now) | 0.26.1 |
| Fix | Locator box actually renders through walls and thick: the 0.26.1 no-depth render type never applied its state through the buffer path; replaced with AE2's own overlay scheme - two immediate-mode passes at AFTER_LEVEL with explicit state (LEQUAL bright where visible, GREATER dim ghost where occluded, RenderSystem.lineWidth feeding the line shader's 4px expansion) | 0.26.2 |
| Fix | Locator visibility: translucent filled faces under the outline in both depth passes (a wireframe alone vanishes against busy scenes), brighter cyan, line width scales with the window like vanilla's outline (fixed 4px was sub-vanilla on retina) | 0.26.3 |
| F11 | Terminal actions reach mesh rows (were silent no-ops): Rename works from endpoint rows too (retags the frequency), Mark target remembers a mesh frequency, Retune to target moves the selected same-network endpoint onto it keeping role/priority/transports (MeshRetunePayload anchors auth on the TERMINAL - the endpoint may sit far away or in another dimension, identified by pos+side+level id among loaded endpoints); buttons disable when they would do nothing | 0.27.0 |
| F13 | Variant Card + ME Variant Import/Export Bus (born as "NBT buses"; renamed honest - 1.21 components): a configured item is a TEMPLATE matching same-item candidates that agree on every component the template carries, absent components ignored (a plain book = any enchanted book; a Mending book = exactly-Mending books) - the design pass found stock fuzzy already ignores components for undamageable items (AE2's own comment says so), so template matching is the genuinely new predicate that SUBSUMES ignore-all; one card, four hosts: Gated Storage Bus + Subnet Link partition by template (the gate re-asserts a variant partition on the wrapped handler before every delegated call - the network only reaches the handler through the gate, so stock re-applies lose; remount restores stock when the card leaves) and new Variant Import/Export Bus parts subclass AE2's on the protected doBusWork seam (import mirrors stock with our filter; export expands templates via findFuzzy(IGNORE_ALL) then narrows; Crafting Card ignored in variant mode; our own StackTransferContext since AE2's impl is package-private); Conform+Variant widens the contains-check to item identity; 5 gametests (template semantics incl. absent-component ignores, gated partition, export expansion, import filtering, socket matrix), guide page, DESIGN F13 records the fuzzy finding; V2 gap noted: collection components match by equality, "contains Mending" is future work | 0.43.0 |
| QoL | Mesh endpoint priority moved onto AE2's own convention: the GUI's inline priority box is gone, replaced by the standard top-right priority tab opening AE2's OWN picker - MeshEndpointPart implements IPriorityHost (setPriority rides the same full config path as GUI edits, so frequency membership and ME lanes stay consistent) and the mesh menu moved from vanilla SimpleMenuProvider onto AE2's MenuTypeBuilder/MenuOpener locator plumbing (typed ctor both sides + an initial-data snapshot the client applies over its local part state), which is what lets SwitchGuisPacket flow to the picker and back; gametest pins the host contract (AE2's PriorityMenu constructs against the part, setPriority applies without disturbing the frequency, NBT keeps it) | 0.42.0 |
| QoL | Pattern Import Card works ONLY from upgrade slots, like every other card (the 0.40.0 sneak-click cable-part install was off-convention jank and is dropped by request): AE2WTLib's wireless encoding + universal terminals are the install surfaces; the cable-part terminal has no upgrade slots upstream and the card deliberately does nothing there (revisit if AE2 ever gives terminals upgrade inventories - upstream-wishlist material); the restock gametest keeps full end-to-end coverage without wtlib on the compile path by wrapping the REAL placed part in a host that fakes only the upgrade inventory (IPart-by-delegation, since AEBaseMenu requires a part/BE/item host) - exactly the delta a wireless terminal adds - and pins that the bare part never feeds even with pocketed cards | 0.41.0 |
| QoL | Pattern Import Card is INSTALLED per terminal, not carried (0.39.0's pocket-aura scoping was wrong by request): AE2WTLib's wireless encoding + universal terminals take it as a REAL upgrade card in their item upgrade slots (associations registered when the mod is present; their WETMenu subclasses AE2's encoding menu so the menu path needs zero compat code), and the cable-part terminal - which upstream gives NO upgrade slots of any kind (view-cell slots are exact-item filtered too, verified) - gets a built-in install: sneak-click the part with the card (consumed, stored per cable side on the P2P-names attachment pattern), sneak-empty pops it out, install rides the side under the part; topUp checks host upgrade inventory OR the part install; AE2WTLib joins the compat bench: gated gametest pins the associations + physical slot insertion on the dedicated terminal (the universal one sizes upgrades by merged terminals, noted) | 0.40.0 |
| QoL | Pattern Import Card (blank feeder, spec chosen from the two TODO readings): kept anywhere in the player's inventory, it restocks the open encoding window's blank-pattern slot from network storage in batches of 8 whenever the slot runs empty - a 10-tick player-tick hook plus instanceof on the open menu, so the cable part, the wireless forms, and ExtendedAE's extended terminal (menu subclasses) all work with zero per-host code; extraction runs as the player through the terminal host's own storage view; gametest drives the same topUp seam the hook calls (no card = untouched, batch of 8 lands, stocked slot left alone, empty network = nothing); FIX en passant: janitorRehomesMisplacedStock was timing-flaky (toggled on a fixed 40-tick delay and asserted running; batch load order can leave the grid unformed) - now a thenWaitUntil on the real precondition, grid up AND stock visible, three consecutive full-suite runs green | 0.39.0 |
| Polish | Trace Panel management GUI: empty-hand click on any member opens a window listing the wall's bound traces (live off the block entity's chunk-watcher sync, no menu replication) with per-row remove and clear-all riding one payload into the same master-resolving bind/clear the card clicks use; sneak gestures unchanged; FIX: the Gated Storage Bus menu shipped 0.37.0 without a client screen registration, so opening its GUI crashed - registered on AE2's StorageBusScreen with its own retitling style doc | 0.38.0 |
| F12 | Storage bus input cards on a new ME Gated Storage Bus (a StorageBusPart subclass intercepting only the mount: super's whole discovery/ticking pipeline runs, the mounted storage gets wrapped in an InputCardGate - AE2's stock bus hardcodes which cards it consults, so ours stay deliberately un-socketable there rather than sit inert): Conform Card (accept only keys the target already holds, live contents not a snapshot - seed the chest to configure the bus; empty target inert; Fuzzy widens the contains-check, Inverter flips it into a self-deduplicating collection chest, partition slots intersect), Stack Limiter Card (single-item inserts, whole-inventory scoping - one item sits there until taken, then the next; non-items pass), both also on the Subnet Link; FIX: Subnet Link had no upgrade-card associations registered, so slot validation rejected every card in-game (gametests configured settings directly and never caught it) - both bus parts now register the stock four (fuzzy/inverter/capacity 5/void) + the two input cards; 6 gametests (incl. socket-validation pin: stock bus REJECTS our cards), plot, guide page, generated card/face sprites | 0.37.0 |
| Infra | 26.1-port backports, the patterns worth having everywhere: provider return paths are BUFFERED (machines insert into AE2's own 9-slot PatternProviderReturnInventory - the network is never routed inside a machine's insert callstack, the reentrancy class the old pass-through needed hop guards for and that crashes outright under 26.1 transactions; parts flush on tick, buffered returns persist NBT + drop on break, backpressure moves to the buffer edge with nine slots of elasticity; tunnel wakes its tick via alertDevice, mesh flushes from MeshRegistry.tick; new gametest pins hold/flush/refuse-keeps/full-refuses/NBT); wireless connector gametest scenes each wear their OWN color and never run fluix in-world (fluix pairs with anything, and gametest batches can pack structures within the 16-block range - on 26.1 they DO, and fluix scenes fused unrelated test networks; isolation on main was placement luck) | 0.36.0 |
| F11.8 | ME Wireless Connector: the colored wireless cable part - every color-compatible pair in mutual range (min of the two ranges) grid-connects exactly like laid cable, fluix pairs with anything and the 16 dye colors pair with themselves (dye recolors in place, fluix crystal resets, no GUI by design), 16-block base range + Wireless Boosters on AE2's own WAP curve (click in, empty hand pops out, cap 8, drop on break); plain node flags park the hop in the pathfinder's last strict BFS tier, so wireless NEVER competes with wired routes - gametested both ways (parallel dense trunk carries with the link at 0 channels; a wireless-only island rides the link at exactly its channel count) - while passing 8 channels and consuming none; per-level link registry on the MeshRegistry pattern (membership hash, destroy-then-relay); 5 gametests incl. NBT round-trip + mutual-reach on a new empty20 template, plot, guide page, 17 generated lens sprites + per-color models | 0.35.0 |
| Infra | Storage Janitor + Trace Panel dev-gated: in production both leave the creative tab/JEI and their recipes drop out via a custom ae2logistics:dev_only recipe condition (one FMLEnvironment.production read); registration untouched, so existing worlds and structure templates load everywhere and guide pages stay readable; ungate both when the art + panel management GUI land | 0.34.0 |
| Fix | Non-tunnel chassis parts no longer see-through at the connector: ae2's back2 is transparent at the four frequency windows BY DESIGN (real P2P tunnels composite the frequency-glow layer behind them - our Provider P2P Tunnel does via P2PModels and keeps ae2's back2), so the 8 mesh endpoints + Subnet Link showed sky through the nub; they now wear a generated opaque mesh_back2 (chassis tones through the shared curve, ae2's ME-purple nub kept, windows lit dim cyan); chassis curve one notch darker (0.78/0.80/0.85) per in-game eyeball | 0.33.4 |
| Polish | House chassis: every p2p-chassis part of ours (8 mesh endpoints, Provider P2P Tunnel, Subnet Link - its model already parented the tunnel chassis) wears a light cool-gray version of ae2's chassis so our parts read as ours next to vanilla tunnels, in world and in inventory - a second paletted_permutations source recolors front/sides/back/back2 (16-gray key; the ME-purple connector nub is deliberately absent from the key and stays purple, it really does plug into ME), models override the chassis texture slots the base model exposes, faces keep ae2's bright palettes and pop harder on the gray; one curve in gen_textures.py is the darkness lever | 0.33.3 |
| Polish | Signal mesh face is a real P2P hue swap: a paletted_permutations atlas source (the armor-trim mechanism) recolors ae2's light P2P face into the signal magenta ramp at resource load - we ship two 3x1 palette strips and zero copied art, the sprite lands as ae2:part/p2p_tunnel_light_logistics_signal (verified against vanilla's PalettedPermutations: alpha-blind exact-RGB matching, unmatched pixels pass through, so an upstream palette change degrades to a visible gold face, never a crash); starburst sprite + ASCII grid retired - all seven P2P faces are one glyph in different palettes and signal now genuinely joins the family | 0.33.2 |
| Polish | Mesh faces borrow AE2's own art: the six typed endpoints + Provider P2P Tunnel reference ae2's face textures in their models (p2p_tunnel_item/fluid/energy/redstone/me + pattern_provider) so each transport is instantly recognizable, universal gets a generated rainbow swirl (every transport at once), signal keeps its starburst (the one transport with no AE2 equivalent); seven generated face sprites deleted | 0.33.1 |
| Feature | Trace Panels: in-world signal dashboards - same-facing panel blocks merge by placement into rectangles up to 4x4 (every member independently computes the group, min-corner master owns bindings + 2-minute/1s ring buffers + client sync), bind by clicking with a bound Signal Card (sneak removes, sneak-empty clears, six traces max), BER draws the merged face (dark screen, per-channel sparkline rows + labels) from the master only; panels stitch grid connections to each other explicitly (passive in-world hosts have no driver - found by gametest); formation + cross-member binding + sampling gametested; guide, plot, generated placeholder art | 0.33.0 |
| Feature | ME Storage Janitor: an in-place IO Port for the whole network, external storages included - a run walks every stored kind through a transient buffer and re-inserts via normal routing, so AE2's insert ordering re-decides placement; fixed two-pass runs (placement changes are unobservable through the aggregate API, so loop-until-no-progress cannot be detected), held-buffer guarantees nothing strands, power per kind, pauses unpowered; GUI (Rejigger/Stop + progress), /ae2logistics janitor look-at toggle, guide, plot; gametested rehoming + idempotence | 0.32.0 |
| Polish | EVERY screen on generated chrome: Pattern Workbench, Guarded Provider, Job Scheduler converted (Icon.SLOT_BACKGROUND insets over generated panels, Palette everywhere) - the entire baked-chrome pipeline retired (gen_ae2_chrome.py + assets/ae2/textures/guis deleted); wireless pair, Adaptive/Guarded Pattern items, Regulus Crystal, Subnet Link verified (guides + recipes; patterns are workbench-encoded, no recipe embeds by design); the polish backlog is now sprite art only | 0.31.0 |
| F6/F7 polish | Queries + Config family closed out (art deferred): Query Terminal (saved-list onto ScrollingRowList), Query Sensor, Query Export Bus, Config Terminal (device list onto ScrollingRowList) all on generatedBackground + Palette; panel_200x166 and terminal_236x190 chrome retired (gen script emissions removed) - three baked screens remain (workbench/scheduler/guarded); Config Blueprint guide gained its recipe embed | 0.30.0 |
| F5 polish | Telemetry boards closed out (art deferred): Job Monitor gained the LIVE BOARD - one row per crafting CPU (output item, remaining, crafting/stalled/idle, stalled first), streamed on the mesh-roster pattern (JobBoardPayload, half-second change-only re-push), window grown to 200x190 generatedBackground; Tracer Terminal onto ScrollingRowList + Palette + generatedBackground, sparkline renderer extracted to shared Sparkline (pre-paves the planned in-world Trace Panels); both guides verified, Job Monitor guide documents the board | 0.29.0 |
| F1/F2 polish | Signal & Logic family closed out (art deferred): LogicPartScreen + LogicCoreScreen draw AE2's generated chrome at live size (BackgroundGenerator.draw handles the one-doc-two-sizes case) with Icon.SLOT_BACKGROUND insets per active slot - three baked chrome textures orphaned and removed (logic_sensor, core_panel, mesh_endpoint) with their gen_ae2_chrome emissions; screens onto the shared Palette (MUTED promoted); op notation unified with the guide and core screen (== and *); guides + themed recipes verified for all thirteen family members; Register Bank + Logic Core join the signal-chain plot | 0.28.0 |

Cut by decision: adaptive smithing/stonecutting patterns (exact-identity recipes have no
fuzziness need). Evaluated and skipped: EMI/REI stack converters (signals have no viewer
representation).

## Next session

1. Publishing pass: gallery screenshots, Modrinth + CurseForge uploads.
2. Publishing pass: gallery screenshots (shot list in docs/publishing/modrinth.md),
   then Modrinth + CurseForge uploads.
3. Later: F10 bundles go/no-go, upstream PR implementations.

## Longer term
- **F10 bundles** (DESIGN.md 4.3): the last unbuilt design-doc feature. Preconditions
  met; waits on the ecosystem-novelty check (DESIGN 7.3, folded into the publishing
  pass) and a deliberate go/no-go on the atomic-delivery pitch.
- **F11.6 extra tunnel types**: provider shipped in 0.23.0 (tunnel + mesh transport);
  experience is straightforward; signal is now mostly superseded by the mesh;
  entity/player/chunk-load are balance decisions first; tick manipulation is a hard no.
- **Foreign-job steering**: everything scheduler-side shipped in 0.15.0; steering
  jobs we did not originate needs an upstream CPU-selection hook (fourth PR
  candidate below).
- **Upstream PRs worth writing**: public grid-connection API for 1.21.1-era AE2 (we
  use internal `GridConnection.create`), dynamic per-node channel demand (would
  unlock per-capability mesh costing and true pooled accounting), a filter-provider
  API so query expressions could drive AE2's own view cells, bus filters, and cell
  partitions (blocked on internals today; the mod stays mixin-free), a
  CPU-selection hook for foreign-job steering, and a storage-service insert filter
  so addons can veto placements network-wide (the Sticky Card in TODO.md - claimed
  keys may only land on sticky-carded storages - is blocked on exactly this).

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
- Mesh endpoint routing is frequency + transport mask only; the 0.6.0 9-slot per-key
  filters were removed in 0.24.0.
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
