---
navigation:
  parent: devices-index.md
  title: ME Job Monitor
  position: 36
  icon: ae2logistics:job_monitor
item_ids:
- ae2logistics:job_monitor
---

# ME Job Monitor

<ItemImage id="job_monitor" scale="2" />

Turns autocrafting activity into [signal](signals.md) channels, so logic can react
to what the CPUs are doing.

- Channels: `<prefix>:active`, `:idle`, `:stalled`, and `:pending` (items
  outstanding). The prefix defaults to `craft`.
- **Stalled** means a busy CPU whose progress has not changed for the configured
  window - a jammed machine or a blocked provider face.
- Name a Crafting Storage with an anvil BEFORE assembling the CPU and the cluster
  gets its own `<prefix>:<name>/remaining` and `/stalled` channels.
- One monitor per prefix per network: two on the same prefix sum and double every
  count (the standard multi-writer rule).

<RecipeFor id="job_monitor" />
