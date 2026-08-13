package io.github.johnhamilto.ae2logistics.wireless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.level.Level;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.util.AEColor;

import io.github.johnhamilto.ae2logistics.parts.WirelessConnectorPart;

/**
 * Server-global registry of loaded wireless connectors, bucketed per level (short-range
 * wireless is same-dimension by definition). Each tick folds every connector's live
 * state - position, color, booster count, node presence - into a per-level membership
 * hash; when it changes, that level's air links are torn down and relaid: every
 * color-compatible pair in MUTUAL range (min of the two ranges, boosters being
 * per-endpoint) gets a real grid connection, exactly as if a cable ran between them.
 * A full mesh within a group is O(n^2) connections by design (DESIGN F11.8): pruning
 * would break the cables-in-air model. Teardown splits grids synchronously (the
 * MeshRegistry precedent), so churn on place/break/recolor/boost is safe; a connector
 * in an unloading chunk drops its node, its links die with it, and the hash change
 * sweeps the tracked list on the next tick.
 */
public final class WirelessLinkRegistry {

    private static final Map<Level, Set<WirelessConnectorPart>> BY_LEVEL = new HashMap<>();
    private static final Map<Level, Long> MEMBERSHIP = new HashMap<>();
    private static final Map<Level, List<IGridConnection>> LINKS = new HashMap<>();

    private WirelessLinkRegistry() {
    }

    public static void register(WirelessConnectorPart part) {
        var level = part.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BY_LEVEL.computeIfAbsent(level, l -> new LinkedHashSet<>()).add(part);
    }

    public static void unregister(WirelessConnectorPart part) {
        var level = part.getLevel();
        var set = BY_LEVEL.get(level);
        if (set != null) {
            set.remove(part);
            if (set.isEmpty()) {
                BY_LEVEL.remove(level);
                MEMBERSHIP.remove(level);
                LINKS.getOrDefault(level, List.of()).forEach(IGridConnection::destroy);
                LINKS.remove(level);
            }
        }
    }

    public static void clear() {
        LINKS.values().forEach(links -> links.forEach(IGridConnection::destroy));
        LINKS.clear();
        MEMBERSHIP.clear();
        BY_LEVEL.clear();
    }

    /** AE2's cable rule: same color pairs, and fluix (TRANSPARENT) pairs with anything. */
    public static boolean colorsCompatible(AEColor a, AEColor b) {
        return a == b || a == AEColor.TRANSPARENT || b == AEColor.TRANSPARENT;
    }

    /** Mutual reach: the shorter of the two ranges must cover the distance. */
    public static boolean inMutualRange(WirelessConnectorPart a, WirelessConnectorPart b) {
        double range = Math.min(a.rangeBlocks(), b.rangeBlocks());
        return a.hostPos().distSqr(b.hostPos()) <= range * range;
    }

    private static boolean linkable(WirelessConnectorPart a, WirelessConnectorPart b) {
        return a.node() != null && b.node() != null
                && colorsCompatible(a.color(), b.color())
                && inMutualRange(a, b);
    }

    /** Once per server tick: relay a level's links only when its membership hash moves. */
    public static void tick() {
        for (var entry : BY_LEVEL.entrySet()) {
            long membership = 1;
            for (var part : entry.getValue()) {
                membership = membership * 31 + part.stableKey();
                membership = membership * 31 + part.color().ordinal();
                membership = membership * 31 + part.boosters();
                membership = membership * 31 + (part.node() == null ? 0 : 1);
            }
            if (MEMBERSHIP.getOrDefault(entry.getKey(), 0L) == membership) {
                continue;
            }
            MEMBERSHIP.put(entry.getKey(), membership);
            rebuild(entry.getKey(), List.copyOf(entry.getValue()));
        }
    }

    private static void rebuild(Level level, List<WirelessConnectorPart> parts) {
        // Destroying a connection splits grids synchronously (only repath is deferred),
        // so relaying from scratch is safe and keeps eligibility in one place.
        LINKS.getOrDefault(level, List.of()).forEach(IGridConnection::destroy);
        var links = new ArrayList<IGridConnection>();
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                var a = parts.get(i);
                var b = parts.get(j);
                if (!linkable(a, b)) {
                    continue;
                }
                try {
                    links.add(GridHelper.createConnection(a.node(), b.node()));
                } catch (IllegalStateException ignored) {
                    // the pair is already directly connected
                }
            }
        }
        if (links.isEmpty()) {
            LINKS.remove(level);
        } else {
            LINKS.put(level, links);
        }
    }
}
