package io.github.johnhamilto.ae2logistics.provider;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

/**
 * The provider-facing half of a provider tunnel or provider mesh endpoint. Pattern
 * providers resolve ME_STORAGE targets and push whole batches through one adapter as a
 * sequence of SIMULATE inserts followed by MODULATE inserts. This storage detects batch
 * boundaries (a simulate after a modulate starts a new batch), routes each batch to the
 * first non-busy output machine that can hold ALL of it, and leaves "busy" per machine
 * as "the previous batch's keys are still in its inventory" - which makes one provider
 * behave as if it were adjacent to every machine behind the outputs, each with true
 * blocking-mode semantics. Key-type agnostic: whatever the output-side adapter accepts
 * (items, fluids, companion-mod chemicals) rides through.
 */
public final class ProviderBatchRouter<T> implements MEStorage {

    public interface Targets<T> {
        Iterable<T> candidates();

        boolean accepts(T target, AEKey what);

        @Nullable
        MEStorage storageOf(T target);

        boolean isBusy(T target);

        void noteDelivered(T target, Set<AEKey> batchKeys);

        /** Called once per successful MODULATE insert with the delivered amount. */
        default void noteTransported(AEKey what, long amount) {
        }

        Component description();
    }

    /** One hop per transfer: a provider delivery can never enter another provider face. */
    private static final ThreadLocal<Boolean> PUSHING = ThreadLocal.withInitial(() -> false);

    private final Targets<T> targets;

    @Nullable
    private T batchTarget;
    private boolean lastWasModulate;
    private final Set<AEKey> batchKeys = new HashSet<>();
    private final LinkedHashMap<AEKey, Long> batchSim = new LinkedHashMap<>();

    public ProviderBatchRouter(Targets<T> targets) {
        this.targets = targets;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (PUSHING.get()) {
            return 0;
        }
        PUSHING.set(true);
        try {
            return routedInsert(what, amount, mode, source);
        } finally {
            PUSHING.set(false);
        }
    }

    private long routedInsert(AEKey what, long amount, Actionable mode, IActionSource source) {
        boolean simulate = mode == Actionable.SIMULATE;

        if (simulate && lastWasModulate) {
            finishBatch();
        }
        lastWasModulate = !simulate;

        if (batchTarget == null || !targets.accepts(batchTarget, what)) {
            batchTarget = selectTarget(what, amount, source);
            if (batchTarget == null) {
                return 0;
            }
        }

        long inserted = insertInto(batchTarget, what, amount, mode, source);
        if (inserted > 0) {
            if (simulate) {
                batchSim.merge(what, inserted, Long::sum);
            } else {
                batchKeys.add(what.dropSecondary());
                targets.noteDelivered(batchTarget, batchKeys);
                targets.noteTransported(what, inserted);
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

    /**
     * Picks the first non-busy machine that can take the whole batch seen so far: the
     * provider pushes every key of a pattern to whichever target we settle on, so a
     * mid-batch switch (say the first target rejects a later ingredient) must re-verify
     * acceptance and capacity for everything already simulated this batch.
     */
    @Nullable
    private T selectTarget(AEKey what, long amount, IActionSource source) {
        for (var candidate : targets.candidates()) {
            if (targets.isBusy(candidate) || !targets.accepts(candidate, what)) {
                continue;
            }
            boolean fits = insertInto(candidate, what, amount, Actionable.SIMULATE, source) >= amount;
            for (var entry : batchSim.entrySet()) {
                if (!fits) {
                    break;
                }
                fits = targets.accepts(candidate, entry.getKey())
                        && insertInto(candidate, entry.getKey(), entry.getValue(),
                                Actionable.SIMULATE, source) >= entry.getValue();
            }
            if (fits) {
                return candidate;
            }
        }
        return null;
    }

    private long insertInto(T target, AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = targets.storageOf(target);
        return storage == null ? 0 : storage.insert(what, amount, mode, source);
    }

    @Override
    public Component getDescription() {
        return targets.description();
    }
}
