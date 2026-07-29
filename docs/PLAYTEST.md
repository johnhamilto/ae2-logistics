# AE2 Logistics — Feature Inventory & Playtest Companion

**As of v0.16.0** · Minecraft 1.21.1 · NeoForge · requires AE2 (built against 19.2.17)

Every feature in the mod, grouped by system, with what is SUPPOSED to happen. The
92-test gametest suite already verifies everything marked programmatic; your session
matters most for anything marked **[HUMAN]** — GUIs, visuals, tooltips, and feel have
never been touched by a person. Each block/item/part has its own heading so notes can
reference them directly. "Not a bug" boxes list known, deliberate, or documented
limitations — note them if they *feel* wrong, but they are expected behavior.

**The in-game guide** now lives as the "AE2 Logistics" category inside **AE2's own
guide** (open AE2's guide, or craft the Guide Tablet: book + certus — it opens the same
guide). **Hold G while hovering any of our items** to jump to its page. A page per
system; where it disagrees with this doc, that's a doc bug — note it.

---

## 0. What only you can test

- **14 GUI screens**: Logic Part (x2 layouts: standard, Stock Sensor with inventory),
  Pattern Workbench, Tracer Terminal, P2P Frequency Terminal, Mesh Endpoint,
  Job Monitor, Guarded Provider, Query Terminal, Query Sensor, Query Export Bus,
  Config Terminal, Job Scheduler, Logic Core, Subnet Core.
- **5 use-item flows**: Signal Card binding, memory cards on parts, Config Blueprint
  corners, Wireless Bridge anchoring, Register Bank right-click.
- **Visuals**: 8 blocks now have 3D models (Register Bank, Pattern Workbench,
  Job Scheduler, Guarded Provider, Logic Core, Subnet Core, Dense WAP, Wireless
  Bridge) — check them in-world and as items in hand/inventory. All part models,
  16x16 art, GUI panels.
- **Feel**: advancement toasts, recipe availability in the crafting book, tooltips,
  guide rendering, chat/action-bar messages, screen resize behavior (known: resizing
  a screen resets unsaved edit-box text — vanilla plumbing, low priority).
- World **save/load**: quit to menu and reload mid-test occasionally — NBT round-trips
  are gametested, but a real save/load cycle through the full chunk pipeline is not.

---

## 1. Signals (F1)

Named numeric channels (`factory:iron`) stored per network. One value per channel per
network; every device sees the same number. Channels driven by logic parts recompute
every tick and override stored values; when a part stops driving, the stored value
returns.

### Signal keys in AE2's own UI
- Signals appear in the **ME Terminal** with a red wave icon; the "stored amount" is
  the value. **[HUMAN]** icon renders, name reads as the channel id.
- They cannot be inserted/extracted by buses or put in cells (assignment-only): expect
  buses/cells to simply never move them.
- **Storage Monitor** displays a signal's live value when configured with a bound
  Signal Card. **[HUMAN]**
- **Level Emitter** with a bound Signal Card thresholds on the signal value →
  computed numbers become redstone. **[HUMAN]**

### ME Register Bank (block)
- Craft: shaped — iron/certus/iron, redstone/logic processor/redstone, iron/certus/iron.
- **Right-click**: lists every signal on the network in chat.
- **Right-click holding a bound Signal Card**: reads that channel to the action bar.
- Stores manually-set signals persistently (survives restart); part-driven channels
  override while driven.
- **[HUMAN]** new 3D model (frame + column).

### Signal Card (item)
- Craft: AE2 Basic Card + redstone torch. Stacks to 1.
- Unbound tooltip says to use `/ae2logistics signal card <channel>`; binding writes
  the channel and the tooltip shows it.
- Works in Level Emitter / Storage Monitor config slots (click the slot holding it).

### Commands (permission 2)
- `/ae2logistics signal set <channel> <value>` / `get <channel>` — target the network
  of the bank you are LOOKING AT.
- `/ae2logistics signal list` — same targeting.
- `/ae2logistics signal card <channel>` — binds the HELD Signal Card.

> **Not a bug**: values ≤ 0 delete a stored channel. Multiple writers of one channel
> SUM (saturating) — that's the multi-writer rule everywhere.

---

## 2. Logic Parts (F2)

