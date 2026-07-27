package io.github.johnhamilto.ae2logistics.mesh;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

/**
 * The provider-facing side of a mesh input endpoint. Pattern providers resolve
 * ME_STORAGE targets first and push whole batches through one adapter as a sequence of
 * SIMULATE inserts followed by MODULATE inserts. This storage detects batch boundaries
 * (a simulate after a modulate starts a new batch), routes each batch to the first
 * non-busy output machine, and reports "busy" per machine as "the previous batch's keys
 * are still in its inventory" - which makes one provider behave as if it were adjacent
 * to every machine on the frequency, each with true blocking-mode semantics.
 */
public final class MeshProviderStorage implements MEStorage {

    private final MeshEndpointPart endpoint;

    @Nullable
    private MeshEndpointPart batchTarget;
    private boolean lastWasModulate;
    private final Set<AEKey> batchKeys = new HashSet<>();
    private final java.util.LinkedHashMap<AEKey, Long> batchSim = new java.util.LinkedHashMap<>();

    public MeshProviderStorage(MeshEndpointPart endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        boolean simulate = mode == Actionable.SIMULATE;

        if (simulate && lastWasModulate) {
            finishBatch();
        }
        lastWasModulate = !simulate;

        if (typeFor(what) == 0) {
            return 0;
        }
        if (batchTarget == null || !accepts(batchTarget, what)) {
            batchTarget = selectTarget(what, amount);
            if (batchTarget == null) {
                return 0;
            }
        }

        long inserted = insertInto(batchTarget, what, amount, simulate);
        if (inserted > 0) {
            if (simulate) {
                batchSim.merge(what, inserted, Long::sum);
            } else {
                batchKeys.add(what.dropSecondary());
                batchTarget.noteDelivered(batchKeys);
            }
        }
        return inserted;
    }

    /** Ends the current batch: the receiving machine keeps its busy set until it empties. */
    private void finishBatch() {
        batchTarget = null;
        batchKeys.clear();
        batchSim.clear();
    }

    private static boolean accepts(MeshEndpointPart target, AEKey what) {
        return target.isValidTarget(typeFor(what)) && target.filterAccepts(what);
    }

    /**
     * Picks the first non-busy machine that can take the whole batch seen so far: the
     * provider pushes every key of a pattern to whichever target we settle on, so a
     * mid-batch switch (say the first target's filter rejects a later ingredient) must
     * re-verify filter and capacity for everything already simulated this batch.
     */
    @Nullable
    private MeshEndpointPart selectTarget(AEKey what, long amount) {
        for (var candidate : MeshRegistry.outputs(endpoint.frequency(), typeFor(what), endpoint)) {
            if (candidate.isBusy() || !candidate.filterAccepts(what)) {
                continue;
            }
            boolean fits = insertInto(candidate, what, amount, true) >= amount;
            for (var entry : batchSim.entrySet()) {
                if (!fits) {
                    break;
                }
                fits = accepts(candidate, entry.getKey())
                        && insertInto(candidate, entry.getKey(), entry.getValue(), true) >= entry.getValue();
            }
            if (fits) {
                return candidate;
            }
        }
        return null;
    }

    private static int typeFor(AEKey what) {
        if (what instanceof AEItemKey) {
            return MeshRegistry.TYPE_ITEM;
        }
        if (what instanceof AEFluidKey) {
            return MeshRegistry.TYPE_FLUID;
        }
        return 0;
    }

    private static long insertInto(MeshEndpointPart target, AEKey what, long amount, boolean simulate) {
        if (what instanceof AEItemKey itemKey) {
            var handler = target.adjacentItemHandler();
            if (handler == null) {
                return 0;
            }
            int count = (int) Math.min(amount, Integer.MAX_VALUE);
            var rest = ItemHandlerHelper.insertItem(handler, itemKey.toStack(count), simulate);
            return count - rest.getCount();
        }
        if (what instanceof AEFluidKey fluidKey) {
            var handler = target.adjacentFluidHandler();
            if (handler == null) {
                return 0;
            }
            int mb = (int) Math.min(amount, Integer.MAX_VALUE);
            return handler.fill(fluidKey.toStack(mb),
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    @Override
    public Component getDescription() {
        return Component.literal("Mesh Frequency " + endpoint.frequency());
    }
}
