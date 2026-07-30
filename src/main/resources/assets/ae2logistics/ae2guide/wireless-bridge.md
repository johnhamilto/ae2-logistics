---
navigation:
  parent: devices-index.md
  title: ME Wireless Bridge
  position: 66
  icon: ae2logistics:wireless_bridge
item_ids:
- ae2logistics:wireless_bridge
---

# ME Wireless Bridge

<BlockImage id="wireless_bridge" scale="4" />

Joins the machines around it to a remote network through wireless access point
coverage - cable-free outposts.

- **Anchor it** by clicking any WAP (AE2's or a
  [Dense WAP](dense-wireless-access-point.md)) with the bridge item, then place
  the bridge; it associates with the nearest in-range access point on that
  network and re-tunes on a timer as coverage changes.
- Machines and cables touching the bridge join the anchored network; channels
  path through the serving access point.
- Out of coverage, the bridge idles until an access point returns.

<RecipeFor id="wireless_bridge" />

Coverage rules and handover live in [Wireless](wireless.md).
