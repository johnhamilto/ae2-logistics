package io.github.johnhamilto.ae2logistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public class PatternWorkbenchBlockEntity extends BlockEntity {

    private final SimpleContainer inventory = new SimpleContainer(1);

    public PatternWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.PATTERN_WORKBENCH_BE.get(), pos, state);
        inventory.addListener(container -> setChanged());
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("pattern", inventory.getItem(0).saveOptional(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.setItem(0, ItemStack.parseOptional(registries, tag.getCompound("pattern")));
    }
}
