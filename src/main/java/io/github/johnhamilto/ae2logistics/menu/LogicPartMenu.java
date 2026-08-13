package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

/**
 * One shared menu for every logic part. Static configuration travels in the menu-open
 * buffer; edits return via {@link ConfigurePartPayload}; the live output value streams
 * through two int data slots.
 */
public class LogicPartMenu extends AEBaseMenu implements GhostSlotPayload.GhostSlotTarget {

    @Nullable
    private final LogicPart part;

    public final BlockPos pos;
    public final Direction side;
    public final LogicPartType type;
    public final String outChannel;
    public final String inA;
    public final String inB;
    public final int op;
    public final long valueA;
    public final long valueB;
    public final boolean flag;

    private int outputHi;
    private int outputLo;

    private final SimpleContainer ghostContainer = new SimpleContainer(1);
    private int ghostSlotIndex = -1;

    public LogicPartMenu(int containerId, Inventory inventory, LogicPart part) {
        super(AE2Logistics.LOGIC_PART_MENU.get(), containerId, inventory, part);
        this.part = part;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
        this.type = part.type();
        this.outChannel = "";
        this.inA = "";
        this.inB = "";
        this.op = 0;
        this.valueA = 0;
        this.valueB = 0;
        this.flag = false;

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return (int) (part.currentOutput() >>> 32);
            }

            @Override
            public void set(int value) {
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return (int) part.currentOutput();
            }

            @Override
            public void set(int value) {
            }
        });

        if (type == LogicPartType.STOCK_SENSOR) {
            ghostContainer.setItem(0, displayStack(part.watchedKey()));
            addGhostSlot();
            createPlayerInventorySlots(inventory);
        }
    }

    public LogicPartMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.LOGIC_PART_MENU.get(), containerId, inventory, null);
        this.part = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        this.type = LogicPartType.byOrdinal(buffer.readByte());
        this.outChannel = buffer.readUtf();
        this.inA = buffer.readUtf();
        this.inB = buffer.readUtf();
        this.op = buffer.readVarInt();
        this.valueA = buffer.readLong();
        this.valueB = buffer.readLong();
        this.flag = buffer.readBoolean();

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return outputHi;
            }

            @Override
            public void set(int value) {
                outputHi = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return outputLo;
            }

            @Override
            public void set(int value) {
                outputLo = value;
            }
        });

        if (type == LogicPartType.STOCK_SENSOR) {
            ghostContainer.setItem(0, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
            addGhostSlot();
            // The ghost slot is set from the carried stack, so the player needs
            // somewhere to pick one up from.
            createPlayerInventorySlots(inventory);
        }
    }

    private void addGhostSlot() {
        ghostSlotIndex = slots.size();
        addSlot(new Slot(ghostContainer, 0, 0, 0) {
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

    @Override
    public boolean acceptsGhost(int slotIndex) {
        return ghostSlotIndex >= 0 && slotIndex == ghostSlotIndex;
    }

    @Override
    public void setGhost(int slotIndex, ItemStack stack) {
        var key = keyFromCarried(stack);
        if (part != null) {
            part.setWatchedKey(key);
        }
        ghostContainer.setItem(0, displayStack(key));
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (ghostSlotIndex >= 0 && slotId == ghostSlotIndex) {
            var key = keyFromCarried(getCarried());
            if (part != null) {
                part.setWatchedKey(key);
            }
            ghostContainer.setItem(0, displayStack(key));
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Nullable
    private static GenericStack keyFromCarried(ItemStack carried) {
        if (carried.isEmpty()) {
            return null;
        }
        var unwrapped = GenericStack.fromItemStack(carried);
        if (unwrapped != null) {
            return new GenericStack(unwrapped.what(), 1);
        }
        var key = AEItemKey.of(carried);
        return key != null ? new GenericStack(key, 1) : null;
    }

    private static ItemStack displayStack(@Nullable GenericStack stack) {
        if (stack != null && stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack();
        }
        return ItemStack.EMPTY;
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, LogicPart part) {
        var host = part.getHost().getBlockEntity();
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeByte(part.getSide().ordinal());
        buffer.writeByte(part.type().ordinal());
        buffer.writeUtf(toStringOrEmpty(part.writtenChannelRaw()));
        buffer.writeUtf(toStringOrEmpty(part.inARaw()));
        buffer.writeUtf(toStringOrEmpty(part.inBRaw()));
        buffer.writeVarInt(part.opRaw());
        buffer.writeLong(part.valueARaw());
        buffer.writeLong(part.valueBRaw());
        buffer.writeBoolean(part.flagRaw());
        if (part.type() == LogicPartType.STOCK_SENSOR) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, displayStack(part.watchedKey()));
        }
    }

    private static String toStringOrEmpty(@Nullable Identifier id) {
        return id == null ? "" : id.toString();
    }

    public long outputValue() {
        return (long) outputHi << 32 | outputLo & 0xFFFFFFFFL;
    }
}
