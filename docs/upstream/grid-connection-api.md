> **SUPERSEDED (2026-07-28):** `GridHelper.createConnection(a, b)` is public API in
> the 19.2 line after all - this draft proposed backporting a method that already
> exists under a different name than 26.x uses. Kept for the record.

# Proposal: public grid-connection API for the 1.21.1 line

**Target:** AE2 19.2.x (Minecraft 1.21.1). The 26.x line already has
`IGridHelper.createGridConnection`; this proposes backporting the public surface.

## Problem

Addons that build virtual or long-range ME topology (quantum-bridge-style links,
wireless connectors, virtual subnets) need to create grid connections between two
nodes that are not physically adjacent. On 19.2.x the only path is the internal
`appeng.me.GridConnection.create(a, b, null)`, which:

- is not API (breaks on internal refactors),
- throws `IllegalStateException` on duplicate connections rather than reporting,
- has no discoverability for addon authors.

## Our use case

AE2 Logistics' mesh-ME endpoints fuse networks with a virtual star of connections
(deterministic hub, `DENSE_CAPACITY` spokes). Everything else in the feature uses
public API; this one call is the single internal dependency, and it is
version-sensitive for every port.

## Proposed API

```java
// appeng.api.networking.GridHelper
@Nullable
static IGridConnection createGridConnection(IGridNode a, IGridNode b)
        throws FailedConnectionException;
```

Mirroring the 26.x shape. Returning the connection allows callers to destroy() it;
`FailedConnectionException` (or a nullable return) covers the already-connected case
without exceptions-as-control-flow.

## Compatibility

Pure addition; the internal class already implements the behavior. The 26.x signature
exists, so backporting keeps addon code identical across lines.
