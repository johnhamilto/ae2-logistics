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
 * Server-global registry of mesh endpoints, keyed by frequency name. Deliberately not a
 * grid service: signal frequencies bridge across networks. Item and fluid delivery is
 * sticky per tick so a pattern provider's batch lands on one machine; a thread-local
 * depth guard gives every transfer a hop budget of one, which makes loops impossible.
 */
public final class MeshRegistry {

    public static final int TYPE_REDSTONE = 1;
    public static final int TYPE_ITEM = 2;
    public static final int TYPE_FLUID = 4;
    public static final int TYPE_ENERGY = 8;
    public static final int TYPE_SIGNAL = 16;

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
    }

    public static void clear() {
        BY_FREQUENCY.clear();
        STICKY_ITEM.clear();
        STICKY_ITEM_TICK.clear();
        STICKY_FLUID.clear();
        STICKY_FLUID_TICK.clear();
        CURSORS.clear();
    }

    public static List<MeshEndpointPart> outputs(String frequency, int type, @Nullable MeshEndpointPart exclude) {
        var set = BY_FREQUENCY.get(frequency);
        if (set == null) {
            return List.of();
        }
        var list = new ArrayList<MeshEndpointPart>();
        for (var part : set) {
            if (part != exclude && part.isValidTarget(type)) {
                list.add(part);
            }
        }
        list.sort(Comparator.comparingInt(MeshEndpointPart::priority).reversed()
                .thenComparingLong(MeshEndpointPart::stableKey));
        return list;
    }

    /** The endpoint the next item/fluid transfer would go to; used for blocking-mode mirroring. */
    @Nullable
    public static MeshEndpointPart peekTarget(String frequency, int type, @Nullable MeshEndpointPart exclude) {
        var sticky = type == TYPE_FLUID ? STICKY_FLUID : STICKY_ITEM;
        var stickyTick = type == TYPE_FLUID ? STICKY_FLUID_TICK : STICKY_ITEM_TICK;
        var current = sticky.get(frequency);
        if (current != null && stickyTick.getOrDefault(frequency, -1L) == gameTick
                && current.isValidTarget(type) && current != exclude) {
            return current;
        }
        var candidates = outputs(frequency, type, exclude);
        if (candidates.isEmpty()) {
            return null;
        }
        int cursor = CURSORS.getOrDefault(frequency + "/" + type, 0);
        return candidates.get(Math.floorMod(cursor, candidates.size()));
    }

    @Nullable
    private static MeshEndpointPart claimTarget(String frequency, int type, @Nullable MeshEndpointPart exclude) {
        var sticky = type == TYPE_FLUID ? STICKY_FLUID : STICKY_ITEM;
        var stickyTick = type == TYPE_FLUID ? STICKY_FLUID_TICK : STICKY_ITEM_TICK;
        var current = sticky.get(frequency);
        if (current != null && stickyTick.getOrDefault(frequency, -1L) == gameTick
                && current.isValidTarget(type) && current != exclude) {
            return current;
        }
        var candidates = outputs(frequency, type, exclude);
        if (candidates.isEmpty()) {
            return null;
        }
        var key = frequency + "/" + type;
        int cursor = CURSORS.getOrDefault(key, 0);
        var chosen = candidates.get(Math.floorMod(cursor, candidates.size()));
        CURSORS.put(key, cursor + 1);
        sticky.put(frequency, chosen);
        stickyTick.put(frequency, gameTick);
        return chosen;
    }

    public static ItemStack forwardItem(MeshEndpointPart from, ItemStack stack, boolean simulate) {
        if (DEPTH.get() > 0 || stack.isEmpty()) {
            return stack;
        }
        var target = simulate
                ? peekTarget(from.frequency(), TYPE_ITEM, from)
                : claimTarget(from.frequency(), TYPE_ITEM, from);
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
        var target = simulate
                ? peekTarget(from.frequency(), TYPE_FLUID, from)
                : claimTarget(from.frequency(), TYPE_FLUID, from);
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

    /** Once per server tick: recompute redstone wired-OR and signal bridging per frequency. */
    public static void tick(long tick) {
        gameTick = tick;

        for (var entry : BY_FREQUENCY.entrySet()) {
            int redstone = 0;
            Map<ResourceLocation, Long> signals = null;

            for (var part : entry.getValue()) {
                if (part.isSource(TYPE_REDSTONE)) {
                    redstone = Math.max(redstone, part.readFaceRedstone());
                }
                if (part.isSource(TYPE_SIGNAL)) {
                    var service = part.signalService();
                    if (service != null) {
                        if (signals == null) {
                            signals = new HashMap<>();
                        }
                        // localCommitted excludes external contributions, so a mesh can
                        // never re-publish what another mesh injected (no feedback).
                        for (var channel : service.localCommitted().entrySet()) {
                            signals.merge(channel.getKey(), channel.getValue(), SignalMath::add);
                        }
                    }
                }
            }

            for (var part : entry.getValue()) {
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
