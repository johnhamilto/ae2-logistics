# Modrinth listing kit

Project name: **AE2 Logistics**
Slug suggestion: `ae2-logistics`
Categories: `technology`, `storage`, `utility`
License: LGPL-3.0
Environments: client + server (required on both)
Dependencies: Applied Energistics 2 (required), GuideME (embedded/transitive)
Game version: 1.21.1 - Loader: NeoForge

## Summary (one-liner)

The missing control plane for Applied Energistics 2: signals, logic, queries,
guarded crafting, job scheduling, and wireless machine coverage - in AE2's own idiom.

## Description (body)

AE2 gives you a storage plane and an execution plane. **AE2 Logistics adds the
control plane**: numbers your network can carry, logic that computes with them, and
policy for what gets crafted where - all as parts on cables, terminals, and keys in
storage. No foreign computer mod required.

**Signals** - named numeric channels stored in your network. Write them from
commands, Register Banks, or sensors; watch them in terminals; bind a Signal Card so
AE2's own Level Emitters and Storage Monitors can use them.

**Logic** - ten cable parts (threshold, hysteresis, arithmetic, gates, counters,
timers, rate meters, stock sensors, redstone ports) evaluated deterministically each
tick - or pack eight of them into one **ME Logic Core** (each entry costs a channel;
that is the trade). The **ME Tracer Terminal** draws five-minute sparklines of any
channel.

**Adaptive patterns** - the Pattern Workbench converts processing patterns to match
by tag, fuzzy identity, damage band, or alternative list, with catalyst inputs
credited back. Stock AE2 planner, zero mixins.

**Guarded crafting** - the Guarded Pattern Provider hides recipes and holds pushes
until signal conditions pass; priority channels move production between recipes live.

**Job policy** - the ME Job Scheduler keeps stock with admission control (plans that
would stall are never submitted), class pools on named CPUs, guards, rate limits,
deadlines with eviction, and priority preemption. The **ME Job Monitor** turns CPU
activity and stalls into signals.

**Queries** - `tag:c:ores AND NOT craftable AND count<1k` in the ME Query Terminal;
count matches into signals with the Query Sensor; export matches with the Query
Export Bus.

**Fleet config** - the ME Config Terminal audits and edits every configurable device
on the grid, snapshots and diffs, copies settings across a device fleet; the Config
Blueprint captures a region's configuration corner-to-corner and reapplies it.

**Mesh + wireless** - Universal Mesh Endpoints carry items, fluids, energy,
redstone, signals, and ME grid bridging over named frequencies; the **ME Wireless
Bridge** joins machines to your network anywhere inside Wireless Access Point
coverage - AE2's own WAPs count, and the Dense Wireless Access Point is the
32-channel cell tower.

Everything is server-computed, deterministic, gametested (90+ in-game tests in CI),
and documented in an in-game GuideME guide (craft the AE2 Logistics Guide tablet).

## Gallery shot list (capture on playtest)

1. Tracer Terminal sparkline over a busy factory (hero shot)
2. Logic Core screen with a full entry list
3. Job Scheduler rules with states visible (run / hold / late / bumped)
4. Config Terminal diff view (gold/cyan/red)
5. Query Terminal with a live preview
6. A wireless outpost: Dense WAP tower + bridge + machines, no cables
7. Guarded Pattern Provider GUI with PASS/HOLD
8. The advancement tree

## Version metadata

- Version naming: `<mod_version>` (e.g. 0.15.0), file `ae2logistics-1.21.1-<v>.jar`
- Changelog: paste the GitHub release notes for the tag
- Release channel: beta until Jack's playtest pass, then release
