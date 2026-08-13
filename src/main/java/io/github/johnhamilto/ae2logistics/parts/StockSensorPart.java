package io.github.johnhamilto.ae2logistics.parts;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.parts.IPartItem;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Writes the network's stored amount of a configured key to the output channel every
 * tick, turning inventory levels into signals without commands or emitters.
 */
public class StockSensorPart extends LogicPart {

    @Nullable
    private GenericStack watched;

    public StockSensorPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.STOCK_SENSOR;
    }

    @Override
    @Nullable
    public GenericStack watchedKey() {
        return watched;
    }

    @Override
    public void setWatchedKey(@Nullable GenericStack stack) {
        this.watched = stack;
        onConfigChanged();
    }

    @Override
    public Set<Identifier> readChannels() {
        return Set.of();
    }

    @Override
    public void evaluate(LogicContext context) {
        if (watched == null) {
            return;
        }
        var node = getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        context.write(node.getGrid().getStorageService().getCachedInventory().get(watched.what()));
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        data.storeNullable("watched", GenericStack.CODEC, watched);
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        watched = data.read("watched", GenericStack.CODEC).orElse(null);
    }
}
