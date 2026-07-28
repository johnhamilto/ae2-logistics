package io.github.johnhamilto.ae2logistics.block;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.me.storage.ExternalStorageFacade;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.config.TransferableSettings;

/**
 * F8 slice 2: an entire ME subnet in one block. The core is a normal machine on the
 * host grid (one channel), and hosts a genuinely separate internal grid - a virtual hub
 * star powered THROUGH the core quartz-fiber-style - whose devices are list entries:
 * face-bound storage/import/export buses, an uplink (subnet sees main storage), and
 * downlinks (main sees subnet storage, costing a main channel each like a physical
 * storage bus would). Internal devices consume the internal ad-hoc grid's eight
 * channels; the subnet buys compactness and legibility, never channel capacity.
 */
public class SubnetCoreBlockEntity extends BlockEntity implements IInWorldGridNodeHost, TransferableSettings {

    public static final int ENTRIES = 8;
    private static final int TRANSFER_INTERVAL_TICKS = 10;
    private static final int ITEMS_PER_OPERATION = 8;

    private static final IGridNodeListener<SubnetCoreBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(SubnetCoreBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    };

    // Power is shared between the two grids exactly like a quartz fiber: each node
    // exposes the OTHER grid's energy service on AE2's overlay energy grid, so power
    // state propagates natively and the subnet goes dark with the main network.
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(2.0)
            .setVisualRepresentation(AE2Logistics.SUBNET_CORE_ITEM.get())
            .addService(appeng.me.energy.IEnergyOverlayGridConnection.class,
                    this::internalEnergyServices);

    /** Backbone of the internal grid; carries no channels itself. */
    private final IManagedGridNode hubNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(false)
            .setTagName("hubnode")
            .setIdlePowerUsage(1.0)
            .addService(appeng.me.energy.IEnergyOverlayGridConnection.class,
                    this::mainEnergyServices);

    private final SubnetCoreEntry[] entries = new SubnetCoreEntry[ENTRIES];
    private final SubnetStorages.GridProxy mainProxy = new SubnetStorages.GridProxy(
            this::mainGrid, () -> mainNode.getNode() != null && mainNode.getNode().isActive(),
            "Subnet uplink to main network");
    private final SubnetStorages.GridProxy internalProxy = new SubnetStorages.GridProxy(
            this::internalGrid, () -> hubNode.getNode() != null,
            "Subnet storage");
    private long tickCounter;

