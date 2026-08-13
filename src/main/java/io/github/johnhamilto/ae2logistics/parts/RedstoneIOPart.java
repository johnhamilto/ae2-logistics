package io.github.johnhamilto.ae2logistics.parts;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/redstone_port"));

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

    /** op selects the emission style in output mode: 0 = strong (default), 1 = weak only. */
    private boolean emitsStrong() {
        return op == 0;
    }

    @Override
    public Set<Identifier> readChannels() {
        return isOutputMode() && inA != null ? Set.of(inA) : Set.of();
    }

    @Nullable
    @Override
    public Identifier writtenChannel() {
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
        return isOutputMode() && emitsStrong() ? emitted : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return isOutputMode() ? emitted : 0;
    }

    @Override
    protected void onConfigChanged() {
        super.onConfigChanged();
        // Strong/weak or mode flips must repropagate immediately, not wait for the
        // next emitted-value change.
        var host = getHost().getBlockEntity();
        var level = host.getLevel();
        if (level != null && !level.isClientSide() && getSide() != null) {
            var block = level.getBlockState(host.getBlockPos()).getBlock();
            level.updateNeighborsAt(host.getBlockPos(), block);
            level.updateNeighborsAt(host.getBlockPos().relative(getSide()), block);
        }
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        data.putInt("emitted", emitted);
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        emitted = data.getIntOr("emitted", 0);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
