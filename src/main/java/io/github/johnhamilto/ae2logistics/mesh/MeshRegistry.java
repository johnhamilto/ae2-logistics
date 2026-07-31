package io.github.johnhamilto.ae2logistics.mesh;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;
import io.github.johnhamilto.ae2logistics.signal.SignalMath;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * Server-global registry of mesh endpoints, keyed by frequency name - but frequencies
 * NEVER cross networks: every operation partitions endpoints by their live host grid,
 * so the network an endpoint sits on is the carrier, exactly like AE2's own P2P.
 * (A global map instead of a grid service only so partitions follow grid splits and
 * merges for free.) Item and fluid delivery is sticky per tick so a pattern provider's
 * batch lands on one machine; a thread-local depth guard gives every transfer a hop
 * budget of one, which makes loops impossible.
 */
public final class MeshRegistry {

    public static final int TYPE_REDSTONE = 1;
    public static final int TYPE_ITEM = 2;
    public static final int TYPE_FLUID = 4;
    public static final int TYPE_ENERGY = 8;
    public static final int TYPE_SIGNAL = 16;
    public static final int TYPE_ME = 32;
    public static final int TYPE_PROVIDER = 64;
    public static final int TYPE_ALL = 127;

    // ME link state per endpoint, evaluated whenever the frequency's lanes (re)build.
    public static final byte ME_STATE_NONE = 0;
    public static final byte ME_STATE_WAITING = 1;
    public static final byte ME_STATE_LINKED = 2;
    public static final byte ME_STATE_LOOP = 3;
    public static final byte ME_STATE_STANDBY = 4;

    // Overall endpoint status for UI and commands. An already-cabled frequency
    // (ME_STATE_LOOP) is deliberately NOT a status: skipping the redundant lane is
    // correct behavior, not a problem to alert on.
    public static final byte STATUS_OK = 0;
    public static final byte STATUS_OFFLINE = 1;
    public static final byte STATUS_ME_WAITING = 2;

    private static final Map<String, Set<MeshEndpointPart>> BY_FREQUENCY = new HashMap<>();
    private static final Map<String, MeshEndpointPart> STICKY_ITEM = new HashMap<>();
    private static final Map<String, Long> STICKY_ITEM_TICK = new HashMap<>();
    private static final Map<String, MeshEndpointPart> STICKY_FLUID = new HashMap<>();
    private static final Map<String, Long> STICKY_FLUID_TICK = new HashMap<>();
    private static final Map<String, Integer> CURSORS = new HashMap<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private static long gameTick;

    private MeshRegistry() {
    }

    public static void register(MeshEndpointPart part) {
        if (!part.frequency().isBlank()) {
            BY_FREQUENCY.computeIfAbsent(part.frequency(), f -> new LinkedHashSet<>()).add(part);
        }
    }

    public static void unregister(MeshEndpointPart part) {
        var set = BY_FREQUENCY.get(part.frequency());
        if (set != null) {
            set.remove(part);
            if (set.isEmpty()) {
                BY_FREQUENCY.remove(part.frequency());
            }
        }
        part.withdrawSignals();
        part.setMeLinkState(ME_STATE_NONE);
    }

    public static byte statusOf(MeshEndpointPart part) {
        if (!part.isActiveAndLoaded()) {
            return STATUS_OFFLINE;
        }
        return switch (part.meLinkState()) {
            case ME_STATE_WAITING -> STATUS_ME_WAITING;
            default -> STATUS_OK;
        };
    }

    public static List<MeshEndpointPart> endpoints(String frequency) {
        var set = BY_FREQUENCY.get(frequency);
        return set == null ? List.of() : List.copyOf(set);
    }

