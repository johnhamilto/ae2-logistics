---
navigation:
  parent: ae2logistics-index.md
  title: ME Job Scheduler
  position: 38
  icon: ae2logistics:job_scheduler
item_ids:
- ae2logistics:job_scheduler
---

# ME Job Scheduler

<BlockImage id="job_scheduler" scale="4" />

AE2 assigns crafting jobs to "the first free CPU that fits" - one bulk job monopolizes
everything and interactive requests starve behind it. The Job Scheduler adds the
missing policy layer for jobs it originates.

Craft it from a Crafting Unit, a Regulus Crystal, and an Engineering Processor.

<RecipeFor id="job_scheduler" />

Four **stock rules**, each: a target item (click the slot with the item or a bucket),
a **floor** to keep stocked, a **batch** size per job, a **class**, and an optional
**guard** signal channel.

Every second, a rule below its floor attempts a craft - with **admission control**:

- The plan must be **complete**. Jobs that would provably stall on missing
  ingredients are never submitted; the rule shows `missing` and retries later.
- A CPU from the rule's **class pool** must be free. `bulk` rules use unnamed CPUs or
  ones named `bulk...`; `maint` rules require CPUs named `maint...` (name a Crafting
  Storage with an anvil). CPUs set to **Player only** in AE2's own crafting screen
  are never touched - that is your interactive reservation, respected end to end.
- Attempts are **rate-limited** (ten seconds between tries per rule), so restocking
  spreads out instead of thundering.
- A **guard** channel below or at zero holds the rule entirely - compose with logic
  parts, query sensors, or the Job Monitor for "only restock while the furnace array
  is idle".

Each rule's second line adds two policy controls:

- **Deadline** (seconds; 0 = off): a watchdog on wall-clock time since submission.
  A job that overruns - typically because a machine jammed and the push stalled -
  is **evicted**: canceled so its CPU frees, shown as `late`, and re-admitted on the
  next attempt. Pairs naturally with the Job Monitor's stall channels.
- **Preempt / polite**: a preempting rule that finds no free CPU in its pool may
  cancel the **youngest** running job of a *lower-priority rule* (higher row) *in the
  same class pool* - rule order is priority order. The victim shows `bumped` and
  retries later. Only jobs this scheduler submitted are ever touched; other players'
  and machines' jobs are not ours to cancel.

The row status shows what each rule is doing: idle, plan, missing, no CPU, run, hold,
wait, late, bumped - and the Job Monitor sees scheduler jobs like any others.

Scheduler rules ride settings transfer: the Config Terminal copies them between
schedulers and a Config Blueprint captures them with the rest of a region.
