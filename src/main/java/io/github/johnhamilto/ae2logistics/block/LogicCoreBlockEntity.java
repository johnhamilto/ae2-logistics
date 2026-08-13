package io.github.johnhamilto.ae2logistics.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
        if (level != null && !level.isClientSide()) {
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
    private static Identifier parseChannel(String text) {
        return text == null || text.isBlank() ? null : Identifier.tryParse(text.trim());
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        saveEntries(output, "entries");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        loadEntries(input, "entries");
    }

    private void saveEntries(ValueOutput output, String name) {
        var list = output.childrenList(name);
        for (var entry : entries) {
            entry.save(list.addChild());
        }
    }

    private void loadEntries(ValueInput input, String name) {
        input.childrenList(name).ifPresent(list -> {
            int i = 0;
            for (var entryInput : list) {
                if (i >= ENTRIES) {
                    break;
                }
                entries[i++].load(entryInput);
            }
        });
    }

    @Override
    public DataComponentMap exportTransferSettings(@Nullable Player player) {
        if (level == null) {
            return DataComponentMap.EMPTY;
        }
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        saveEntries(output, "coreEntries");
        var tag = output.buildResult();
        return DataComponentMap.builder()
                .set(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get(), tag)
                .build();
    }

    @Override
    public void importTransferSettings(DataComponentMap settings, @Nullable Player player) {
        if (level == null || level.isClientSide()) {
            return;
        }
        var tag = settings.get(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get());
        if (tag == null || !tag.contains("coreEntries")) {
            return;
        }
        loadEntries(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag),
                "coreEntries");
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
        // The core's node is dense-capacity (nine channels through one face).
        return AECableType.DENSE_SMART;
    }
}