    public SubnetCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.SUBNET_CORE_BE.get(), pos, state);
        for (int i = 0; i < ENTRIES; i++) {
            entries[i] = new SubnetCoreEntry(this, i);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            GridHelper.onFirstTick(this, be -> {
                be.mainNode.create(be.level, be.getBlockPos());
                be.hubNode.create(be.level, be.getBlockPos());
                for (var entry : be.entries) {
                    entry.enable();
                    // Entries configured before this tick have nodes but no star yet.
                    be.connectEntry(entry);
                }
                // Both sides exist now; rebuild the shared energy overlay.
                be.invalidateEnergyOverlays();
            });
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        destroyNodes();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        destroyNodes();
    }

    private void destroyNodes() {
        for (var entry : entries) {
            entry.disable();
        }
        hubNode.destroy();
        mainNode.destroy();
    }

    /** Downlinks join the MAIN star (they are main-grid devices); everything else joins the hub. */
    void connectEntry(SubnetCoreEntry entry) {
        var managed = entry.managedNode();
        if (managed == null || managed.getNode() == null || entry.isConnected()
                || entry.type() == null) {
            return;
        }
        var star = entry.type() == SubnetCoreEntry.Type.DOWNLINK ? mainNode.getNode()
                : hubNode.getNode();
        if (star == null) {
            return;
        }
        appeng.me.GridConnection.create(star, managed.getNode(), null);
        entry.markConnected();
    }

    public SubnetCoreEntry entry(int slot) {
        return entries[slot];
    }

    public void configureEntry(int slot, int typeOrdinal, int faceOrdinal, int priority) {
        var type = typeOrdinal < 0 ? null : SubnetCoreEntry.Type.byOrdinal(typeOrdinal);
        var face = Direction.values()[Math.floorMod(faceOrdinal, Direction.values().length)];
        entries[slot].configure(type, face, priority);
        setChanged();
    }

    public void setEntryFilter(int slot, @Nullable GenericStack stack) {
        entries[slot].setFilter(stack);
        setChanged();
    }

    public int activeEntries() {
        int active = 0;
        for (var entry : entries) {
            if (entry.isActive()) {
                active++;
            }
        }
        return active;
    }

    public boolean coreActive() {
        var node = mainNode.getNode();
        return node != null && node.isActive();
    }

    @Nullable
    public IGrid mainGrid() {
        var node = mainNode.getNode();
        return node == null ? null : node.getGrid();
    }

    @Nullable
    public IGrid internalGrid() {
        var node = hubNode.getNode();
        return node == null ? null : node.getGrid();
    }

    MEStorage mainGridProxy() {
        return mainProxy;
    }

    MEStorage internalGridProxy() {
        return internalProxy;
    }

    private java.util.Collection<appeng.me.service.EnergyService> mainEnergyServices() {
        var grid = mainGrid();
        return grid == null ? List.of()
                : List.of((appeng.me.service.EnergyService) grid.getEnergyService());
    }

    private java.util.Collection<appeng.me.service.EnergyService> internalEnergyServices() {
        var grid = internalGrid();
        return grid == null ? List.of()
                : List.of((appeng.me.service.EnergyService) grid.getEnergyService());
    }

    private void invalidateEnergyOverlays() {
        var main = mainGrid();
        if (main != null) {
            ((appeng.me.service.EnergyService) main.getEnergyService()).invalidateOverlayEnergyGrid();
        }
        var internal = internalGrid();
        if (internal != null) {
            ((appeng.me.service.EnergyService) internal.getEnergyService()).invalidateOverlayEnergyGrid();
        }
    }

    /** External inventories behind an entry's face, wrapped for ME. */
    List<MEStorage> externalStoragesFor(SubnetCoreEntry entry) {
        var result = new ArrayList<MEStorage>(2);
        if (level == null || !entry.type().faceBound()) {
            return result;
        }
        var targetPos = worldPosition.relative(entry.face());
        var side = entry.face().getOpposite();
        var items = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, side);
        if (items != null) {
            result.add(ExternalStorageFacade.of(items));
        }
        var fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, side);
        if (fluids != null) {
            result.add(ExternalStorageFacade.of(fluids));
        }
        return result;
    }

    /** Called from the block on neighbor changes so storage bus mounts re-resolve. */
    public void onNeighborChanged() {
        for (var entry : entries) {
            if (entry.type() == SubnetCoreEntry.Type.STORAGE_BUS) {
                entry.requestRemount();
            }
        }
    }

    public void serverTick() {
        if (++tickCounter % TRANSFER_INTERVAL_TICKS != 0 || level == null) {
            return;
        }
        var internal = internalGrid();
        if (internal == null) {
            return;
        }
        var inventory = internal.getStorageService().getInventory();
        var source = IActionSource.empty();
        for (var entry : entries) {
            if (!entry.isActive()) {
                continue;
            }
            if (entry.type() == SubnetCoreEntry.Type.EXPORT_BUS) {
                exportTick(entry, inventory, source);
            } else if (entry.type() == SubnetCoreEntry.Type.IMPORT_BUS) {
                importTick(entry, inventory, source);
            }
        }
    }

    private void exportTick(SubnetCoreEntry entry, MEStorage inventory, IActionSource source) {
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.relative(entry.face()), entry.face().getOpposite());
        if (handler == null) {
            return;
        }
        long budget = ITEMS_PER_OPERATION;
        var keys = new ArrayList<AEItemKey>();
        var filter = entry.filter();
        if (filter != null) {
            if (filter.what() instanceof AEItemKey itemKey) {
                keys.add(itemKey);
            }
        } else {
            var available = new appeng.api.stacks.KeyCounter();
            inventory.getAvailableStacks(available);
            for (var stack : available) {
                if (stack.getKey() instanceof AEItemKey itemKey) {
                    keys.add(itemKey);
                    if (keys.size() >= 8) {
                        break;
                    }
                }
            }
        }
        for (var key : keys) {
            if (budget <= 0) {
                break;
            }
            long extracted = inventory.extract(key, budget, Actionable.MODULATE, source);
            if (extracted <= 0) {
                continue;
            }
            var rest = ItemHandlerHelper.insertItem(handler, key.toStack((int) extracted), false);
            if (!rest.isEmpty()) {
                inventory.insert(key, rest.getCount(), Actionable.MODULATE, source);
            }
            budget -= extracted - rest.getCount();
        }
    }

    private void importTick(SubnetCoreEntry entry, MEStorage inventory, IActionSource source) {
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.relative(entry.face()), entry.face().getOpposite());
        if (handler == null) {
            return;
        }
        long budget = ITEMS_PER_OPERATION;
        var filter = entry.filter();
        for (int slot = 0; slot < handler.getSlots() && budget > 0; slot++) {
            var simulated = handler.extractItem(slot, (int) budget, true);
            if (simulated.isEmpty()) {
                continue;
            }
            var key = AEItemKey.of(simulated);
            if (key == null || filter != null && !filter.what().equals(key)) {
                continue;
            }
            long accepted = inventory.insert(key, simulated.getCount(), Actionable.SIMULATE, source);
            if (accepted <= 0) {
                continue;
            }
            var extracted = handler.extractItem(slot, (int) accepted, false);
            if (!extracted.isEmpty()) {
                inventory.insert(key, extracted.getCount(), Actionable.MODULATE, source);
                budget -= extracted.getCount();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        hubNode.saveToNBT(tag);
        tag.put("entries", saveEntries(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        hubNode.loadFromNBT(tag);
        loadEntries(tag.getList("entries", Tag.TAG_COMPOUND), registries);
    }

    private ListTag saveEntries(HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : entries) {
            list.add(entry.save(registries));
        }
        return list;
    }

    private void loadEntries(ListTag list, HolderLookup.Provider registries) {
        for (int i = 0; i < ENTRIES && i < list.size(); i++) {
            entries[i].load(list.getCompound(i), registries);
        }
    }

    @Override
    public DataComponentMap exportTransferSettings(@Nullable Player player) {
        if (level == null) {
            return DataComponentMap.EMPTY;
        }
        var tag = new CompoundTag();
        tag.put("subnetEntries", saveEntries(level.registryAccess()));
        return DataComponentMap.builder()
                .set(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get(), tag)
                .build();
    }

    @Override
    public void importTransferSettings(DataComponentMap settings, @Nullable Player player) {
        if (level == null || level.isClientSide) {
            return;
        }
        var tag = settings.get(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get());
        if (tag == null || !tag.contains("subnetEntries")) {
            return;
        }
        // Entry stars depend on type, so rebuild nodes from scratch.
        for (var entry : entries) {
            entry.disable();
        }
        loadEntries(tag.getList("subnetEntries", Tag.TAG_COMPOUND), level.registryAccess());
        for (var entry : entries) {
            entry.enable();
        }
        setChanged();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

}
