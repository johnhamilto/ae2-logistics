package io.github.johnhamilto.ae2logistics.parts;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.items.parts.PartModels;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.provider.ProviderBatchRouter;
import io.github.johnhamilto.ae2logistics.provider.ProviderTargets;

/**
 * A P2P tunnel for pattern-provider pushes: point a provider at the input tunnel and it
 * behaves as if it were adjacent to every machine behind the output tunnels - whole
 * batches land on the first machine that finished its previous batch, with true
 * per-machine blocking at range. Key-type agnostic like a provider itself: items,
 * fluids, and any companion-mod key type (chemicals, flux) push through. Standard AE2
 * P2P in every other way - frequencies, memory-card linking, attunement (hold a pattern
 * provider), and the P2P Frequency Terminal.
 */
public class ProviderP2PTunnelPart extends P2PTunnelPart<ProviderP2PTunnelPart> {

    private static final P2PModels MODELS = new P2PModels(AE2Logistics.id("part/provider_p2p_tunnel"));

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    /** Remembers the last batch delivered here; the machine counts as busy until it drains. */
    private final Set<AEKey> lastBatch = new HashSet<>();

    private final ProviderBatchRouter<ProviderP2PTunnelPart> router =
            new ProviderBatchRouter<>(new TunnelTargets());

    public ProviderP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
    }

    /** The provider-facing storage, exposed on the input tunnel's face. */
    @Nullable
    public MEStorage exposedStorage() {
        return isOutput() ? null : router;
    }

    /** The push target behind this (output) tunnel's face. */
    @Nullable
    private MEStorage adjacentTarget() {
        var host = getBlockEntity();
        if (!(host.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        return ProviderTargets.resolve(level, host.getBlockPos().relative(getSide()),
                getSide().getOpposite());
    }

    private void noteDelivered(Set<AEKey> keys) {
        lastBatch.clear();
        lastBatch.addAll(keys);
    }

    private boolean isBusy() {
        if (lastBatch.isEmpty()) {
            return false;
        }
        var target = adjacentTarget();
        if (target != null && ProviderTargets.containsAny(target, lastBatch)) {
            return true;
        }
        lastBatch.clear();
        return false;
    }

    private class TunnelTargets implements ProviderBatchRouter.Targets<ProviderP2PTunnelPart> {
        @Override
        public Iterable<ProviderP2PTunnelPart> candidates() {
            return isActive() ? getOutputs() : List.of();
        }

        @Override
        public boolean accepts(ProviderP2PTunnelPart target, AEKey what) {
            return target.isActive();
        }

        @Override
        @Nullable
        public MEStorage storageOf(ProviderP2PTunnelPart target) {
            return target.adjacentTarget();
        }

        @Override
        public boolean isBusy(ProviderP2PTunnelPart target) {
            return target.isBusy();
        }

        @Override
        public void noteDelivered(ProviderP2PTunnelPart target, Set<AEKey> batchKeys) {
            target.noteDelivered(batchKeys);
        }

        @Override
        public void noteTransported(AEKey what, long amount) {
            deductTransportCost(amount, what.getType());
        }

        @Override
        public Component description() {
            return Component.literal("Provider P2P " + getFrequency());
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(isPowered(), isActive());
    }
}
