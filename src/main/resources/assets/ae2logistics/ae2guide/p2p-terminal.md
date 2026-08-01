---
navigation:
  parent: devices-index.md
  title: P2P Frequency Terminal
  position: 61
  icon: ae2logistics:p2p_frequency_terminal
item_ids:
- ae2logistics:p2p_frequency_terminal
---

# P2P Frequency Terminal

P2P tunnels are AE2's most powerful transport tool and its worst ergonomics: linking is
a memory-card ritual against an unlabeled 2x2 color code. The P2P Frequency Terminal
puts the whole system in one table.

Mount it on a cable and it lists **every P2P tunnel on the network**: frequency, tunnel
type, input or output role, and position - refreshed live.

- **Select a row** to work with it; buttons that would do nothing render disabled.
- **Rename** gives the selected row's frequency a human name.
- **Mark target** remembers the selected row's frequency.
- **Retune to target** moves the selected tunnel or mesh endpoint onto the marked
  frequency - build a tunnel array by marking the input once and retuning each
  output in two clicks.

Names live **on the tunnels themselves**, not in the terminal: every terminal on the
network shows the same names, moving or breaking terminals loses nothing, and a name
survives exactly as long as one of its tunnels does. A tunnel retuned onto a named
frequency adopts that name; retuned away, it drops it.

Retuning refuses to create a second input on a frequency, so you cannot break a link by
accident. Roles (input vs output) are still set the usual way when placing tunnels.

# Mesh frequencies

The table also lists every **mesh frequency** that touches this network, with all of its
endpoints server-wide: role, capabilities, whether the endpoint sits on this grid or a
remote one, and a live status - `OK`, `off` (unpowered), or `wait` (an ME endpoint with
no peer). Selecting any mesh row - the header or an endpoint - and pressing **Rename**
retags every loaded endpoint of that frequency in one action; endpoints in unloaded
chunks keep the old frequency until they load and are renamed again.

Mesh rows mark and retune too: **Mark target** on any mesh row remembers that
frequency, and **Retune to target** moves the selected endpoint onto it, keeping its
role, priority, and transports. Only endpoints on THIS network retune from here -
a `remote` row must be retuned from a terminal on its own network.

Craft it from an AE2 Terminal, an ME P2P Tunnel, and a logic processor.

<RecipeFor id="p2p_frequency_terminal" />