Ten cable-mounted parts. All evaluate **once per tick in dependency order** — a chain
settles in one tick; feedback loops advance one step per tick at a deterministic point;
two writers of one channel add. Parts use **no AE2 channels**, idle at 0.5 AE/t.

**Shared GUI** (right-click a part): out / in A / in B channel text boxes, an operator
cycle button where relevant, two value boxes, a toggle switching operand B between the
literal value and channel B, Apply, and a **live output readout**. **[HUMAN]** the
Stock Sensor's GUI variant additionally has a ghost slot AND a player inventory (click
the slot with a held item to set the watched item).

> **Not a bug (placement)**: a part occupies its cable face, which **blocks grid
> connections through that face** — machines or cables behind a part won't connect.
> This is AE2 behavior and it will bite you in test builds too.

Per part (craft = logic processor + certus + the listed vanilla item):

- **Signal Constant** (redstone torch): writes value A to `out`. Every tick.
- **Signal Threshold** (comparator): compares A against operand B with `<` `<=` `==`
  `>=` `>`; writes 1/0.
- **Signal Hysteresis** (lever): latches to 1 when A drops **below value A** (low),
  back to 0 when A rises **above value B** (high). No flicker between. Latch state
  persists across save/load.
- **Signal Arithmetic** (calculation processor instead of certus item — recipe:
  logic processor + certus + calculation processor): `+ - * / min max mod`,
  saturating at 0 and the long limit; divide/mod by zero yield 0.
- **Signal Logic Gate** (repeater): AND OR XOR NOT over zero/nonzero.
- **Redstone Signal Port** (redstone block): OUTPUT mode emits channel value as
  redstone 0–15 on its face; INPUT mode writes the neighbor's redstone level to the
  channel. **[HUMAN]** check both directions with a lever and a lamp.
- **Signal Stock Sensor** (observer): writes the network-stored amount of the watched
  item every tick.
- **Signal Rate Meter** (hopper): growth of channel A per second over a window of
  value-A seconds (clamped 1–60). Reads 0 until the window fills; decreasing values
  read 0.
- **Signal Counter** (tripwire hook): counts rising edges of A; value A > 0 wraps
  (modulo); channel B (when set) holds the count at 0 while nonzero (reset line).
  Count persists across save/load.
- **Signal Timer** (clock): pulse train — 1 for the first value-B ticks of every
  value-A tick period (period clamped 2–72000, pulse 1..period-1).

### Memory cards (AE2's, on our parts)
- Shift-click a part with a memory card = save; click another part = apply. Same-type
  only (AE2 enforces via the exported description).
- Carries EVERYTHING per part type: logic tuple + watched item; mesh endpoint
  frequency/role/caps/priority/filters; monitor prefix+window; query part expressions.

---

## 3. Observability (F5)

### ME Tracer Terminal (part)
- Craft: AE2 terminal + logic processor + certus.
- Lists every channel on the network with live values (1 Hz refresh), wheel-scrolls.
- Click a channel → **five-minute sparkline** (1 sample/second server-side) with
  min/max/now labels. **[HUMAN]** sparkline drawing, selection highlight.
- Cap: 64 tracked channels per network (excess channels simply aren't charted).

### ME Job Monitor (part)
- Craft: AE2 terminal + calculation processor + crafting unit.
- GUI: prefix box (default `craft`), stall window in seconds, live readout.
- Channels: `<prefix>:active`, `:idle`, `:stalled`, `:pending` (items outstanding).
- **Stalled** = a busy CPU whose progress hasn't changed for the window (blocked
  provider face, machine jam). Watch it flip while you deliberately jam a provider.
- Name a Crafting Storage with an **anvil** before building the CPU: the cluster gets
  `<prefix>:<name>/remaining` and `/stalled` (name slugged to lowercase `a-z0-9_.-`,
  spaces become `_`, 24 chars max).

> **Not a bug**: one monitor per prefix per network — two double every count
> (multi-writer sum). Stall detection is polling, not events.

---

## 4. ME Logic Core (F8, slice 1)

- Craft: Register Bank + Regulus + engineering processor.
- One block hosts **8 logic entries** — same evaluators as the parts (everything
  except the Redstone Port), same scheduler, same-tick chaining inside one core.
