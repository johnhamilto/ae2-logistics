package io.github.johnhamilto.ae2logistics.parts;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalMath;

/**
 * Bridges signals and vanilla redstone on the part's face. Input mode (flag=false) writes
 * the face's redstone level (0-15) to the output channel; output mode (flag=true) emits
 * the value of input channel A, clamped to 0-15, as redstone.
 */
public class RedstoneIOPart extends LogicPart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/redstone_io"));

    private int emitted;

    public RedstoneIOPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.REDSTONE_IO;
    }

    private boolean isOutputMode() {
        return flag;
    }

    @Override
    public Set<ResourceLocation> readChannels() {
        return isOutputMode() && inA != null ? Set.of(inA) : Set.of();
    }

    @Nullable
    @Override
    public ResourceLocation writtenChannel() {
        return isOutputMode() ? null : outChannel;
    }

    @Override
    public void evaluate(LogicContext context) {
        var host = getHost().getBlockEntity();
        var level = host.getLevel();
        if (level == null) {
            return;
        }

        if (isOutputMode()) {
            int newLevel = (int) SignalMath.clamp(inA != null ? context.read(inA) : 0, 0, 15);
            if (newLevel != emitted) {
                emitted = newLevel;
                getHost().markForSave();
                var block = level.getBlockState(host.getBlockPos()).getBlock();
                level.updateNeighborsAt(host.getBlockPos(), block);
                level.updateNeighborsAt(host.getBlockPos().relative(getSide()), block);
            }
        } else {
            var neighborPos = host.getBlockPos().relative(getSide());
            context.write(level.getSignal(neighborPos, getSide()));
        }
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public int isProvidingStrongPower() {
        return isOutputMode() ? emitted : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return isOutputMode() ? emitted : 0;
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putInt("emitted", emitted);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        emitted = data.getInt("emitted");
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
