# Proposal: filter-provider API for view cells and bus filters

**Target:** AE2, any current line.

## Problem

Terminal view cells and bus/cell-partition filtering are built from finite key sets
(`IPartitionList` assembled by internal statics from config inventories). An addon
item cannot contribute a *predicate* - "mod:ae2", "tag:c:ores AND NOT craftable",
anything intensional - because the pipeline only consults AE2's own item classes and
only understands enumerable keys.

## Our use case

AE2 Logistics ships a query language (named, per-network expressions) with its own
terminal, sensor, and export bus. Binding those queries to AE2's *own* view cells,
export/storage bus filters, and cell partitions is the natural completion - and is
currently impossible without mixins into terminal repo filtering and bus logic.

## Proposed API sketch

```java
// new: appeng.api.storage.IFilterProviderItem
interface IFilterProviderItem {
    /** Stack-derived predicate; evaluated wherever partition lists are consulted. */
    AEKeyFilter createFilter(ItemStack stack, @Nullable IGrid context);
}
```

- `ViewCellItem.createItemFilter(...)` and bus filter assembly check for the interface
  before falling back to config-inventory partition lists.
- `IPartitionList` gains a predicate-backed implementation (it is nearly one already).
- The optional grid context lets filters resolve network-stored definitions; client-side
  consumers (terminal repo) pass null and items embed a snapshot.

## Compatibility

Additive. Existing view cells implement the interface trivially via their current
partition list.
