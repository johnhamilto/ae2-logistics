# ME Control Plane — AE2 Addon Design Doc

**Status:** Draft / ideation
**Target:** Applied Energistics 2, Minecraft 1.21.1, NeoForge (see §7 for version caveats)
**Author:** Jack
**Last updated:** 2026-07-24

---

## 0. Thesis

AE2 has a **storage plane** (what you have, where it lives, how it moves) and an
**execution plane** (crafting CPUs, pattern providers, molecular assemblers). It has
almost no **control plane**. The only native conditional primitive in the entire mod is
the Level Emitter, which produces a boolean redstone signal from a single threshold on a
single key.

Everything downstream of that — sequencing, hysteresis, rate limiting, conditional
routing, job policy, observability — is either impossible, or is solved by escaping into
another mod entirely (CC:Tweaked, Integrated Dynamics, redstone spaghetti).

The existing addon ecosystem has almost uniformly attacked a different axis: **more slots,
bigger cells, faster buses, wider terminals** (ExtendedAE, ExtendedAE-Plus, MEGA Cells,
patternbetter). That axis is saturated. The control-plane axis is empty.

**Design goal:** make network state programmable *in AE2's own idiom* — parts on cables,
upgrade cards, terminals, keys in storage — rather than by bolting on a foreign computer.

> **As-built status (2026-07-28, v0.15.0).** The architecture bet held everywhere it was
> tested. Shipped and CI-verified (87 in-game gametests): **F1** signals + Register Bank
> + Signal Card; **F2** ten logic parts on a deterministic topological scheduler;
> **F3 complete** - Guarded Pattern Provider (plan-time hiding + toggleable push
> gating, both layers) and Guarded Pattern wrappers, with dynamic priority bound to
> signal channels; **F4 complete** - Job Scheduler with admission control, class
> pools, wall-clock deadlines with eviction, and within-pool priority preemption
> (foreign jobs never touched); **F5 complete** - Stock Sensor / Rate Meter / ME Tracer Terminal
> with five-minute history, and job telemetry via the ME Job Monitor (crafting CPU
> activity, stalls, and per-named-CPU detail as signal channels); **F9** adaptive
> processing patterns (fuzzy, damage bands, tags, any-of, catalyst) + Pattern
> Workbench (smithing/stonecutting variants cut by decision); **F11.1** P2P Frequency
> Terminal with tunnel-resident frequency names; **F11.2/3/4** as one Universal Mesh
> Endpoint part (five transports, named frequencies, nine-slot whitelists, true
> provider-P2P with per-machine blocking at range, mesh-ME grid bridging via a virtual
> quantum-bridge star, and status diagnostics with cabled-loop detection); **F8** the
> ME Logic Core - eight virtual logic nodes as list entries in one block, on the same
> scheduler, each requiring a channel (the deliberate hard gate); **F11.5** wireless
> bridging - the ME Wireless Bridge joins machines through WAP coverage (AE2's WAPs
> serve too) with the Dense WAP as the carrier tier. Memory
> cards carry every part's settings; **Regulus** is the mod's themed resource
> (in-world transform, prices the control tier). Current status, queue, and known debt
> live in ROADMAP.md; per-feature notes below are marked "As built".

---

## 1. API foundations

Everything below is grounded in AE2's documented addon API (`API.md`, `appliedenergistics.github.io/javadoc`).

### 1.1 Registration extension points

| Class | What it buys us |
|---|---|
| `appeng.api.stacks.AEKeyTypes` | Register custom storage types alongside `AEItemKey` / `AEFluidKey`. **This is the keystone of F1.** |
| `appeng.api.networking.GridServices` | Register our own grid-wide services. Backbone for F4, F5. |
| `appeng.api.features.P2PTunnelAttunement` | New P2P tunnel attunement items. |
| `appeng.api.storage.StorageCells` | Custom storage cell items. |
| `appeng.api.features.GridLinkables` | Items linkable to a grid via the security station (wireless tooling). |
| `appeng.api.features.Locatables` | Locate quantum bridges by key regardless of position. |
| `appeng.api.client.StorageCellModels` | Cell model customization in drives / ME chests. |

These registries are thread-safe and callable from the mod constructor, but mutating them
after mod init is undefined behaviour.

### 1.2 Grids, nodes, ticking

- Grids are **server-side only**. Nothing in the logic layer may assume a client-side grid;
  all UI is snapshot-and-sync.
- `IManagedGridNode` handles node lifecycle: `create` on first tick, `destroy` on removal or
  chunk unload, `loadFromNBT` **before** `create`, `saveToNBT` on write.
- **Virtual nodes** do not auto-connect to neighbours and exist specifically so that addons
  can "build ME networks outside the normal world." Connections are made explicitly with
  `IGridHelper.createGridConnection(IGridNode, IGridNode)`; removing a connection means
  destroying the node. **This is the whole basis of F8.**
- `ITickManager` gives us: ticking without being a tickable block entity, variable tick
  rates, sleeping devices that run out of work, and waking sleeping devices on events such
  as neighbours changing. Participation is via the `IGridTickable` **node service**.

  Practically: this is an event loop with backpressure, for free. Logic parts should sleep
  aggressively and be woken by storage watchers, not poll.

### 1.3 Storage and crafting

- `IStorageService` (`IGrid.getStorageService`) exposes grid inventory and monitoring.
  Node storage is "mounted" via `IStorageProvider.mountInventories`; re-mount is requested
  with `IStorageGrid.refreshNodeStorageProvider` / `IStorageProvider.requestUpdate`.
- **Stack watching only reports keys whose stored amount changed** — not amounts. Amounts
  reported to watchers were historically unreliable and unused. So F5 must read current
  amounts on wake, not trust a delta payload.
- **Craftables are no longer part of network storage.** Use
  `grid.getCraftingService().getCraftables()`. Filters: `NoOpKeyFilter`, or the convenience
  `AEItemKey.filter()` / `AEFluidKey.filter()`.
- `IPatternDetails` describes a pattern to the autocrafting system;
  `IPatternDetailsDecoder` lets a mod decode its own pattern items.
  `PatternDetailsTooltip` (with `addInput`, `addInputsAndOutputs`) renders them.

### 1.4 Parts, upgrades, rendering

