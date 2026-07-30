package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart;

public class SubnetLinkMenu extends AEBaseMenu implements GhostSlotPayload.GhostSlotTarget {

    @Nullable
    private final SubnetLinkPart part;

    public final BlockPos pos;
    public final Direction side;
    public final byte mode;
    public final int priority;

    private final SimpleContainer filterContainer = new SimpleContainer(SubnetLinkPart.FILTER_SLOTS);
    private int filterSlotStart = -1;

    private int activeValue;
    private int subnetSizeValue;

    public SubnetLinkMenu(int containerId, Inventory inventory, SubnetLinkPart part) {
        super(AE2Logistics.SUBNET_LINK_MENU.get(), containerId, inventory, part);
        this.part = part;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
        this.mode = part.mode();
        this.priority = part.priority();
        for (int i = 0; i < SubnetLinkPart.FILTER_SLOTS; i++) {
            filterContainer.setItem(i, displayStack(part.filterSlot(i)));
        }
        addSlots(inventory);
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return part.isActiveAndLoaded() ? 1 : 0;
            }

            @Override
            public void set(int value) {
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                var grid = part.subnetGrid();
                return grid == null ? 0 : grid.size();
            }

            @Override
            public void set(int value) {
            }
        });
    }

    public SubnetLinkMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.SUBNET_LINK_MENU.get(), containerId, inventory, null);
        this.part = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        this.mode = buffer.readByte();
        this.priority = buffer.readVarInt();
        for (int i = 0; i < SubnetLinkPart.FILTER_SLOTS; i++) {
            filterContainer.setItem(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        addSlots(inventory);
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return activeValue;
            }

            @Override
            public void set(int value) {
                activeValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return subnetSizeValue;
            }

            @Override
            public void set(int value) {
                subnetSizeValue = value;
            }
        });
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, SubnetLinkPart part) {
        var host = part.getHost().getBlockEntity();
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeByte(part.getSide().ordinal());
        buffer.writeByte(part.mode());
        buffer.writeVarInt(part.priority());
        for (int i = 0; i < SubnetLinkPart.FILTER_SLOTS; i++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, displayStack(part.filterSlot(i)));
        }
    }

    private void addSlots(Inventory inventory) {
        filterSlotStart = slots.size();
        for (int i = 0; i < SubnetLinkPart.FILTER_SLOTS; i++) {
            addSlot(new Slot(filterContainer, i, 0, 0) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            }, SlotSemantics.CONFIG);
        }
        createPlayerInventorySlots(inventory);
    }

    public boolean linkActive() {
        return activeValue != 0;
    }

    public int subnetSize() {
        return subnetSizeValue;
    }

    @Override
    public boolean acceptsGhost(int slotIndex) {
        return filterSlotStart >= 0 && slotIndex >= filterSlotStart
                && slotIndex < filterSlotStart + SubnetLinkPart.FILTER_SLOTS;
    }

    @Override
    public void setGhost(int slotIndex, ItemStack stack) {
        int index = slotIndex - filterSlotStart;
        var stackFilter = filterFromCarried(stack);
        if (part != null) {
            part.setFilterSlot(index, stackFilter);
        }
        filterContainer.setItem(index, displayStack(stackFilter));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (acceptsGhost(slotId)) {
            int index = slotId - filterSlotStart;
            var stack = filterFromCarried(getCarried());
            if (part != null) {
                part.setFilterSlot(index, stack);
            }
            filterContainer.setItem(index, displayStack(stack));
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Wrapped generic stacks and container items (buckets become fluid filters) beat plain items. */
    @Nullable
    private static GenericStack filterFromCarried(ItemStack carried) {
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
