---
navigation:
  title: ME Job Scheduler
  position: 38
---

# ME Job Scheduler

AE2 assigns crafting jobs to "the first free CPU that fits" - one bulk job monopolizes
everything and interactive requests starve behind it. The Job Scheduler adds the
missing policy layer for jobs it originates.

Craft it from a Crafting Unit, a Regulus Crystal, and an Engineering Processor.

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

The row status shows what each rule is doing: idle, plan, missing, no CPU, run, hold,
wait - and the Job Monitor sees scheduler jobs like any others.
