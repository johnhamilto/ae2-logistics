package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.PatternWorkbenchBlockEntity;

public class PatternWorkbenchMenu extends AbstractContainerMenu {

    public final BlockPos pos;
    private final Container container;

    public PatternWorkbenchMenu(int containerId, Inventory inventory, PatternWorkbenchBlockEntity workbench) {
        super(AE2Logistics.PATTERN_WORKBENCH_MENU.get(), containerId);
        this.pos = workbench.getBlockPos();
        this.container = workbench.inventory();
        buildSlots(inventory);
    }

    public PatternWorkbenchMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.PATTERN_WORKBENCH_MENU.get(), containerId);
        this.pos = buffer.readBlockPos();
        this.container = new SimpleContainer(1);
        buildSlots(inventory);
    }

    private void buildSlots(Inventory playerInventory) {
        addSlot(new Slot(container, 0, 152, 21) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PatternDetailsHelper.isEncodedPattern(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public ItemStack patternStack() {
        return container.getItem(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        var stack = slot.getItem();
        var original = stack.copy();

        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
