package io.github.johnhamilto.ae2logistics.parts;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * A universal mesh endpoint: joins a named frequency with a role (in/out/both), a
 * priority, and any subset of transport capabilities (redstone, items, fluids, energy,
 * signals). Two endpoints on one frequency are a universal point-to-point tunnel; more
 * make a many-to-many mesh. Costs one AE2 channel.
 */
public class MeshEndpointPart extends AEBasePart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/mesh_endpoint"));

    public static final byte ROLE_IN = 0;
    public static final byte ROLE_OUT = 1;
    public static final byte ROLE_BOTH = 2;

    public static final int FILTER_SLOTS = 9;

    private String frequency = "";
    private byte role = ROLE_IN;
    private int priority;
    private int capabilities;
    private final appeng.api.stacks.GenericStack[] filter =
            new appeng.api.stacks.GenericStack[FILTER_SLOTS];

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
    private final io.github.johnhamilto.ae2logistics.mesh.MeshProviderStorage providerStorage =
            new io.github.johnhamilto.ae2logistics.mesh.MeshProviderStorage(this);
    private final java.util.Set<appeng.api.stacks.AEKey> lastBatch = new java.util.HashSet<>();

    public MeshEndpointPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
                .setIdlePowerUsage(1.0);
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

    @Nullable
    public appeng.api.stacks.GenericStack filterSlot(int slot) {
        return filter[slot];
    }

    public void setFilterSlot(int slot, @Nullable appeng.api.stacks.GenericStack stack) {
        filter[slot] = stack == null ? null : new appeng.api.stacks.GenericStack(stack.what(), 1);
        getHost().markForSave();
    }

    /** An empty filter accepts everything; otherwise the key must match a slot exactly. */
    public boolean filterAccepts(appeng.api.stacks.AEKey key) {
        boolean empty = true;
        for (var slot : filter) {
            if (slot != null) {
                if (slot.what().equals(key)) {
                    return true;
                }
                empty = false;
            }
        }
        return empty;
    }

    public long stableKey() {
        var host = getHost().getBlockEntity();
        return host.getBlockPos().asLong() * 31 + (getSide() == null ? 6 : getSide().ordinal());
    }

    public void applyMeshConfig(String newFrequency, byte newRole, int newPriority, int newCapabilities) {
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
            var stacks = new java.util.ArrayList<appeng.api.stacks.GenericStack>(FILTER_SLOTS);
            boolean any = false;
            for (var stack : filter) {
                stacks.add(stack);
                any |= stack != null;
            }
            if (any) {
                builder.set(AE2Logistics.EXPORTED_MESH_FILTER.get(),
                        java.util.Collections.unmodifiableList(stacks));
            }
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
            applyMeshConfig(tag.getString("freq"), (byte) Math.floorMod(tag.getByte("role"), 3),
                    tag.getInt("priority"), tag.getInt("capabilities") & 63);
            var stacks = input.get(AE2Logistics.EXPORTED_MESH_FILTER.get());
            for (int i = 0; i < FILTER_SLOTS; i++) {
                setFilterSlot(i, stacks != null && i < stacks.size() ? stacks.get(i) : null);
            }
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
        return host.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                host.getBlockPos().relative(getSide()), getSide().getOpposite());
    }

    @Nullable
    public IFluidHandler adjacentFluidHandler() {
        var host = getHost().getBlockEntity();
        if (host.getLevel() == null) {
            return null;
        }
        return host.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                host.getBlockPos().relative(getSide()), getSide().getOpposite());
    }

    @Nullable
    public IEnergyStorage adjacentEnergyHandler() {
        var host = getHost().getBlockEntity();
        if (host.getLevel() == null) {
            return null;
        }
        return host.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
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

    @Nullable
    public SignalService signalService() {
        var node = getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return null;
        }
        return node.getGrid().getService(SignalService.class);
    }

    public void publishSignals(Map<ResourceLocation, Long> signals) {
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

    // --- provider-P2P support ---

    /** Remembers the last batch delivered here; the machine counts as busy until it drains. */
    public void noteDelivered(java.util.Set<appeng.api.stacks.AEKey> keys) {
        lastBatch.clear();
        lastBatch.addAll(keys);
    }

    public boolean isBusy() {
        if (lastBatch.isEmpty()) {
            return false;
        }
        var items = adjacentItemHandler();
        var fluids = adjacentFluidHandler();
        for (var key : lastBatch) {
            if (key instanceof appeng.api.stacks.AEItemKey itemKey && items != null) {
                for (int i = 0; i < items.getSlots(); i++) {
                    if (itemKey.matches(items.getStackInSlot(i))) {
                        return true;
                    }
                }
            } else if (key instanceof appeng.api.stacks.AEFluidKey fluidKey && fluids != null) {
                for (int i = 0; i < fluids.getTanks(); i++) {
                    var tank = fluids.getFluidInTank(i);
                    if (!tank.isEmpty() && FluidStack.isSameFluidSameComponents(fluidKey.toStack(1), tank)) {
                        return true;
                    }
                }
            }
        }
        lastBatch.clear();
        return false;
    }

    @Nullable
    public appeng.api.storage.MEStorage exposedMeStorage() {
        return attuned(MeshRegistry.TYPE_ITEM | MeshRegistry.TYPE_FLUID) && role != ROLE_OUT
                ? providerStorage
                : null;
    }

    // --- exposed capabilities (insert-only; mirror the next target for blocking mode) ---

    @Nullable
    public IItemHandler exposedItemHandler() {
        return attuned(MeshRegistry.TYPE_ITEM) && role != ROLE_OUT ? itemHandler : null;
    }

    @Nullable
    public IFluidHandler exposedFluidHandler() {
        return attuned(MeshRegistry.TYPE_FLUID) && role != ROLE_OUT ? fluidHandler : null;
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
            var key = appeng.api.stacks.AEItemKey.of(stack);
            if (key == null || !filterAccepts(key)) {
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
            var key = appeng.api.stacks.AEItemKey.of(stack);
            return key != null && filterAccepts(key);
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
            if (resource.isEmpty() || !filterAccepts(appeng.api.stacks.AEFluidKey.of(resource))) {
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
        bch.addBox(5, 5, 11, 11, 11, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 16;
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
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putString("freq", frequency);
        data.putByte("role", role);
        data.putInt("priority", priority);
        data.putInt("capabilities", capabilities);
        var filterList = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            var tag = new CompoundTag();
            if (filter[i] != null) {
                tag.put("key", filter[i].what().toTagGeneric(registries));
            }
            filterList.add(tag);
        }
        data.put("filter", filterList);
        if (carriedNode != null) {
            carriedNode.saveToNBT(data);
        }
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        frequency = data.getString("freq");
        role = data.getByte("role");
        priority = data.getInt("priority");
        capabilities = data.getInt("capabilities");
        var filterList = data.getList("filter", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filter[i] = null;
            if (i < filterList.size()) {
                var tag = filterList.getCompound(i);
                if (tag.contains("key")) {
                    var key = appeng.api.stacks.AEKey.fromTagGeneric(registries, tag.getCompound("key"));
                    filter[i] = key == null ? null : new appeng.api.stacks.GenericStack(key, 1);
                }
            }
        }
        if ((capabilities & MeshRegistry.TYPE_ME) != 0 && !isClientSide()) {
            carriedInstance().loadFromNBT(data);
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
