---
navigation:
  parent: ae2logistics-index.md
  title: ME Subnet Core
  position: 41
  icon: ae2logistics:subnet_core
item_ids:
- ae2logistics:subnet_core
---

# ME Subnet Core

Subnetworks are AE2's most powerful technique and its most tedious: real cable, real
space, fragile to one wrong connection, unreadable to anyone who didn't build them.
The Subnet Core holds an **entire subnet inside one block**, configured as a list.

Craft it from a Storage Bus, a Regulus Crystal, and an Engineering Processor.

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
- **uplink**: the subnet sees your MAIN network's storage. An export entry plus an
  uplink feeds machines straight from main storage - the classic interface trick,
  minus the interface, the cable, and the second network.
- **downlink**: your MAIN network sees the subnet's storage - aggregate a wall of
  face inventories into one mount with its own priority. Each downlink is a
  main-grid device and **costs a main channel**, exactly like the physical storage
  bus it replaces.

Click a row to select it; set type, face, and priority below; click the filter slot
with a held item (or bucket) to whitelist. Entries need internal channels (the
subnet is an ad-hoc grid - eight is the natural budget) and go dark when the main
network loses power or the core loses its channel.

## Loops are safe

An uplink and a downlink together form a cycle: main sees the subnet, which sees
main. The core cuts the loop at the second visit - counting, inserting, and
extracting each resolve exactly once, so nothing duplicates and nothing hangs.
Items inserted into a loop still land in exactly one real inventory.

## The trade

Like the Logic Core, the Subnet Core converts space and fragility into channel
pressure and legibility: one block, one list, named parts - and the same routing
semantics AE2 taught you, because underneath it *is* an AE2 grid.
