---
navigation:
  title: Guarded Crafting
  position: 35
---

# Guarded Crafting

Pattern selection in AE2 is static: fixed priorities, and the only dynamic input is
whether ingredients exist. Guarded crafting makes pattern availability a **signal**: use
the cheap recipe unless a byproduct is backed up, stop this line while power headroom is
low, switch recipes when stock crosses a threshold.

# Regulus

The control tier is built from **Regulus Crystal**, this mod's resource. It forms the
way Fluix does: drop a **Charged Certus Quartz Crystal**, **Redstone**, and **Glowstone
Dust** into water and they fuse into two Regulus Crystals.

# The Guarded Pattern Provider

Craft it from a Pattern Provider, a Regulus Crystal, and a Logic Processor. It is a full
pattern provider - nine pattern slots, pushes to adjacent machines, visible in pattern
access terminals - with a gate in front:

- **Guard**: a signal channel compared against a constant (`<`, `<=`, `=`, `>=`, `>`).
  While the guard fails, every pattern in this provider is **invisible to the planner**:
  jobs route to other recipes instead of starting and stalling. Leave the channel empty
  for no guard.
- **Gate: plan + push** (default): a failing guard also refuses pushes, so a job that is
  already running holds - and resumes by itself when the guard opens. The Job Monitor
  will show such a job as stalled; the systems are designed to meet. Switch to
  **plan only** if running jobs should always finish.
- **Priority channel**: when set, the provider's priority is the live signal value
  instead of the fixed number. Priority decides **which recipe the planner prefers**
  when several providers can make the same thing - so a signal can move production
  between recipes on the fly. (Note: identical patterns in several providers are
  round-robined at push time; give competing providers different recipes.)

Complex conditions belong in the logic graph: compute them with logic parts into one
channel, then guard on `channel > 0`.

Guard flips are detected every ten ticks, so changes take effect within half a second.
A plan computed while a guard was open keeps its patterns even if the guard closes
before submission - the push gate is what stops it then.

# Guarded Patterns

The **Pattern Workbench** wraps any encoded pattern behind its own guard: insert the
pattern, type a channel, pick an operator and value, and press **Wrap**. The result is a
Guarded Pattern that plans and pushes only while its condition holds - letting two
recipes for the same output live in ONE provider with opposite guards. **Unwrap**
restores the original pattern.

Per-pattern guards are enforced by the Guarded Pattern Provider. In a plain pattern
provider a Guarded Pattern behaves exactly like its inner pattern - the guard is inert.
