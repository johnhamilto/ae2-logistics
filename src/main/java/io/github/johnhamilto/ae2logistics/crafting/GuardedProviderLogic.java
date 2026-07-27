package io.github.johnhamilto.ae2logistics.crafting;

import java.util.ArrayList;
import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;

import io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity;

/**
 * AE2's pattern provider logic with a gate in front: the provider-level guard hides every
 * pattern, per-pattern guards hide themselves, and (when execution gating is on) pushes
 * for a blocked pattern are refused so the job waits instead of running. Registering this
 * subclass makes its overrides the grid's {@code ICraftingProvider} view of the machine.
 */
public class GuardedProviderLogic extends PatternProviderLogic {

    private final GuardedPatternProviderBlockEntity guardHost;

    public GuardedProviderLogic(IManagedGridNode mainNode, GuardedPatternProviderBlockEntity host) {
        super(mainNode, host);
        this.guardHost = host;
    }

    /** The unfiltered pattern list, for guard-change fingerprinting. */
    public List<IPatternDetails> rawPatterns() {
        return super.getAvailablePatterns();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (!guardHost.guardPasses()) {
            return List.of();
        }
        var all = super.getAvailablePatterns();
        var service = guardHost.signalService();
        if (service == null) {
            return all;
        }
        List<IPatternDetails> filtered = null;
        for (var pattern : all) {
            boolean blocked = pattern instanceof GuardedPattern guarded && !guarded.passes(service);
            if (blocked && filtered == null) {
                filtered = new ArrayList<>(all.subList(0, all.indexOf(pattern)));
            } else if (!blocked && filtered != null) {
                filtered.add(pattern);
            }
        }
        return filtered != null ? filtered : all;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (guardHost.gateExecution()) {
            if (!guardHost.guardPasses()) {
                return false;
            }
            var service = guardHost.signalService();
            if (service != null && patternDetails instanceof GuardedPattern guarded
                    && !guarded.passes(service)) {
                return false;
            }
        }
        return super.pushPattern(patternDetails, inputHolder);
    }

    @Override
    public int getPatternPriority() {
        var live = guardHost.livePriority();
        return live != null ? live : super.getPatternPriority();
    }
}
