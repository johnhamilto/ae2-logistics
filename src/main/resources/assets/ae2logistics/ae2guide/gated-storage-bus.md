---
navigation:
  parent: devices-index.md
  title: ME Gated Storage Bus
  position: 32
  icon: ae2logistics:gated_storage_bus
item_ids:
- ae2logistics:gated_storage_bus
- ae2logistics:conform_card
- ae2logistics:stack_limiter_card
---

# ME Gated Storage Bus

<ItemImage id="gated_storage_bus" scale="2" />

A storage bus that takes **input cards** - upgrade cards that shape what the bus
will *accept*. Everything else is the storage bus you know: the same GUI,
partition slots, fuzzy/inverter/capacity/void cards, access modes, priority, and
memory-card support. A refused insert simply falls through to the rest of your
storage, so the cards carve out lanes without ever trapping items.

AE2's stock storage bus only consults its own cards, which is why these cards
need this bus (the [ME Subnet Link](subnet-link.md) takes them too). It also
takes the [Variant Card](variant-card.md) and the [Query Card](queries.md),
which replace the partition outright.

<RecipeFor id="gated_storage_bus" />

## Conform Card

<ItemImage id="conform_card" scale="2" />

**The target's live contents are the filter.** The bus accepts only what the
inventory already holds - no partition config at all: you configure the bus by
seeding the chest. Drop one stack of each item a barrel-wall lane should hold
and the lane keeps that assortment forever.

- An empty target accepts nothing; the card is inert until seeded.
- Extract a type to zero and its door closes. The card tracks contents, not
  history.
- Partition slots still narrow further (intersection). A **Fuzzy Card** widens
  the contains-check exactly as it widens partitions.
- **Inverter Card** flips it: accept only what is *not* yet present - a
  self-deduplicating collection chest that takes one of each new thing, ever.

<RecipeFor id="conform_card" />

## Stack Limiter Card

<ItemImage id="stack_limiter_card" scale="2" />

**Items go in one at a time.** The bus inserts a single item, and only while the
target holds no items at all - the next single arrives after something takes the
first. Strict one-by-one delivery for machines and contraptions that misbehave
when handed a stack, without hopper-timing rigs. Non-item types pass unchanged.

<RecipeFor id="stack_limiter_card" />
