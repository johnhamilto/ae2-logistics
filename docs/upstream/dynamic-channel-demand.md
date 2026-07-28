# Proposal: dynamic per-node channel demand

**Target:** AE2, any current line.

## Problem

`GridFlags.REQUIRE_CHANNEL` is boolean: a node uses exactly zero or one channel
(`DENSE_CAPACITY` only affects carrying). A device whose channel cost should depend on
its configuration - a multi-transport tunnel using one channel per active capability, a
virtual subnet block hosting N internal machines - cannot express that. Addons either
under-charge (everything for one channel) or over-charge (a node per capability, with
node-lifecycle complexity).

## Our use case

AE2 Logistics' Universal Mesh Endpoint carries up to six transport capabilities on one
part for one channel. We would prefer honest costing - channels equal to active
capabilities - and the same mechanism would let a virtual-subnet block charge for its
internal machine count.

## Proposed API

```java
// appeng.api.networking.IManagedGridNode / GridNode
void setChannelDemand(int channels); // default 1 when REQUIRE_CHANNEL, 0 otherwise
```

The pather sums demand instead of counting flagged nodes. Changing demand triggers a
repath like flag changes do today.

## Compatibility

Default behavior unchanged (demand = flag ? 1 : 0). Pathing already aggregates per-node
usage internally; this exposes the count instead of the boolean.
