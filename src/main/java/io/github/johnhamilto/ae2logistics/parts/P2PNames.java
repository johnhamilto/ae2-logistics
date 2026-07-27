package io.github.johnhamilto.ae2logistics.parts;

import appeng.api.networking.IGrid;
import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * P2P frequency names live on the tunnels themselves: a data attachment on each tunnel's
 * hosting cable-bus block entity maps the part's side to a name. Terminals are stateless
 * readers, renaming writes to every tunnel of the frequency, and a name survives exactly
 * as long as any one of its tunnels does.
 */
public final class P2PNames {

    private P2PNames() {
    }

    public static String nameOn(P2PTunnelPart<?> tunnel) {
        var side = tunnel.getSide();
        if (side == null) {
            return "";
        }
        var host = tunnel.getHost().getBlockEntity();
        var map = host.getExistingDataOrNull(AE2Logistics.P2P_NAMES);
        return map == null ? "" : map.getOrDefault(side.getName(), "");
    }

    /** First non-blank name among the frequency's tunnels on this grid. */
    public static String resolve(IGrid grid, short frequency) {
        for (var node : grid.getNodes()) {
            if (node.getOwner() instanceof P2PTunnelPart<?> tunnel && tunnel.getFrequency() == frequency) {
                var name = nameOn(tunnel);
                if (!name.isBlank()) {
                    return name;
                }
            }
        }
        return "";
    }

    /** Writes (or clears, when blank) the name on every tunnel of the frequency. */
    public static void rename(IGrid grid, short frequency, String name) {
        var trimmed = name.length() > 32 ? name.substring(0, 32) : name;
        for (var node : grid.getNodes()) {
            if (node.getOwner() instanceof P2PTunnelPart<?> tunnel && tunnel.getFrequency() == frequency) {
                write(tunnel, trimmed);
            }
        }
    }

    public static void write(P2PTunnelPart<?> tunnel, String name) {
        var side = tunnel.getSide();
        if (side == null) {
            return;
        }
        var host = tunnel.getHost().getBlockEntity();
        var map = host.getData(AE2Logistics.P2P_NAMES);
        if (name.isBlank()) {
            map.remove(side.getName());
        } else {
            map.put(side.getName(), name);
        }
        host.setChanged();
    }
}
