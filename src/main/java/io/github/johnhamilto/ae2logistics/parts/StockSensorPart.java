package io.github.johnhamilto.ae2logistics.parts;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.GenericStack;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Writes the network's stored amount of a configured key to the output channel every
 * tick, turning inventory levels into signals without commands or emitters.
 */
public class StockSensorPart extends LogicPart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/stock_sensor"));

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
    public Set<ResourceLocation> readChannels() {
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
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        if (watched != null) {
            data.put("watched", GenericStack.writeTag(registries, watched));
        }
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        watched = data.contains("watched") ? GenericStack.readTag(registries, data.getCompound("watched")) : null;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
