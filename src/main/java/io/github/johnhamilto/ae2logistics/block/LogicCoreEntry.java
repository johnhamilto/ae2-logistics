package io.github.johnhamilto.ae2logistics.block;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.parts.LogicPartType;
import io.github.johnhamilto.ae2logistics.signal.ILogicNode;
import io.github.johnhamilto.ae2logistics.signal.SignalMath;

/**
 * One virtual logic node inside an ME Logic Core. Each configured entry owns a
 * non-in-world grid node wired to the core's main node, so the grid's signal scheduler
 * evaluates it exactly like the equivalent cable part. Unlike the channel-free physical
 * parts, entries require a channel and go dark without one - the core trades space for
 * channel pressure by design.
 */
public class LogicCoreEntry implements ILogicNode {

    private static final IGridNodeListener<LogicCoreEntry> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(LogicCoreEntry owner, IGridNode node) {
            owner.core.setChanged();
        }
    };

    private final LogicCoreBlockEntity core;
    private final int slot;

    @Nullable
    private LogicPartType type;
    @Nullable
    ResourceLocation outChannel;
    @Nullable
    ResourceLocation inA;
    @Nullable
    ResourceLocation inB;
    int op;
    long valueA;
    long valueB;
    boolean flag;
    @Nullable
    GenericStack watched;

    // Per-type evaluation state, reset on type change.
    private boolean latched;
    private long count;
    private boolean lastInput;
    private long ticks;
    private long[] samples = new long[0];
    private int cursor;
    private int filled;

    @Nullable
    private IManagedGridNode node;
    private boolean connected;

    public LogicCoreEntry(LogicCoreBlockEntity core, int slot) {
        this.core = core;
        this.slot = slot;
    }

    public int slot() {
        return slot;
    }

    @Nullable
    public ResourceLocation inARaw() {
        return inA;
    }

    @Nullable
    public ResourceLocation inBRaw() {
        return inB;
    }

    public int opRaw() {
        return op;
    }

    public long valueARaw() {
        return valueA;
    }

    public long valueBRaw() {
        return valueB;
    }

    public boolean flagRaw() {
        return flag;
    }

    @Nullable
    public LogicPartType type() {
        return type;
    }

    @Nullable
    public GenericStack watchedKey() {
        return watched;
    }

    public void setWatchedKey(@Nullable GenericStack stack) {
        this.watched = stack;
    }

    public boolean isActive() {
        if (node == null) {
            return false;
        }
        var gridNode = node.getNode();
        return gridNode != null && gridNode.isActive();
    }

    @Nullable
    IManagedGridNode managedNode() {
        return node;
    }

    boolean isConnected() {
        return connected;
    }

    void markConnected() {
        this.connected = true;
    }

    /**
     * Applies configuration, handling the node lifecycle: enabling creates a fresh
     * virtual node (managed nodes are single-use), disabling destroys it, and a type
     * switch keeps the node but resets evaluation state.
     */
    public void configure(@Nullable LogicPartType newType, @Nullable ResourceLocation out,
            @Nullable ResourceLocation a, @Nullable ResourceLocation b, int op, long valueA,
            long valueB, boolean flag) {
        if (newType == LogicPartType.REDSTONE_IO) {
            newType = null; // world-facing; physical Redstone Signal Ports only
        }
        boolean typeChanged = this.type != newType;
        this.type = newType;
        this.outChannel = out;
        this.inA = a;
        this.inB = b;
        this.op = op;
        this.valueA = valueA;
        this.valueB = valueB;
        this.flag = flag;
        if (typeChanged) {
            resetState();
        }
        if (type == null) {
            disable();
        } else {
            enable();
        }
    }

    private void resetState() {
        latched = false;
        count = 0;
        lastInput = false;
        ticks = 0;
        samples = new long[0];
        cursor = 0;
        filled = 0;
    }

    void enable() {
        if (node != null || core.getLevel() == null || core.getLevel().isClientSide) {
            return;
        }
        node = GridHelper.createManagedNode(this, NODE_LISTENER)
                .setInWorldNode(false)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(0.5)
                .addService(ILogicNode.class, this);
        node.create(core.getLevel(), core.getBlockPos());
        core.connectEntry(this);
    }

    void disable() {
        if (node != null) {
            node.destroy();
            node = null;
        }
        connected = false;
    }

    @Override
    public Set<ResourceLocation> readChannels() {
        if (type == null) {
            return Set.of();
        }
        var channels = new HashSet<ResourceLocation>(2);
        switch (type) {
            case THRESHOLD, ARITHMETIC, BOOLEAN -> {
                if (inA != null) {
                    channels.add(inA);
                }
                if (inB != null && flag) {
                    channels.add(inB);
                }
            }
            case HYSTERESIS, RATE, COUNTER -> {
                if (inA != null) {
                    channels.add(inA);
                }
                if (type == LogicPartType.COUNTER && inB != null && flag) {
                    channels.add(inB);
                }
            }
            default -> {
            }
        }
        return channels;
    }

    @Nullable
    @Override
    public ResourceLocation writtenChannel() {
        return type == null ? null : outChannel;
    }

    @Override
    public long stableKey() {
        // Physical parts use pos*31 + side ordinal (0-6); slots start above that range.
        return core.getBlockPos().asLong() * 31 + 7 + slot;
    }

    private long readB(LogicContext context) {
        return flag && inB != null ? context.read(inB) : valueA;
    }

    @Override
    public void evaluate(LogicContext context) {
        if (type == null || !isActive()) {
            return;
        }
        switch (type) {
            case CONSTANT -> context.write(valueA);
            case THRESHOLD -> {
                long a = inA != null ? context.read(inA) : 0;
                long b = readB(context);
                boolean result = switch (op) {
                    case 0 -> a < b;
                    case 1 -> a <= b;
                    case 2 -> a == b;
                    case 3 -> a >= b;
                    default -> a > b;
                };
                context.write(result ? 1 : 0);
            }
            case HYSTERESIS -> {
                long a = inA != null ? context.read(inA) : 0;
                boolean previous = latched;
                if (a < valueA) {
                    latched = true;
                } else if (a > valueB) {
                    latched = false;
                }
                if (previous != latched) {
                    core.setChanged();
                }
                context.write(latched ? 1 : 0);
            }
            case ARITHMETIC -> {
                long a = inA != null ? context.read(inA) : 0;
                long b = readB(context);
                long result = switch (op) {
                    case 0 -> SignalMath.add(a, b);
                    case 1 -> SignalMath.subtract(a, b);
                    case 2 -> SignalMath.multiply(a, b);
                    case 3 -> SignalMath.divide(a, b);
                    case 4 -> Math.min(a, b);
                    case 5 -> Math.max(a, b);
                    default -> SignalMath.modulo(a, b);
                };
                context.write(result);
            }
            case BOOLEAN -> {
                boolean a = inA != null && context.read(inA) != 0;
                boolean b = readB(context) != 0;
                boolean result = switch (op) {
                    case 0 -> a && b;
                    case 1 -> a || b;
                    case 2 -> a ^ b;
                    default -> !a;
                };
                context.write(result ? 1 : 0);
            }
            case STOCK_SENSOR -> {
                if (watched == null || node == null) {
                    return;
                }
                var gridNode = node.getNode();
                if (gridNode == null || gridNode.getGrid() == null) {
                    return;
                }
                context.write(gridNode.getGrid().getStorageService().getCachedInventory()
                        .get(watched.what()));
            }
            case RATE -> {
                int windowSeconds = (int) SignalMath.clamp(valueA, 1, 60);
                int windowTicks = windowSeconds * 20;
                if (samples.length != windowTicks) {
                    samples = new long[windowTicks];
                    cursor = 0;
                    filled = 0;
                }
                long current = inA != null ? context.read(inA) : 0;
                long oldest = samples[cursor];
                samples[cursor] = current;
                cursor = (cursor + 1) % windowTicks;
                if (filled < windowTicks) {
                    filled++;
                    context.write(0);
                    return;
                }
                long delta = current - oldest;
                context.write(delta <= 0 ? 0 : delta / windowSeconds);
            }
            case COUNTER -> {
                if (inB != null && context.read(inB) != 0) {
                    count = 0;
                }
                boolean input = inA != null && context.read(inA) != 0;
                if (input && !lastInput) {
                    long next = SignalMath.add(count, 1);
                    if (valueA > 0) {
                        next = next % valueA;
                    }
                    count = next;
                    core.setChanged();
                }
                lastInput = input;
                context.write(count);
            }
            case TIMER -> {
                long period = SignalMath.clamp(valueA, 2, 72000);
                long pulse = SignalMath.clamp(valueB, 1, period - 1);
                context.write(ticks % period < pulse ? 1 : 0);
                ticks++;
            }
            default -> {
            }
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (type != null) {
            tag.putByte("type", (byte) type.ordinal());
        }
        if (outChannel != null) {
            tag.putString("out", outChannel.toString());
        }
        if (inA != null) {
            tag.putString("inA", inA.toString());
        }
        if (inB != null) {
            tag.putString("inB", inB.toString());
        }
        tag.putInt("op", op);
        tag.putLong("valueA", valueA);
        tag.putLong("valueB", valueB);
        tag.putBoolean("flag", flag);
        if (watched != null) {
            tag.put("watched", GenericStack.writeTag(registries, watched));
        }
        tag.putBoolean("latched", latched);
        tag.putLong("count", count);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        type = tag.contains("type") ? LogicPartType.byOrdinal(tag.getByte("type")) : null;
        if (type == LogicPartType.REDSTONE_IO) {
            type = null;
        }
        outChannel = tag.contains("out") ? ResourceLocation.tryParse(tag.getString("out")) : null;
        inA = tag.contains("inA") ? ResourceLocation.tryParse(tag.getString("inA")) : null;
        inB = tag.contains("inB") ? ResourceLocation.tryParse(tag.getString("inB")) : null;
        op = tag.getInt("op");
        valueA = tag.getLong("valueA");
        valueB = tag.getLong("valueB");
        flag = tag.getBoolean("flag");
        watched = tag.contains("watched")
                ? GenericStack.readTag(registries, tag.getCompound("watched"))
                : null;
        latched = tag.getBoolean("latched");
        count = tag.getLong("count");
    }
}