    @Nullable
    private static appeng.api.networking.IGrid hostGrid(MeshEndpointPart part) {
        var node = part.getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    /** Frequencies never cross networks: two endpoints mesh only on one host grid. */
    public static boolean sameCarrier(MeshEndpointPart a, MeshEndpointPart b) {
        var grid = hostGrid(a);
        return grid != null && grid == hostGrid(b);
    }

    /** How many endpoints share this part's frequency ON ITS NETWORK (itself included). */
    public static int carrierEndpointCount(MeshEndpointPart part) {
        return carrierEndpoints(part).size();
    }

    /** Every endpoint sharing this part's frequency on the same host network, self included. */
    public static List<MeshEndpointPart> carrierEndpoints(MeshEndpointPart part) {
        var list = new ArrayList<MeshEndpointPart>();
        for (var candidate : endpoints(part.frequency())) {
            if (candidate == part || sameCarrier(part, candidate)) {
                list.add(candidate);
            }
        }
        return list;
    }

    public static java.util.SortedMap<String, List<MeshEndpointPart>> allFrequencies() {
        var map = new java.util.TreeMap<String, List<MeshEndpointPart>>();
        for (var entry : BY_FREQUENCY.entrySet()) {
            map.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return map;
    }

    /**
     * Retags the frequency's loaded endpoints on the given carrier network (all
     * networks when null); endpoints in unloaded chunks keep the old frequency.
     */
    public static void renameFrequency(String from, String to,
            @Nullable appeng.api.networking.IGrid carrier) {
        for (var part : endpoints(from)) {
            if (carrier == null || hostGrid(part) == carrier) {
                part.applyMeshConfig(to, part.role(), part.priority(), part.capabilityMask());
            }
        }
    }

    /** Forces the frequency's ME lanes to rebuild (and re-diagnose loops) next tick. */
    public static void forceRelink(String frequency) {
        ME_MEMBERSHIP.remove(frequency);
    }

    /** Compact capability label, e.g. "R,I,F" or "ME". */
    public static String describeTypes(int mask) {
        var parts = new ArrayList<String>();
        if ((mask & TYPE_REDSTONE) != 0) {
            parts.add("R");
        }
        if ((mask & TYPE_ITEM) != 0) {
            parts.add("I");
        }
        if ((mask & TYPE_FLUID) != 0) {
            parts.add("F");
        }
        if ((mask & TYPE_ENERGY) != 0) {
            parts.add("E");
        }
        if ((mask & TYPE_SIGNAL) != 0) {
            parts.add("S");
        }
        if ((mask & TYPE_ME) != 0) {
            parts.add("ME");
        }
        if ((mask & TYPE_PROVIDER) != 0) {
            parts.add("P");
        }
        return String.join(",", parts);
    }

    public static void clear() {
        ME_LINKS.clear();
        ME_MEMBERSHIP.clear();
        BY_FREQUENCY.clear();
        STICKY_ITEM.clear();
        STICKY_ITEM_TICK.clear();
        STICKY_FLUID.clear();
        STICKY_FLUID_TICK.clear();
        CURSORS.clear();
    }

    /** Valid targets on the SOURCE endpoint's own network, priority-ordered. */
    public static List<MeshEndpointPart> outputs(String frequency, int type, MeshEndpointPart from) {
        var set = BY_FREQUENCY.get(frequency);
        if (set == null) {
            return List.of();
        }
        var list = new ArrayList<MeshEndpointPart>();
        for (var part : set) {
            if (part != from && part.isValidTarget(type) && sameCarrier(from, part)) {
                list.add(part);
            }
        }
        list.sort(Comparator.comparingInt(MeshEndpointPart::priority).reversed()
                .thenComparingLong(MeshEndpointPart::stableKey));
        return list;
    }

    /** Source endpoints of the frequency on {@code from}'s network, priority-ordered; where returns land. */
    public static List<MeshEndpointPart> inputs(String frequency, int type, MeshEndpointPart from) {
        var set = BY_FREQUENCY.get(frequency);
        if (set == null) {
            return List.of();
        }
        var list = new ArrayList<MeshEndpointPart>();
        for (var part : set) {
            if (part != from && part.isSource(type) && sameCarrier(from, part)) {
                list.add(part);
            }
        }
        list.sort(Comparator.comparingInt(MeshEndpointPart::priority).reversed()
                .thenComparingLong(MeshEndpointPart::stableKey));
        return list;
    }

    /** The endpoint the next item/fluid transfer would go to; used for blocking-mode mirroring. */
    @Nullable
    public static MeshEndpointPart peekTarget(String frequency, int type, MeshEndpointPart from) {
        return peekTarget(frequency, type, from, null);
    }

    @Nullable
    public static MeshEndpointPart peekTarget(String frequency, int type, MeshEndpointPart from,
            @Nullable appeng.api.stacks.AEKey key) {
        var sticky = type == TYPE_FLUID ? STICKY_FLUID : STICKY_ITEM;
        var stickyTick = type == TYPE_FLUID ? STICKY_FLUID_TICK : STICKY_ITEM_TICK;
        var current = sticky.get(frequency);
        if (current != null && stickyTick.getOrDefault(frequency, -1L) == gameTick
                && current.isValidTarget(type) && current != from && sameCarrier(from, current)) {
            return current;
        }
        var candidates = outputs(frequency, type, from);
        if (candidates.isEmpty()) {
            return null;
        }
        int cursor = CURSORS.getOrDefault(frequency + "/" + type, 0);
        return candidates.get(Math.floorMod(cursor, candidates.size()));
    }

    @Nullable
    private static MeshEndpointPart claimTarget(String frequency, int type, MeshEndpointPart from,
            @Nullable appeng.api.stacks.AEKey key) {
        var sticky = type == TYPE_FLUID ? STICKY_FLUID : STICKY_ITEM;
        var stickyTick = type == TYPE_FLUID ? STICKY_FLUID_TICK : STICKY_ITEM_TICK;
        var current = sticky.get(frequency);
        if (current != null && stickyTick.getOrDefault(frequency, -1L) == gameTick
                && current.isValidTarget(type) && current != from && sameCarrier(from, current)) {
            return current;
        }
        var candidates = outputs(frequency, type, from);
        if (candidates.isEmpty()) {
            return null;
        }
        var cursorKey = frequency + "/" + type;
        int cursor = CURSORS.getOrDefault(cursorKey, 0);
        var chosen = candidates.get(Math.floorMod(cursor, candidates.size()));
        CURSORS.put(cursorKey, cursor + 1);
        sticky.put(frequency, chosen);
        stickyTick.put(frequency, gameTick);
        return chosen;
    }

    public static ItemStack forwardItem(MeshEndpointPart from, ItemStack stack, boolean simulate) {
        if (DEPTH.get() > 0 || stack.isEmpty()) {
            return stack;
        }
        var itemKey = appeng.api.stacks.AEItemKey.of(stack);
        var target = simulate
                ? peekTarget(from.frequency(), TYPE_ITEM, from, itemKey)
                : claimTarget(from.frequency(), TYPE_ITEM, from, itemKey);
        if (target == null) {
            return stack;
        }
        DEPTH.set(DEPTH.get() + 1);
        try {
            var handler = target.adjacentItemHandler();
            return handler == null ? stack : ItemHandlerHelper.insertItem(handler, stack, simulate);
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }

    public static int forwardFluid(MeshEndpointPart from, FluidStack stack, boolean simulate) {
        if (DEPTH.get() > 0 || stack.isEmpty()) {
            return 0;
        }
        var fluidKey = appeng.api.stacks.AEFluidKey.of(stack);
        var target = simulate
                ? peekTarget(from.frequency(), TYPE_FLUID, from, fluidKey)
                : claimTarget(from.frequency(), TYPE_FLUID, from, fluidKey);
        if (target == null) {
            return 0;
        }
        DEPTH.set(DEPTH.get() + 1);
        try {
            var handler = target.adjacentFluidHandler();
            return handler == null ? 0
                    : handler.fill(stack, simulate
                            ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                            : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }

    /** Energy is divisible: spread across every valid target in priority order. */
    public static int forwardEnergy(MeshEndpointPart from, int amount, boolean simulate) {
        if (DEPTH.get() > 0 || amount <= 0) {
            return 0;
        }
        DEPTH.set(DEPTH.get() + 1);
        try {
            int inserted = 0;
            for (var target : outputs(from.frequency(), TYPE_ENERGY, from)) {
                var handler = target.adjacentEnergyHandler();
                if (handler != null) {
                    inserted += handler.receiveEnergy(amount - inserted, simulate);
                    if (inserted >= amount) {
                        break;
                    }
                }
            }
            return inserted;
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }

    private static final Map<String, List<appeng.api.networking.IGridConnection>> ME_LINKS = new HashMap<>();
    private static final Map<String, Long> ME_MEMBERSHIP = new HashMap<>();

    /**
     * Mesh-ME is true P2P with quantum-bridge mechanics underneath: every ME-attuned
     * endpoint exposes a CARRIED node on its face, and the mesh links those carried
     * nodes - the networks fed INTO the endpoints' faces fuse across the frequency,
     * while the host networks the endpoints sit on are never touched.
     *
     * <p>The topology is pairing, not a star. AE2's pathfinder allocates channels
     * along a BFS tree and never reroutes around a saturated node, so one node linked
     * to everything caps the whole frequency at its own 32-channel dense ceiling.
     * Instead, endpoints are grouped by the carried grid their faces already share,
     * and every pair of groups gets min(|a|, |b|) disjoint lanes between least-loaded
     * endpoints: each far-side region hangs under its own BFS branch, and capacity
     * bundles at 32 channels per lane - exactly N parallel ME P2P tunnels, managed
     * automatically.
     */
    private static void manageMeLinks() {
        ME_LINKS.keySet().removeIf(freq -> {
            if (!BY_FREQUENCY.containsKey(freq)) {
                ME_LINKS.getOrDefault(freq, List.of())
                        .forEach(appeng.api.networking.IGridConnection::destroy);
                ME_MEMBERSHIP.remove(freq);
                return true;
            }
            return false;
        });

        for (var entry : BY_FREQUENCY.entrySet()) {
            // Frequencies never cross networks: only endpoints whose ONLINE host grid
            // matches can lane together, so membership folds in the carrier identity -
            // host splits, merges, and power changes all trigger a rebuild.
            var members = new ArrayList<MeshEndpointPart>();
            long membership = 0;
            for (var part : entry.getValue()) {
                if (part.attuned(TYPE_ME) && part.carriedNode() != null && part.isActiveAndLoaded()) {
                    members.add(part);
                    membership = membership * 31 + part.stableKey();
                    membership = membership * 31 + System.identityHashCode(hostGrid(part));
                }
            }
            membership = membership * 31 + members.size();

            if (ME_MEMBERSHIP.getOrDefault(entry.getKey(), 0L) == membership) {
                continue;
            }
            ME_MEMBERSHIP.put(entry.getKey(), membership);

            // Destroying a connection splits grids synchronously (validateGrid runs the
            // split detector), so the groups read below are the true pre-mesh grids.
            ME_LINKS.getOrDefault(entry.getKey(), List.of())
                    .forEach(appeng.api.networking.IGridConnection::destroy);
            var links = new ArrayList<appeng.api.networking.IGridConnection>();

            for (var part : entry.getValue()) {
                part.setMeLinkState(part.attuned(TYPE_ME) ? ME_STATE_WAITING : ME_STATE_NONE);
            }
            members.sort(Comparator.comparingLong(MeshEndpointPart::stableKey));
            var carriers = new java.util.LinkedHashMap<appeng.api.networking.IGrid, List<MeshEndpointPart>>();
            for (var part : members) {
                carriers.computeIfAbsent(hostGrid(part), g -> new ArrayList<>()).add(part);
            }
            for (var carrierMembers : carriers.values()) {
                if (carrierMembers.size() < 2) {
                    continue;
                }
                var groups = new java.util.LinkedHashMap<appeng.api.networking.IGrid, List<MeshEndpointPart>>();
                for (var part : carrierMembers) {
                    groups.computeIfAbsent(part.carriedNode().getGrid(), g -> new ArrayList<>()).add(part);
                }
                if (groups.size() == 1) {
                    // Cables already join every fed network; a lane would only add a
                    // redundant parallel path - the classic half-a-base-offline trap.
                    for (var part : carrierMembers) {
                        part.setMeLinkState(ME_STATE_LOOP);
                    }
                    continue;
                }
                var sides = new ArrayList<>(groups.values());
                var load = new HashMap<MeshEndpointPart, Integer>();
                for (int i = 0; i < sides.size(); i++) {
                    for (int j = i + 1; j < sides.size(); j++) {
                        int lanes = Math.min(sides.get(i).size(), sides.get(j).size());
                        for (int lane = 0; lane < lanes; lane++) {
                            var a = leastLoaded(sides.get(i), load);
                            var b = leastLoaded(sides.get(j), load);
                            try {
                                links.add(appeng.api.networking.GridHelper
                                        .createConnection(a.carriedNode(), b.carriedNode()));
                                load.merge(a, 1, Integer::sum);
                                load.merge(b, 1, Integer::sum);
                                a.setMeLinkState(ME_STATE_LINKED);
                                b.setMeLinkState(ME_STATE_LINKED);
                            } catch (IllegalStateException ignored) {
                                // the pair is already directly connected
                            }
                        }
                    }
                }
                for (var part : carrierMembers) {
                    if (part.meLinkState() == ME_STATE_WAITING) {
                        part.setMeLinkState(ME_STATE_STANDBY);
                    }
                }
            }
            if (links.isEmpty()) {
                ME_LINKS.remove(entry.getKey());
            } else {
                ME_LINKS.put(entry.getKey(), links);
            }
        }
    }

    /** Lowest lane count wins; stableKey breaks ties so lane assignment is deterministic. */
    private static MeshEndpointPart leastLoaded(List<MeshEndpointPart> side,
            Map<MeshEndpointPart, Integer> load) {
        var best = side.get(0);
        for (var part : side) {
            int candidate = load.getOrDefault(part, 0);
            int current = load.getOrDefault(best, 0);
            if (candidate < current || (candidate == current && part.stableKey() < best.stableKey())) {
                best = part;
            }
        }
        return best;
    }

    /** Once per server tick: recompute redstone wired-OR and signal bridging per frequency. */
    public static void tick(long tick) {
        gameTick = tick;
        manageMeLinks();

        for (var entry : BY_FREQUENCY.entrySet()) {
            // Frequencies never cross networks: every transport runs per host grid.
            var carriers = new java.util.LinkedHashMap<Object, List<MeshEndpointPart>>();
            for (var part : entry.getValue()) {
                var grid = hostGrid(part);
                carriers.computeIfAbsent(grid == null ? part : grid, g -> new ArrayList<>()).add(part);
            }

            for (var carrierMembers : carriers.values()) {
                int redstone = 0;
                Map<ResourceLocation, Long> signals = null;

                for (var part : carrierMembers) {
                    if (part.isSource(TYPE_REDSTONE)) {
                        redstone = Math.max(redstone, part.readFaceRedstone());
                    }
                    if (part.isSource(TYPE_SIGNAL)) {
                        var service = part.signalService();
                        if (service != null) {
                            if (signals == null) {
                                signals = new HashMap<>();
                            }
                            // localCommitted excludes external contributions, so a mesh
                            // can never re-publish what another mesh injected.
                            for (var channel : service.localCommitted().entrySet()) {
                                signals.merge(channel.getKey(), channel.getValue(), SignalMath::add);
                            }
                        }
                    }
                }

                for (var part : carrierMembers) {
                    if (part.attuned(TYPE_REDSTONE)) {
                        part.setMeshRedstone(part.isValidTarget(TYPE_REDSTONE) ? redstone : 0);
                    }
                    if (part.isValidTarget(TYPE_SIGNAL)) {
                        part.publishSignals(signals == null ? Map.of() : signals);
                    } else if (part.attuned(TYPE_SIGNAL)) {
                        part.withdrawSignals();
                    }
                }
            }
        }
    }

}
