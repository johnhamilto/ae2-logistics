package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity;

public class GuardedProviderMenu extends AbstractContainerMenu {

    public static final int PATTERN_X = 19;
    public static final int PATTERN_Y = 20;
    public static final int INV_X = 19;
    public static final int INV_Y = 140;
    public static final int HOTBAR_Y = 198;

    @Nullable
    private final GuardedPatternProviderBlockEntity provider;

    public final BlockPos pos;
    public final String guardChannel;
    public final int guardOp;
    public final long guardValue;
    public final boolean gateExecution;
    public final String priorityChannel;
    public final int basePriority;

    private final Container patterns;
    private int passingValue;
    private int priorityValue;

    public GuardedProviderMenu(int containerId, Inventory inventory, GuardedPatternProviderBlockEntity provider) {
        super(AE2Logistics.GUARDED_PROVIDER_MENU.get(), containerId);
        this.provider = provider;
        this.pos = provider.getBlockPos();
        this.guardChannel = provider.guardChannel() == null ? "" : provider.guardChannel().toString();
        this.guardOp = provider.guardOp();
        this.guardValue = provider.guardValue();
        this.gateExecution = provider.gateExecution();
        this.priorityChannel = provider.priorityChannel() == null ? "" : provider.priorityChannel().toString();
        this.basePriority = provider.getLogic().getPriority();
        this.patterns = provider.getLogic().getPatternInv().toContainer();
        buildSlots(inventory);
        addLiveSlots();
    }

    public GuardedProviderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.GUARDED_PROVIDER_MENU.get(), containerId);
        this.provider = null;
        this.pos = buffer.readBlockPos();
        this.guardChannel = buffer.readUtf();
        this.guardOp = buffer.readVarInt();
        this.guardValue = buffer.readLong();
        this.gateExecution = buffer.readBoolean();
        this.priorityChannel = buffer.readUtf();
        this.basePriority = buffer.readVarInt();
        this.patterns = new SimpleContainer(9);
        buildSlots(inventory);
        addLiveSlots();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, GuardedPatternProviderBlockEntity provider) {
        buffer.writeBlockPos(provider.getBlockPos());
        buffer.writeUtf(provider.guardChannel() == null ? "" : provider.guardChannel().toString());
        buffer.writeVarInt(provider.guardOp());
        buffer.writeLong(provider.guardValue());
        buffer.writeBoolean(provider.gateExecution());
        buffer.writeUtf(provider.priorityChannel() == null ? "" : provider.priorityChannel().toString());
        buffer.writeVarInt(provider.getLogic().getPriority());
    }

    private void buildSlots(Inventory inventory) {
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(patterns, i, PATTERN_X + i * 18, PATTERN_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return PatternDetailsHelper.isEncodedPattern(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    private void addLiveSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return provider != null ? (provider.guardPasses() ? 1 : 0) : passingValue;
            }

            @Override
            public void set(int value) {
                passingValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (provider == null) {
                    return priorityValue;
                }
                var live = provider.livePriority();
                return live != null ? live : provider.getLogic().getPriority();
            }

            @Override
            public void set(int value) {
                priorityValue = value;
            }
        });
    }

    public boolean guardPassing() {
        return passingValue != 0;
    }

    public int livePriority() {
        return priorityValue;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        var stack = slot.getItem();
        var original = stack.copy();

        if (index < 9) {
            if (!moveItemStackTo(stack, 9, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!PatternDetailsHelper.isEncodedPattern(stack)
                    || !moveItemStackTo(stack, 0, 9, false)) {
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