- Custom cable parts: implement `IPartItem`; extend `AEBasePart`. **On the 19.2.x (1.21.1)
  target, part models are static:** register every model `ResourceLocation` up front via
  the `PartModels` registry, and the part reports its currently-active set through
  `IPart.getStaticModels()` returning an `IPartModel` — state toggling is returning a
  different set (AE2's own parts collect theirs with the `@PartModels` annotation).
  Reference: `StorageLevelEmitterPart`. *Correction note: an earlier draft described
  `RegisterPartModelsEvent`, `StaticPartModel.Unbaked`, `PartRenderer` +
  `RegisterPartRendererEvent`, and JSON at `assets/<ns>/ae2/parts/<id>.json` — that is the
  26.x-line API, verified absent from 19.2.x. It becomes relevant only when porting past
  1.21.1.*
- Upgrade cards: `Upgrades#createUpgradeCardItem` produces a card that behaves natively
  (network tool toolbelt insertion, tooltips, right-click insertion). Associate cards to
  machines with `Upgrades.add`, optionally grouping machines under a `tooltipGroup`
  translation key. Machine-side inventories come from `UpgradeInventories.forMachine` /
  `.forItem` (the item variant self-saves to the stack; the machine variant does not — you
  save it from the change callback).

### 1.5 Semantics we're building on

- Pattern providers **push all ingredients of a batch at once**; they cannot push half a
  batch. They push directly from crafting-CPU storage, so they never hold ingredients and
  you cannot pipe out of them.
- Provider → unmodified interface on a subnet: the provider **skips the interface** and
  pushes straight into subnet storage, and critically **won't insert the next batch until
  there is space in the machine**, with blocking mode watching the machine's slots rather
  than the interface's. This is the existing (undocumented-feeling, expert-only) flow
  control mechanism.
- Identical patterns in multiple providers run in parallel; providers round-robin batches
  across their faces.
- Pattern priority is **static**, set per provider via the wrench menu. Higher priority
  patterns win unless the network lacks the ingredients for them.
- Lock Crafting already supports redstone conditions and "until the previous result is
  inserted into this provider." This is the closest thing to a native conditional in the
  crafting path and F3 should extend it rather than fight it.

---

## 2. Prior art (explicit non-goals)

Do not rebuild:

| Mod | Covers |
|---|---|
| **ExtendedAE** | 36-slot pattern provider/interface, ×8 import/export buses, extended (wired + wireless) pattern access terminal, pattern modifier, tag/mod-filtered export & storage buses, precise per-slot export bus. |
| **ExtendedAE-Plus** | Tiered crafting accelerators (4×–1024×), parallel processing units, wireless transceiver / channel cards, entity accelerators, smart blocking, pattern multiplication. |
| **patternbetter** | Pattern quantity multipliers (×2 ÷2 ×5 ÷5 ×10 ÷10), page jump, one-click upload, per-pattern multiplier editing, smart balancing across a recipe chain. |
| **ME Requester** | Keeping items and fluids in stock with configurable batch sizes; central stock-management terminal. |
| **Recursive AE2 Pattern Provider** | Auto-generating dependency patterns to configurable recursion depth. |
| **Advanced Peripherals (ME Bridge) / AE2CC Bridge** | Lua scripting against the ME system: item listing, craft scheduling, and a job lifecycle with `crafting_started` / `crafting_done` / `crafting_cancelled` plus typed failure states (no plan, no CPU, CPU busy/offline/too small, missing ingredient). |
| **Open Energistics** (1.16.5 only) | OpenComputers network as an AE2 crafting subnet, so Lua services a pattern callable from a terminal. Conceptually the closest ancestor of F3/F4 — and abandoned. |
| **Applied Mekanistics** | The canonical worked example of a third-party `AEKeyType` (chemicals). Read its source before writing F1. |

The through-line: everything is either **capacity/throughput** or **escape to Lua**.
Nothing is native logic.

---

## 3. Feature specifications

### F1 — Signal keys (`AEKeyType`)

**Problem.** There is no way to represent a *number* in an ME network. Every value that
matters (a threshold, a counter, a rate, a setpoint) lives in a GUI field on exactly one
block and cannot be read, displayed, transported, or compared anywhere else.

**Design.** Register a custom `AEKeyType` — `signal` — whose keys identify a named channel
(namespaced id + optional label) and whose `GenericStack` amount is the value. Because
nearly all of AE2's interfaces are generic over `AEKey`, this makes signals immediately
visible to:

- ME Terminal (as searchable, sortable entries)
- Storage Monitors / Conversion Monitors (a wall-mounted numeric readout, free)
- Level Emitters (native thresholding on a computed value, free)
- Export bus filters, view cells, storage buses, priority — all free
- Storage cells (a "register file" you can physically move between networks)

**API surface.**
- `AEKeyTypes.register(...)` in the mod constructor.
- `AEKeyType`: amount-per-unit and unit symbol drive amount formatting automatically
  (fluids use 1000 / "B"); signals want 1 and no symbol.
- Serialization: `toTagGeneric` / `AEKey.fromTagGeneric`; packets via `AEKey.writeToPacket`
  / `AEKey.readKey`.
- Rendering in AE2 UIs: `IAEStackRenderHandler`.
- JEI: implement an `IngredientConverter` and register it so signals behave in recipe
  transfer and R/U keybinds.

**Semantics to pin down (open questions).**
- Signals must **not** be storable in ordinary item cells by accident, must **not** count
  toward storage bytes in a way that feels exploitable, and must **not** be craftable
  targets. Probably: signals only mount from a dedicated "Register Bank" storage provider.
- Signed values. `GenericStack` amounts are `long`; negative amounts are almost certainly
  not expected anywhere in AE2. **Decision: signals are non-negative; offset-encode if
  needed.** Verify against `MEStorage` implementations before committing.
- Insert/extract semantics are wrong for a register. Writing a signal is *assignment*, not
  *insertion*. Either (a) implement `MEStorage` with clobber-on-insert, or (b) forbid
  external insertion entirely and only allow logic parts to write. **(b) is safer.**

**Risk.** This is the load-bearing decision of the whole mod. If signal-as-key turns out to
fight AE2's storage invariants, F2/F5 fall back to a private grid service with bespoke UI —
much more work, much less elegance. **Prototype F1 before committing to anything else.**

> **As built.** The spike passed in-game: signals display in terminals, and Level
> Emitters/Storage Monitors accept them via a Signal Card (`ContainerItemStrategy`) since
> config slots fill from held items. Values live in a `SignalGridService` (one per grid,
> mounted once via `addGlobalStorageProvider`) — banks are persistence anchors and
> interaction points, not mount points, which resolves multi-bank ambiguity by
> construction. Option (b) held: MEStorage's default insert/extract refuse transfers.
> One correction to §1.3's assumption set: 19.2.x has no storage change-notification API,
> but none is needed — `StorageService` re-reads mounted storage every tick and diffs.

---

### F2 — Logic parts

**Problem.** Level emitters are the only conditional. They chatter at thresholds, can't
compare two values, can't count, can't delay, and can't express "start when below 1k, stop
when above 10k."

**Design.** A family of cable parts, each an `IGridTickable` node that reads zero or more
signal/item/fluid keys and writes one signal key. Small, composable, no scripting.

Proposed set (v1):

| Part | Behaviour |
|---|---|
| **Threshold** | `out = in ⋈ constant`, `⋈ ∈ {<, ≤, =, ≥, >}`. Level emitter, generalized to any key type, outputting a signal instead of redstone. |
| **Hysteresis** | Two setpoints. `out` latches high below `low`, latches low above `high`. **The single most requested behaviour that AE2 lacks.** |
| **Arithmetic** | `out = a ⊕ b` for `⊕ ∈ {+, −, ×, ÷, min, max, mod}`, saturating at 0 and `Long.MAX_VALUE`. |
| **Boolean** | AND / OR / NOT / XOR over 0-or-nonzero. |
| **Counter** | Increments on rising edge of input; reset input; optional wrap. |
| **Timer** | Emits 1 for N ticks every M ticks; or a monostable one-shot. |
| **Rate** | `out = Δ(key) / window`, i.e. items per second of a tracked key. Feeds F5. |
| **Redstone I/O** | Signal ↔ vanilla redstone on the part's face, both directions. |
| **Constant** | Writes a fixed value; the way you set setpoints. |

**API surface.**
- `IPartItem` + `AEBasePart`; static models via the `PartModels` registry;
  `StorageLevelEmitterPart` is the reference implementation for a state-toggled model.
- `IGridTickable` node service. **Sleep by default; wake on storage watcher callback or
  neighbour change.** A 200-part logic graph that polls every tick is a TPS bug report.
- Configuration via upgrade cards where it maps naturally (`Upgrades.add`), GUI otherwise.

**Evaluation model (open question, important).** Naive per-part ticking gives you one
propagation step per part per tick — a 10-deep chain has 10 ticks of latency and
order-dependent glitches. Options:

1. **Per-part, order-undefined.** Simplest. Latency = graph depth. Acceptable for factory
   control, ugly for anything clocked.
2. **Grid-level scheduler.** A `GridService` that topologically sorts the logic subgraph
   each time it changes and evaluates in order within one tick. Cycles get an explicit
   one-tick delay element (like a Factorio combinator). **Preferred.** Costs a graph
   rebuild on every part add/remove, which is fine — that's a rare event.

> **As built.** Option 2, with refinements: the graph is over *channel bindings* (writers
> of X → readers of X), so rebuilds also trigger on part reconfiguration; evaluation order
> is deterministic via a stable per-part key (position+side); cycles are broken at the
> smallest key rather than by an explicit delay part, giving feedback loops an implicit
> deterministic one-tick delay; and multiple writers to one channel **sum, saturating** —
> commutative, so order never matters. All of this is pinned by gametests (same-tick
> chain propagation, +1/tick cycle advancement, multi-writer sums). All nine parts
> shipped, plus a Stock Sensor that writes any stored key's amount to a channel — making
> item/fluid reads a dedicated part rather than extra input plumbing on every part.
> Config GUIs are AE2-styled visually but run on vanilla menus + one payload; migrating
> to AE2's menu framework is deliberate later work.

**Risk.** Scope creep into a visual programming language. Hold the line at ~9 part types;
composition is the feature.

---

### F3 — Conditional patterns

**Problem.** Pattern selection is static: fixed provider priority, and the only dynamic
input is whether the network has the ingredients. You cannot express "use the cheap recipe
unless byproduct X is backed up," "don't run this while power headroom is under 20%," or
"prefer the recipe whose catalyst I actually have."

There is precedent that this is a recognized gap: an AE2 issue proposes adding a method to
`ICraftingProvider` to check for "extra" requirements during simulation, returning what is
missing so the crafting subsystem can *prevent automated jobs from being kicked off and
stalling*, without the subsystem needing visibility into how those requirements are
checked. That is exactly the shape of the mechanism F3 needs.

**Design.** Two layers, shippable independently.

*Layer A — Guarded patterns.* An item that wraps an encoded pattern with a predicate over
signal keys. Implement `IPatternDetails` + `IPatternDetailsDecoder`. If the guard is false,
the pattern reports itself as unusable to `ICraftingService`, so the planner routes around
it instead of stalling.

*Layer B — Dynamic priority.* A provider upgrade card that binds provider priority to a
signal key, so priority becomes a runtime value rather than a GUI constant.

**API surface.** `IPatternDetails`, `IPatternDetailsDecoder`, `PatternDetailsTooltip`,
`ICraftingService`, `ICraftingProvider`. Also `PatternDetailsTooltip`'s decode-failure
strategy so guarded patterns degrade gracefully when the addon is removed.

**Open questions.**
- **When is the guard evaluated?** At plan time only, at dispatch time only, or both? Plan
  time gives correct plans but stale decisions on long jobs; dispatch time gives fresh
  decisions but can strand a plan mid-flight. Probably: plan-time for *feasibility*,
  dispatch-time for *preference*.
- Guard evaluation must be **cheap and side-effect free** — the planner may call it
  thousands of times during a deep simulation.
- Determinism: if guards flip mid-simulation the planner may not converge. Snapshot signal
  values at the start of a planning pass.

**Risk.** Highest of any feature. AE2's crafting planner is the most complex and most
performance-sensitive code in the mod, and it is the thing players will blame when a job
mysteriously fails. Ship F1/F2/F5 first and treat F3 as v2.

> **As built (v0.8.0) - both layers, zero planner changes.** The load-bearing insight:
> patterns cannot see their grid (no API path from IPatternDetails to a network), so
> guards are evaluated by the grid-aware object - the provider. The **Guarded Pattern
> Provider** subclasses AE2's own PatternProviderLogic (which registers itself as the
> node's ICraftingProvider, making our overrides the grid's view of the machine):
> getAvailablePatterns() returns nothing while the provider guard fails and filters
> per-pattern guards individually; pushPattern() refuses while gated (toggleable
> "plan + push" vs "plan only"); getPatternPriority() reads a signal channel live.
> Guard flips re-index through the public updatePatterns() path on a ten-tick
> fingerprint, so the doc's snapshot worry dissolves: plans hold their pattern set,
> and the push gate arbitrates after that. **Guarded Patterns** are wrapper items
> (component: inner pattern + channel/op/constant) that delegate every crafting
> behavior to the wrapped pattern; our provider enforces their guards, a vanilla
> provider crafts them unconditionally (documented, gametested). Guard expressions are
> deliberately one condition - channel OP constant - because F2 logic parts already
> compose arbitrary logic into a channel. Dispatch semantics landed exactly as the
> open question suggested: plan-time for feasibility, push-time for preference.
> Discovered en route: AE2 round-robins push targets among providers holding
> IDENTICAL patterns; priority orders pattern CHOICE at plan time - so dynamic
> priority moves production between different recipes, not between copies of one.
> The tier also introduced **Regulus** (charged certus + redstone + glowstone in
> water, one ae2:transform JSON), the mod's first themed resource.

---

### F4 — Job scheduler / policy layer

**Problem.** Crafting CPU assignment is essentially "first free CPU that's big enough."
There is no notion of job class, budget, priority, preemption, deadline, or admission
control. On a large network one bulk job monopolizes the CPUs and every interactive
request queues behind it.

**Design.** A `GridService` sitting in front of `ICraftingService`, plus a **Scheduler
Terminal**:

- **Job classes**, tagged at request time (interactive / bulk / maintenance), with CPU
  quotas per class.
- **Reservation**: mark CPUs as reserved for a class, so an interactive craft always has a
  CPU available.
- **Admission control**: refuse to start a job whose plan is missing a resource with no
  active production path — i.e. don't start jobs that will provably stall. (Directly the
  motivation in the `ICraftingProvider` issue above.)
- **Deadlines / rate limits** for recurring stock jobs, so restocking spreads over time
  instead of thundering.
- **Preemption** (stretch): pause a bulk job, return its crafting storage, resume later.
  Almost certainly needs to be modelled as cancel-and-replan rather than true preemption.

**API surface.** `GridServices.register`, `ICraftingService`, `IActionSource` (to attribute
requests to a class), `ITickManager`.

**Open questions.** Whether policy can be enforced without forking AE2's CPU selection.
If AE2 selects CPUs internally with no hook, F4 may need to hold jobs *before* submission
(an admission queue) rather than steer them after — which is less powerful but far less
invasive. **Investigate before speccing further.**

**Interaction with prior art.** ME Requester already does stock-keeping with batch sizing;
F4 should *complement* it (schedule requester-issued jobs politely) rather than replace it.

