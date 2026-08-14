---
navigation:
  parent: devices-index.md
  title: Variant Card
  position: 34
  icon: ae2logistics:variant_card
item_ids:
- ae2logistics:variant_card
- ae2logistics:variant_import_bus
- ae2logistics:variant_export_bus
---

# Variant Card

<ItemImage id="variant_card" scale="2" />

**A configured item becomes a template.** With the card installed, a config slot
matches any item that agrees with every component the template carries -
components the template does not carry are ignored. A plain enchanted book
matches every enchanted book. A Mending-only book matches exactly Mending-only
books. A renamed paper matches any paper with that name, whatever else rides on
it.

Where AE2's Fuzzy Card asks "same item, roughly?", the Variant Card asks "does
it agree with what I deliberately configured?" - and only about what you
configured.

<RecipeFor id="variant_card" />

## ME Variant Import Bus

<ItemImage id="variant_import_bus" scale="2" />

An import bus that takes the card: its filter slots become templates. One plain
enchanted book pulls every enchanted book out of the machine; iron next to them
stays put. Without the card it is a normal import bus, stock cards and all.

<RecipeFor id="variant_import_bus" />

## ME Variant Export Bus

<ItemImage id="variant_export_bus" scale="2" />

An export bus that takes the card: each config slot exports **all stored
variants** matching its template. One slot empties the network of enchanted
books; a Mending-only template exports exactly those. The Crafting Card is
ignored while the Variant Card is in - crafting produces exact items, and
"craft me any variant" has no honest answer.

<RecipeFor id="variant_export_bus" />

## On storage buses

The [Gated Storage Bus](gated-storage-bus.md) and
[Subnet Link](subnet-link.md) take the card too: their partition slots become
templates, so one plain book partitions a barrel for every enchanted book. With
a [Conform Card](gated-storage-bus.md) alongside it, the contains-check widens
the same way: the chest accepts any variant of whatever it already holds.
