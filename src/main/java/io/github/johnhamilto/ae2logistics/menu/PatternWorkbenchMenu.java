package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.PatternWorkbenchBlockEntity;

/**
 * Built on AE2's menu framework: slots carry semantics and are positioned by the
 * screen's style document, and shift-click routing comes from the base class.
 */
public class PatternWorkbenchMenu extends AEBaseMenu {

    public final BlockPos pos;
    private final Container container;

    public PatternWorkbenchMenu(int containerId, Inventory inventory, PatternWorkbenchBlockEntity workbench) {
        super(AE2Logistics.PATTERN_WORKBENCH_MENU.get(), containerId, inventory, workbench);
        this.pos = workbench.getBlockPos();
        this.container = workbench.inventory();
        buildSlots(inventory);
    }

    public PatternWorkbenchMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.PATTERN_WORKBENCH_MENU.get(), containerId, inventory, null);
        this.pos = buffer.readBlockPos();
        this.container = new SimpleContainer(1);
        buildSlots(inventory);
    }

    private void buildSlots(Inventory playerInventory) {
        addSlot(new Slot(container, 0, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PatternDetailsHelper.isEncodedPattern(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        }, SlotSemantics.MACHINE_INPUT);

        createPlayerInventorySlots(playerInventory);
    }

    public ItemStack patternStack() {
        return container.getItem(0);
    }
}
