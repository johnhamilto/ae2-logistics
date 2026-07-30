---
navigation:
  parent: devices-index.md
  title: ME Subnet Core
  position: 31
  icon: ae2logistics:subnet_core
item_ids:
- ae2logistics:subnet_core
---

# ME Subnet Core

<BlockImage id="subnet_core" scale="4" />

Subnetworks are AE2's most powerful technique and its most tedious: real cable, real
space, fragile to one wrong connection, unreadable to anyone who didn't build them.
The Subnet Core holds an **entire subnet inside one block**, configured as a list.

Craft it from a Storage Bus, a Regulus Crystal, and an Engineering Processor.

<RecipeFor id="subnet_core" />

## How it works

The core is one machine on your main network (one channel). Inside it runs a
genuinely **separate internal grid**, powered through the core the way a quartz
fiber shares power - no cables, no accidental merging. Up to eight entries:

- **storage** (face-bound): mounts the inventory behind that face into the subnet,
  with a priority and an optional filter. Items and fluids.
- **import** (face-bound): pulls items from the face inventory into subnet storage,
  eight per operation, every half second.
- **export** (face-bound): pushes items from subnet storage into the face inventory
  at the same rate; combine with a filter for machine feeding.
- **from main**: the main network's storage appears INSIDE the subnet. An export
  entry plus a from-main entry feeds machines straight from main storage - the
  classic interface trick, minus the interface, the cable, and the second network.
- **to main**: the subnet's storage appears ON the main network - aggregate a wall
  of face inventories into one mount with its own priority. A to-main entry is a
  main-grid device and **costs a main channel**, exactly like the physical storage
  bus it replaces.
- **port** (face-bound): the subnet made physical. The core exposes the INTERNAL
  grid on that face instead of the main network - cable it to hang real ME devices
  on the subnet: terminals, drives, buses, anything. They share the subnet's ad-hoc
  channels and its power-through-the-core, and go dark with it.

The two link types are named for whose storage appears where: *from main* pulls
main's storage into the subnet's view; *to main* pushes the subnet's storage into
main's view. Both are windows, not conveyors - items flow through them in either
direction when something inserts or extracts.

Click a row to select it; set type, face, and priority below; click the filter slot
with a held item or bucket - or drag from JEI - to whitelist **the selected
entry**: buses move only the filtered thing, and a filtered from-main/to-main
window shows only it. Ports take no filter. Entries need internal channels (the
subnet is an ad-hoc grid - eight is the natural budget) and go dark when the main
network loses power or the core loses its channel.

## The one rule

Import and export entries move items to and from **the subnet's storage** - never
the main network directly - and a fresh subnet has NO storage. Every working core
pairs a *mover* (import/export) with *storage* (a from-main or storage entry):

- **Chest into the main network**: an import entry on the chest **plus a from-main
  entry**. The import pulls into subnet storage; from-main IS the main network's
  storage; items land in your cells.
- **Feed a machine from main storage**: an export entry on the machine plus a
  from-main entry.
- **A chest wall as one mount**: storage entries on each face plus a **to-main
  entry** - main sees the whole wall as one storage with one priority.
- **A pipe**: an import entry on one face plus a storage entry on another moves
  items between two inventories without ever touching main.

`/ae2logistics subnet status` (while looking at the core) prints every entry's
state and calls out a subnet whose items would have nowhere to go.

## Loops are safe

A from-main and a to-main entry together form a cycle: main sees the subnet, which
sees main. The core cuts the loop at the second visit - counting, inserting, and
extracting each resolve exactly once, so nothing duplicates and nothing hangs.
Items inserted into a loop still land in exactly one real inventory.

## The trade

Like the Logic Core, the Subnet Core converts space and fragility into channel
pressure and legibility: one block, one list, named parts - and the same routing
semantics AE2 taught you, because underneath it *is* an AE2 grid.
