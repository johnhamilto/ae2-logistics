package io.github.johnhamilto.ae2logistics.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;

/**
 * One virtual device inside an ME Subnet Core. Face-bound bus entries and the uplink
 * live on the core's internal grid (requiring internal ad-hoc channels); downlink
 * entries are main-grid devices wired to the core's node - exposing the subnet costs a
 * main channel exactly like a physical storage bus would.
 */
public class SubnetCoreEntry implements IStorageProvider {

    public enum Type {
        STORAGE_BUS,
        IMPORT_BUS,
        EXPORT_BUS,
        UPLINK,
        DOWNLINK;

        public static Type byOrdinal(int ordinal) {
            var values = values();
            return values[Math.floorMod(ordinal, values.length)];
        }

        public boolean faceBound() {
            return this == STORAGE_BUS || this == IMPORT_BUS || this == EXPORT_BUS;
        }
    }

    private static final IGridNodeListener<SubnetCoreEntry> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(SubnetCoreEntry owner, IGridNode node) {
            owner.core.setChanged();
        }
    };

    private final SubnetCoreBlockEntity core;
    private final int slot;

    @Nullable
    private Type type;
    private Direction face = Direction.NORTH;
    private int priority;
    @Nullable
    private GenericStack filter;

    @Nullable
    private IManagedGridNode node;
    private boolean connected;

    public SubnetCoreEntry(SubnetCoreBlockEntity core, int slot) {
        this.core = core;
        this.slot = slot;
    }

    public int slot() {
        return slot;
    }

    @Nullable
    public Type type() {
        return type;
    }

    public Direction face() {
        return face;
    }

    public int priority() {
        return priority;
    }

    @Nullable
    public GenericStack filter() {
        return filter;
    }

    public void setFilter(@Nullable GenericStack stack) {
        this.filter = stack;
        requestRemount();
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

    public void configure(@Nullable Type newType, Direction newFace, int newPriority) {
        boolean typeChanged = this.type != newType;
        this.type = newType;
        this.face = newFace;
        this.priority = newPriority;
        if (type == null) {
            disable();
        } else if (typeChanged) {
            // The star a node belongs to depends on its type; rebuild it.
            disable();
            enable();
        } else {
            enable();
            requestRemount();
        }
    }

    void enable() {
        if (node != null || type == null || core.getLevel() == null
                || core.getLevel().isClientSide) {
            return;
        }
        node = GridHelper.createManagedNode(this, NODE_LISTENER)
                .setInWorldNode(false)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(0.5)
                .addService(IStorageProvider.class, this);
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

    void requestRemount() {
        if (node != null) {
            IStorageProvider.requestUpdate(node);
        }
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        if (type == null) {
            return;
        }
        switch (type) {
            case STORAGE_BUS -> {
                for (var external : core.externalStoragesFor(this)) {
                    mounts.mount(new SubnetStorages.Gated(external, this::isActive, filterKey()),
                            priority);
                }
            }
            case UPLINK -> mounts.mount(
                    new SubnetStorages.Gated(core.mainGridProxy(), this::isActive, filterKey()),
                    priority);
            case DOWNLINK -> mounts.mount(
                    new SubnetStorages.Gated(core.internalGridProxy(), this::isActive, filterKey()),
                    priority);
            default -> {
            }
        }
    }

    @Nullable
    private appeng.api.stacks.AEKey filterKey() {
        return filter == null ? null : filter.what();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (type != null) {
            tag.putByte("type", (byte) type.ordinal());
        }
        tag.putByte("face", (byte) face.ordinal());
        tag.putInt("priority", priority);
        if (filter != null) {
            tag.put("filter", GenericStack.writeTag(registries, filter));
        }
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        type = tag.contains("type") ? Type.byOrdinal(tag.getByte("type")) : null;
        face = Direction.values()[Math.floorMod(tag.getByte("face"), Direction.values().length)];
        priority = tag.getInt("priority");
        filter = tag.contains("filter")
                ? GenericStack.readTag(registries, tag.getCompound("filter"))
                : null;
    }
}
