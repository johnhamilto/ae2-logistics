package io.github.johnhamilto.ae2logistics.block;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Joins its local grid segment to a remote network wirelessly - but only while inside
 * the coverage volume of a powered access point on that network. Availability is
 * binary: in coverage the bridge is a full dense link whose channels are drawn through
 * the serving access point; out of coverage it is dark. The bridge associates with the
 * nearest in-range access point (position hash tiebreak) and re-associates when its
 * access point goes away - AE2's own WAPs and Dense Wireless Access Points both serve.
 */
public class WirelessBridgeBlockEntity extends BlockEntity implements IInWorldGridNodeHost {

    private static final IGridNodeListener<WirelessBridgeBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(WirelessBridgeBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
            .setIdlePowerUsage(2.0);

    @Nullable
    private GlobalPos anchor;
    @Nullable
    private IGridConnection wirelessLink;
    @Nullable
    private IGridNode linkedApNode;
    @Nullable
    private BlockPos linkedApPos;
    private int tickCounter;

    public WirelessBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.WIRELESS_BRIDGE_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            GridHelper.onFirstTick(this, be -> be.mainNode.create(be.level, be.getBlockPos()));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        dropLink();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        dropLink();
        mainNode.destroy();
    }

    public void serverTick() {
        if (++tickCounter % io.github.johnhamilto.ae2logistics.AE2LogisticsConfig
                .bridgeRetuneIntervalTicks() != 0) {
            return;
        }
        refreshLink();
    }

    public void setAnchor(@Nullable GlobalPos anchor) {
        this.anchor = anchor;
        setChanged();
    }

    @Nullable
    public GlobalPos anchor() {
        return anchor;
    }

    @Nullable
    public BlockPos linkedApPos() {
        return linkedApNode != null ? linkedApPos : null;
    }

    public boolean isLinked() {
        return linkedApNode != null;
    }

    @Nullable
    public IGrid grid() {
        var node = mainNode.getNode();
        return node == null ? null : node.getGrid();
    }

    private void refreshLink() {
        var bridgeNode = mainNode.getNode();
        if (bridgeNode == null) {
            return;
        }
        // A destroyed access point takes the connection with it; drop our stale handle.
        if (wirelessLink != null && !bridgeNode.getConnections().contains(wirelessLink)) {
            wirelessLink = null;
            linkedApNode = null;
            linkedApPos = null;
        }

        var best = pickAccessPoint(resolveTargetGrid());
        var bestNode = best == null ? null : best.getActionableNode();
        if (bestNode == linkedApNode) {
            return;
        }
        dropLink();
        if (best == null || bestNode == null) {
            return;
        }
        // If the bridge is already cabled to this exact node, don't double-connect.
        boolean directlyConnected = false;
        for (var connection : bridgeNode.getConnections()) {
            if (connection.getOtherSide(bridgeNode) == bestNode) {
                directlyConnected = true;
                break;
            }
        }
        if (!directlyConnected) {
            wirelessLink = appeng.api.networking.GridHelper.createConnection(bestNode, bridgeNode);
        }
        linkedApNode = bestNode;
        linkedApPos = best.getLocation().getPos();
    }

    private void dropLink() {
        if (wirelessLink != null) {
            var bridgeNode = mainNode.getNode();
            if (bridgeNode != null && bridgeNode.getConnections().contains(wirelessLink)) {
                wirelessLink.destroy();
            }
            wirelessLink = null;
        }
        linkedApNode = null;
        linkedApPos = null;
    }

    /** The network is whatever grid the anchor access point currently belongs to. */
    @Nullable
    private IGrid resolveTargetGrid() {
        if (anchor == null || level == null || level.getServer() == null) {
            return null;
        }
        var anchorLevel = level.getServer().getLevel(anchor.dimension());
        if (anchorLevel == null || !anchorLevel.isLoaded(anchor.pos())) {
            return null;
        }
        return anchorLevel.getBlockEntity(anchor.pos()) instanceof IWirelessAccessPoint wap
                ? wap.getGrid()
                : null;
    }

    /**
     * Nearest active in-range access point on the target grid in this dimension,
     * tiebroken by position hash. AE2 WAPs and Dense WAPs both count.
     */
    @Nullable
    private IWirelessAccessPoint pickAccessPoint(@Nullable IGrid grid) {
        if (grid == null || level == null) {
            return null;
        }
        var candidates = new ArrayList<IWirelessAccessPoint>();
        candidates.addAll(grid.getMachines(appeng.blockentity.networking.WirelessAccessPointBlockEntity.class));
        candidates.addAll(grid.getMachines(DenseWapBlockEntity.class));

        IWirelessAccessPoint best = null;
        double bestDistance = Double.MAX_VALUE;
        long bestKey = Long.MAX_VALUE;
        for (var candidate : candidates) {
            if (!candidate.isActive()) {
                continue;
            }
            var location = candidate.getLocation();
            if (location.getLevel() != level) {
                continue;
            }
            double distance = location.getPos().distSqr(worldPosition);
            double range = candidate.getRange();
            if (distance > range * range) {
                continue;
            }
            long key = location.getPos().asLong();
            if (best == null || distance < bestDistance
                    || distance == bestDistance && key < bestKey) {
                best = candidate;
                bestDistance = distance;
                bestKey = key;
            }
        }
        return best;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        output.storeNullable("anchor", GlobalPos.CODEC, anchor);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        anchor = input.read("anchor", GlobalPos.CODEC).orElse(null);
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
}
