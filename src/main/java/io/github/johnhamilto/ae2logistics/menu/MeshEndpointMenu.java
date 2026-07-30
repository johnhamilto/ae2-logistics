package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public class MeshEndpointMenu extends AbstractContainerMenu {

    public static final int FILTER_X = 10;
    public static final int FILTER_Y = 118;
    public static final int INV_X = 19;
    public static final int INV_Y = 140;
    public static final int HOTBAR_Y = 198;

    @Nullable
    private final MeshEndpointPart part;

    public final BlockPos pos;
    public final Direction side;
    public final String frequency;
    public final byte role;
    public final int priority;
    public final int capabilities;

    private final SimpleContainer filterContainer = new SimpleContainer(MeshEndpointPart.FILTER_SLOTS);
    private int filterSlotStart = -1;

    private int statusValue;
    private int countValue;
    private int meStateValue;

    public MeshEndpointMenu(int containerId, Inventory inventory, MeshEndpointPart part) {
        super(AE2Logistics.MESH_ENDPOINT_MENU.get(), containerId);
        this.part = part;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
        this.frequency = part.frequency();
        this.role = part.role();
        this.priority = part.priority();
        this.capabilities = part.capabilityMask();

        for (int i = 0; i < MeshEndpointPart.FILTER_SLOTS; i++) {
            filterContainer.setItem(i, displayStack(part.filterSlot(i)));
        }
        addSlots(inventory);

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return MeshRegistry.statusOf(part);
            }

            @Override
            public void set(int value) {
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return MeshRegistry.carrierEndpointCount(part);
            }

            @Override
            public void set(int value) {
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return part.meLinkState();
            }

            @Override
            public void set(int value) {
            }
        });
    }

    public MeshEndpointMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.MESH_ENDPOINT_MENU.get(), containerId);
        this.part = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        this.frequency = buffer.readUtf();
        this.role = buffer.readByte();
        this.priority = buffer.readVarInt();
        this.capabilities = buffer.readVarInt();
        for (int i = 0; i < MeshEndpointPart.FILTER_SLOTS; i++) {
            filterContainer.setItem(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        addSlots(inventory);

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return statusValue;
            }

            @Override
            public void set(int value) {
                statusValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return countValue;
            }

            @Override
            public void set(int value) {
                countValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return meStateValue;
            }

            @Override
            public void set(int value) {
                meStateValue = value;
            }
        });
    }

    private void addSlots(Inventory inventory) {
        filterSlotStart = slots.size();
        for (int i = 0; i < MeshEndpointPart.FILTER_SLOTS; i++) {
            addSlot(new Slot(filterContainer, i, FILTER_X + i * 18, FILTER_Y) {
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

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, MeshEndpointPart part) {
        var host = part.getHost().getBlockEntity();
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeByte(part.getSide().ordinal());
        buffer.writeUtf(part.frequency());
        buffer.writeByte(part.role());
        buffer.writeVarInt(part.priority());
        buffer.writeVarInt(part.capabilityMask());
        for (int i = 0; i < MeshEndpointPart.FILTER_SLOTS; i++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, displayStack(part.filterSlot(i)));
        }
    }

    public byte status() {
        return (byte) statusValue;
    }

    public int endpointCount() {
        return countValue;
    }

    public byte meState() {
        return (byte) meStateValue;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (filterSlotStart >= 0 && slotId >= filterSlotStart
                && slotId < filterSlotStart + MeshEndpointPart.FILTER_SLOTS) {
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

    /**
     * Carried item to filter entry: wrapped generic stacks and container items (a water
     * bucket becomes a water filter when the endpoint moves fluids) beat plain items.
     */
    @Nullable
    private GenericStack filterFromCarried(ItemStack carried) {
        if (carried.isEmpty()) {
            return null;
        }
        var unwrapped = GenericStack.fromItemStack(carried);
        if (unwrapped != null) {
            return new GenericStack(unwrapped.what(), 1);
        }
        boolean fluidAttuned = part != null
                ? part.attuned(MeshRegistry.TYPE_FLUID)
                : (capabilities & MeshRegistry.TYPE_FLUID) != 0;
        if (fluidAttuned) {
            var contained = ContainerItemStrategies.getContainedStack(carried);
            if (contained != null) {
                return new GenericStack(contained.what(), 1);
            }
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
