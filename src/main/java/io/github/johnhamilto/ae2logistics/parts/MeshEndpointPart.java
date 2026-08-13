package io.github.johnhamilto.ae2logistics.parts;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.networking.GridFlags;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.MeshEndpointMenu;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * A mesh endpoint: joins a named frequency with a role (in/out/both), a priority, and a
 * set of transport capabilities (redstone, items, fluids, energy, signals, ME). Two
 * endpoints on one frequency are a universal point-to-point tunnel; more make a
 * many-to-many mesh. Costs one AE2 channel.
 *
 * <p>The universal part item exposes all six capabilities as GUI toggles; the typed part
 * items lock the mask to a single transport (combine all six to craft the universal).
 */
public class MeshEndpointPart extends AEBasePart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/mesh_endpoint"));
    @PartModels
    public static final IPartModel MODEL_REDSTONE = new PartModel(AE2Logistics.id("part/mesh_endpoint_redstone"));
    @PartModels
    public static final IPartModel MODEL_ITEM = new PartModel(AE2Logistics.id("part/mesh_endpoint_item"));
    @PartModels
    public static final IPartModel MODEL_FLUID = new PartModel(AE2Logistics.id("part/mesh_endpoint_fluid"));
    @PartModels
    public static final IPartModel MODEL_ENERGY = new PartModel(AE2Logistics.id("part/mesh_endpoint_energy"));
    @PartModels
    public static final IPartModel MODEL_SIGNAL = new PartModel(AE2Logistics.id("part/mesh_endpoint_signal"));
    @PartModels
    public static final IPartModel MODEL_ME = new PartModel(AE2Logistics.id("part/mesh_endpoint_me"));
    @PartModels
    public static final IPartModel MODEL_PROVIDER = new PartModel(AE2Logistics.id("part/mesh_endpoint_provider"));

    private record TypedVariant(int mask, IPartModel model) {
    }

    /** Part items whose capability mask is fixed; anything else is the universal part. */
    private static final Map<Identifier, TypedVariant> TYPED = Map.of(
            AE2Logistics.id("mesh_endpoint_redstone"), new TypedVariant(MeshRegistry.TYPE_REDSTONE, MODEL_REDSTONE),
            AE2Logistics.id("mesh_endpoint_item"), new TypedVariant(MeshRegistry.TYPE_ITEM, MODEL_ITEM),
            AE2Logistics.id("mesh_endpoint_fluid"), new TypedVariant(MeshRegistry.TYPE_FLUID, MODEL_FLUID),
            AE2Logistics.id("mesh_endpoint_energy"), new TypedVariant(MeshRegistry.TYPE_ENERGY, MODEL_ENERGY),
            AE2Logistics.id("mesh_endpoint_signal"), new TypedVariant(MeshRegistry.TYPE_SIGNAL, MODEL_SIGNAL),
            AE2Logistics.id("mesh_endpoint_me"), new TypedVariant(MeshRegistry.TYPE_ME, MODEL_ME),
            AE2Logistics.id("mesh_endpoint_provider"), new TypedVariant(MeshRegistry.TYPE_PROVIDER, MODEL_PROVIDER));

    public static final byte ROLE_IN = 0;
    public static final byte ROLE_OUT = 1;
    public static final byte ROLE_BOTH = 2;


    private String frequency = "";
    private byte role = ROLE_IN;
    private int priority;
    private int capabilities = MeshRegistry.TYPE_ALL;

    private int meshRedstone;
    /** Transient ME link state, maintained by {@link MeshRegistry#tick}. */
    private byte meLinkState;

    /**
     * ME tunneling is true P2P: this second node is exposed ONLY on the part's face, so
     * whatever network touches the face is carried through the mesh star - the host
     * network the endpoint sits on is never fused. Same two-node trick as AE2's own ME
     * P2P tunnel; fresh instance per attunement because managed nodes are single-use.
     */
    @Nullable
    private appeng.api.networking.IManagedGridNode carriedNode;
    private boolean carriedCreated;
    @Nullable
    private SignalService publishedTo;

    private final IItemHandler itemHandler = new MeshItemHandler();
    private final IFluidHandler fluidHandler = new MeshFluidHandler();
    private final IEnergyStorage energyHandler = new MeshEnergyHandler();
    private final io.github.johnhamilto.ae2logistics.provider.ProviderBatchRouter<MeshEndpointPart> providerStorage =
            new io.github.johnhamilto.ae2logistics.provider.ProviderBatchRouter<>(new MeshProviderTargets());
    private final java.util.Set<appeng.api.stacks.AEKey> lastBatch = new java.util.HashSet<>();
    private final appeng.api.storage.MEStorage providerReturnPath = new ProviderReturnPath();
    private final appeng.api.behaviors.GenericInternalInventory returnGenericInv =
            io.github.johnhamilto.ae2logistics.provider.ReturnAdapters.genericInv(providerReturnPath);
    private final IItemHandler returnItemHandler =
            io.github.johnhamilto.ae2logistics.provider.ReturnAdapters.itemHandler(providerReturnPath);
    private final IFluidHandler returnFluidHandler =
            io.github.johnhamilto.ae2logistics.provider.ReturnAdapters.fluidHandler(providerReturnPath);

    public MeshEndpointPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
                .setIdlePowerUsage(1.0);
        var typed = typedVariant();
        if (typed != null) {
            capabilities = typed.mask();
        }
    }

    @Nullable
    private TypedVariant typedVariant() {
        return TYPED.get(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(getPartItem().asItem()));
    }

    /** True for the typed part items, whose capability mask cannot be edited. */
    public boolean capabilityLocked() {
        return typedVariant() != null;
    }

    public String frequency() {
        return frequency;
    }

    public int priority() {
        return priority;
    }

    public byte role() {
        return role;
    }

    public int capabilityMask() {
        return capabilities;
    }

    public boolean attuned(int type) {
        return (capabilities & type) != 0;
    }

    public boolean isSource(int type) {
        return attuned(type) && role != ROLE_OUT && isActiveAndLoaded();
    }

    public boolean isValidTarget(int type) {
        return attuned(type) && role != ROLE_IN && isActiveAndLoaded();
    }

    public boolean isActiveAndLoaded() {
        var host = getHost().getBlockEntity();
        return host.getLevel() != null && !host.isRemoved() && getMainNode().isActive();
    }

    public byte meLinkState() {
        return meLinkState;
    }

    public void setMeLinkState(byte state) {
        meLinkState = state;
    }


    public long stableKey() {
        var host = getHost().getBlockEntity();
        return host.getBlockPos().asLong() * 31 + (getSide() == null ? 6 : getSide().ordinal());
    }

    public void applyMeshConfig(String newFrequency, byte newRole, int newPriority, int newCapabilities) {
        var typed = typedVariant();
        if (typed != null) {
            newCapabilities = typed.mask();
        }
        // Unregistering changes the old frequency's membership, so the registry tears
        // down and rebuilds that frequency's ME lanes without us on the next tick.
        MeshRegistry.unregister(this);
        this.frequency = newFrequency.length() > 32 ? newFrequency.substring(0, 32) : newFrequency;
        this.role = newRole;
        this.priority = newPriority;
        this.capabilities = newCapabilities;
        if (!attuned(MeshRegistry.TYPE_SIGNAL)) {
            withdrawSignals();
        }
        if (!attuned(MeshRegistry.TYPE_REDSTONE)) {
            setMeshRedstone(0);
        }
        if (attuned(MeshRegistry.TYPE_ME)) {
            ensureCarriedNode();
        } else {
            destroyCarriedNode();
        }
        MeshRegistry.register(this);
        getHost().markForSave();
    }

    private appeng.api.networking.IManagedGridNode carriedInstance() {
        if (carriedNode == null) {
            carriedNode = appeng.api.networking.GridHelper
                    .createManagedNode(this, NodeListener.INSTANCE)
                    .setInWorldNode(true)
                    .setTagName("carried")
                    .setIdlePowerUsage(0)
                    .setFlags(appeng.api.networking.GridFlags.DENSE_CAPACITY,
                            appeng.api.networking.GridFlags.CANNOT_CARRY_COMPRESSED)
                    .setVisualRepresentation(getPartItem().asItem());
            carriedCreated = false;
        }
        return carriedNode;
    }

    private void ensureCarriedNode() {
        if (isClientSide()) {
            return;
        }
        var node = carriedInstance();
        if (!carriedCreated && getLevel() != null && getSide() != null) {
            node.setExposedOnSides(java.util.EnumSet.of(getSide()));
            node.create(getLevel(), getBlockEntity().getBlockPos());
            carriedCreated = true;
        }
    }

    private void destroyCarriedNode() {
        if (carriedNode != null) {
            carriedNode.destroy();
            carriedNode = null;
            carriedCreated = false;
        }
    }

    /** The node the mesh's ME lanes link; carries the fed network, never the host. */
    @Nullable
    public appeng.api.networking.IGridNode carriedNode() {
        return carriedNode == null ? null : carriedNode.getNode();
    }

    @Override
    public appeng.api.networking.IGridNode getExternalFacingNode() {
        return carriedNode();
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
            net.minecraft.core.component.DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);
        if (mode == appeng.util.SettingsFrom.MEMORY_CARD) {
            var tag = new CompoundTag();
            tag.putString("freq", frequency);
            tag.putByte("role", role);
            tag.putInt("priority", priority);
            tag.putInt("capabilities", capabilities);
            builder.set(AE2Logistics.EXPORTED_MESH_SETTINGS.get(), tag);
        }
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
            net.minecraft.core.component.DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (isClientSide()) {
            return;
        }
        var tag = input.get(AE2Logistics.EXPORTED_MESH_SETTINGS.get());
        if (tag != null) {
            applyMeshConfig(tag.getStringOr("freq", ""), (byte) Math.floorMod(tag.getByteOr("role", (byte) 0), 3),
                    tag.getIntOr("priority", 0), tag.getIntOr("capabilities", 0) & MeshRegistry.TYPE_ALL);
        }
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        if (!isClientSide()) {
            if (attuned(MeshRegistry.TYPE_ME)) {
                ensureCarriedNode();
            }
            MeshRegistry.register(this);
        }
    }

    @Override
    public void removeFromWorld() {
        if (!isClientSide()) {
            MeshRegistry.unregister(this);
            destroyCarriedNode();
        }
        super.removeFromWorld();
    }

    // --- transport plumbing used by MeshRegistry ---

    @Nullable
    public IItemHandler adjacentItemHandler() {
        var host = getHost().getBlockEntity();
        if (host.getLevel() == null) {
            return null;
        }
        return host.getLevel().getCapability(Capabilities.Item.BLOCK,
                host.getBlockPos().relative(getSide()), getSide().getOpposite());
    }

    @Nullable
    public IFluidHandler adjacentFluidHandler() {
        var host = getHost().getBlockEntity();
        if (host.getLevel() == null) {
            return null;
        }
        return host.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                host.getBlockPos().relative(getSide()), getSide().getOpposite());
    }

    @Nullable
    public IEnergyStorage adjacentEnergyHandler() {
        var host = getHost().getBlockEntity();
        if (host.getLevel() == null) {
            return null;
        }
        return host.getLevel().getCapability(Capabilities.Energy.BLOCK,
                host.getBlockPos().relative(getSide()), getSide().getOpposite());
    }

    public int readFaceRedstone() {
        var host = getHost().getBlockEntity();
        if (host.getLevel() == null) {
            return 0;
        }
        return host.getLevel().getSignal(host.getBlockPos().relative(getSide()), getSide());
    }

    public void setMeshRedstone(int level) {
        if (meshRedstone != level) {
            meshRedstone = level;
            var host = getHost().getBlockEntity();
            var world = host.getLevel();
            if (world != null) {
                var block = world.getBlockState(host.getBlockPos()).getBlock();
                world.updateNeighborsAt(host.getBlockPos(), block);
                world.updateNeighborsAt(host.getBlockPos().relative(getSide()), block);
            }
        }
    }

    /**
     * The FED network's signal service - the grid touching the part's face, like every
     * other transport (the host network is only the carrier). Input faces read it,
     * output faces inject into it, so logic graphs bridge subnets THROUGH the mesh.
     */
    @Nullable
    public SignalService signalService() {
        var host = getHost().getBlockEntity();
        var level = host.getLevel();
        if (level == null || level.isClientSide() || getSide() == null) {
            return null;
        }
        var node = appeng.api.networking.GridHelper.getExposedNode(level,
                host.getBlockPos().relative(getSide()), getSide().getOpposite());
        if (node == null || node.getGrid() == null) {
            return null;
        }
        return node.getGrid().getService(SignalService.class);
    }

    public void publishSignals(Map<Identifier, Long> signals) {
        var service = signalService();
        if (service != publishedTo && publishedTo != null) {
            publishedTo.setExternal(this, Map.of());
        }
        publishedTo = service;
        if (service != null) {
            service.setExternal(this, signals);
        }
    }

    public void withdrawSignals() {
        if (publishedTo != null) {
            publishedTo.setExternal(this, Map.of());
            publishedTo = null;
        }
    }

    // --- provider-P2P support (the provider transport; key-type agnostic) ---

    /** Remembers the last batch delivered here; the machine counts as busy until it drains. */
    public void noteDelivered(java.util.Set<appeng.api.stacks.AEKey> keys) {
        lastBatch.clear();
        lastBatch.addAll(keys);
    }

    /** The push target behind this endpoint's face, resolved like a pattern provider's. */
    @Nullable
    public appeng.api.storage.MEStorage adjacentProviderTarget() {
        var host = getHost().getBlockEntity();
        if (!(host.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return null;
        }
        return io.github.johnhamilto.ae2logistics.provider.ProviderTargets.resolve(level,
                host.getBlockPos().relative(getSide()), getSide().getOpposite());
    }

    public boolean isBusy() {
        if (lastBatch.isEmpty()) {
            return false;
        }
        var target = adjacentProviderTarget();
        if (target != null
                && io.github.johnhamilto.ae2logistics.provider.ProviderTargets.containsAny(target, lastBatch)) {
            return true;
        }
        lastBatch.clear();
        return false;
    }

    /**
     * Inputs face the real provider and expose the push router; strict Outputs face
     * machines and expose the return path (parity with the Provider P2P Tunnel).
     * Both-role endpoints keep the router: a face cannot serve both directions.
     */
    @Nullable
    public appeng.api.storage.MEStorage exposedMeStorage() {
        if (!attuned(MeshRegistry.TYPE_PROVIDER)) {
            return null;
        }
        return role != ROLE_OUT ? providerStorage : providerReturnPath;
    }

    /** Machines return any registered key type through the generic view (chemicals, flux, ...). */
    @Nullable
    public appeng.api.behaviors.GenericInternalInventory exposedReturnGenericInv() {
        return attuned(MeshRegistry.TYPE_PROVIDER) && role == ROLE_OUT ? returnGenericInv : null;
    }

    /** The bare return path, for compat bridges wrapping their own capability around it. */
    @Nullable
    public appeng.api.storage.MEStorage exposedProviderReturnPath() {
        return attuned(MeshRegistry.TYPE_PROVIDER) && role == ROLE_OUT ? providerReturnPath : null;
    }

    /** Insert-only view forwarding output-face returns to the frequency's input faces. */
    private class ProviderReturnPath implements appeng.api.storage.MEStorage {

        /** One hop per transfer: a return can never re-enter another endpoint's return. */
        private static final ThreadLocal<Boolean> RETURNING = ThreadLocal.withInitial(() -> false);

        @Override
        public long insert(appeng.api.stacks.AEKey what, long amount,
                appeng.api.config.Actionable mode,
                appeng.api.networking.security.IActionSource source) {
            if (RETURNING.get()) {
                return 0;
            }
            RETURNING.set(true);
            try {
                if (!isActiveAndLoaded()) {
                    return 0;
                }
                long total = 0;
                for (var input : MeshRegistry.inputs(frequency, MeshRegistry.TYPE_PROVIDER,
                        MeshEndpointPart.this)) {
                    var target = input.adjacentProviderTarget();
                    if (target == null) {
                        continue;
                    }
                    total += target.insert(what, amount - total, mode, source);
                    if (total >= amount) {
                        break;
                    }
                }
                return total;
            } finally {
                RETURNING.set(false);
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal("Mesh Provider Return " + frequency);
        }
    }

    private class MeshProviderTargets
            implements io.github.johnhamilto.ae2logistics.provider.ProviderBatchRouter.Targets<MeshEndpointPart> {
        @Override
        public Iterable<MeshEndpointPart> candidates() {
            return MeshRegistry.outputs(frequency, MeshRegistry.TYPE_PROVIDER, MeshEndpointPart.this);
        }

        @Override
        public boolean accepts(MeshEndpointPart target, appeng.api.stacks.AEKey what) {
            return target.isValidTarget(MeshRegistry.TYPE_PROVIDER);
        }

        @Override
        @Nullable
        public appeng.api.storage.MEStorage storageOf(MeshEndpointPart target) {
            return target.adjacentProviderTarget();
        }

        @Override
        public boolean isBusy(MeshEndpointPart target) {
            return target.isBusy();
        }

        @Override
        public boolean blockingMode() {
            var host = getHost().getBlockEntity();
            return host.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                    && io.github.johnhamilto.ae2logistics.provider.ProviderTargets
                            .blockingModeAt(level, host.getBlockPos(), getSide());
        }

        @Override
        public void noteDelivered(MeshEndpointPart target, java.util.Set<appeng.api.stacks.AEKey> keys) {
            target.noteDelivered(keys);
        }

        @Override
        public Component description() {
            return Component.literal("Mesh Frequency " + frequency);
        }
    }

    // --- exposed capabilities (insert-only; mirror the next target for blocking mode) ---

    @Nullable
    public IItemHandler exposedItemHandler() {
        if (attuned(MeshRegistry.TYPE_ITEM) && role != ROLE_OUT) {
            return itemHandler;
        }
        return attuned(MeshRegistry.TYPE_PROVIDER) && role == ROLE_OUT ? returnItemHandler : null;
    }

    @Nullable
    public IFluidHandler exposedFluidHandler() {
        if (attuned(MeshRegistry.TYPE_FLUID) && role != ROLE_OUT) {
            return fluidHandler;
        }
        return attuned(MeshRegistry.TYPE_PROVIDER) && role == ROLE_OUT ? returnFluidHandler : null;
    }

    @Nullable
    public IEnergyStorage exposedEnergyHandler() {
        return attuned(MeshRegistry.TYPE_ENERGY) && role != ROLE_OUT ? energyHandler : null;
    }

    private class MeshItemHandler implements IItemHandler {
        @Nullable
        private IItemHandler mirror() {
            var target = MeshRegistry.peekTarget(frequency, MeshRegistry.TYPE_ITEM, MeshEndpointPart.this);
            return target == null ? null : target.adjacentItemHandler();
        }

        @Override
        public int getSlots() {
            var mirror = mirror();
            return mirror == null ? 1 : mirror.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            var mirror = mirror();
            return mirror == null || slot >= mirror.getSlots() ? ItemStack.EMPTY : mirror.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (appeng.api.stacks.AEItemKey.of(stack) == null) {
                return stack;
            }
            return MeshRegistry.forwardItem(MeshEndpointPart.this, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return appeng.api.stacks.AEItemKey.of(stack) != null;
        }
    }

    private class MeshFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 16000;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            return MeshRegistry.forwardFluid(MeshEndpointPart.this, resource, action.simulate());
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    private class MeshEnergyHandler implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return MeshRegistry.forwardEnergy(MeshEndpointPart.this, maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    // --- redstone emission ---

    @Override
    public boolean canConnectRedstone() {
        return attuned(MeshRegistry.TYPE_REDSTONE);
    }

    @Override
    public int isProvidingStrongPower() {
        return role != ROLE_IN && attuned(MeshRegistry.TYPE_REDSTONE) ? meshRedstone : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return isProvidingStrongPower();
    }

    // --- part boilerplate ---

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        // AE2's P2P tunnel chassis boxes - the placement wireframe must match the model.
        bch.addBox(5, 5, 12, 11, 11, 13);
        bch.addBox(3, 3, 13, 13, 13, 14);
        bch.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        // AE2's P2P tunnel chassis values: short host-side arm, dense-smart face
        // connection so fed cables visually connect to the tunnel plate.
        return 1;
    }

    @Override
    public AECableType getExternalCableConnectionType() {
        return AECableType.DENSE_SMART;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new MeshEndpointMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> MeshEndpointMenu.writeOpenData(buffer, this));
        }
        return true;
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        data.putString("freq", frequency);
        data.putByte("role", role);
        data.putInt("priority", priority);
        data.putInt("capabilities", capabilities);
        if (carriedNode != null) {
            carriedNode.serialize(data);
        }
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        frequency = data.getStringOr("freq", "");
        role = data.getByteOr("role", (byte) 0);
        priority = data.getIntOr("priority", 0);
        capabilities = data.getIntOr("capabilities", 0);
        var typed = typedVariant();
        if (typed != null) {
            capabilities = typed.mask();
        }
        if ((capabilities & MeshRegistry.TYPE_ME) != 0 && !isClientSide()) {
            carriedInstance().deserialize(data);
        }
    }

    @Override
    public IPartModel getStaticModels() {
        var typed = typedVariant();
        return typed == null ? MODEL : typed.model();
    }
}
