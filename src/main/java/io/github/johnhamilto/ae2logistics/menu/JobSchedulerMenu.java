package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity;

public class JobSchedulerMenu extends AbstractContainerMenu {

    public static final int GHOST_X = 10;
    public static final int GHOST_Y = 20;
    public static final int ROW_STEP = 26;
    public static final int INV_X = 19;
    public static final int INV_Y = 140;
    public static final int HOTBAR_Y = 198;

    @Nullable
    private final JobSchedulerBlockEntity scheduler;

    public final BlockPos pos;
    public final long[] floors = new long[JobSchedulerBlockEntity.RULES];
    public final long[] batches = new long[JobSchedulerBlockEntity.RULES];
    public final byte[] classes = new byte[JobSchedulerBlockEntity.RULES];
    public final String[] guards = new String[JobSchedulerBlockEntity.RULES];

    private final SimpleContainer ghosts = new SimpleContainer(JobSchedulerBlockEntity.RULES);
    private final int[] stateValues = new int[JobSchedulerBlockEntity.RULES];

    public JobSchedulerMenu(int containerId, Inventory inventory, JobSchedulerBlockEntity scheduler) {
        super(AE2Logistics.JOB_SCHEDULER_MENU.get(), containerId);
        this.scheduler = scheduler;
        this.pos = scheduler.getBlockPos();
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            var rule = scheduler.rule(i);
            floors[i] = rule.floor;
            batches[i] = rule.batch;
            classes[i] = rule.jobClass;
            guards[i] = rule.guard == null ? "" : rule.guard.toString();
            ghosts.setItem(i, displayStack(rule.target));
        }
        addSlots(inventory);
        addStateSlots();
    }

    public JobSchedulerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.JOB_SCHEDULER_MENU.get(), containerId);
        this.scheduler = null;
        this.pos = buffer.readBlockPos();
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            floors[i] = buffer.readLong();
            batches[i] = buffer.readLong();
            classes[i] = buffer.readByte();
            guards[i] = buffer.readUtf();
            ghosts.setItem(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        addSlots(inventory);
        addStateSlots();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, JobSchedulerBlockEntity scheduler) {
        buffer.writeBlockPos(scheduler.getBlockPos());
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            var rule = scheduler.rule(i);
            buffer.writeLong(rule.floor);
            buffer.writeLong(rule.batch);
            buffer.writeByte(rule.jobClass);
            buffer.writeUtf(rule.guard == null ? "" : rule.guard.toString());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, displayStack(rule.target));
        }
    }

    private void addSlots(Inventory inventory) {
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            addSlot(new Slot(ghosts, i, GHOST_X, GHOST_Y + i * ROW_STEP) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
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

    private void addStateSlots() {
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            int index = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return scheduler != null ? schedulerState(index) : stateValues[index];
                }

                @Override
                public void set(int value) {
                    stateValues[index] = value;
                }
            });
        }
    }

    private int schedulerState(int index) {
        return scheduler == null ? 0 : scheduler.ruleState(index);
    }

    public int ruleStateValue(int index) {
        return stateValues[index];
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < JobSchedulerBlockEntity.RULES) {
            var stack = fromCarried(getCarried());
            if (scheduler != null) {
                scheduler.setRuleTarget(slotId, stack);
            }
            ghosts.setItem(slotId, displayStack(stack));
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Nullable
    private static GenericStack fromCarried(ItemStack carried) {
        if (carried.isEmpty()) {
            return null;
        }
        var unwrapped = GenericStack.fromItemStack(carried);
        if (unwrapped != null) {
            return new GenericStack(unwrapped.what(), 1);
        }
        var contained = ContainerItemStrategies.getContainedStack(carried);
        if (contained != null) {
            return new GenericStack(contained.what(), 1);
        }
        var key = AEItemKey.of(carried);
        return key != null ? new GenericStack(key, 1) : null;
    }

    private static ItemStack displayStack(@Nullable GenericStack stack) {
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        if (stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack();
        }
        if (stack.what() instanceof AEFluidKey fluidKey && fluidKey.getFluid().getBucket() != Items.AIR) {
            return new ItemStack(fluidKey.getFluid().getBucket());
        }
        return GenericStack.wrapInItemStack(stack);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
