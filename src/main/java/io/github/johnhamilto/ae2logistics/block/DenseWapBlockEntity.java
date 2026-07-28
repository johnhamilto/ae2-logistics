package io.github.johnhamilto.ae2logistics.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalBlockPos;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * A wireless access point built for machine coverage: a dense carrier (32 channels
 * through it) with a wide fixed range, so ME Wireless Bridges can draw their channels
 * through it. Implements AE2's own {@link IWirelessAccessPoint}, so anything that
 * understands access points (including AE2 wireless terminals' linking) sees it.
 */
public class DenseWapBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IWirelessAccessPoint {

    public static final int DEFAULT_RANGE = 32;

    private static final IGridNodeListener<DenseWapBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(DenseWapBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
            .setIdlePowerUsage(4.0);

    private int range = DEFAULT_RANGE;

    public DenseWapBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.DENSE_WAP_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            GridHelper.onFirstTick(this, be -> be.mainNode.create(be.level, be.getBlockPos()));
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

    /** Coverage radius in blocks; adjustable for tests and future boosting. */
    public void setRange(int range) {
        this.range = Math.max(1, range);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        tag.putInt("range", range);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        range = tag.contains("range") ? Math.max(1, tag.getInt("range")) : DEFAULT_RANGE;
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public DimensionalBlockPos getLocation() {
        return new DimensionalBlockPos(this);
    }

    @Override
    public double getRange() {
        return range;
    }

    @Override
    public boolean isActive() {
        return mainNode.isOnline();
    }

    @Nullable
    @Override
    public IGrid getGrid() {
        var node = mainNode.getNode();
        return node == null ? null : node.getGrid();
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }
}