> **As built (v0.11.0).** The open question resolved exactly as suspected: AE2 selects
> CPUs internally for foreign jobs, but `submitJob` takes an explicit CPU - so F4
> holds jobs BEFORE submission and steers the ones it originates. The **ME Job
> Scheduler** block runs four stock rules (target, floor, batch, class, guard
> channel) through a per-rule state machine: plan asynchronously, then admit only
> when the plan is complete (provably-stalling jobs never start - the
> ICraftingProvider-issue motivation, delivered) AND a class-pool CPU is free.
> Pools ride the named-CPU convention (bulk*/unnamed vs maint*), and CPUs set to
> AE2's own Player-Only mode are never taken - the interactive reservation composes
> from upstream instead of fighting it. Attempts rate-limit at ten seconds with the
> defer reason held visible through the wait. Deadlines and preemption remain
> stretch; job classes, reservation, admission control, and rate limiting shipped.
> (One bug for the annals: the rate-limit sentinel `Long.MIN_VALUE` overflowed the
> window subtraction, making the scheduler wait forever - caught by the admission gametest.)

---

### F5 — ME Tracer (observability)

**Problem.** AE2 tells you *what you have* and nothing about *rates*. There is no answer to
"how many iron/sec is this base actually producing," "which pattern stalled and why," "what
is my p99 craft latency," or "which of my 40 subnets is the bottleneck." Every player
debugs this by staring at a terminal and guessing.

This is the feature most likely to be broadly popular, because it requires no change to how
anyone builds — it just makes an existing base legible.

**Design.**

- A **Monitoring Card** / **Tracer part** that subscribes a set of keys to the storage
  watcher and maintains ring buffers of (tick, amount) samples.
- Derived metrics: instantaneous and windowed throughput (per second / minute), net
  production vs consumption, time-to-empty and time-to-full projections.
- **Job telemetry**: start/finish/cancel timestamps, wall-clock duration, and stall
  attribution — which pattern, which missing key, which provider face was blocked. The
  AE2CC Bridge failure taxonomy (no plan / no CPU / CPU busy / CPU offline / CPU too small
  / missing ingredient) is a good starting vocabulary.
- A **Tracer Terminal** with sparklines and a sortable table, plus per-key drill-down.
- Outputs feed back into F1 as signal keys, so a measured rate can drive an F2 threshold
  which drives an F3 guard. **This is what makes the features one mod rather than five.**

**API surface.** `IStorageService` monitoring / stack watchers, `ICraftingService`,
`ITickManager` for sampling cadence.

**Critical constraint.** Stack watching reports only *which keys changed*, not by how much.
Sampling must therefore read current amounts on wake. Sample at a fixed cadence (e.g. every
20 ticks) with event-driven wakeups for edges, and cap tracked keys per part
(configurable, default maybe 16) — an unbounded tracer on a megabase is a memory and TPS
hazard.

**Storage budget.** Ring buffers must be bounded and server-side; the client receives
downsampled snapshots only. Budget the whole feature at a few hundred KB per network.

> **As built (v0.2.0 sensors/tracer, v0.7.0 job telemetry — F5 complete).** The sensor
> half shipped early: Stock Sensor, Rate Meter, and the ME Tracer Terminal with
> five-minute ring-buffer sparklines (64 channels per network, sampled every 20 ticks).
> Job telemetry shipped as the **ME Job Monitor** part, an opt-in poller of
> `ICraftingService.getCpus()`: it drives `<prefix>:active`, `idle`, `stalled`
> (no progress movement for a configurable window, default 10s), and `pending`
> (items outstanding), plus `<prefix>:<name>/remaining` and `/stalled` for every CPU
> cluster the player has named - naming a CPU is the natural opt-in for per-line
> detail, keeping channel cardinality bounded. The doc's event vocabulary (start/
> finish/cancel timestamps, per-pattern stall attribution) collapsed to polling
> because 19.2.x has no job event API; progress-freeze detection turned out to cover
> the useful cases. The scheduler contract grew `writtenChannels()` for this - the
> monitor is the first multi-output logic node, and its channels flow through
> `localCommitted()`, so job stats cross mesh bridges and feed thresholds like any
> other signal. **This closes the F1-F2-F5 loop the doc called the point of the mod.**

---

### F6 — Query language + saved views

**Problem.** AE2's terminal search is a flat string filter. Every filter you build is typed
by hand, lives in one place, and can't be named, reused, composed, or shared. ExtendedAE's
response was to ship one-off *tag* buses and *mod* buses as separate blocks — evidence of
demand, solved by proliferation instead of abstraction.

**Design.** One expression engine, many consumers.

- Grammar: `mod:`, `tag:`, `name:`, `count <op> N`, `craftable`, `stored`, `damage`,
  component/NBT predicates, combined with `AND` / `OR` / `NOT` / parentheses. Signals from
  F1 are first-class terms.
- **Named queries** persisted per-network (a "View" registry) and editable in a terminal.
- Bind a named query to: view cells, export bus filters, storage bus filters, storage cell
  partitions, tracer key sets. One definition, many call sites; edit once, everything
  follows.
- Compile to a `Predicate<AEKey>` compatible with AE2's key filters (`NoOpKeyFilter`,
  `AEItemKey.filter()`, `AEFluidKey.filter()` are the existing shapes).

> **As built (v0.9.0).** One expression engine, three consumers, no mixins. The
> grammar shipped whole minus component/NBT predicates: mod:/tag:/name:, count with
> k/m/b suffixes, craftable, stored, damage, signal(channel) OP N as first-class
> terms, @name inclusion of saved queries (depth-capped), AND/OR/NOT/parens with
> search-bar implicit AND. Hand-rolled recursive descent, ~300 lines, errors with
> positions surfaced live in the editor. Named queries are **replicated across every
> Query Terminal on the grid** (the stateless-names lesson generalized: any one
> survivor preserves the library; edits write through the grid service to all).
> Evaluation runs over `IStorageService.getCachedInventory()` - AE2 already pays for
> the per-tick KeyCounter - through a per-tick QueryContext cached in the grid
> service. Consumers: the **ME Query Terminal** (editor + live results browser), the
> **Signal Query Sensor** (total matching amount onto a signal channel, evaluated in
> scheduler order with same-tick signal() reads - queries feed thresholds feed
> guards), and the **Query Export Bus** (the generalized tag-bus, @name-retargetable).
> The doc's other bind sites - AE2 view cells, bus filters, cell partitions - need
> mixins into terminal/bus internals and moved to the upstream-PR list (a
> filter-provider API). One emergent bug worth remembering: F1 signals LIVE in the
> network inventory, so `stored` matched the sensor's own output channel and fed
> back - queries now range over item/fluid keys only, by contract.

**Open questions.** Filters run in hot paths — compile once to a closure tree, never
re-parse per evaluation. Decide early whether views live on the grid (shared, needs
security-station gating) or on the item (portable, duplicated).

---

### F7 — Network configuration terminal + blueprints

**Problem.** Memory cards are per-block, one at a time. A 200-block network's configuration
is write-only in practice: there is no way to audit "which of my export buses filter on
redstone," or to reapply a known-good subnet layout after rebuilding it.

**Design.**

- A terminal that enumerates every configurable device on the grid (buses, providers,
  interfaces, planes, emitters, our own logic parts) with filters, upgrades, priorities,
  and modes, and lets you **edit them in place**. Sortable and searchable; F6 queries apply.
- **Diff view**: what changed since the last snapshot.
- **Blueprint item**: capture a region's device configuration (relative positions +
  settings) and re-apply it to a matching region. Multi-block memory card.
- Security-station gated — this is a powerful remote-write capability.

**Precedent.** ExtendedAE's Extended Pattern Access Terminal already proves the "one
terminal, all providers on the network" UX pattern works and is popular. F7 is that idea
generalized from patterns to *all* device config.

**Open questions.** Discovery of third-party devices. AE2 has no generic "configurable
device" interface, so v1 covers AE2's own devices plus ours, with an extension registry for
other addons to opt in.