- **GUI [HUMAN]**: 8 rows (index, type, out channel, live value; red label = entry
  dark, gray = empty), click to select (highlight), detail strip below: type cycle
  (empty→const→thresh→hyst→arith→gate→stock→rate→count→timer→empty), out/a/b boxes,
  op cycle, two value boxes, `b=#`/`b=ch` toggle, Apply. Stock entries swap the
  second line for a ghost slot ("click slot with held item to watch"). Header shows
  online/offline.
- **The trade (verify it!)**: every configured entry **requires a channel** drawn
  through the core (a dense carrier). On an ad-hoc network or behind one glass cable,
  a full core canNOT fully light — entries beyond capacity show red and write
  nothing. On a controller face or dense cable, all 8 run. Physical parts remain
  channel-free.
- Config Terminal copy/paste and Config Blueprint clone whole cores (entries, values,
  watched items).

---

## 5. ME Subnet Core (F8, slice 2)

- Craft: Storage Bus + Regulus + engineering processor.
- One block = one machine on your network (1 channel) containing a genuinely
  **separate internal grid**, powered through the core (quartz-fiber-style): when the
  main network loses power or the core loses its channel, every entry goes dark.
- **GUI [HUMAN]**: same 8-row layout as the Logic Core. Detail: type cycle
  (empty→storage→import→export→uplink→downlink), face cycle (D/U/N/S/E/W, only for
  the three face-bound types), priority box, Apply, one ghost filter slot (click with
  item or bucket; empty hand clears).
- Entry behaviors:
  - **storage** (face): mounts the block behind that face (items AND fluids) into the
    subnet at its priority, through its filter. Breaking/replacing the neighbor
    updates the mount.
  - **import** (face): pulls items from the face inventory into subnet storage —
    8 items per operation, every 10 ticks. Items only.
  - **export** (face): pushes from subnet storage into the face inventory, same rate;
    with a filter, only that item. Items only.
  - **uplink**: subnet sees MAIN storage. Uplink + export = feed machines straight
    from main storage with no interface or cable.
  - **downlink**: MAIN sees subnet storage at the entry's priority — and **costs a
    main channel** like the physical storage bus it replaces.
- Internal entries consume the internal ad-hoc grid's 8 channels (8 entries = the
  natural budget).
- **Loop safety (verify!)**: uplink + downlink together must NOT freeze the game,
  double-count, or dupe. Items inserted into main with only a subnet chest for
  storage land in that chest exactly once and extract exactly once.
- Copy/paste + blueprint support, NBT persistence.

---

## 6. Adaptive Patterns (F9)

### Adaptive Processing Pattern (item)
- Made from normal processing patterns at the workbench; use in ANY pattern provider.
- Per-ingredient modes: **Exact** / **Fuzzy** (same item, ignore components; damage
  bands 99/75/50/25 for damageables) / **Tag** / **Any-of** (explicit list, cap 8).
- **Catalyst flag** (any ingredient): must be present, ships with the batch, credited
  back by AE2's container-item flow — one pickaxe serves many queued crafts.
- Planner semantics: substitutes are consumed from storage **at every level of a
  crafting tree**, but only the canonical (encoded) item is ever autocrafted.
- Tooltip lists non-exact specs. **[HUMAN]**

### Pattern Workbench (block)
- Craft: crafting table + AE2 Fuzzy Card + certus + iron.
- **GUI [HUMAN]**: pattern slot + 3x3 ingredient view + player inventory. Click an
  ingredient: cycles exact → fuzzy (→ damage bands if damageable) → each of its tags.
  Click **holding an item**: adds it as an any-of alternative. Shift-click: reset to
  exact. Ctrl-click: toggle catalyst. Badges: `F`/`99`/`75`/`50`/`25`, `#` (tag),
  `A<n>` (any-of), gold `C` (catalyst); hover shows the full spec.
- **Guard strip** (bottom): channel box, operator cycle, value box, **Wrap** →
  produces a Guarded Pattern; **Unwrap** restores the original.
- **[HUMAN]** new 3D model.

> **Not a bug**: AE2's blocking mode already respects adaptive specs (verified in
> source) — a machine holding a fuzzy/tag variant of a pattern input still blocks.
> **Real footgun (documented)**: a catalyst pattern that ALSO lists the tool as a
> machine OUTPUT will dupe the tool — don't do that.

