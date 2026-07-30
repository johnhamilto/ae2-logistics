---
navigation:
  parent: devices-index.md
  title: Pattern Workbench
  position: 40
  icon: ae2logistics:pattern_workbench
item_ids:
- ae2logistics:pattern_workbench
---

# Pattern Workbench

<BlockImage id="pattern_workbench" scale="4" />

The editing bench for encoded patterns: it turns processing patterns into
[Adaptive Processing Patterns](adaptive-processing-pattern.md) and wraps any
encoded pattern into a [Guarded Pattern](guarded-pattern.md).

- Insert an encoded pattern; the 3x3 grid shows its ingredients.
- **Click an ingredient** to cycle exact, fuzzy (damage bands for damageables),
  then each of its tags. **Click holding an item** to add an any-of alternative.
  **Shift-click** resets to exact; **Ctrl-click** toggles the catalyst flag.
- Badges mark non-exact specs (`F`/`99`/`75`/`50`/`25`, `#` for tag, `A<n>` for
  any-of, gold `C` for catalyst); hover for the full spec.
- The **guard strip** below wraps the pattern with a signal condition -
  channel, operator, value - and unwraps it later.
- Adaptive matching applies to processing patterns; crafting, smithing, and
  stonecutting patterns match exactly, but guards still apply to them.

<RecipeFor id="pattern_workbench" />

See [Adaptive Patterns](adaptive-patterns.md) and
[Guarded Crafting](guarded-crafting.md) for the systems behind both halves.
