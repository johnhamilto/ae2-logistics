package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.SubnetCoreBlockEntity;

/**
 * Entry list plus a detail editor for the selected entry, mirroring the Logic Core
 * menu: snapshot in the open buffer, edits via {@link ConfigureSubnetEntryPayload},
 * one ghost filter slot bound server-side to the selection.
 */
public class SubnetCoreMenu extends AEBaseMenu {

    public static final int ROWS = SubnetCoreBlockEntity.ENTRIES;

    public static final int ROW_Y = 18;
    public static final int ROW_STEP = 13;
    public static final int GHOST_X = 10;
    public static final int GHOST_Y = 137;

    @Nullable
    private final SubnetCoreBlockEntity core;

    public final BlockPos pos;
    public final byte[] types = new byte[ROWS];
    public final byte[] faces = new byte[ROWS];
    public final int[] priorities = new int[ROWS];

    private final SimpleContainer ghost = new SimpleContainer(1);
    private final int ghostSlotIndex;
    private int selected;
    private int activeMask;

    public SubnetCoreMenu(int containerId, Inventory inventory, SubnetCoreBlockEntity core) {
        super(AE2Logistics.SUBNET_CORE_MENU.get(), containerId, inventory, core);
        this.core = core;
        this.pos = core.getBlockPos();
        for (int i = 0; i < ROWS; i++) {
            var entry = core.entry(i);
            var type = entry.type();
            types[i] = type == null ? -1 : (byte) type.ordinal();
            faces[i] = (byte) entry.face().ordinal();
            priorities[i] = entry.priority();
        }
        this.ghostSlotIndex = addGhostSlot();
        addPlayerSlots(inventory);
        addSyncSlots();
        refreshGhost();
    }

    public SubnetCoreMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.SUBNET_CORE_MENU.get(), containerId, inventory, null);
        this.core = null;
        this.pos = buffer.readBlockPos();
        for (int i = 0; i < ROWS; i++) {
            types[i] = buffer.readByte();
            faces[i] = buffer.readByte();
            priorities[i] = buffer.readVarInt();
        }
        this.ghostSlotIndex = addGhostSlot();
        addPlayerSlots(inventory);
        addSyncSlots();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, SubnetCoreBlockEntity core) {
        buffer.writeBlockPos(core.getBlockPos());
        for (int i = 0; i < ROWS; i++) {
            var entry = core.entry(i);
            var type = entry.type();
            buffer.writeByte(type == null ? -1 : type.ordinal());
            buffer.writeByte(entry.face().ordinal());
            buffer.writeVarInt(entry.priority());
        }
    }

    private int addGhostSlot() {
        int index = slots.size();
        addSlot(new Slot(ghost, 0, 0, 0) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        }, SlotSemantics.CONFIG);
        return index;
    }

    private void addPlayerSlots(Inventory inventory) {
        createPlayerInventorySlots(inventory);
    }

    private void addSyncSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (core == null) {
                    return activeMask;
                }
                int mask = 0;
                for (int i = 0; i < ROWS; i++) {
                    if (core.entry(i).isActive()) {
                        mask |= 1 << i;
                    }
                }
                if (core.coreActive()) {
                    mask |= 1 << ROWS;
                }
                return mask;
            }

            @Override
            public void set(int value) {
                activeMask = value;
            }
        });
    }

    public boolean entryActive(int index) {
        return (activeMask & 1 << index) != 0;
    }

    public boolean coreActive() {
        return (activeMask & 1 << ROWS) != 0;
    }

    public int selected() {
        return selected;
    }

    public void setSelected(int slot) {
        if (slot >= 0 && slot < ROWS) {
            selected = slot;
            refreshGhost();
        }
    }

    void noteApplied(ConfigureSubnetEntryPayload payload) {
        int i = payload.slot();
        types[i] = payload.entryType();
        faces[i] = payload.face();
        priorities[i] = payload.priority();
    }

    private void refreshGhost() {
        if (core != null) {
            ghost.setItem(0, displayStack(core.entry(selected).filter()));
        }
    }

    @Override
    public void broadcastChanges() {
        refreshGhost();
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == ghostSlotIndex) {
            if (core != null && types[selected] >= 0) {
                var stack = fromCarried(getCarried());
                core.setEntryFilter(selected, stack);
                ghost.setItem(0, displayStack(stack));
            }
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
}
