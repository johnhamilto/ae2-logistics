---
navigation:
  parent: ae2logistics-index.md
  title: Logic Parts
  position: 20
  icon: ae2logistics:logic_gate
---

# Logic Parts

<ItemGrid>
  <ItemIcon id="constant" />
  <ItemIcon id="threshold" />
  <ItemIcon id="hysteresis" />
  <ItemIcon id="arithmetic" />
  <ItemIcon id="logic_gate" />
  <ItemIcon id="redstone_port" />
  <ItemIcon id="stock_sensor" />
  <ItemIcon id="rate" />
  <ItemIcon id="counter" />
  <ItemIcon id="timer" />
</ItemGrid>

Logic parts mount on cables like buses. Right-click one to configure it: which channels
it reads, which channel it writes, and its constants. Parts use no AE2 channels and idle
at almost no power.

All parts on a network evaluate **once per tick, in dependency order** — a chain of five
parts reacts within a single tick. Feedback loops are allowed and advance one step per
tick. If two parts write the same channel, the values add.

# The parts

- **Signal Constant** — writes a fixed value. This is how you set setpoints.
- **Signal Threshold** — compares input A against a constant or channel B
  (`<`, `<=`, `=`, `>=`, `>`), writes 1 or 0.
- **Signal Hysteresis** — the farm controller. Latches to 1 when input drops below the
  low setpoint, back to 0 when it rises above the high one. No flicker at the boundary.
- **Signal Arithmetic** — `+ - x / min max mod`, saturating at zero and the long limit.
- **Signal Logic Gate** — AND, OR, XOR, NOT over zero/nonzero.
- **Redstone Signal Port** — the bridge to the world. Input mode writes the face's
  redstone level (0-15) to a channel; output mode emits a channel as redstone.
- **Signal Stock Sensor** — writes the network's stored amount of an item to a channel.
  Configure it by clicking the slot in its GUI with the item to watch.
- **Signal Rate Meter** — how fast a channel is growing, per second, over a window.
- **Signal Counter** — counts rising edges of a channel; optional wrap and reset channel.
- **Signal Timer** — emits a pulse train: 1 for the first N ticks of every M-tick period.

# ME Job Monitor

The Job Monitor turns autocrafting activity into signals. Mount one on a cable and it
polls every crafting CPU on the network each tick, driving four channels under a
configurable prefix (default `craft`):

<RecipeFor id="job_monitor" />

- `craft:active` — jobs currently running; `craft:idle` — CPUs with nothing to do.
- `craft:stalled` — jobs that have made **no progress** for the configured window
  (default 10 seconds): a blocked provider face, a missing ingredient, a machine jam.
- `craft:pending` — items still outstanding across all running jobs.

Name a Crafting Storage block (an anvil does it) and its cluster gets its own channels:
`craft:<name>/remaining` and `craft:<name>/stalled`. Naming a CPU is how you opt a
production line into detailed monitoring.

These are ordinary signals: watch them on the Tracer Terminal, alarm on
`craft:stalled` with a Threshold into a Redstone Port, or bridge them across networks
with a signal mesh. One monitor per network — two on the same prefix double every count.

# Memory cards

AE2 memory cards work on every part in this mod: shift-click to save a part's full
settings (a logic part's channels and constants, the sensor's watched item, a mesh
endpoint's frequency, role, priorities, and filters), click to apply to another part of
the same type.

# A worked example: keep 10-50k iron in stock

1. **Stock Sensor** watching iron ingots, output `factory:iron_count`.
2. **Hysteresis** with input `factory:iron_count`, low `10000`, high `50000`, output
   `factory:iron_farm`.
3. **Redstone Signal Port** in output mode, input `factory:iron_farm`, facing your
   farm's enable line.

The farm turns on below 10k, off above 50k, and never chatters in between — the thing a
plain Level Emitter cannot do.

## Devices

One page per part under [Devices](devices-index.md): <ItemLink id="constant" />, <ItemLink id="threshold" />, <ItemLink id="hysteresis" />, <ItemLink id="arithmetic" />, <ItemLink id="logic_gate" />, <ItemLink id="redstone_port" />, <ItemLink id="stock_sensor" />, <ItemLink id="rate" />, <ItemLink id="counter" />, <ItemLink id="timer" /> - plus the <ItemLink id="logic_core" /> that hosts eight entries in one block.
