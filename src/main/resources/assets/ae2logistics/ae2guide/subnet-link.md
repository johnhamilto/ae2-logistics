---
navigation:
  parent: devices-index.md
  title: ME Subnet Link
  position: 31
  icon: ae2logistics:subnet_link
item_ids:
- ae2logistics:subnet_link
---

# ME Subnet Link

<ItemImage id="subnet_link" scale="2" />

A storage bus whose target is a **subnet** instead of an inventory. Its face
carries a genuinely separate grid: run cable from it and build with real AE2
devices. Power passes through like a quartz fiber - the subnet lives and dies
with the main network - and the main network mounts the subnet's storage.

**It IS a storage bus**, config and all: the same GUI, the same 63 partition
slots, the same fuzzy/inverter/capacity/void cards, access modes,
filter-on-extract, priority, and memory-card support. If you know how to
configure a storage bus, you already know this part. One channel on the main
network, exactly like the bus it replaces. It also takes the
[input cards](gated-storage-bus.md) - Conform and Stack Limiter - gating what
the main network may push into the subnet.

- Devices on the subnet share its power feed and AE2's normal ad-hoc limit of
  eight channels (add a controller on the subnet for more).
- Push into the subnet by inserting into main at the link's priority (partition
  it and set it high - the storage-bus idiom); pull the same way.
- The classic builds: an overflow/void wall, an isolated mass-storage wing,
  member storage a terminal on the subnet can see but main filters, or a
  machine island whose clutter never reaches your main storage list.

<RecipeFor id="subnet_link" />
