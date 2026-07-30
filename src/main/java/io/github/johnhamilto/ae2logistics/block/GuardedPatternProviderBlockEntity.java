package io.github.johnhamilto.ae2logistics.block;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.crafting.GuardedPattern;
import io.github.johnhamilto.ae2logistics.crafting.GuardedProviderLogic;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * A pattern provider whose patterns sit behind a signal guard. The guard (channel OP
 * constant) hides all patterns from the planner while false; per-pattern guards on
 * Guarded Patterns filter individually; execution gating optionally refuses pushes too.
 * An optional priority channel makes provider priority a live signal value.
 */
public class GuardedPatternProviderBlockEntity extends BlockEntity
        implements PatternProviderLogicHost, IInWorldGridNodeHost,
        io.github.johnhamilto.ae2logistics.config.TransferableSettings {

    private static final IGridNodeListener<GuardedPatternProviderBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(GuardedPatternProviderBlockEntity owner, IGridNode node) {
                    owner.setChanged();
                }

                @Override
                public void onStateChanged(GuardedPatternProviderBlockEntity owner, IGridNode node,
                        IGridNodeListener.State state) {
                    owner.logic.onMainNodeStateChanged();
                }
            };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setIdlePowerUsage(2.0)
            .setVisualRepresentation(AE2Logistics.GUARDED_PROVIDER_ITEM.get());

    private final GuardedProviderLogic logic = new GuardedProviderLogic(mainNode, this);

    @Nullable
    private ResourceLocation guardChannel;
    private int guardOp = 4;
    private long guardValue;
    private boolean gateExecution = true;
    @Nullable
    private ResourceLocation priorityChannel;

    private int tickCounter;
    private long guardFingerprint;

    public GuardedPatternProviderBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.GUARDED_PROVIDER_BE.get(), pos, state);
    }

    // --- guard evaluation ---

    @Nullable
    public SignalService signalService() {
        var node = mainNode.getNode();
        if (node == null || node.getGrid() == null) {
            return null;
        }
        return node.getGrid().getService(SignalService.class);
    }

    public boolean guardPasses() {
        if (guardChannel == null) {
            return true;
        }
        var service = signalService();
        return service == null || GuardedPattern.test(service.get(guardChannel), guardOp, guardValue);
    }

    public boolean gateExecution() {
        return gateExecution;
    }

    @Nullable
    public Integer livePriority() {
        if (priorityChannel == null) {
            return null;
        }
        var service = signalService();
        if (service == null) {
            return null;
        }
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, service.get(priorityChannel)));
    }

    @Nullable
    public ResourceLocation guardChannel() {
        return guardChannel;
    }

    public int guardOp() {
        return guardOp;
    }

    public long guardValue() {
        return guardValue;
    }

    @Nullable
    public ResourceLocation priorityChannel() {
        return priorityChannel;
    }

    public void applyGuardConfig(@Nullable ResourceLocation channel, int op, long value,
            boolean gateExec, @Nullable ResourceLocation newPriorityChannel, int basePriority) {
        this.guardChannel = channel;
        this.guardOp = Math.floorMod(op, GuardedPattern.OPS.length);
        this.guardValue = value;
        this.gateExecution = gateExec;
        this.priorityChannel = newPriorityChannel;
        logic.setPriority(basePriority);
        setChanged();
        logic.updatePatterns();
        guardFingerprint = computeFingerprint();
    }

    /** Called every server tick by the block; re-indexes patterns when guard state flips. */
    public void serverTick() {
        if (++tickCounter % 10 != 0 || level == null || level.isClientSide) {
            return;
        }
        long fingerprint = computeFingerprint();
        if (fingerprint != guardFingerprint) {
            guardFingerprint = fingerprint;
            logic.updatePatterns();
        }
    }

    private long computeFingerprint() {
        long fingerprint = guardPasses() ? 1 : 0;
        var service = signalService();
        if (service != null) {
            for (var pattern : logic.rawPatterns()) {
                if (pattern instanceof GuardedPattern guarded) {
                    fingerprint = fingerprint * 31 + (guarded.passes(service) ? 2 : 1);
                }
            }
            var live = livePriority();
            fingerprint = fingerprint * 31 + (live != null ? live : 0);
        }
        return fingerprint;
    }

    // --- lifecycle ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            GridHelper.onFirstTick(this, be -> {
                be.mainNode.create(be.level, be.getBlockPos());
                be.logic.updatePatterns();
                be.guardFingerprint = be.computeFingerprint();
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        logic.writeToNBT(tag, registries);
        if (guardChannel != null) {
            tag.putString("guardChannel", guardChannel.toString());
        }
        tag.putInt("guardOp", guardOp);
        tag.putLong("guardValue", guardValue);
        tag.putBoolean("gateExecution", gateExecution);
        if (priorityChannel != null) {
            tag.putString("priorityChannel", priorityChannel.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        logic.readFromNBT(tag, registries);
        guardChannel = tag.contains("guardChannel")
                ? ResourceLocation.tryParse(tag.getString("guardChannel"))
                : null;
        guardOp = tag.getInt("guardOp");
        guardValue = tag.getLong("guardValue");
        gateExecution = !tag.contains("gateExecution") || tag.getBoolean("gateExecution");
        priorityChannel = tag.contains("priorityChannel")
                ? ResourceLocation.tryParse(tag.getString("priorityChannel"))
                : null;
    }

    @Override
    public net.minecraft.core.component.DataComponentMap exportTransferSettings(
            @Nullable net.minecraft.world.entity.player.Player player) {
        var builder = net.minecraft.core.component.DataComponentMap.builder();
        logic.exportSettings(builder);
        var tag = new CompoundTag();
        if (guardChannel != null) {
            tag.putString("guardChannel", guardChannel.toString());
        }
        tag.putInt("guardOp", guardOp);
        tag.putLong("guardValue", guardValue);
        tag.putBoolean("gateExecution", gateExecution);
        if (priorityChannel != null) {
            tag.putString("priorityChannel", priorityChannel.toString());
        }
        builder.set(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get(), tag);
        return builder.build();
    }

    @Override
    public void importTransferSettings(net.minecraft.core.component.DataComponentMap input,
            @Nullable net.minecraft.world.entity.player.Player player) {
        logic.importSettings(input, player);
        var tag = input.get(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get());
        if (tag != null) {
            applyGuardConfig(
                    tag.contains("guardChannel")
                            ? ResourceLocation.tryParse(tag.getString("guardChannel"))
                            : null,
                    tag.getInt("guardOp"),
                    tag.getLong("guardValue"),
                    !tag.contains("gateExecution") || tag.getBoolean("gateExecution"),
                    tag.contains("priorityChannel")
                            ? ResourceLocation.tryParse(tag.getString("priorityChannel"))
                            : null,
                    logic.getPriority());
        }
    }

    // --- host contract ---

    @Override
    public GuardedProviderLogic getLogic() {
        return logic;
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void saveChanges() {
        setChanged();
    }

    @Override
    @Nullable
    public IGrid getGrid() {
        var node = mainNode.getNode();
        return node == null ? null : node.getGrid();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(AE2Logistics.GUARDED_PROVIDER_ITEM.get());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(AE2Logistics.GUARDED_PROVIDER_ITEM.get());
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public appeng.api.util.AECableType getCableConnectionType(Direction dir) {
        return appeng.api.util.AECableType.SMART;
    }
}
