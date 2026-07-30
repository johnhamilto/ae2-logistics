---
navigation:
  parent: devices-index.md
  title: ME Subnet Link
  position: 32
  icon: ae2logistics:subnet_link
item_ids:
- ae2logistics:subnet_link
---

# ME Subnet Link

<ItemImage id="subnet_link" scale="2" />

A quartz fiber, an empty interface, and a storage bus in one cable part. Its face
carries a **genuinely separate subnet** - run cable from it and build with real AE2
devices - while power passes through like a fiber and a configurable storage
window links the two networks. One channel on the main network, exactly like the
storage bus it replaces.

- **Window modes** (right-click cycles back):
  - *Subnet sees main* - the classic interface trick: subnet devices pull from and
    push to main storage.
  - *Main sees subnet* - the subnet's storage appears on main at the link's
    priority, like a storage bus on a subnet interface.
  - *Both ways* - both windows at once, loop-safe (each transfer resolves exactly
    once).
- Nine whitelist filter slots narrow the window (click, bucket-click, or drag from
  JEI); a priority orders it among the target network's storage.
- Devices on the subnet power through the link and go dark with the main network.
  No controller on the subnet means AE2's normal ad-hoc limit of eight channels.

<RecipeFor id="subnet_link" />

For a whole subnet in one block with virtual entries instead of real devices, see
the [ME Subnet Core](subnet-core.md) - the link is the primitive it is built from.
