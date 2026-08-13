package io.github.johnhamilto.ae2logistics.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * One block of a trace panel wall. Panels of the same facing merge by placement
 * into rectangles (up to {@link #MAX_EDGE} per side): every member computes the
 * same group after a neighbor change, and the MIN-corner member is the master -
 * it owns the channel bindings, records the per-second history ring buffers, and
 * syncs them to watching clients; the renderer draws only on the master, scaled
 * across the whole face. Every block carries its own (channel-free) grid node, so
 * breaking any member never disturbs the others' grid presence - the survivors
 * just re-form smaller panels (bindings live on each group's master and follow it).
 */
public class TracePanelBlockEntity extends BlockEntity implements IInWorldGridNodeHost {

    public static final int MAX_EDGE = 4;
    public static final int MAX_CHANNELS = 6;
    public static final int SAMPLES = 120;

    private static final IGridNodeListener<TracePanelBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(TracePanelBlockEntity owner, IGridNode node) {
                    owner.setChanged();
                }
            };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setIdlePowerUsage(0.5);

    // Formation, recomputed on placement changes; synced so the renderer knows.
    private BlockPos groupOrigin = BlockPos.ZERO;
    private int groupWidth = 1;
    private int groupHeight = 1;

    /** Master-only state: bound channels and their ring buffers. */
    private final Map<Identifier, long[]> traces = new LinkedHashMap<>();
    private int sampleCursor;
    private boolean buffersFull;
    private int tickCounter;
    private boolean syncDirty;

    public TracePanelBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.TRACE_PANEL_BE.get(), pos, state);
        this.groupOrigin = pos;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            GridHelper.onFirstTick(this, be -> {
                be.mainNode.create(be.level, be.getBlockPos());
                be.reformGroup();
            });
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    public Direction facing() {
        return getBlockState().getValue(TracePanelBlock.FACING);
    }

    public boolean isMaster() {
        return worldPosition.equals(groupOrigin);
    }

    public BlockPos groupOrigin() {
        return groupOrigin;
    }

    public int groupWidth() {
        return groupWidth;
    }

    public int groupHeight() {
        return groupHeight;
    }

    /** Viewer-right axis of the panel plane (up is world up): counterclockwise of facing. */
    public Direction rightAxis() {
        return facing().getCounterClockWise();
    }

    /**
     * Recomputes this panel's rectangle: flood same-facing panels in the plane
     * (capped), take the bounding box, and use it only if every cell is a panel -
     * otherwise this block stands alone as a 1x1. Every member arrives at the same
     * answer independently, so no formation messages are needed.
     */
    public void reformGroup() {
        if (level == null || level.isClientSide()) {
            return;
        }
        var facing = facing();
        var right = rightAxis();
        var mine = worldPosition;

        var members = new ArrayList<BlockPos>();
        var queue = new ArrayList<BlockPos>();
        queue.add(mine);
        var seen = new java.util.HashSet<BlockPos>();
        seen.add(mine);
        int scanCap = MAX_EDGE * MAX_EDGE * 4;
        while (!queue.isEmpty() && seen.size() <= scanCap) {
            var pos = queue.remove(queue.size() - 1);
            if (!(level.getBlockEntity(pos) instanceof TracePanelBlockEntity other)
                    || other.facing() != facing) {
                continue;
            }
            members.add(pos);
            for (var step : new Direction[] {Direction.UP, Direction.DOWN, right, right.getOpposite()}) {
                var next = pos.relative(step);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }

        int minR = Integer.MAX_VALUE;
        int maxR = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (var pos : members) {
            int r = pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }
        int width = maxR - minR + 1;
        int height = maxY - minY + 1;
        boolean rectangle = width <= MAX_EDGE && height <= MAX_EDGE
                && members.size() == width * height;

        BlockPos origin;
        if (rectangle) {
            int myR = mine.getX() * right.getStepX() + mine.getZ() * right.getStepZ();
            origin = mine.relative(right, minR - myR).atY(minY);
        } else {
            origin = mine;
            width = 1;
            height = 1;
        }
        if (!origin.equals(groupOrigin) || width != groupWidth || height != groupHeight) {
            groupOrigin = origin;
            groupWidth = width;
            groupHeight = height;
            if (!isMaster()) {
                traces.clear();
            }
            setChanged();
            syncNow();
        }
        stitchNeighbors();
    }

    /**
     * Panel-to-panel grid links must be made explicitly: cables drive their own
     * connections to us, but two passive in-world hosts side by side have no
     * driver. Each panel stitches to initialized neighbors; ordering resolves
     * itself because the later panel stitches back to the earlier one.
     */
    private void stitchNeighbors() {
        var node = mainNode.getNode();
        if (node == null || level == null) {
            return;
        }
        for (var dir : Direction.values()) {
            if (!(level.getBlockEntity(worldPosition.relative(dir))
                    instanceof TracePanelBlockEntity other)) {
                continue;
            }
            var otherNode = other.mainNode.getNode();
            if (otherNode == null || otherNode == node) {
                continue;
            }
            boolean linked = false;
            for (var connection : node.getConnections()) {
                if (connection.getOtherSide(node) == otherNode) {
                    linked = true;
                    break;
                }
            }
            if (!linked) {
                GridHelper.createConnection(node, otherNode);
            }
        }
    }

    /** Adds (or with {@code remove}) a channel binding on this panel's MASTER. */
    public boolean bind(Identifier channel, boolean remove) {
        if (level == null) {
            return false;
        }
        if (!isMaster()) {
            return level.getBlockEntity(groupOrigin) instanceof TracePanelBlockEntity master
                    && master.bind(channel, remove);
        }
        if (remove) {
            if (traces.remove(channel) == null) {
                return false;
            }
        } else {
            if (traces.containsKey(channel) || traces.size() >= MAX_CHANNELS) {
                return false;
            }
            traces.put(channel, new long[SAMPLES]);
        }
        sampleCursor = 0;
        buffersFull = false;
        for (var buffer : traces.values()) {
            java.util.Arrays.fill(buffer, 0);
        }
        setChanged();
        syncNow();
        return true;
    }

    public void clearBindings() {
        if (level != null && !isMaster()) {
            if (level.getBlockEntity(groupOrigin) instanceof TracePanelBlockEntity master) {
                master.clearBindings();
            }
            return;
        }
        traces.clear();
        setChanged();
        syncNow();
    }

    public List<Identifier> boundChannels() {
        if (level != null && !isMaster()
                && level.getBlockEntity(groupOrigin) instanceof TracePanelBlockEntity master) {
            return master.boundChannels();
        }
        return List.copyOf(traces.keySet());
    }


    /** Renderer access: the ring buffer unrolled oldest-first, empty until sampled. */
    public long[] samples(Identifier channel) {
        var buffer = traces.get(channel);
        if (buffer == null) {
            return new long[0];
        }
        int count = buffersFull ? SAMPLES : sampleCursor;
        var out = new long[count];
        for (int i = 0; i < count; i++) {
            int index = buffersFull ? (sampleCursor + i) % SAMPLES : i;
            out[i] = buffer[index];
        }
        return out;
    }

    public void serverTick() {
        if (!isMaster() || traces.isEmpty()) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        var node = mainNode.getNode();
        var service = node != null && node.getGrid() != null
                ? node.getGrid().getService(SignalService.class) : null;
        for (var entry : traces.entrySet()) {
            long value = service != null ? service.get(entry.getKey()) : 0;
            if (entry.getValue()[sampleCursor] != value) {
                syncDirty = true;
            }
            entry.getValue()[sampleCursor] = value;
        }
        sampleCursor++;
        if (sampleCursor >= SAMPLES) {
            sampleCursor = 0;
            buffersFull = true;
        }
        if (syncDirty) {
            syncDirty = false;
            setChanged();
            syncNow();
        }
    }

    private void syncNow() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // --- sync plumbing: the update tag carries formation + traces for the renderer ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        saveShared(output);
        return output.buildResult();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void saveShared(ValueOutput output) {
        output.putLong("origin", groupOrigin.asLong());
        output.putInt("width", groupWidth);
        output.putInt("height", groupHeight);
        output.putInt("cursor", sampleCursor);
        output.putBoolean("full", buffersFull);
        var list = output.childrenList("traces");
        for (var entry : traces.entrySet()) {
            var trace = list.addChild();
            trace.putString("channel", entry.getKey().toString());
            trace.store("samples", Codec.LONG.listOf(),
                    java.util.Arrays.stream(entry.getValue()).boxed().toList());
        }
    }

    private void loadShared(ValueInput input) {
        groupOrigin = BlockPos.of(input.getLongOr("origin", 0L));
        groupWidth = Math.max(1, input.getIntOr("width", 0));
        groupHeight = Math.max(1, input.getIntOr("height", 0));
        sampleCursor = input.getIntOr("cursor", 0);
        buffersFull = input.getBooleanOr("full", false);
        traces.clear();
        input.childrenList("traces").ifPresent(list -> {
            for (var trace : list) {
                var channel = trace.getString("channel").map(Identifier::tryParse).orElse(null);
                if (channel != null) {
                    var samples = trace.read("samples", Codec.LONG.listOf()).orElse(List.of());
                    var buffer = new long[SAMPLES];
                    for (int i = 0; i < samples.size() && i < SAMPLES; i++) {
                        buffer[i] = samples.get(i);
                    }
                    traces.put(channel, buffer);
                }
            }
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        saveShared(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        loadShared(input);
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
