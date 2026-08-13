---
navigation:
  parent: ae2logistics-index.md
  title: Wireless Bridging
  position: 40
  icon: ae2logistics:wireless_bridge
---

# Wireless Bridging

AE2 has always had a spatial coverage mechanic - Wireless Access Points - and nothing
ever used it but terminals. Wireless bridging promotes your access points into
logistics infrastructure: machines can participate in the network without a cable
path, **but only inside coverage**.

For short point-to-point hops with no access point at all, the
<ItemLink id="wireless_connector" /> is the other wireless: a colored
cable-through-air part with a 16-block base reach. This page is the
infrastructure tier; the connector has its own page.

## ME Wireless Bridge

<BlockImage id="wireless_bridge" scale="4" />

Craft it from a Wireless Receiver, a Regulus Crystal, and a Calculation Processor.

<RecipeFor id="wireless_bridge" />

**Anchor before placing**: click any access point (AE2's or a Dense one) with the
bridge item to bind it to that access point's network, then place the bridge
anywhere. The placed bridge continuously looks for the **nearest active access point
of that network in range** and joins its grid through it. Cables, machines, storage
buses, mesh endpoints - anything attached to the bridge becomes part of the remote
network while coverage holds.

- **Availability is binary.** In coverage the bridge is a full dense link; out of
  coverage it is dark. No degraded fringe - exactly like channels and power.
- **Channels flow through the serving access point.** The bridge is a dense carrier,
  but everything it brings still paths through the access point serving it - an
  access point on thin cable is a thin pipe. This is the balance rule: wireless is
  never a channel bypass.
- **Handover is automatic.** If the serving access point loses power, channels, or
  existence, the bridge re-associates to another one in range within a second.
- Same-dimension only; if the anchor access point is gone or unloaded, the bridge
  waits. `/ae2logistics wireless status` prints anchor, serving AP, and grid size
  while looking at a bridge.

## Dense Wireless Access Point

<BlockImage id="dense_wireless_access_point" scale="4" />

AE2's access points serve bridges, but they are channel-starved carriers for machine
work. The Dense Wireless Access Point (Wireless Access Point + Regulus + Engineering
Processor) is the scaling tier: a **32-channel dense carrier** with a fixed 32-block
range, built to be the cell tower your bridged outposts hang from. Feed it dense
cable or a controller face, or the fat pipe chokes at its own uplink.

<RecipeFor id="dense_wireless_access_point" />

## Composition

A Universal Mesh Endpoint on a bridged segment works wirelessly - items, fluids,
energy, redstone, and signals to anywhere in coverage. A Logic Core on a bridged
segment computes with the main network's signals. Coverage planning - how many
towers, where, with what uplink - becomes part of factory design, which is the whole
point.

## Devices

<ItemLink id="wireless_bridge" /> joins machines through coverage from AE2 WAPs or the <ItemLink id="dense_wireless_access_point" />.
<ItemLink id="wireless_connector" /> lays cable through the air instead: short range, color-paired, channel-honest.