---

## 7. Guarded Crafting (F3) + Regulus

### Regulus Crystal (resource)
- **In-world transform**: drop a Charged Certus Quartz Crystal + Redstone + Glowstone
  Dust into water → they fuse into **2 Regulus Crystals**. **[HUMAN]** watch it
  happen; check JEI/EMI shows the transform if you run a viewer.

### Guarded Pattern Provider (block)
- Craft: Pattern Provider + Regulus + logic processor.
- A full pattern provider (9 slots, pushes to adjacent machines, shows in pattern
  access terminals) with a gate:
  - **Guard**: channel + op (`<` `<=` `==` `>=` `>`) + value. While failing, ALL its
    patterns are invisible to the planner (jobs route elsewhere). Empty channel = no
    guard. Status line shows **PASS/HOLD** live.
  - **Gate toggle**: *plan + push* (default — failing guard also holds pushes of
    running jobs; they resume when it opens; Job Monitor shows them stalled) vs
    *plan only* (running jobs always finish).
  - **Priority channel** + base: when set, provider priority = live signal value.
    Moves the planner between DIFFERENT recipes for the same output.
- Guard flips take effect within ~half a second (ten-tick fingerprint).
- **GUI [HUMAN]**: 9 pattern slots, guard fields, gate toggle, priority fields,
  PASS/HOLD + live priority readouts. **[HUMAN]** new 3D model.

### Guarded Pattern (item)
- Wrapped at the workbench; plans + pushes only while its own condition holds —
  two opposite-guarded recipes can share ONE provider.
- **Inert in vanilla providers** (behaves as the inner pattern; documented).

> **Not a bug**: a plan computed while a guard was open keeps its patterns if the
> guard closes before submission — the push gate arbitrates then. Identical patterns
> in several providers round-robin at push time; dynamic priority moves production
> between different recipes, not copies of one.

---

## 8. ME Job Scheduler (F4)

- Craft: crafting unit + Regulus + engineering processor.
- **4 stock rules**, each: ghost target (click slot with item, or bucket for fluid),
  **floor** (keep ≥ this stocked), **batch** (per job), **class** (bulk/maint),
  **guard** channel; second line per rule: **deadline** (seconds, 0=off) and
  **preempt/polite** toggle.
- Every second, a rule below floor attempts a craft with **admission control**:
  - Plan must be **complete** — provably-stalling jobs are never submitted
    (`missing`).
  - A free CPU from the **class pool**: bulk = unnamed or `bulk*`-named CPUs;
    maint = `maint*`-named ONLY (anvil-name a crafting storage). **Player-Only**
    CPUs are never touched. Smallest fitting CPU wins.
  - Attempts are rate-limited (10s between tries per rule; server-configurable).
- **Deadline**: wall-clock from submission; an overdue job is canceled (CPU frees),
  the rule shows `late`, then re-admits.