> **As built (v0.10.0, first slice).** The open question dissolved on inspection: AE2
> DOES have generic surfaces - IConfigurableObject (enum settings with
> exportSettings()/importSettings() as string maps and Setting.setFromString for
> cycling), IPriorityHost, and the memory-card exportSettings/importSettings pair on
> every AEBasePart and AEBaseBlockEntity. The **ME Config Terminal** enumerates any
> grid device exposing one of those (AE2's machines and third-party addons that build
> on AE2's bases come along for free - better than an opt-in registry), searches by
> name/type/setting text, cycles generic settings in place, edits priorities, and does
> memory-card-semantics **Copy / Paste / Paste-to-all-same-type** remotely - the
> audit-and-fix workflow. One divergence: the doc's "security-station gated" clause is
> unimplementable because AE2 19.2.x has no security station or permission API at all;
> writes gate on player.mayBuild() + level.mayInteract(terminal), which respects
> adventure mode and protection mods. Deferred to a second slice: diff-since-snapshot
> and the region blueprint item.
>
> **As built, second slice (v0.11.0).** Snapshots landed persistent-in-part (keyed
> type+dimension+position, diffing settings+priority) with changed/new/gone coloring
> and a differences-only filter. The **Config Blueprint** item captures a region
> corner-to-corner - every AE2-based device plus ours via the TransferableSettings
> bridge (our custom BEs do not extend AE2 bases) - and reapplies by relative
> position, part side, and matching type at an anchor. Both gametested round-trip.

---

### F8 — Virtual-node logic core

**Problem.** Subnetworks are the single most powerful technique in AE2 and the single most
tedious: they exist as physical cable, cost space, are fragile to accidental connection,
and are unreadable to anyone who didn't build them.

**Design.** A block containing an entire **virtual** ME subnet, configured in a GUI. Inside:
storage buses, interfaces, and logic parts as *entries in a list*, not blocks in the world.
The block exposes a small number of physical faces that map to configured internal nodes.

AE2 explicitly supports this: virtual nodes don't auto-connect and exist so addons can build
ME networks outside the normal world, wired manually with
`IGridHelper.createGridConnection`.

**Why it matters.** It converts subnet tricks from spatial puzzles into configuration —
which is precisely "expanding interaction." It also composes with F2: a logic graph is much
nicer as a list inside one block than as 40 cable parts.

**Open questions.**
- **Balance.** This trivializes a skill-expression axis some players value. Gate it hard
  behind cost and channel accounting; consider a per-core node budget.
- Node lifecycle: virtual nodes must be created/destroyed on block load/unload with correct
  NBT ordering (`loadFromNBT` before `create`).
- Removing a connection requires destroying the node — so reconfiguration is teardown +
  rebuild, not mutation. Design the UI around apply-on-close, not live editing.

**Risk.** Highest *design* risk (balance, legibility) even though the API risk is low.
Consider shipping this last, or as a config-gated module.

> **As built (v0.13.0).** Shipped as the **ME Logic Core**, deliberately narrower than
> the full virtual-subnet vision: the core hosts up to **eight logic entries** (every
> part evaluator except the world-facing Redstone Port) as non-in-world grid nodes
> wired to the core's dense node with `GridConnection.create` - they join the *host*
> grid's signal scheduler rather than an internal subnet, so one block replaces a wall
> of cable parts with identical semantics (gametested: same-tick chaining, stock
> sensing through the entry's own node, TransferableSettings round-trip). The balance
> gate resolved the doc's open question: physical parts stay channel-free, but **every
> core entry requires a channel** drawn through the core (a full core is nine channels
> of pressure; a glass segment provably cannot light it - gametested). Entries without
> a channel evaluate to nothing. Virtual storage devices with face mapping remain a
> possible slice 2; node lifecycle followed the predicted teardown-and-rebuild shape
> (managed nodes are single-use - fresh node per enable).

---

### F9 — Fuzzy and alternative pattern inputs

**This is the strongest crafting-side idea in the doc.** It is a real, universal,
version-independent gap, unlike ingredient count (§4.2).

> **As built (first slice).** Open question 1 resolved emphatically yes:
> `IPatternDetails.IInput` is public API with `getPossibleInputs()` + `isValid()`, and the
> stock planner honors both at calculation and execution — an end-to-end gametest proves a
> `Tag("#minecraft:planks")` input consuming birch when the pattern was encoded with oak.
> Shipped: Adaptive Processing Pattern (Exact / Fuzzy-ignore-components / Tag specs,
> candidates expanded at decode with the canonical item first, so rule 2 "the planner
> never branches" is inherited from AE2 itself) and the Pattern Workbench, which converts
> vanilla processing patterns by clicking ingredients (gated by a Fuzzy Card in its
> recipe rather than an encoding-terminal upgrade). Since shipped in 0.2.0: `AnyOf`, damage-band
> fuzzy, and `Catalyst` (via `getRemainingKey`/container-item flow), all pinned by a
> generated crafting-tree gametest matrix. **Cut by decision (2026-07-26): smithing and
> stonecutting variants — those recipes have no real fuzziness need.** Still deferred:
> crafting-pattern substitution policy, encoding-terminal integration, JEI ghost-drag.
> Finding for the test harness: crafting calculations require a machine-backed
> `IActionSource` or the planner silently skips all patterns.

**Problem.** Pattern inputs are matched by *exact key identity*, and an `AEItemKey` includes
its data components. Any ingredient carrying NBT is therefore effectively un-autocraftable.

Concretely, AE2's current state:

- **Processing patterns have no substitution at all and always use the exact item.** A
  pattern requiring "a diamond pickaxe" will not match a pickaxe with 1 damage, an
  enchantment, or a custom name. It is a different key.
- **Crafting patterns have substitution**, but it is a coarse on/off toggle derived from the
  vanilla recipe's own `Ingredient` — "enabling substitutions allows things like crafting
  sticks from any plank type," with the guide explicitly warning it "should only be used
  when absolutely necessary." You cannot say *which* plank, or express a preference order,
  or restrict it to a subset.
- **Smithing patterns** are the worst case in pure vanilla: upgrading an enchanted diamond
  sword to netherite requires matching a base item whose components vary per instance.
  Currently impossible to automate generically — you need one pattern per exact sword.
- **Stonecutting patterns** have a single input and the same exactness problem.

Meanwhile AE2 *already has fuzzy semantics everywhere else*: Fuzzy Cards let devices and
tools with filters filter by damage level and/or ignore item NBT — "export all iron axes no
matter the damage level and enchantments, or only export damaged diamond swords" — and they
apply to formation planes, import/export buses, level emitters, storage buses, view cells,
and storage cells.

**So the gap is precise and embarrassing: fuzzy matching exists throughout the storage
plane and is entirely absent from the crafting plane.** F9 closes exactly that gap, and the
whole feature can be framed to players in one sentence: *"Fuzzy Cards, but for patterns."*

#### Design

Replace each pattern input's `GenericStack` with an **ingredient spec**:

| Spec | Meaning |
|---|---|
| `Exact(key, n)` | Status quo. Default; existing patterns migrate to this. |
| `Fuzzy(key, n, mode)` | Reuse AE2's existing `FuzzyMode` verbatim — damage-percentage bands, ignore-all, ignore-components. Same semantics players already know from the Fuzzy Card. |
| `Tag(tagKey, n)` | Matches any item in a tag. `c:ingots/iron`, `minecraft:planks`. |
| `AnyOf([spec…], n)` | **Explicit ordered alternatives.** Preference order is the list order. This is the one nothing else provides. |
| `Component(key, n, filter)` | *Stretch.* Match on specific data components — "any enchanted book with `minecraft:efficiency` ≥ 3". |

Applies to **all four pattern types**, but with different character:

- **Processing** — full support for every spec. Genuinely new capability, biggest win.
- **Crafting** — the assembler still delegates to a real vanilla recipe, so an alternative
  must still satisfy that recipe's `Ingredient`. F9's contribution here is therefore
  **substitution policy**: not *whether* to substitute, but *which* candidate to pick and in
  what order. "Prefer birch, then oak, never crimson." That converts a footgun the guide
  warns you about into a controllable tool.
- **Smithing** — fuzzy/component matching on the **base** input. Highest-value single case
  in vanilla; makes "netherite-ify whatever diamond gear I have" a one-pattern job.
- **Stonecutting** — `AnyOf` on the single input.

#### The planner problem, and the rule that makes it tractable

Fuzzy inputs turn a deterministic crafting tree into a search problem, and AE2's planner is
the most performance-sensitive code in the mod. The discipline that avoids a combinatorial
explosion:

> **Fuzzy for consumption, exact for production. The planner never branches.**

Resolution order for each fuzzy input:

1. **Enumerate candidates present in network storage** and select by policy: user-declared
   preference order first, then most-abundant, then lowest-damage. This is a filtered
   storage lookup, not a search — the common case costs one pass.
2. **If nothing is in stock**, fall back to a *single* designated craftable candidate
   declared in the pattern (the "canonical" candidate). The planner recurses on exactly one
   key, exactly as today. No branching, no backtracking.
3. **Pin the chosen concrete key at plan time.** Pattern providers push all ingredients at
   once, directly out of crafting-CPU storage, so the CPU must reserve a specific key.
   Snapshot at plan time; re-verify at dispatch and fail loudly rather than silently
   substituting.

Rule 2 is the whole design. Without it this feature is a TPS bug report.

#### The catalyst case

A dedicated spec mode: `Catalyst(spec)` — "this input is required and returned, not
consumed." Combined with fuzzy damage matching this solves the classic damaged-tool loop:
the pattern accepts a diamond pickaxe at any damage and returns one at any damage, and the
planner treats it as net-zero rather than as a consumed input that must be re-crafted every
cycle.

Note that AE2's existing advice for catalysts is "don't put it in the pattern at all"
(the guide's processor-automation example explicitly omits the inscriber press because it
stays in the machine). That works only when the catalyst physically remains in the machine.
It fails whenever the tool cycles back through the network — which is exactly the case
players hit and cannot solve.

This is also the same shape as the long-standing AE2 request to let `ICraftingProvider`
report "extra" requirements during simulation so jobs that would stall are never started
(issue #1761). **F9's catalyst mode and F3's guards should share one mechanism.**

#### Interaction / UI — the other half of the feature

The matching engine is worthless if encoding a fuzzy pattern is miserable. In the ME Pattern
Encoding Terminal:

- Modifier-click an input slot to cycle its match mode; each mode gets a distinct slot
  border colour and corner icon so a pattern's matching behaviour is readable at a glance.
- A slot-detail popup showing the resolved candidate set — *"this matches 14 items"* with
  the list — so you find out at encode time, not at 3am when the job stalls.
- Drag-and-drop from JEI/REI to build `AnyOf` lists; drag a tag to create a `Tag` spec.
- **Gating:** give the ME Pattern Encoding Terminal an upgrade inventory via
  `UpgradeInventories.forMachine` that accepts the existing **Fuzzy Card**, unlocking the
  fuzzy modes. Register with `Upgrades.add`. This reuses AE2's own card, its own
  progression, and its own mental model — no new gating item, and the name already tells
  players what it does.
- Crafting-monitor integration: show *which concrete key* each fuzzy input resolved to for
  the running job. Non-determinism that players can't inspect will be reported as a bug.

#### API surface

`IPatternDetails`, `IPatternDetailsDecoder`, `PatternDetailsTooltip` (+ its decode-failure
strategy for graceful degradation), `ICraftingService`, `ICraftingProvider`,
`UpgradeInventories.forMachine`, `Upgrades.add`, AE2's existing `FuzzyMode` enum,
`AEItemKey.filter()` / `NoOpKeyFilter` for candidate enumeration.

#### Open questions

1. **Does `IPatternDetails` already model multi-candidate inputs?** Strong hypothesis: AE2's
   own crafting-pattern substitution must express "any of these keys satisfy this slot"
   somewhere, most likely on an input abstraction with a possible-inputs list and a
   validity check. **If that abstraction is public API, F9 collapses from a planner problem
   into a UI-and-encoding problem and becomes dramatically cheaper.** This is the single
   highest-value thing to verify in the whole doc — check the javadoc for `IPatternDetails`
   and its nested input type before writing any other line of code.
2. **Determinism and player trust.** Two identical requests may consume different concrete
   items. Needs the monitor UI above, plus possibly a "strict" toggle that fails rather than
   picking when the candidate set is ambiguous.
3. **Interaction with blocking mode**, which watches machine slots for ingredients — does it
   compare by exact key? If so it needs to become spec-aware or blocking mode will
   misbehave on fuzzy patterns.
4. **Candidate enumeration cost.** For a `Tag` or ignore-all-components spec on a megabase,
   the candidate set can be large. Cache resolved candidate sets per pattern and invalidate
   on storage change rather than enumerating per planning step.
5. **Tag vs. component matching precedence** when specs overlap.
6. **Cross-addon fluid/chemical keys** — fuzzy semantics for non-item key types are probably
   meaningless; restrict fuzzy modes to `AEItemKey` and allow `AnyOf` / `Tag` for all types.

#### Why this may be the actual flagship

Reconsider the ordering in §5. F9 has properties none of the other features have:

- **It fixes a problem every AE2 player has hit**, not just megabase operators.
- **It requires no new mental model** — "Fuzzy Cards, but for patterns" is the entire pitch.
- **It has no incumbent.** Nothing in §2 addresses it.
- **It is independent of F1.** If the `AEKeyType` spike in Phase 0 fails, F9 is unaffected.

That last point matters a lot: F9 is the natural hedge against the one experiment that could
invalidate the rest of the doc.

---

## 4. The PackagedAuto question

### 4.1 What PackagedAuto actually is

PackagedAuto is an AE2 addon that uses "packages" of items to allow autocrafting with more
than 9 items, with fluid crafting added in 1.18.2+. The workflow: encode two Recipe Pattern
Holders with an Encoder, put one in a **Packager** and one in an **Unpackager**, both on the
ME network. The Packager turns items into Recipe Packages and ejects them into the network;
the Unpackager takes them and outputs items into an adjacent inventory. Both machines
advertise to the ME network what they can do, based on their pattern holder. It's designed
to run without AE2, but AE2 is recommended.

Throughput scales via **Packager Extensions** placed in a ring around the packager, and a
second packager with the same holder parallelizes if connected to ME.

The community framing is worth quoting in spirit: modpacks like Nomifactory involve
automation that "can't be done with pure AE2" — recipes with 10+ ingredients, or
parallelizing machines that need two different inputs. The problem it solves is often
described as **input flow control**, not just ingredient count.

### 4.2 Is the underlying problem still real in modern AE2?

Partly. Let's separate the two.

**Problem A — ingredient count. This is dead.** The 9-input / 3-output figure is stale; it
comes from the 1.18 / 1.19.2 guide pages and has not been true for years. As of the 1.20.1
guide onward, the pattern encoding terminal's **input and output slots scroll, giving 81
distinct ingredients and 26 secondary outputs**. Even the largest Extended Crafting and
GregTech assembly-line recipes fit comfortably.

The residual need is per-machine *integration* (teaching AE2 how to drive an exotic
multiblock), not encoding capacity — and that's already served by targeted mods like
Applied Extended Crafting, which implements seven automation assemblers so Extended
Crafting's full production chain runs under AE2 autocrafting.

**Conclusion: do not build anything whose pitch is "more than 9 ingredients."** That
problem was solved upstream.

**Problem B — input flow control.** *This* is the durable one, and modern AE2 has partially
solved it in a way most players don't know about. The provider → unmodified interface on a
subnet trick means the provider pushes directly into subnet storage and **won't insert the
next batch until there's space in the machine**, with blocking mode watching the machine's
slots. Combined with the fact that a provider must push all ingredients at once, and
directional/flat provider variants for subnet isolation, AE2 already has a native flow
control story.

The trouble is that it's an **expert technique with terrible discoverability** — the guide
literally has a "A Common Misconception" section for people who wire providers into cables
and expect it to work. Nobody discovers the interface-skip behaviour by playing.

### 4.3 So: is there room?

**Yes — but not for a reimplementation. For a reframing.**

A straight "PackagedAuto but native" would be a mod whose value proposition is "you can now
encode 12-ingredient recipes." That's a narrow, pack-dependent win, and it competes with
per-pack integrations that do it better because they know the target machine.

The genuinely open opportunity is a **native "bundle" key type + provider mode**, where the
package is not a new item flowing through a new pair of machines, but a **first-class
concept the crafting system already understands**. Concretely:

- **Bundles as an `AEKeyType`** (same mechanism as F1). A bundle key identifies an ordered
  multiset of stacks. It has an amount, so it stores, transports, monitors, and displays
  through every existing AE2 mechanism with zero new blocks.
- **Pattern provider "bundle mode"** — an upgrade card, not a new block. When set, the
  provider emits its batch as a single bundle key instead of N stacks. The value here is
  **not** ingredient count (see above) — it's that a batch becomes a single indivisible
  object in transit, so it can't be partially delivered, partially consumed, or interleaved
  with another batch in a shared inventory.
- **Unbundling as a storage-side behaviour** — a bus/part that expands a bundle into a
  target inventory, respecting the target's slot layout, with a per-slot mapping encoded in
  the bundle. This is where PackagedAuto's Unpackager lives, but as a *part on a cable*
  rather than a machine with its own pattern holder to keep in sync.
- **The killer simplification: one artifact, not two.** PackagedAuto's real ergonomic tax
  is that you encode *two* pattern holders and keep a Packager and Unpackager in agreement.
  A native design encodes once, in the ME Pattern Encoding Terminal, and the bundle carries
  its own unpack mapping. That alone is a compelling pitch.
- **Flow control as the headline feature, not a side effect.** Sell it as "atomic recipe
  delivery": exactly one batch is in flight, it arrives complete or not at all, and the
  machine can't be half-fed. That's what people actually want from packaging, and it makes
  the subnet-interface trick a checkbox instead of folklore.

**My honest read on demand.** PackagedAuto has ~13.3M CurseForge downloads, so the demand is
demonstrated and large. But most of that is pack-driven (Omnifactory/Nomifactory/E2E) rather
than organic, and the packs that need it are the packs that already ship it. A "native
PackagedAuto" would be competing against an incumbent that works, on the incumbent's turf.

**Where it becomes clearly worth building** is if bundles are designed as a *primitive that
the rest of this mod uses*, rather than a standalone feature:

- Bundles + F2: logic decides *which* bundle to emit.
- Bundles + F3: guarded patterns choose bundle composition at dispatch time.
- Bundles + F5: "atomic delivery" makes stall attribution tractable, because a stall is
  always "bundle N never arrived" rather than "some subset of eight items is stuck
  somewhere."
- Bundles + F8: a logic core with an internal unbundler is a complete machine-feeding
  solution in one block.

**Recommendation: F10 (bundles) is a defensible idea, but it is now the *weakest* of the
crafting-side features and should be sequenced after F1 and F9.** With the ingredient-count
justification gone, its entire remaining value is atomic delivery — real, but a narrower
pitch than it looked. It shares the `AEKeyType` machinery with F1 entirely, so if signal
keys work, bundles are mostly free; if F1 fights AE2's storage invariants, you've learned
that cheaply instead of expensively. **Do not start here, and be honest that F9 below is
the better use of the same crafting-side effort.**

> **Status (2026-07-28):** both sequencing preconditions are long met (F1's key machinery
> is proven twice over, F9 is complete), and every composition partner named above now
> exists — including the F8 logic core. F10 is the last unbuilt feature in this document.
> Whether it is worth building is a demand question, not a feasibility one; it waits on
> the §7 ecosystem-novelty check and a deliberate go/no-go, not on any groundwork.

---

## 4A. F11 — P2P Tunnel Expansion

**Selected direction.** This is the feature set that best fits everything above: it is
pure interaction-and-logic, it has a documented extension point
(`P2PTunnelAttunement`), it composes with F1/F9/F10, and AE2's own P2P system has been
essentially frozen for a decade.

### F11.0 Baseline — what P2P is today

- P2P tunnels move items, fluids, redstone, power, light, and channels around a network
  **without interacting with the network**. They act like portals directly connecting two
  block faces at range, and they are **not bidirectional** — there are defined inputs and
  outputs.
- **One input, many outputs.** Fan-out only.
- **Attunement by item.** Only the ME P2P tunnel is directly craftable; the rest are made by
  right-clicking a tunnel with a representative item: any cable → ME, redstone components →
  Redstone, chest/hopper → Item, bucket/bottle → Fluid, almost any energy-containing item →
  Energy, torch/glowstone → Light. **This idiom is the key to F11.2.**
- **Linking via memory card.** The frequency shows as a 2×2 colour array on the back of the
  tunnel. Shift-right-click generates a new frequency and marks that tunnel the input;
  right-click pastes it and marks that tunnel an output.
- **Known quirks.** ME P2P channels cannot pass through other ME P2P tunnels, and Energy P2P
  takes a 5% tax on FE by increasing its own energy draw.
- **The canonical use** is channel density: 32 tunnels per dense cable × 32 channels per
  controller face = 1024 channels through a single dense cable.

Everything below either removes a limitation in that list or generalizes it.

> **As built (v0.3.0-v0.5.1).** F11.1 shipped as designed (named frequencies persist in
> the terminal part; retune guards input collisions; role flipping is impossible from
> outside AE2 - `setOutput` is package-private). F11.2, F11.3, and F11.4 collapsed into
> one part, the **Universal Mesh Endpoint**: named string frequencies, in/out/both
> roles, priorities, and any subset of {redstone, items, fluids, energy, signals, ME}
> per endpoint at one channel each - two endpoints are the universal P2P, more are the
> mesh. Redstone is the wired-OR bus; items/fluids deliver whole batches with a
> one-hop budget (loops structurally impossible); signals bridge across networks
> through a layered view that can never re-publish mesh input (no feedback). Provider
> P2P works through the ME_STORAGE adapter providers prefer: batch boundaries detected
> in the simulate/modulate stream, each batch routed complete to the first machine not
> still holding its previous batch, refusal when all busy - per-machine blocking at
> range. Mesh-ME diverged from the pooling design: there is no dynamic channel-demand
> API, but ME P2P is grid *connections*, so ME-attuned endpoints form a virtual
> quantum-bridge star (deterministic hub, DENSE_CAPACITY spokes at 32 channels) and
> AE2's pather does the rest. Per-capability channel costing remains future work
> (see ROADMAP.md).
>
> **As built, continued (v0.6.0).** The 0.5.1 note above about names persisting in the
> terminal part is superseded: frequency names now live on the tunnels themselves, as a
> data attachment on each tunnel's cable-bus block entity keyed by part side. Terminals
> are stateless readers (any number agree, none is load-bearing), renaming writes every
> tunnel of the frequency, retuning adopts the destination frequency's name, and old
> per-terminal names migrate automatically. The terminal also lists mesh frequencies
> touching the grid with all endpoints server-wide and renames a whole frequency in one
> action. Endpoints gained nine-slot exact-match whitelists enforced at the input
> handler, at output targeting, and batch-wise for provider pushes (a batch relocates
> whole to a machine accepting every ingredient, verified by re-simulation on switch).
> The collision-diagnosis model shipped as status tracking: per-endpoint OK / offline /
> waiting / CABLED LOOP, the loop case detected at star (re)build when hub and spoke
> already share a grid, surfaced in the terminal, the endpoint GUI, and
> `/ae2logistics mesh list|status|relink`.

---

### F11.1 — Linking overhaul

**Problem.** The memory-card dance is the single worst ergonomic in AE2. Building the
canonical 32-tunnel array means 33 precise shift-click/click operations against a 2×2 colour
code with no labels, no list view, and no way to verify the result except by walking the
network. This is not a new complaint: a 2016 forum request asked for an "Advanced Memory
Card" that could store multiple input tunnels and let you choose which to apply. **Ten years
later nobody has built it.**

**Design.**

- **Named frequencies.** Replace (or rather, alias) the colour array with a player-supplied
  string. Colours stay as the visual, names become the identity.
- **P2P Frequency Terminal.** A network-wide table of every tunnel: frequency, type,
  in/out role, position, dimension, and current throughput. Click two rows to link. Rename,
  regroup, and delete frequencies in bulk. *This is F7 (network config terminal) scoped down
  to P2P, and it is independently shippable — probably the single highest
  value-per-line-of-code item in this entire document.*
- **Array pairing.** Select N input tunnels and N output tunnels (by drag-select in the
  terminal, or by a "linking wand" that accumulates a queue) and pair them in order in one
  action. Turns the 33-click ritual into two clicks.
- **Auto-link mode.** A tool mode where newly placed tunnels inherit the last-used frequency
  and alternate role, so an array can be built by simply placing blocks.
- **In-world visualization.** Holding the tool highlights a tunnel's partners with a beam or
  outline. AE2 Stuff shipped a network visualization tool showing P2P links long ago; this
  is proven UX that never made it upstream.
- **Blueprints.** Capture a tunnel array's topology and reproduce it elsewhere. Shares
  machinery with F7's blueprint item.

**Risk.** Low. This is almost pure UI and is the natural v1.

---

### F11.2 — Universal P2P

**Problem.** Each tunnel variant transports exactly one kind of thing. A machine that needs
items *and* fluids *and* power needs three tunnels, three frequencies, three channels, and
three faces — for one logical connection.

**Design.** A **Universal P2P Tunnel** with an internal *attunement inventory*. Each
attunement item inserted enables that capability on the tunnel: drop in a bucket and it
carries fluids; add a hopper and it also carries items; add a redstone torch and it also
carries signal. This is not a new mechanic — it is the **existing attunement-by-item idiom
generalized from a one-shot transformation into a configurable slot set**, which means it
needs no new player education and automatically inherits every mod-added attunement
registered through `P2PTunnelAttunement`.

Implementation shape: the universal tunnel is a container of sub-tunnels sharing one
frequency and one face. Per capability, it exposes the corresponding handler on its face and
forwards to the matching capability on the paired output.

**The balance question, which is the whole design.** A P2P tunnel costs a channel. If one
universal tunnel carries five types for one channel, it is a 5× capacity buff to AE2's
central constraint, and channel scarcity is the mechanic the entire mod is balanced around.

> **Recommendation: charge one channel per enabled capability.** The universal tunnel then
> wins on *compactness, tidiness, and linking effort* — five capabilities on one face and
> one frequency instead of five of each — without touching the channel economy at all. It
> becomes a quality-of-life and aesthetics feature rather than a power creep feature, which
> is both more defensible and, honestly, what players actually want from it.

Secondary knobs if that proves too generous: a flat power premium, an expensive craft
requiring one of each attunement item, or a cap on simultaneous capabilities.

**Open questions.**
1. **Exclude ME channels from universal mode.** ME P2P channels already cannot pass through
   other ME P2P tunnels, and mixing a grid connection with transport capabilities on one
   face invites cycles and undefined behaviour. Ship without it; revisit later.
2. **Face contention on the output side.** Items and fluids sharing an output face is only
   useful if the destination block accepts both. That's common (Mekanism, GregTech, Create
   machines) but not universal; the terminal should warn when an enabled capability has no
   consumer at the far end.
3. **Does the Energy tunnel's 5% tax apply per-capability or per-tunnel?** Per-capability is
   more consistent; per-tunnel is friendlier. Pick and document.

---

### F11.3 — Mesh P2P (many-to-many)

**The most interesting and most dangerous idea in this section.**

**Problem.** P2P is strictly fan-out: one input, many outputs, unidirectional. There is no
way to express "these twelve points are all on the same bus." Players emulate it today with
sprawling subnets and storage-bus contortions.

**Design.** A frequency in **mesh mode** where every endpoint has a role
(`in` / `out` / `bidirectional`), a filter, and a priority. Anything entering at any input is
distributed to matching outputs by priority, then round-robin. It is a switching fabric with
AE2's existing filter-and-priority vocabulary rather than a new one.

Per-type behaviour differs sharply:

| Type | Mesh semantics | Difficulty |
|---|---|---|
| **Redstone** | Wired-OR bus. Highest signal on the mesh wins. | Trivial, and delightful. |
| **Energy** | Pool all inputs, distribute to outputs by demand. | Easy. |
| **Fluid** | Filtered distribution by priority then round-robin. | Moderate. |
| **Item** | Same, plus the loop problem below. | Moderate–hard. |
| **Signal** (F1) | Named bus for computed values. Composes directly with F2 logic parts. | Easy, high value. |
| **ME channels** | Pooled capacity with collision shutdown. See below. | Hard, but tractable. |

**Why ME channels were initially excluded — and why that was too pessimistic.** An earlier
draft of this doc claimed mesh-ME would create cycles that break channel routing. That
overstated the risk. **AE2 already tolerates loops.** The guide's position is that networks
*should* be treelike or bushlike and that loops and ambiguous channel paths should be
*minimized* — not that they're illegal. The actual documented consequence of ambiguity is a
gameplay hazard, not a crash: if a spot in your system has two possible routes, you may
"find yourself returning home from a mining trip to see half your devices offline."

So the router is already graph-safe. The real problem was never cycles; it was
**non-determinism** — a mesh silently reshuffling which devices get channels.

#### The collision model (preferred design)

Rather than solving this with automatic spanning-tree election, make ambiguity an **explicit,
visible, diagnosable failure**:

> **If two mesh ME output endpoints would deliver channels into the same already-connected
> segment, that is a *collision*. The colliding branch shuts down and reports itself.**

This is the right answer for three reasons:

1. **It matches existing AE2 semantics exactly.** AE2 already shuts devices down on
   over-subscription and ambiguous routing; ad-hoc networks already shut down all
   channel-using devices past 8. A mesh collision is the same class of event, so it needs no
   new player mental model.
2. **It's strictly better than vanilla's version of the same failure.** Today, ambiguous
   routing produces a mystery: half your base is dark and nothing tells you why. A mesh
   collision is *named*, *located*, and *surfaced in the frequency terminal* (F11.1) with the
   two offending endpoints highlighted in-world. We're converting AE2's worst debugging
   experience into a diagnostic.
3. **It sidesteps distributed consensus entirely.** No election, no convergence, no
   reconciliation across chunk loads. Detection is a connected-component query on grid
   topology change, which AE2 already recomputes anyway.

**Detection.** On grid topology change, for each mesh frequency, walk the endpoints and test
whether any two are already in the same connected component via a non-mesh path. If so, mark
the lower-priority one `COLLIDED` and refuse to carry channels through it. Deterministic
tiebreak by explicit user priority, then by a stable position hash, so behaviour is identical
across reloads.

#### Channel pooling — the actually-new capability

This is the part with no vanilla equivalent, and it's the real prize.

Today a ME P2P pair is a **fixed 32-channel pipe between exactly two points**. In mesh mode,
inputs contribute capacity to a **pool** and outputs draw from it **on demand**:

- *n* input endpoints contribute *n* × 32 channels of pooled capacity.
- Output endpoints request only what their downstream devices actually consume.
- Allocation is by explicit per-endpoint priority, then stable position hash. Never
  first-come, which would be load-order dependent and therefore unreproducible.
- Over-subscription drops the lowest-priority endpoints and reports them, identically to the
  collision path above.

The gameplay effect: instead of provisioning 32 channels to a wing of your base that uses
six, you provision a pool and it distributes itself. That is a genuine improvement over
vanilla P2P, not just a convenience, and it turns channel planning from static allocation
into capacity planning — much more interesting, and much more forgiving of base growth
without being *free*.

**Open questions.**
1. Can an addon observe grid topology changes cheaply enough to run collision detection on
   every change? `ITickManager`'s wake-on-neighbour-change is the likely hook, but the
   connected-component query needs to be incremental on large networks, not a full rewalk.
2. Does AE2's channel allocator expose enough to implement pooling, or does pooling require
   the mesh to present itself as a synthetic node with a dynamic channel capacity? The
   latter is probably the tractable path.
3. Interaction with the existing rule that ME P2P channels cannot pass through other ME P2P
   tunnels — mesh must not become a laundering mechanism for that restriction.
4. `/ae2 channelmode` exists and can disable channels entirely; mesh must degrade sanely in
   every channel mode, not just the default.

**The loop problem.** Two `bidirectional` endpoints facing inventories on the same frequency
will shuffle items back and forth forever. Mitigations, all needed:
- An item entering the mesh at endpoint *E* is never delivered back to *E*.
- Per-transfer visited-set, or a simple hop budget.
- Insertion is pull-based from a designated source rather than push-from-anywhere by default,
  with push-from-anywhere as an explicit opt-in.
- The frequency terminal must render the mesh as a graph and flag suspected cycles at
  configuration time, not at 3am.

**Throughput accounting.** A mesh with *n* endpoints must not become a free
*n*-to-*n* transfer bus with one channel's worth of cost. Charge per endpoint, as today —
each tunnel is still a tunnel.

---

### F11.4 — Pattern Provider P2P

**The sleeper hit of this section, and the one that ties F11 back to the rest of the doc.**

**Problem.** Pattern providers push all ingredients of a batch at once, directly out of
crafting-CPU storage, and they round-robin batches across their own faces. Route that
through a plain Item P2P today and you lose all three of the properties that make providers
work:

1. **Blocking mode goes blind.** Blocking mode watches the destination machine's slots; a
   generic item tunnel is not a machine, so the provider sees a tunnel, not the furnace.
2. **Batch atomicity breaks.** A fan-out item tunnel round-robins *items*, not *batches*, so
   one recipe's ingredients can be split across several machines. Every ingredient of a
   batch must land in the same place.
3. **No per-endpoint targeting.** The provider's round-robin is across faces; it has no
   notion of the tunnel's far-side endpoints as distinct destinations.

**Design.** A P2P type that forwards **provider semantics rather than items**. The input
tunnel presents itself to the provider as a valid pattern-provider target; each output
tunnel presents itself to its adjacent machine as a provider face. Then:

- **Batches are atomic across the tunnel.** One batch → one endpoint, all ingredients
  together.
- **Blocking mode reads through the tunnel** to the real destination inventory, so
  "don't send the next batch until this machine has space" works at range.
- **Round-robin is across output endpoints**, so one provider genuinely feeds *n* machines
  scattered across the base — which is exactly the contraption people build subnet towers to
  approximate today.

**Composition.** This is where the document becomes one mod rather than a list:
- With **F10 bundles**, the atomic unit crossing the tunnel is a first-class object rather
  than an implicit convention.
- With **F9 fuzzy inputs**, resolution happens at the provider and delivery happens through
  the tunnel — the two never interfere.
- With **F5 tracer**, per-endpoint throughput and stall attribution become directly
  measurable, which is otherwise impossible in a P2P-heavy base.

**Open questions.**
1. **Is blocking mode's slot inspection reachable through public API**, or does forwarding it
   across a tunnel require reimplementing the check? This determines whether F11.4 is a
   weekend or a month. **Investigate first.**
2. Interaction with the provider's existing directional/flat variants and with subnet
   isolation — a provider P2P is a third way to isolate, and the three must not conflict.
3. What happens when an output endpoint is destroyed mid-batch? Needs an explicit
   return-to-CPU path, not item loss.

---

### F11.5 — Wireless machine connectivity via Wireless Access Point coverage

**Reframed.** The goal is *not* another wireless-channels mod (see the incumbent list below).
The goal is **distribution and logistically complex automation**: machines that participate
in transport without a cable path, using AE2's own already-balanced coverage system as the
enabling condition.

**The core idea.** AE2 already has a spatial coverage mechanic that nobody has ever used for
anything but terminals. A network can have **any number of wireless access points with any
number of Wireless Boosters in each one, letting you optimize power usage and range by
altering your setup — and each access point requires a channel.**

That is already a complete, tuned economy: range costs boosters, boosters cost power,
coverage costs channels, and multiple APs let you shape the covered volume. **Hook machine
connectivity to it and you inherit all of that balance for free**, while retroactively
promoting the Wireless Access Point from a terminal convenience into genuine logistics
infrastructure. You stop placing one AP near your base and start planning cell-tower
coverage for your factory.

**Design.**

- A **wireless P2P endpoint** (or wireless machine adapter part) functions only when it sits
  inside the coverage volume of at least one powered WAP on its network. Outside coverage it
  goes dark, exactly like an unpowered device.
- It carries **transport types — items, fluids, energy, redstone, signals** — not channels.
  This is the differentiator from every incumbent.
- **Channel accounting is the load-bearing balance decision.** A wireless endpoint must still
  consume a channel, drawn *through its serving access point*. So an AP serving *n* wireless
  machines needs *n* channels plus its own. This reuses the existing "requires a channel"
  rule verbatim and — critically — **prevents wireless from becoming a channel bypass**,
  which is the trap every wireless addon falls into.
- **Availability is binary — deliberately.** An endpoint is either in coverage and fully
  operational, or out of coverage and dark. No throughput gradient, no degraded fringe.
  An earlier draft proposed signal strength scaling with distance; that was a mistake.
  **AE2 has no graded failure mode anywhere** — devices have channels or they don't,
  networks are powered or they aren't, tunnels are linked or unlinked. Introducing an
  analogue-valued mechanic would be the only one in the mod, would need its own UI to be
  legible, and would make every "why is this slow" question harder to answer. Binary
  coverage is both more honest to AE2's design language and easier to debug. The interesting
  constraint here is *spatial planning*, which coverage already provides; it doesn't need a
  second constraint layered on top.
- **Association and handover.** In overlapping coverage, an endpoint associates with the
  nearest AP, tiebroken by stable position hash. If that AP loses power or channels, the
  endpoint re-associates to another AP in range. This is free resilience gameplay, and it
  produces exactly the kind of event stream F5's tracer should be recording.

> **As built (v0.14.0).** Shipped as the **ME Wireless Bridge** + **Dense Wireless
> Access Point**. The bridge is a block (not a part - wireless means no cable to sit
> on): anchor it by clicking any access point with the item, place it anywhere, and it
> grid-connects to the nearest active in-range access point *of that network* via
> `GridConnection.create` through the AP's node - so every bridged channel paths
> through the serving AP, which is the balance rule working exactly as designed.
> AE2's own WAPs serve bridges (gametested - the retroactive promotion landed);
> binary availability and sub-second handover on AP loss both gametested. The Dense
> WAP implements AE2's public `IWirelessAccessPoint` (dense carrier, fixed 32 range,
> per-BE adjustable for future boosting). Same-dimension only; association tiebreak
> is position hash as specced. `/ae2logistics wireless status` is the diagnostic.

#### The Dense Wireless Access Point

**Not optional — this is load-bearing for F11.5.** Given the channel rule above, a standard
access point that itself requires a channel can serve very few endpoints before the feature
becomes pointless. A higher-capacity tier is required for wireless machine connectivity to
be usable at all.

**Design.** A full-block **Dense Wireless Access Point** that carries up to **32 channels**
when fed by dense cable, dense smart cable, or an ME P2P tunnel. This maps directly onto
AE2's existing and already-learned hierarchy — normal cable carries 8, dense carries 32, and
the only other devices capable of transmitting 32 are the ME P2P Tunnel and the Quantum
Network Bridge. A dense tier of an existing device that steps 8 → 32 is the most
well-established pattern in the entire mod, so it needs no explanation.

| | Standard WAP | Dense WAP |
|---|---|---|
| Form | Existing block | New full block |
| Feed | Normal cable (8-channel ceiling) | Dense cable, dense smart, or ME P2P |
| Endpoint capacity | Up to 8 | Up to 32 |
| Boosters | Existing inventory | Larger inventory, longer reach, higher draw |
| Serves terminals | Yes | Yes, at greater range |

Notes:

- **It should serve wireless terminals too**, at better range than the standard AP. That
  gives it a reason to exist for players who never touch the machine-connectivity feature,
  which matters a lot for adoption — the block is useful the moment you craft it.
- **Build vocabulary.** Dense APs become macro cells; standard APs become infill for
  awkward corners. That's a satisfying and readable base-planning language, and it emerges
  from the capacity tiers rather than needing to be designed.
- **Power.** Channels consume 1⁄128 ae/t per node traversed, so a dense AP running 32
  endpoints is a genuine load, not a rounding error. No extra balancing lever needed.
- **Recipe.** Standard WAP + dense/engineering-tier components, sitting naturally in the
  existing progression.

**The balance check.** A dense AP serving 32 machines costs: the block, a dense feed, a
booster loadout, ongoing power, *and* 32 channels — versus running cable, which costs 32
channels plus the cable. So wireless is roughly **channel-neutral against cable**, and you
pay the difference in power and boosters for the freedom from a physical path. That is the
correct trade, and it is the same trade F11.2 makes.

**Why this is the novel part of F11.** Every wireless AE2 addon extends the *network*.
None of them, as far as I can find, hook the *coverage volume* as a precondition for machine
connectivity. It's a small conceptual move that changes what wireless access points are for.

**Incumbents — still worth knowing, but no longer competitors:**
- **AE Wireless Transceiver** (~75.9K downloads) — fully wireless transceiver system, no
  cables, **no power required**, cross-dimensional, named channels, FTB Teams isolation, and
  a Wireless Connector that binds blocks to a transceiver channel without cable.
- **ExtendedAE-Plus** — wireless transceiver with channel cards.
- **AE2 Crystal Science** — Ender Emitter / Ender Broadcaster.

All three extend channels and most are explicitly cost-free. This design is the opposite
bet: costed, ranged, spatial, and about transport rather than connectivity.

**Open questions.**
1. **Is WAP coverage queryable from the public API?** There is very likely a wireless grid
   service and an access-point interface exposing range and position, but **this is not yet
   verified against the javadoc.** *Decision: proceed with the design regardless.* If
   coverage turns out to be internal-only, the fallback is to compute coverage ourselves
   from AP position and booster count (the range formula is observable in-game and the
   booster inventory is readable), or to upstream a small accessor PR. Neither invalidates
   the design; both change the cost.
2. Does range use Euclidean distance or a bounding box? Determines whether "coverage
   planning" is sphere-packing or box-tiling, which materially changes the gameplay.
3. Cross-dimensional behaviour — almost certainly should *not* work, unlike the incumbents.
   Coverage is spatial; that's the point.
4. Chunk loading. A wireless endpoint in an unloaded chunk is a correctness problem, not just
   a performance one. Probably requires spatial anchor interaction or explicit
   "unloaded = disassociated" semantics.
5. **Does the Dense WAP need to be a block, or can it be a multiblock?** A 2×2×2 or 3×3×3
   structure would justify the capacity more visibly and give it presence in a base, but
   costs a lot more implementation. Start with a single full block; revisit if it feels
   under-costed in play.
6. Whether standard APs should serve endpoints at all, or whether endpoint service should be
   a Dense-only capability. Single-tier is simpler to explain; two tiers gives a progression
   step. Leaning two tiers.

---

### F11.6 — New tunnel types

Cheap to add, since `P2PTunnelAttunement` is a documented registration point and each type
is small. Ordered by confidence:

| Type | Attune with | Notes |
|---|---|---|
| **Signal** | An F1 signal-carrying item | Direct F1/F2 composition; makes logic graphs routable. |
| **Experience** | A bottle o' enchanting | Straightforward; commonly wanted. |
| **Entity** | A lead | Mob transport. Needs care with entity NBT and mounted stacks. |
| **Player** | An ender pearl | Teleporter. Fun, but a balance decision — gate hard. |
| **Chunk-load** | A spatial anchor | Keeps the far end loaded. Balance-sensitive. |
| **Tick / acceleration** | ? | **Do not ship.** Tick manipulation is a TPS and balance minefield. |

Note the *self-attunement* joke available here: attuning a Universal tunnel with the
attunement item of a type it already carries should do something sensible (or nothing), not
crash.

---

### F11.7 Phasing within F11

> **Status (2026-07-28):** F11.1 ✓, F11.2/3/4 ✓ (as one Universal Mesh Endpoint, including
> mesh-ME grid bridging), F11.5 ✓ (coverage WAS queryable — `IWirelessAccessPoint` is
> public API, and AE2's own WAPs serve bridges). Only **F11.6** remains, and its
> entity/player/chunk-load rows are balance decisions before they are code. The signal
> tunnel row is now partially superseded: the mesh already carries signals across grids,
> so a signal P2P tunnel adds AE2-native routing convenience, not new capability.

1. **F11.1 (linking overhaul)** — highest value, lowest risk, ships alone as a useful mod.
2. **F11.2 (universal)** — moderate, gated on the channel-cost decision.
3. **F11.6 (new types)** — incremental, ship continuously.
4. **F11.4 (provider P2P)** — gated on the blocking-mode API investigation.
5. **F11.5 (WAP-coverage wireless)** — gated on whether coverage is queryable from public
   API. Do that check early; it's cheap and it's the most differentiated idea in F11.
6. **F11.3 (mesh)** — transport types first, then ME with pooling + collision shutdown once
   F11.1's terminal exists to make collisions diagnosable. Mesh-ME is meaningfully harder
   than everything above it and should not be attempted without the diagnostic UI in place.

---

## 5. Suggested phasing

> **Status (2026-07-28): every phase is complete.** Phase 0 ✓ (one day), Phase 1 ✓ (ten
> parts), Phase 1b ✓ (F9 complete; smithing/stonecutting cut by decision), Phase 2 ✓
> (tracer + job telemetry), Phase 3 ✓ (F6 + F7), Phase 4 ✓ (F3 + F4 including deadline
> and preemption stretch goals; F10 deliberately not started — see §4.3), Phase 5 ✓ (F8
> shipped as the ME Logic Core, channel-gated rather than config-gated; virtual storage
> devices are the remaining slice). F11 (added later, §4A) shipped through F11.5; F11.6
> tunnel types remain. The gametest harness grew far beyond plan — 87 in-game tests in CI
> are the source of truth for behavioral claims (see ROADMAP.md).

**Phase 0 — Spike (1–2 weeks).**
Prove `AEKeyTypes` registration works for a non-item, non-fluid, non-chemical key with
assignment semantics. Read Applied Mekanistics' source as the reference implementation.
Success criterion: a signal key appears in the ME Terminal, drives a Storage Monitor, and
trips a vanilla Level Emitter. **If this fails, the whole architecture changes — stop and
re-plan.**

**Phase 1 — MVP.** F1 (signals) + F2 (logic parts, ~6 of the 9) + Redstone I/O.
This is a complete, shippable, coherent mod on its own.

**Phase 1b — parallel, and the hedge.** F9 (fuzzy/alternative pattern inputs). Deliberately
placed alongside Phase 1 rather than after it, because it has **no dependency on the Phase 0
spike**. If custom key types turn out not to work, F9 is still a complete mod. Do the
`IPatternDetails` API investigation (§3 F9 open question 1) during Phase 0 — it's a
javadoc read, not an experiment.

**Phase 2 — The reason people install it.** F5 (Tracer). Depends on F1 for output, but the
sampling layer is independent and can be built in parallel.

**Phase 3.** F6 (queries) and F7 (config terminal) — both large UI efforts, both independent
of each other, both independently shippable.

**Phase 4.** F3 (guarded patterns), then F4 (scheduler), then F10 (bundles) if still
justified. Ordered by ascending risk of breaking someone's base. Note F3 and F9 share the
"requirement without consumption" mechanism, so build F9 first and F3 gets cheaper.

**Phase 5 / optional.** F8 (virtual logic core), config-gated.

---

## 6. Cross-cutting concerns

- **The channel principle.** A rule has emerged independently across F11.2 (universal
  tunnels charged per capability), F11.3 (mesh pools allocate but never create), and F11.5
  (wireless is channel-neutral against cable): **new capabilities buy convenience,
  compactness, and legibility — never channel capacity.** Channel scarcity is what the
  entire mod is balanced around, and it is the one number this addon should never inflate.
  Smarter *allocation* of existing channels is fair game; more channels is not. When a
  future feature is ambiguous, this is the tiebreaker.
- **Failure modes are binary.** AE2 has no analogue-valued mechanic anywhere: devices have
  channels or they don't, networks are powered or they aren't, tunnels are linked or they
  aren't. Every feature here should fail the same way — off, named, and located — rather
  than degraded. This was the reason the wireless signal-strength gradient was cut, and it
  applies equally to any future temptation toward "partial" states.
- **Performance is the acceptance criterion.** AE2 addons get uninstalled for TPS, not for
  missing features. Every part sleeps by default. Every watcher is bounded. Every terminal
  sends downsampled snapshots. Budget: a 500-part logic graph should be unmeasurable on a
  tick profiler.
- **Server-authoritative.** Grids are server-side only; no logic evaluation on the client,
  ever.
- **Graceful degradation.** Guarded patterns, bundle keys, and signal keys must all fail
  legibly if the mod is removed — AE2 provides a decode-failure tooltip strategy for
  exactly this; use it.
- **Security station integration.** F7 and F8 are remote-write capabilities and must respect
  network security. *(As built: unimplementable on this line — AE2 19.2.x removed the
  security station and ships no permission API. Writes gate on `mayBuild` +
  `level.mayInteract` instead, which respects adventure mode and claim mods; if AE2
  regains a security API, gate on it.)*
- **Datagen everything.** Part models, recipes, tags, lang. AE2 recommends datagenning part
  model JSON. *(As built: datagen runs for the scaffold, but models/recipes/tags/lang are
  maintained as handwritten JSON plus the generated-art pipeline (`make textures`); at this
  asset count the JSON is the simpler system. Revisit if the 26.x port multiplies model
  formats.)*
- **Ship a guide.** AE2's own player guide is the gold standard and the reason people
  understand the mod at all. A logic mod without documentation is a logic mod nobody uses.

---

## 7. Open questions and unverified items

Flagged explicitly — **verify before building**:

1. ~~**Target version / API baseline.**~~ **Resolved — shipped against Minecraft 1.21.1 /
   NeoForge / AE2 19.2.17** (Maven Central; Modmaven is stale for AE2). The hosted javadoc
   tracks the newest dev line (26.x), so API questions are answered against the local
   1.21.1 clone, not the website. Multi-version strategy is branch-per-version mirroring
   AE2 (documented in README).
2. ~~**Processing pattern limits (9 in / 3 out).**~~ **Resolved — the 9/3 figure was stale.**
   Those numbers come from the 1.18 / 1.19.2 guide pages. From the 1.20.1 guide onward the
   encoding terminal's input and output slots scroll, giving **81 ingredients and 26
   secondary outputs**. §4 has been rewritten accordingly. *Lesson: the AE2 guide is
   versioned per Minecraft release and old versions rank highly in search — always check the
   `development` / current-version page.*
8. ~~**Does `IPatternDetails` expose a multi-candidate input abstraction?**~~ **Resolved —
   yes.** `IPatternDetails.IInput.getPossibleInputs()` + `isValid(AEKey, Level)` are
   public API, honored by the planner at calculation and execution. Proven end-to-end by
   the autocraft gametest.
9. ~~**Does blocking mode compare machine-slot contents by exact key?**~~ **Resolved — no,
   and no work is needed.** Source-verified (19.2.17): `PatternProviderLogic.updatePatterns`
   collects **every `getPossibleInputs()` candidate** of every pattern into `patternInputs`
   with `.dropSecondary()` (components stripped), and the blocking check compares machine
   contents the same way (`stack.getKey().dropSecondary()`). Blocking is therefore
   spec-aware for every adaptive spec type for free: tag and any-of specs enumerate their
   candidates into the set, and fuzzy / damage-band variants collapse to the same
   component-stripped item identity.
10. ~~**Does AE2's `FuzzyMode` enum live in public API?**~~ **Resolved — yes**
    (`appeng.api.config.FuzzyMode`, with codecs), though note its constants are
    damage-band-only; "ignore components" is plain item-identity comparison, which is how
    the shipped Fuzzy spec matches.
3. **Novelty of every feature here is unverified.** The AE2 addon ecosystem is large and a
   substantial fraction is Chinese-language and poorly indexed by Western search. Search
   CurseForge and Modrinth directly, and ask in the AE2 Discord, before committing to any
   feature. *Still open — queued as part of the publishing pass (docs/publishing/), since
   the listing copy should not claim firsts without this check.*
4. ~~**Whether CPU selection is hookable** (F4).~~ **Resolved — not hookable in 19.2 public
   API.** `submitJob` accepts an explicit target CPU, which is how the shipped scheduler
   steers everything it originates (admission control, class pools, preemption). Foreign
   jobs (players, other mods) go through AE2's internal selection with no extension point;
   steering them is an upstream-PR candidate (a CPU-selection hook), tracked in ROADMAP
   alongside docs/upstream/.
5. ~~**Whether custom `AEKey` types can safely carry assignment semantics** (F1).~~
   **Resolved — yes.** Registers refuse insert/extract via MEStorage defaults and are
   written by assignment through the grid service; terminals, emitters, and monitors all
   cooperate. Verified in-game and by gametest.
6. ~~**Whether guard evaluation can be made cheap enough for the planner** (F3).~~
   **Resolved — by never entering the planner.** The as-built F3 evaluates guards in the
   grid-aware *provider* (patterns cannot see their grid): plan-time hiding via
   `getAvailablePatterns` plus a push gate in `pushPattern`, re-indexed on a ten-tick
   fingerprint. The planner runs stock and unmodified.
7. ~~**AE2's licensing.**~~ **Resolved — LGPL-3.0**, matching AE2's own LGPL-3.0; reference
   reading and pattern reuse are license-compatible. Chosen 2026-07-25 (delegated call,
   noted as changeable pre-release).

---

## 8. References

**AE2 official**
- Addon and Mod API — <https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/API.md>
- Javadocs (latest unreleased) — <https://appliedenergistics.github.io/javadoc/>
- Player guide, Pattern Provider — <https://guide.appliedenergistics.org/development/items-blocks-machines/pattern_provider>
- Player guide, Patterns (1.20.1) — <https://guide.appliedenergistics.org/1.20.1/items-blocks-machines/patterns>
- Player guide, Terminals (1.20.1 — **current** pattern encoding limits: 81 ingredients / 26 secondary outputs, substitution behaviour) — <https://guide.appliedenergistics.org/1.20.1/items-blocks-machines/terminals>
- Player guide, Upgrade Cards (1.21.1 — Fuzzy Card semantics) — <https://guide.appliedenergistics.org/1.21.1/items-blocks-machines/upgrade_cards>
- Player guide, Fuzzy Card (1.19.2 — list of fuzzy-capable devices) — <https://guide.appliedenergistics.org/1.19.2/features/upgrades/fuzzy-card>
- Player guide, Auto-Crafting (1.19.2 — **stale** 9/3 pattern limits, retained to document the correction) — <https://guide.appliedenergistics.org/1.19.2/features/auto-crafting>
- All The Guides, AE2 autocrafting (processing patterns have no substitution) — <https://allthemods.github.io/alltheguides/mods/ae2/autocrafting/>
- `ICraftingProvider` "extra requirements" discussion — <https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/1761>

**P2P (F11)**
- Player guide, Point To Point Tunnels (1.21.1) — <https://guide.appliedenergistics.org/1.21.1/items-blocks-machines/p2p_tunnels>
- Player guide, P2P quirks (ME-through-ME restriction, 5% energy tax) — <https://guide.appliedenergistics.org/1.20.1/items-blocks-machines/p2p_tunnels>
- Player guide, The Memory Card — <https://guide.appliedenergistics.org/1.21.1/items-blocks-machines/memory_card>
- Player guide, Channels (routing algorithm, per-node power cost) — <https://guide.appliedenergistics.org/development/ae2-mechanics/channels>
- Player guide, The Wireless Access Point (any number of APs, any number of boosters, requires a channel) — <https://guide.appliedenergistics.org/1.21.1/items-blocks-machines/wireless_access_point>
- Player guide, Channels (loops "should be minimized," ambiguous routes → devices offline) — <https://guide.appliedenergistics.org/1.19.2/features/me-network/channels>
- All The Guides, P2P Subnets (32×32 = 1024 channel math) — <https://allthemods.github.io/alltheguides/mods/ae2/p2p/>
- 2016 "Advanced Memory Card" feature request (still unbuilt) — <https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2434203-ae2-stuff-v0-5-0-added-wireless-connector-and?page=2>
- AE Wireless Transceiver (wireless incumbent) — <https://modrinth.com/mod/ae-wireless-transceiver>
- AE2 Crystal Science (Ender Emitter / Broadcaster) — <https://modrinth.com/mod/ae2-crystal-science>

**Prior art**
- ExtendedAE — <https://www.curseforge.com/minecraft/mc-mods/ex-pattern-provider>
- ExtendedAE-Plus — <https://modrinth.com/mod/extendedae-plus>
- patternbetter — <https://modrinth.com/mod/patternbetter>
- ME Requester — <https://www.curseforge.com/minecraft/mc-mods/merequester>
- Recursive AE2 Pattern Provider — <https://www.curseforge.com/minecraft/mc-mods/recursive-ae2-pattern-provider>
- Applied Extended Crafting — <https://www.curseforge.com/minecraft/mc-mods/applied-extended-crafting>
- Advanced Peripherals (ME Bridge docs) — <https://docs.advanced-peripherals.de/0.7/peripherals/me_bridge/>
- AE2CC Bridge — <https://github.com/TheMrMilchmann/AE2CCBridge>
- Open Energistics (OpenComputers ↔ AE2, 1.16.5) — <https://github.com/inraito/open-energistics>

**PackagedAuto**
- PackagedAuto — <https://www.curseforge.com/minecraft/mc-mods/packagedauto>
- PackagedAuto (Modrinth) — <https://modrinth.com/project/ugIdhQx4>
- Nomifactory "How to PAuto" guide — <https://github.com/Nomifactory/Guides/blob/latest/guides/HowToPAuto.md>
- AE2 Fluid Crafting (PackagedAuto integration module) — <https://github.com/phantamanta44/ae2-fluid-crafting>
