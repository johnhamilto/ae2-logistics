package io.github.johnhamilto.ae2logistics.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.config.TransferableSettings;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * ME Logic Core: hosts up to {@link #ENTRIES} virtual logic nodes as list entries
 * instead of cable parts. The core's own node is a dense carrier so entry channels can
 * be drawn through it; every configured entry requires a channel of its own.
 */
public class LogicCoreBlockEntity extends BlockEntity implements IInWorldGridNodeHost, TransferableSettings {

    public static final int ENTRIES = 8;

    private static final IGridNodeListener<LogicCoreBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(LogicCoreBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
            .setIdlePowerUsage(2.0);

    private final LogicCoreEntry[] entries = new LogicCoreEntry[ENTRIES];

    public LogicCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.LOGIC_CORE_BE.get(), pos, state);
        for (int i = 0; i < ENTRIES; i++) {
            entries[i] = new LogicCoreEntry(this, i);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            GridHelper.onFirstTick(this, be -> {
                be.mainNode.create(be.level, be.getBlockPos());
                for (var entry : be.entries) {
                    if (entry.type() != null) {
                        entry.enable();
                    }
                }
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
        mainNode.destroy();
    }

    /** Wires a freshly created entry node to the core's node; the entry calls back here. */
    void connectEntry(LogicCoreEntry entry) {
        var hub = mainNode.getNode();
        var managed = entry.managedNode();
        if (hub == null || managed == null || managed.getNode() == null || entry.isConnected()) {
            return;
        }
        appeng.api.networking.GridHelper.createConnection(hub, managed.getNode());
        entry.markConnected();
    }

    public LogicCoreEntry entry(int slot) {
        return entries[slot];
    }

    public void configureEntry(int slot, int typeOrdinal, String out, String inA, String inB,
            int op, long valueA, long valueB, boolean flag) {
        var type = typeOrdinal < 0 ? null : LogicPartType.byOrdinal(typeOrdinal);
        entries[slot].configure(type,
                parseChannel(out), parseChannel(inA), parseChannel(inB),
                op, valueA, valueB, flag);
        invalidateGraph();
        setChanged();
    }

    public void setEntryWatched(int slot, @Nullable GenericStack stack) {
        entries[slot].setWatchedKey(stack);
        invalidateGraph();
        setChanged();
    }

    private void invalidateGraph() {
        mainNode.ifPresent(grid -> grid.getService(SignalService.class).invalidateGraph());
    }

    @Nullable
    private static ResourceLocation parseChannel(String text) {
        return text == null || text.isBlank() ? null : ResourceLocation.tryParse(text.trim());
    }

    public long entryValue(int slot) {
        var out = entries[slot].writtenChannel();
        if (out == null) {
            return 0;
        }
        var node = mainNode.getNode();
        if (node == null || node.getGrid() == null) {
            return 0;
        }
        return node.getGrid().getService(SignalService.class).get(out);
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        tag.put("entries", saveEntries(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
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
        tag.put("coreEntries", saveEntries(level.registryAccess()));
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
        if (tag == null || !tag.contains("coreEntries")) {
            return;
        }
        loadEntries(tag.getList("coreEntries", Tag.TAG_COMPOUND), level.registryAccess());
        for (var entry : entries) {
            if (entry.type() == null) {
                entry.disable();
            } else {
                entry.enable();
            }
        }
        invalidateGraph();
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
