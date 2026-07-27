---
navigation:
  title: Adaptive Patterns
  position: 30
---

# Adaptive Patterns

AE2 matches processing pattern ingredients by exact item identity: a pattern encoded
with oak planks will not accept birch, and a pattern wanting a pickaxe will not accept
one with a single point of damage. Adaptive Patterns fix that — think "Fuzzy Cards, but
for patterns."

Each ingredient of an adaptive pattern has a match mode:

- **Exact** — vanilla behavior.
- **Fuzzy** — same item, ignoring damage, enchantments, and all other components.
  "Any diamond pickaxe."
- **Tag** — any item in a tag. "Any #c:ingots/iron", "any #minecraft:planks."

The item you encoded stays the *canonical* ingredient: substitutes are consumed from
storage when available, but the network only ever autocrafts the canonical one, so
crafting plans stay fast and predictable.

# Pattern Workbench

Craft the Pattern Workbench (a crafting table, an AE2 Fuzzy Card, certus quartz, and
iron). Then:

1. Encode a processing pattern normally in the ME Pattern Encoding Terminal.
2. Put it in the workbench's pattern slot.
3. Click an ingredient in the 3x3 view to cycle its mode: exact, fuzzy, then each of
   the item's tags. Badges mark the mode (F for fuzzy, # for tags); hover for details.
4. Take the resulting Adaptive Processing Pattern and use it in any Pattern Provider,
   exactly like the original.

The conversion keeps inputs and outputs unchanged — only the matching rules differ.
