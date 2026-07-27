package io.github.johnhamilto.ae2logistics.parts;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;

import io.github.johnhamilto.ae2logistics.menu.LogicPartMenu;
import io.github.johnhamilto.ae2logistics.signal.ILogicNode;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * Base for all logic parts: channel-free cable parts whose {@link ILogicNode} service is
 * evaluated by the grid's {@link SignalService} in topological order each tick.
 */
public abstract class LogicPart extends AEBasePart implements ILogicNode {

    @Nullable
    protected ResourceLocation outChannel;
    @Nullable
    protected ResourceLocation inA;
    @Nullable
    protected ResourceLocation inB;
    protected int op;
    protected long valueA;
    protected long valueB;
    protected boolean flag;

    public LogicPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(0.5)
                .addService(ILogicNode.class, this);
    }

    /** Identifies the concrete part type to the shared menu/screen. */
    public abstract LogicPartType type();

    /** The storage key watched by sensor-style parts; null for everything else. */
    @Nullable
    public GenericStack watchedKey() {
        return null;
    }

    public void setWatchedKey(@Nullable GenericStack stack) {
    }

    @Override
    public Set<ResourceLocation> readChannels() {
        var channels = new HashSet<ResourceLocation>(2);
        if (inA != null) {
            channels.add(inA);
        }
        if (inB != null && flag) {
            channels.add(inB);
        }
        return channels;
    }

    @Nullable
    @Override
    public ResourceLocation writtenChannel() {
        return outChannel;
    }

    @Override
    public long stableKey() {
        var host = getHost().getBlockEntity();
        return host.getBlockPos().asLong() * 31 + (getSide() == null ? 6 : getSide().ordinal());
    }

    protected long readB(LogicContext context) {
        return flag && inB != null ? context.read(inB) : valueA;
    }

    public void applyConfig(@Nullable ResourceLocation out, @Nullable ResourceLocation a,
            @Nullable ResourceLocation b, int op, long valueA, long valueB, boolean flag) {
        this.outChannel = out;
        this.inA = a;
        this.inB = b;
        this.op = op;
        this.valueA = valueA;
        this.valueB = valueB;
        this.flag = flag;
        onConfigChanged();
    }

    protected void onConfigChanged() {
        getHost().markForSave();
        getMainNode().ifPresent(grid -> grid.getService(SignalService.class).invalidateGraph());
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        if (outChannel != null) {
            data.putString("out", outChannel.toString());
        }
        if (inA != null) {
            data.putString("inA", inA.toString());
        }
        if (inB != null) {
            data.putString("inB", inB.toString());
        }
        data.putInt("op", op);
        data.putLong("valueA", valueA);
        data.putLong("valueB", valueB);
        data.putBoolean("flag", flag);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        outChannel = data.contains("out") ? ResourceLocation.tryParse(data.getString("out")) : null;
        inA = data.contains("inA") ? ResourceLocation.tryParse(data.getString("inA")) : null;
        inB = data.contains("inB") ? ResourceLocation.tryParse(data.getString("inB")) : null;
        op = data.getInt("op");
        valueA = data.getLong("valueA");
        valueB = data.getLong("valueB");
        flag = data.getBoolean("flag");
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(4, 4, 12, 12, 12, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 16;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide() && player instanceof ServerPlayer serverPlayer) {
            var host = getHost().getBlockEntity();
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new LogicPartMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> LogicPartMenu.writeOpenData(buffer, this));
        }
        return true;
    }

    @Nullable
    public ResourceLocation writtenChannelRaw() {
        return outChannel;
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

    public long currentOutput() {
        if (outChannel == null) {
            return 0;
        }
        var node = getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return 0;
        }
        return node.getGrid().getService(SignalService.class).get(outChannel);
    }
}
