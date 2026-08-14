# Modrinth listing kit

Project name: **AE2 Logistics**
Slug suggestion: `ae2-logistics`
Categories: `technology`, `storage`, `utility`
License: LGPL-3.0
Environments: client + server (required on both)
Dependencies: Applied Energistics 2 (required), GuideME (embedded/transitive)
Game version: 1.21.1 - Loader: NeoForge

## Summary (one-liner)

The operations layer for Applied Energistics 2: signals and logic on your cables,
crafting that follows policy, storage that keeps itself sorted, and wireless
everything - in AE2's own idiom.

## Description (body)

AE2 stores your items and crafts your things. AE2 Logistics is everything around
that: seeing what your network is doing, deciding when it acts, and retiring the
babysitting. No computer mod, no scripts. Every feature is a cable part, a
terminal, or a card that works the way AE2 taught you things work.

**Give your network a pulse.** Signals are named numbers that live on your
network the way channels live on your cables. A Stock Sensor turns "how much
iron do I actually have" into one. A Rate Meter tells you how fast it is
changing. Ten logic parts compute with them: thresholds that will not flap,
timers, counters, arithmetic, gates. Redstone Signal Ports cross into vanilla
redstone in both directions, and the ME Logic Core packs eight rules into a
single block when the cable clutter gets old. The ME Tracer Terminal draws live
five-minute graphs of any of it. Your base has been running blind; this is the
instrument panel.

**Crafting that answers to you.** "Why is my crafting stuck" should have an
answer. The ME Job Monitor turns every crafting CPU's activity, stalls, and
progress into signals you can watch and react to. The ME Job Scheduler keeps
stock the way you actually want it kept: it only submits jobs that can finish,
respects your named CPUs, rate-limits, and bumps low-priority work when
something urgent lands. The Guarded Pattern Provider hides recipes from the
planner while a condition holds, so the machine that is mid-cycle never gets
handed a second job.

**Patterns that bend instead of break.** One recipe, any log. The Pattern
Workbench upgrades processing patterns to accept whole tags, damage ranges, or
a list of alternatives, and hands catalysts back when the job is done. The
planner you already know does the rest. And with a Pattern Import Card in your
wireless encoding terminal, blank patterns restock themselves from storage
while you work.

**Ask your storage real questions.** `tag:c:ores AND NOT craftable AND
count<1k` is a sentence your network can now answer. Type queries in the ME
Query Terminal and watch results update live, turn a query's match count into a
signal with the Query Sensor, or export everything that matches with the Query
Export Bus. It is the tag bus, the overflow finder, and the "what am I missing"
tool in one grammar.

**Configure the fleet, not the device.** The ME Config Terminal lists every
configurable device on your network in one window: read it, edit it, change
priorities, and copy one device's settings to every device of the same kind.
Snapshots record how the network was configured and diff it against now, so
"what did I change last weekend" stops being archaeology. The Config Blueprint
captures a whole build's configuration and stamps it onto the next copy.

**Rewire space.** The P2P Frequency Terminal finally gives P2P frequencies
names, live from one screen. Mesh Endpoints go further: name a mesh, drop typed
endpoints anywhere on the network, and items, fluids, energy, redstone,
signals, even whole grid bridges find their own way; no pairing ceremony, no
memory-card roulette. The Provider P2P Tunnel puts a pattern provider's faces
wherever your machines are. The ME Subnet Link is a storage bus whose target is
an entire subnet. The ME Wireless Bridge joins machines to the network anywhere
inside access point coverage, with the Dense Wireless Access Point as your
32-channel cell tower, and the ME Wireless Connector is the colored wireless
cable: dye-coded pairs that link like a cable you did not have to route.

**Storage with standards.** The ME Gated Storage Bus takes input cards. With a
Conform Card, the chest's own contents are the filter: seed a barrel wall once
and every lane keeps its assortment forever, no partition screens. Flip it with
an Inverter Card and you get a collection chest that accepts one of each new
thing, ever. The Stack Limiter Card feeds machines one item at a time, which
retires an entire genre of hopper-timing contraptions. Both cards also fit the
Subnet Link.

**Items that come in flavors.** The Variant Card turns any configured item into
a template: same item, and it must agree with every detail you deliberately set,
nothing more. One plain enchanted book means "any enchanted book". A
Mending-only book means exactly that. The card fits the Variant Import and
Export Buses (one template slot moves every matching variant) and the gated
storage buses (one template partitions a whole barrel of variants).

**It plays well with others.** The test bench runs the standard AE2 suite:
ExtendedAE, MEGA Cells, Applied Mekanistics (chemicals ride the mesh and the
return paths), ME Requester, and AE2WTLib, where the wireless terminals take
our cards in their upgrade slots.

**And it is honest.** Everything above is server-computed and covered by an
in-game test suite north of 140 scenarios that runs on every change. The
in-game guide documents every device; craft the guide tablet or press G over
any of our items.

In the workshop: the ME Storage Janitor (re-settles your whole store after you
repartition) and in-world Trace Panel dashboards are built and tested, waiting
on their looks.

## Gallery shot list (capture on playtest)

1. Tracer Terminal sparkline over a busy factory (hero shot)
2. A barrel wall on gated storage buses, Conform Cards keeping the lanes
3. Wireless Connector pair bridging a ravine, dye-coded
4. Job Scheduler rules with states visible (run / hold / late / bumped)
5. Config Terminal diff view (gold/cyan/red)
6. Query Terminal with a live preview
7. A wireless outpost: Dense WAP tower + bridge + machines, no cables
8. Mesh endpoint wall feeding a machine hall through a Provider P2P Tunnel

## Version metadata

- Version naming: `<mod_version>` (e.g. 0.41.0), file `ae2logistics-1.21.1-<v>.jar`
- Changelog: paste the GitHub release notes for the tag
- Release channel: beta until Jack's playtest pass, then release
