package io.github.johnhamilto.ae2logistics.parts;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.items.parts.PartModels;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.provider.ProviderTargets;

/**
 * A P2P tunnel that REPLICATES the pattern provider on its input face onto every output
 * face: each output registers an invisible {@link ICraftingProvider} that mirrors the
 * real provider's patterns, priority, and blocking mode live, and pushes into the one
 * machine on its own face - crafting machines first (assembler patterns cross the
 * tunnel), external inventories otherwise. AE2's crafting service does the scheduling
 * across the replicas exactly as if the provider were physically adjacent to every
 * machine; the replicas hold no state, save no NBT, and appear in no UI - the real
 * provider stays the single place patterns live. Machines push results back through
 * their output face into whatever the input tunnel faces, like returning into the
 * provider itself. Standard AE2 P2P in every other way - frequencies, memory-card
 * linking, attunement (hold a pattern provider), and the P2P Frequency Terminal.
 */
public class ProviderP2PTunnelPart extends P2PTunnelPart<ProviderP2PTunnelPart>
        implements IGridTickable {

    private static final P2PModels MODELS = new P2PModels(AE2Logistics.id("part/provider_p2p_tunnel"));

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    private final VirtualProvider virtualProvider = new VirtualProvider();
    private final MEStorage returnPath = new ReturnPath();
    private final GenericInternalInventory returnGenericInv = new ReturnGenericInv();

    public ProviderP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .addService(ICraftingProvider.class, virtualProvider)
                .addService(IGridTickable.class, this);
    }

    /**
     * Output faces expose the return path only. Pushing INTO the tunnel happens through
     * the virtual providers, never through a storage on the input face - the real
     * provider's own push finds no target there, and the crafting service routes to
     * the replicas instead.
     */
    @Nullable
    public MEStorage exposedStorage() {
        return isOutput() ? returnPath : null;
    }

    /**
     * AE2's generic-inventory view of the return path: the surface addons bridge their
     * own key types through (chemicals, flux, ...), so returns stay type-agnostic
     * instead of supporting baseline items and fluids only.
     */
    @Nullable
    public GenericInternalInventory exposedReturnGenericInv() {
        return isOutput() ? returnGenericInv : null;
    }

    /** Machines return results via plain item capability on the output face. */
    @Nullable
    public net.neoforged.neoforge.items.IItemHandler exposedReturnItemHandler() {
        return isOutput() ? returnItemHandler : null;
    }

    @Nullable
    public net.neoforged.neoforge.fluids.capability.IFluidHandler exposedReturnFluidHandler() {
        return isOutput() ? returnFluidHandler : null;
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

    /** The real provider's blocking-mode setting, read live off the input face. */
    private boolean blockingEnabled() {
        var input = getInput();
        if (input == null) {
            return false;
        }
        var host = input.getBlockEntity();
        return host.getLevel() instanceof ServerLevel level
                && ProviderTargets.blockingModeAt(level, host.getBlockPos(), input.getSide());
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        if (!isClientSide()) {
            ICraftingProvider.requestUpdate(getMainNode());
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // Safety net for mirrored-pattern staleness: the crafting service caches
        // pattern indices, and nothing notifies us when the real provider's patterns
        // change. The check is a handful of reference compares, so this is free.
        return new TickingRequest(20, 40, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (isOutput()) {
            virtualProvider.maybeRequestUpdate();
        }
        return TickRateModulation.SLOWER;
    }

    /**
     * The invisible replica: mirrors the input-face provider live and pushes into the
     * single machine on this output's face. Holds no persistent state - patterns,
     * priority, and blocking always read through to the real provider.
     */
    private class VirtualProvider implements ICraftingProvider {

        private List<IPatternDetails> lastSeen = List.of();
        private Set<AEKey> patternInputs = Set.of();

        /**
         * The real provider on the input tunnel's face, but only when it sits on the
         * SAME grid as this tunnel - a physical provider serves only the network it is
         * on, and the replicas mimic physical adjacency, not a pattern bridge.
         */
        @Nullable
        private ICraftingProvider realProvider() {
            if (!isOutput() || !isActive()) {
                return null;
            }
            var input = getInput();
            if (input == null || !input.isActive()) {
                return null;
            }
            var host = input.getBlockEntity();
            if (!(host.getLevel() instanceof ServerLevel level)) {
                return null;
            }
            var providerHost = ProviderTargets.providerHostAt(level, host.getBlockPos(),
                    input.getSide());
            if (providerHost == null) {
                return null;
            }
            var providerPos = host.getBlockPos().relative(input.getSide());
            var exposed = GridHelper.getExposedNode(level, providerPos,
                    input.getSide().getOpposite());
            var ourNode = getMainNode().getNode();
            if (exposed == null || ourNode == null || exposed.getGrid() != ourNode.getGrid()) {
                return null;
            }
            return providerHost.getLogic();
        }

        /** Re-registers with the crafting service when the mirrored list changed. */
        private void maybeRequestUpdate() {
            var real = realProvider();
            var current = real == null ? List.<IPatternDetails>of() : real.getAvailablePatterns();
            if (!current.equals(lastSeen)) {
                lastSeen = List.copyOf(current);
                var inputs = new HashSet<AEKey>();
                for (var pattern : lastSeen) {
                    for (var input : pattern.getInputs()) {
                        for (var stack : input.getPossibleInputs()) {
                            inputs.add(stack.what().dropSecondary());
                        }
                    }
                }
                patternInputs = inputs;
                ICraftingProvider.requestUpdate(getMainNode());
            }
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            var real = realProvider();
            return real == null ? List.of() : real.getAvailablePatterns();
        }

        @Override
        public int getPatternPriority() {
            var real = realProvider();
            return real == null ? 0 : real.getPatternPriority();
        }

        @Override
        public boolean isBusy() {
            maybeRequestUpdate();
            var real = realProvider();
            if (real == null) {
                return false;
            }
            if (real.isBusy()) {
                return true;
            }
            if (!blockingEnabled()) {
                return false;
            }
            var target = adjacentTarget();
            return target != null && ProviderTargets.containsAny(target, patternInputs);
        }

        @Override
        public boolean pushPattern(IPatternDetails details, KeyCounter[] inputHolder) {
            var real = realProvider();
            var host = getBlockEntity();
            if (real == null || !isActive() || !(host.getLevel() instanceof ServerLevel level)) {
                return false;
            }
            var facePos = host.getBlockPos().relative(getSide());
            var machineSide = getSide().getOpposite();

            // Crafting machines first, exactly like a physically adjacent provider -
            // this is what lets assembler patterns cross the tunnel.
            var machine = ICraftingMachine.of(level, facePos, machineSide);
            if (machine != null && machine.acceptsPlans()) {
                if (machine.pushPattern(details, inputHolder, machineSide)) {
                    deductCost(inputHolder);
                    return true;
                }
                return false;
            }

            if (!details.supportsPushInputsToExternalInventory()) {
                return false;
            }
            var target = adjacentTarget();
            if (target == null) {
                return false;
            }
            for (var list : inputHolder) {
                for (var entry : list) {
                    if (target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE,
                            IActionSource.empty()) < entry.getLongValue()) {
                        return false;
                    }
                }
            }
            details.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
                long inserted = target.insert(what, amount, Actionable.MODULATE,
                        IActionSource.empty());
                if (inserted < amount) {
                    // Simulate/modulate divergence: send the shortfall back through the
                    // provider's return inventory instead of stranding it.
                    returnPath.insert(what, amount - inserted, Actionable.MODULATE,
                            IActionSource.empty());
                }
            });
            deductCost(inputHolder);
            return true;
        }

        private void deductCost(KeyCounter[] inputHolder) {
            for (var list : inputHolder) {
                for (var entry : list) {
                    deductTransportCost(entry.getLongValue(), entry.getKey().getType());
                }
            }
        }
    }

    /** Insert-only view forwarding output-face returns to the input tunnel's face. */
    private class ReturnPath implements MEStorage {

        /** One hop per transfer: a return can never re-enter another tunnel's return. */
        private static final ThreadLocal<Boolean> RETURNING = ThreadLocal.withInitial(() -> false);

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (RETURNING.get()) {
                return 0;
            }
            RETURNING.set(true);
            try {
                var input = getInput();
                if (input == null || !input.isActive() || !isActive()) {
                    return 0;
                }
                var target = input.adjacentTarget();
                if (target == null) {
                    return 0;
                }
                long inserted = target.insert(what, amount, mode, source);
                if (inserted > 0 && mode == Actionable.MODULATE) {
                    deductTransportCost(inserted, what.getType());
                }
                return inserted;
            } finally {
                RETURNING.set(false);
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal("Provider P2P Return " + getFrequency());
        }
    }

    /** Insert-only pass-through; slot indices are meaningless for a forwarding view. */
    private class ReturnGenericInv implements GenericInternalInventory {
        @Override
        public int size() {
            return 1;
        }

        @Override
        @Nullable
        public GenericStack getStack(int slot) {
            return null;
        }

        @Override
        @Nullable
        public AEKey getKey(int slot) {
            return null;
        }

        @Override
        public long getAmount(int slot) {
            return 0;
        }

        @Override
        public long getMaxAmount(AEKey key) {
            return 1_000_000_000L;
        }

        @Override
        public long getCapacity(AEKeyType type) {
            return 1_000_000_000L;
        }

        @Override
        public boolean canInsert() {
            return true;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public void setStack(int slot, GenericStack stack) {
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return true;
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey key) {
            return true;
        }

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            return returnPath.insert(what, amount, mode, IActionSource.empty());
        }

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            return 0;
        }

        @Override
        public void beginBatch() {
        }

        @Override
        public void endBatch() {
        }

        @Override
        public void endBatchSuppressed() {
        }

        @Override
        public void onChange() {
        }
    }

    private final net.neoforged.neoforge.items.IItemHandler returnItemHandler =
            new net.neoforged.neoforge.items.IItemHandler() {
                @Override
                public int getSlots() {
                    return 1;
                }

                @Override
                public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
                    return net.minecraft.world.item.ItemStack.EMPTY;
                }

                @Override
                public net.minecraft.world.item.ItemStack insertItem(int slot,
                        net.minecraft.world.item.ItemStack stack, boolean simulate) {
                    var key = appeng.api.stacks.AEItemKey.of(stack);
                    if (key == null) {
                        return stack;
                    }
                    long inserted = returnPath.insert(key, stack.getCount(),
                            simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                            IActionSource.empty());
                    return inserted >= stack.getCount() ? net.minecraft.world.item.ItemStack.EMPTY
                            : stack.copyWithCount(stack.getCount() - (int) inserted);
                }

                @Override
                public net.minecraft.world.item.ItemStack extractItem(int slot, int amount,
                        boolean simulate) {
                    return net.minecraft.world.item.ItemStack.EMPTY;
                }

                @Override
                public int getSlotLimit(int slot) {
                    return 64;
                }

                @Override
                public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                    return true;
                }
            };

    private final net.neoforged.neoforge.fluids.capability.IFluidHandler returnFluidHandler =
            new net.neoforged.neoforge.fluids.capability.IFluidHandler() {
                @Override
                public int getTanks() {
                    return 1;
                }

                @Override
                public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
                    return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                }

                @Override
                public int getTankCapacity(int tank) {
                    return 16000;
                }

                @Override
                public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
                    return true;
                }

                @Override
                public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
                    var key = appeng.api.stacks.AEFluidKey.of(resource);
                    if (key == null) {
                        return 0;
                    }
                    return (int) returnPath.insert(key, resource.getAmount(),
                            action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                            IActionSource.empty());
                }

                @Override
                public net.neoforged.neoforge.fluids.FluidStack drain(
                        net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
                    return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                }

                @Override
                public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
                    return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                }
            };

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(isPowered(), isActive());
    }
}
