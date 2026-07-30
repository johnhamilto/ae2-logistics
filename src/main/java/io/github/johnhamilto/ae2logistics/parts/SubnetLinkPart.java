package io.github.johnhamilto.ae2logistics.parts;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModels;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.PartModel;
import appeng.parts.storagebus.StorageBusPart;
import appeng.util.prioritylist.IPartitionList;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.SubnetStorages;

/**
 * A storage bus whose target is a SUBNET instead of an inventory: the face carries a
 * genuinely separate grid (build it with real AE2 devices), power passes through like a
 * quartz fiber, and the main network mounts the subnet's storage - configured EXACTLY
 * like the storage bus this extends: partition slots, fuzzy/inverter/capacity/void
 * cards, access modes, priority, memory cards. One channel, like the bus it replaces.
 */
public class SubnetLinkPart extends StorageBusPart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/subnet_link"));

    /** Overlay energy and the storage window both key off the subnet grid. */
    private static final IGridNodeListener<SubnetLinkPart> SUBNET_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(SubnetLinkPart owner, IGridNode node) {
            owner.getHost().markForSave();
        }

        @Override
        public void onGridChanged(SubnetLinkPart owner, IGridNode node) {
            owner.invalidateEnergyOverlays();
            owner.refreshSubnetMount();
        }
    };

    @Nullable
    private IManagedGridNode subnetNode;
    private boolean subnetCreated;

    private final SubnetStorages.GridProxy subnetWindow = new SubnetStorages.GridProxy(
            this::subnetGrid, this::linkActive, "Subnet");
    private final MEInventoryHandler subnetHandler = new MEInventoryHandler(subnetWindow);

    public SubnetLinkPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().addService(appeng.me.energy.IEnergyOverlayGridConnection.class,
                this::subnetEnergyServices);
    }

    private boolean linkActive() {
        var host = getHost().getBlockEntity();
        return host.getLevel() != null && !host.isRemoved() && getMainNode().isActive();
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

    /** The storage-bus half always mounts the subnet, configured like a storage bus. */
    @Override
    public void mountInventories(IStorageMounts mounts) {
        configureSubnetHandler();
        mounts.mount(subnetHandler, getPriority());
    }

    private void configureSubnetHandler() {
        var access = getConfigManager().getSetting(Settings.ACCESS);
        subnetHandler.setAllowExtraction(access.isAllowExtraction());
        subnetHandler.setAllowInsertion(access.isAllowInsertion());
        subnetHandler.setWhitelist(isUpgradedWith(AEItems.INVERTER_CARD)
                ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        subnetHandler.setVoidOverflow(isUpgradedWith(AEItems.VOID_CARD));

        var filterBuilder = IPartitionList.builder();
        if (isUpgradedWith(AEItems.FUZZY_CARD)) {
            filterBuilder.fuzzyMode(getConfigManager().getSetting(Settings.FUZZY_MODE));
        }
        var config = getConfig();
        int slotsToUse = 18 + getInstalledUpgrades(AEItems.CAPACITY_CARD) * 9;
        for (int x = 0; x < config.size() && x < slotsToUse; x++) {
            filterBuilder.add(config.getKey(x));
        }
        subnetHandler.setPartitionList(filterBuilder.build());

        boolean filterOnExtract = getConfigManager().getSetting(Settings.FILTER_ON_EXTRACT) == YesNo.YES;
        // Extractable-only targets external inventories; a grid window is always extractable.
        subnetHandler.setExtractFiltering(filterOnExtract, false);
    }

    @Override
    protected void onConfigurationChanged() {
        super.onConfigurationChanged();
        refreshSubnetMount();
    }

    private void refreshSubnetMount() {
        if (getMainNode().getNode() != null) {
            configureSubnetHandler();
            IStorageProvider.requestUpdate(getMainNode());
        }
    }

    /** Shown in the storage bus GUI's "connected to" line. */
    @Override
    public Component getConnectedToDescription() {
        var subnet = subnetGrid();
        return Component.literal(subnet == null ? "Subnet" : "Subnet - " + subnet.size() + " devices");
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
        if (subnetNode != null) {
            subnetNode.saveToNBT(data);
        }
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        if (!isClientSide()) {
            subnetInstance().loadFromNBT(data);
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
