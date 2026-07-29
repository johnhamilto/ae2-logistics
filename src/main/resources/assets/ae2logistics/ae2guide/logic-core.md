---
navigation:
  parent: ae2logistics-index.md
  title: ME Logic Core
  position: 39
  icon: ae2logistics:logic_core
item_ids:
- ae2logistics:logic_core
---

# ME Logic Core

<BlockImage id="logic_core" scale="4" />

Forty cable parts of logic work, but they cost space, break when a cable is nudged,
and read like spaghetti. The Logic Core holds up to **eight logic nodes as list
entries** inside one block - the same evaluators as the physical parts, on the same
deterministic scheduler, configured in one screen.

Craft it from an ME Register Bank, a Regulus Crystal, and an Engineering Processor.

<RecipeFor id="logic_core" />

## Entries

Click a row to select it, then use the detail strip below the list:

- **Type** cycles through const / thresh / hyst / arith / gate / stock / rate /
  count / timer (Redstone Ports stay physical parts - they need a face in the world).
- **out** is the channel the entry writes; **a** and **b** are its input channels.
- The **b=#** toggle switches the second operand between the literal value and
  channel **b**, exactly like the parts' GUI.
- Stock entries take their watched item via the ghost slot: hold an item (or a
  filled bucket) and click the slot.

Entries evaluate in the grid's topological signal order, so a constant feeding an
arithmetic feeding a threshold settles in a single tick even inside one core.

## Channels are the cost

Physical logic parts are channel-free. Core entries are not: **every configured
entry requires a channel**, drawn through the core's own dense node. A full core is
nine channels of pressure - ad-hoc networks cannot fully light one, and a glass
cable segment will starve it. Feed cores from dense cable or a controller face.
Entries without a channel (or power) go dark and write nothing.

That is the trade, stated plainly: the core converts space and fragility into
channel demand.

## Fleet management

Cores support settings transfer: the Config Terminal can copy/paste them, and a
Config Blueprint captures and applies entire configured cores - entries, values,
and watched items included.