- **Preempt**: a deferred preempting rule cancels the **youngest** same-class job of
  a HIGHER-INDEX rule (row order = priority). Victim shows `bumped`, retries later.
  Foreign jobs (yours, other mods') are never canceled.
- Rule states **[HUMAN]** (label + color): idle, plan, missing (red), no CPU (red),
  run (green), hold, wait, late (red), bumped (yellow).
- Rules ride Config Terminal copy/paste + Config Blueprint. Persist across reload.
- **[HUMAN]** new 3D model; second-line GUI layout is new this version.

---

## 9. Query Language (F6)

Grammar: `mod:x` `tag:ns:path` `name:"substring"` (case-insensitive), `count OP N`
(k/m/b suffixes), `stored`, `craftable`, `damage OP N` (percent), `signal(chan) OP N`,
`@saved`, implicit AND between terms, `OR`, `NOT`, parentheses. Queries range over
items + fluids; signal keys are excluded by contract.

### ME Query Terminal (part)
- Craft: AE2 terminal + Regulus + calculation processor.
- **GUI [HUMAN]**: expression box with ~1 Hz **live preview** — matching kinds,
  total, up to 6 sample stacks; parse errors shown as you type. Name + Save adds to
  the network library; saved list click-to-load; `@name` references saved queries.
- Library is **replicated across every Query Terminal** on the network; editing a
  saved query updates everything referencing it.

### Signal Query Sensor (part)
- Craft: Stock Sensor + Regulus.
- Writes the total stored amount matching its query to a channel every tick;
  `signal()` terms evaluate in dependency order with the logic graph.

### Query Export Bus (part)
- Craft: AE2 Export Bus + Regulus + calculation processor.
- Exports matching items into the faced inventory: 8 items/operation, scanning up to
  32 matching kinds, ticking 10–40 ticks (speeds up while it finds work).

### Command
- `/ae2logistics query <expression>` — runs against the network you're LOOKING AT;
  prints up to 8 matches + kinds/total; parse errors as command failure.

---

## 10. Fleet Configuration (F7)

### ME Config Terminal (part)
- Craft: AE2 terminal + Regulus + engineering processor.
- Lists **every configurable device** on the network (AE2's and ours): icon, name,
  position, priority, settings summary. Search filters by name/type/setting text.
  Session list caps at 256 rows; **Refresh** rebuilds.
- Select a device → up to 4 `setting = value` lines with `>` cycle buttons; priority
  editable. **[HUMAN]** try cycling a storage bus's settings, an export bus's, and
  one of our parts'.
- **Copy / Paste / All**: memory-card-grade settings, same-type only; **All** hits
  every same-type device on the network.
- Writes gate on mayBuild + mayInteract (adventure/claims respected; no security
  station exists in this AE2 line).
- **Snap** stores a persistent baseline; list then colors **gold=changed, cyan=new,
  red=gone**; Δ toggle filters to differences. **[HUMAN]** colors + toggle.

### Config Blueprint (item)
- Craft: paper + Regulus + certus.
- Click corner 1, click corner 2 (≤4096 blocks): captures every AE2-based device +
  our TransferableSettings devices in the box (types, sides, full settings).
- **Sneak-click the minimum corner** of a rebuilt region: applies to type-matching
  devices. Sneak-use in air: clears. **[HUMAN]** action-bar feedback.
- Devices that transfer: all AE2-base parts/BEs + Guarded Provider (guard + patterns
  config), Job Scheduler (rules), Logic Core (entries), Subnet Core (entries).

---

## 11. Mesh & P2P (F11.1–11.4)

### Universal Mesh Endpoint (part)
- Craft: ME P2P Tunnel + logic processor + fluix.
- Named frequency + role (in/out/both) + priority + capability toggles: **redstone,
  items, fluids, energy, signals, ME link**. Each endpoint costs 1 channel, idles
  1 AE/t. Two endpoints = universal P2P; more = mesh.
- **GUI [HUMAN]**: frequency box, role cycle, six capability toggles, priority,
  9 ghost filter slots (item, or bucket→fluid; empty hand clears), live status line
  (endpoint count, ME hub/spoke, status).
- Transport expectations:
  - Redstone: wired-OR (outputs emit the highest input level).
  - Items/fluids: pushed into an IN endpoint, delivered to OUT endpoints' faced
    inventories by priority then round-robin; batches stay whole.
  - **Provider P2P**: a pattern provider facing an IN endpoint pushes each batch
    complete to the first non-busy machine on the frequency; all busy = nothing
    pushed (true per-machine blocking at range).
  - Energy: FE spreads to outputs by priority.
  - Signals: the only cross-NETWORK transport — IN publishes its network's channels,
    OUT injects into its own; bridged values sum; re-publication is impossible.
  - **ME link**: fuses networks like a multi-point quantum bridge (hub + star, up to
    32 channels/spoke, power + membership through the mesh).
- Filters: exact match (components included); IN refuses non-matching insertions;
  OUT is skipped for non-matching stacks; provider batches land only where the whole
  batch matches.
- Hop budget 1: a mesh delivery can never enter another mesh.

### P2P Frequency Terminal (part)
- Craft: AE2 terminal + ME P2P Tunnel + logic processor.
- Lists every P2P tunnel (frequency, type, in/out, position, live). **Rename** names
  a frequency (stored ON the tunnels — every terminal shows the same names). **Mark
  target** + **Retune to target** re-links tunnels in two clicks; refuses to create
  a second input on a frequency.
- **Mesh rows**: every mesh frequency touching the network, all endpoints
  server-wide with role/caps/same-grid/status (`OK`/`off`/`wait`/`LOOP`); Rename
  retags all LOADED endpoints.
- Commands: `/ae2logistics mesh list | status <freq> | relink <freq>`.

> **Not a bug**: provider blocking THROUGH a mesh is always per-machine — the
> provider's own blocking toggle is effectively bypassed. Mesh rename skips
> unloaded-chunk endpoints until they load. CABLED LOOP is a warning, not a failure.

---

## 12. Wireless (F11.5)

### ME Wireless Bridge (block)
- Craft: Wireless Receiver + Regulus + calculation processor.
- **Anchor flow [HUMAN]**: click any access point (AE2's or Dense) with the item →
  action-bar "anchored" message, tooltip shows the position. Place it anywhere.
- In coverage of ANY active access point of the anchored network (same dimension):
  the bridge and everything cabled to it **joins that network** — storage, machines,
  mesh endpoints, all of it. Out of coverage: dark. **Binary** — no degraded fringe.
- Channels for the bridged segment path through the SERVING access point — an AP on
  thin cable is a thin pipe (this is the balance rule).
- **Handover**: kill the serving AP → the bridge re-associates to another in range
  within ~1 second (nearest AP, position-hash tiebreak).
- Breaking the bridge drops the anchor (re-anchor after re-place; documented).
- `/ae2logistics wireless status` while looking at a bridge: anchor, serving AP,
  grid size.

### Dense Wireless Access Point (block)
- Craft: Wireless Access Point + Regulus + engineering processor.
- A 32-block-range, 32-channel dense carrier — the cell tower tier. No GUI. Feed it
  dense cable or a controller face or its uplink chokes.
- AE2's own WAPs also serve bridges (16 base range, boosters extend it) — verify an
  existing AP works as coverage.

---

## 13. Cross-cutting

### Commands (all permission 2)
`/ae2logistics signal set|get|list|card` · `mesh list|status|relink` ·
`query <expr>` · `wireless status`

### Advancements **[HUMAN]**
14-node tree under its own tab (background: sky stone). Root = obtain Regulus
("The Control Plane"), then: Register Bank → Signal Card / logic part → Tracer /
Logic Core(goal); Workbench → Adaptive Pattern; Guarded Provider → Job Scheduler;
Query Terminal; Config Terminal; Mesh Endpoint → Wireless Bridge (goal). Each fires
on obtaining the item.

### Server config
`serverconfig/ae2logistics-server.toml` per world: `schedulerAttemptIntervalTicks`
(default 200), `denseWapRange` (32, new placements), `bridgeRetuneIntervalTicks` (20).

### Creative tab
"AE2 Logistics" tab, Register Bank icon: 8 blocks (Register Bank, Pattern Workbench,
Guarded Provider, Job Scheduler, Logic Core, Subnet Core, Dense WAP, Wireless
Bridge), Signal Card, all 18 parts (10 logic + Tracer + Job Monitor + 3 query parts +
Config Terminal + P2P Terminal + Mesh Endpoint), Config Blueprint, Regulus Crystal,
Guide Tablet. (Adaptive/Guarded patterns are made, not tabbed.)

### Guide
13 pages as the "AE2 Logistics" category inside AE2's guide; hold G on any of our
items jumps to its page. The Guide Tablet (book + certus) opens AE2's guide.

### Known global limits (not bugs)
- en_us only. Programmatic 16x16 art everywhere.
- Endpoint/entry filters are exact-match; no fuzzy/tag filter cards yet.
- Mesh registry rebuilds live; nothing mesh-side persists beyond part NBT.
- Queries cap previews at 6 rows; export scans ≤32 kinds/op.

---

## Suggested first hour

1. Regulus transform in a puddle → advancement toast.
2. Bank + card + emitter: the F1 loop the mod was founded on.
3. The guide's worked example (sensor → hysteresis → redstone port farm gate).
4. One of each GUI, clicking everything — that's the highest-value bug hunt.
5. Then the big rigs: scheduler + monitor + guarded provider on one CPU; a Logic
   Core replacing your part chain; a Subnet Core feeding furnaces; a wireless
   outpost.
