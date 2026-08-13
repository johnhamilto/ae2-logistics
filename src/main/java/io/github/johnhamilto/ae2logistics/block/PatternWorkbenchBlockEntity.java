package io.github.johnhamilto.ae2logistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("pattern", ItemStack.OPTIONAL_CODEC, inventory.getItem(0));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.setItem(0, input.read("pattern", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }
}
