package io.github.johnhamilto.ae2logistics.parts;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.SubnetStorages;
import io.github.johnhamilto.ae2logistics.menu.SubnetLinkMenu;

/**
 * A quartz fiber, an empty interface, and a storage bus in one part: the face carries a
 * genuinely separate SUBNET grid (build it with real AE2 devices), power passes through
 * like a fiber, and a configurable storage window links the two networks - the subnet
 * sees main, main sees the subnet, or both, with a priority and a nine-slot whitelist.
 * Costs one channel on the main network, exactly like the storage bus it replaces.
 */
public class SubnetLinkPart extends AEBasePart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/subnet_link"));

    public static final byte MODE_SUBNET_SEES_MAIN = 0;
    public static final byte MODE_MAIN_SEES_SUBNET = 1;
    public static final byte MODE_BOTH = 2;

    public static final int FILTER_SLOTS = 9;

    /** Overlay energy + storage windows both key off the subnet grid, so changes matter. */
    private static final IGridNodeListener<SubnetLinkPart> SUBNET_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(SubnetLinkPart owner, IGridNode node) {
            owner.getHost().markForSave();
        }

        @Override
        public void onGridChanged(SubnetLinkPart owner, IGridNode node) {
            owner.invalidateEnergyOverlays();
            owner.requestStorageUpdates();
        }
    };

    private byte mode = MODE_SUBNET_SEES_MAIN;
    private int priority;
    private final appeng.api.stacks.GenericStack[] filter =
            new appeng.api.stacks.GenericStack[FILTER_SLOTS];

    @Nullable
    private IManagedGridNode subnetNode;
    private boolean subnetCreated;

    private final SubnetStorages.GridProxy mainWindow = new SubnetStorages.GridProxy(
            this::mainGrid, this::isActiveAndLoaded, "Subnet window into the main network");
    private final SubnetStorages.GridProxy subnetWindow = new SubnetStorages.GridProxy(
            this::subnetGrid, this::isActiveAndLoaded, "Subnet storage");

    public SubnetLinkPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0)
                .addService(appeng.me.energy.IEnergyOverlayGridConnection.class,
                        this::subnetEnergyServices)
                .addService(IStorageProvider.class, mounts -> {
                    if (mode == MODE_MAIN_SEES_SUBNET || mode == MODE_BOTH) {
                        mounts.mount(new SubnetStorages.Gated(subnetWindow,
                                this::isActiveAndLoaded, this::filterAccepts), priority);
                    }
                });
    }

    public byte mode() {
        return mode;
    }

    public int priority() {
        return priority;
    }

    public boolean isActiveAndLoaded() {
        var host = getHost().getBlockEntity();
        return host.getLevel() != null && !host.isRemoved() && getMainNode().isActive();
    }

    @Nullable
    public appeng.api.stacks.GenericStack filterSlot(int slot) {
        return filter[slot];
    }

    public void setFilterSlot(int slot, @Nullable appeng.api.stacks.GenericStack stack) {
        filter[slot] = stack == null ? null : new appeng.api.stacks.GenericStack(stack.what(), 1);
        getHost().markForSave();
        requestStorageUpdates();
    }

    /** An empty filter accepts everything; otherwise the key must match a slot exactly. */
    public boolean filterAccepts(AEKey key) {
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

    public void applyConfig(byte newMode, int newPriority) {
        this.mode = (byte) Math.floorMod(newMode, 3);
        this.priority = newPriority;
        getHost().markForSave();
        requestStorageUpdates();
    }

    private void requestStorageUpdates() {
        if (getMainNode().getNode() != null) {
            IStorageProvider.requestUpdate(getMainNode());
        }
        if (subnetNode != null && subnetNode.getNode() != null) {
            IStorageProvider.requestUpdate(subnetNode);
        }
    }

    @Nullable
    public IGrid mainGrid() {
        var node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    @Nullable
    public IGrid subnetGrid() {
        var node = subnetNode();
        return node == null ? null : node.getGrid();
    }

    private java.util.Collection<appeng.me.service.EnergyService> mainEnergyServices() {
        var grid = mainGrid();
        return grid == null ? java.util.List.of()
                : java.util.List.of((appeng.me.service.EnergyService) grid.getEnergyService());
    }

    private java.util.Collection<appeng.me.service.EnergyService> subnetEnergyServices() {
        var grid = subnetGrid();
        return grid == null ? java.util.List.of()
                : java.util.List.of((appeng.me.service.EnergyService) grid.getEnergyService());
    }

    private void invalidateEnergyOverlays() {
        var main = mainGrid();
        if (main != null) {
            ((appeng.me.service.EnergyService) main.getEnergyService()).invalidateOverlayEnergyGrid();
        }
        var subnet = subnetGrid();
        if (subnet != null) {
            ((appeng.me.service.EnergyService) subnet.getEnergyService()).invalidateOverlayEnergyGrid();
        }
    }

    private IManagedGridNode subnetInstance() {
        if (subnetNode == null) {
            subnetNode = appeng.api.networking.GridHelper
                    .createManagedNode(this, SUBNET_LISTENER)
                    .setInWorldNode(true)
                    .setTagName("subnet")
                    .setIdlePowerUsage(0)
                    .addService(appeng.me.energy.IEnergyOverlayGridConnection.class,
                            this::mainEnergyServices)
                    .addService(IStorageProvider.class, mounts -> {
                        if (mode == MODE_SUBNET_SEES_MAIN || mode == MODE_BOTH) {
                            mounts.mount(new SubnetStorages.Gated(mainWindow,
                                    this::isActiveAndLoaded, this::filterAccepts), priority);
                        }
                    })
                    .setVisualRepresentation(getPartItem().asItem());
            subnetCreated = false;
        }
        return subnetNode;
    }

    private void ensureSubnetNode() {
        if (isClientSide()) {
            return;
        }
        var node = subnetInstance();
        if (!subnetCreated && getLevel() != null && getSide() != null) {
            node.setExposedOnSides(java.util.EnumSet.of(getSide()));
            node.create(getLevel(), getBlockEntity().getBlockPos());
            subnetCreated = true;
            invalidateEnergyOverlays();
        }
    }

    private void destroySubnetNode() {
        if (subnetNode != null) {
            subnetNode.destroy();
            subnetNode = null;
            subnetCreated = false;
        }
    }

    /** The face-side node that carries the subnet grid. */
    @Nullable
    public IGridNode subnetNode() {
        return subnetNode == null ? null : subnetNode.getNode();
    }

    @Override
    public IGridNode getExternalFacingNode() {
        return subnetNode();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        invalidateEnergyOverlays();
        requestStorageUpdates();
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        if (!isClientSide()) {
            ensureSubnetNode();
        }
    }

    @Override
    public void removeFromWorld() {
        if (!isClientSide()) {
            destroySubnetNode();
        }
        super.removeFromWorld();
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putByte("mode", mode);
        data.putInt("priority", priority);
        var filterTag = new CompoundTag();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (filter[i] != null) {
                filterTag.put("slot" + i,
                        appeng.api.stacks.GenericStack.writeTag(registries, filter[i]));
            }
        }
        data.put("filter", filterTag);
        if (subnetNode != null) {
            subnetNode.saveToNBT(data);
        }
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        mode = (byte) Math.floorMod(data.getByte("mode"), 3);
        priority = data.getInt("priority");
        var filterTag = data.getCompound("filter");
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filter[i] = filterTag.contains("slot" + i)
                    ? appeng.api.stacks.GenericStack.readTag(registries, filterTag.getCompound("slot" + i))
                    : null;
        }
        if (!isClientSide()) {
            subnetInstance().loadFromNBT(data);
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        // AE2's P2P tunnel chassis boxes - the wireframe must match the model.
        bch.addBox(5, 5, 12, 11, 11, 13);
        bch.addBox(3, 3, 13, 13, 13, 14);
        bch.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    @Override
    public AECableType getExternalCableConnectionType() {
        return AECableType.SMART;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new SubnetLinkMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> SubnetLinkMenu.writeOpenData(buffer, this));
        }
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
