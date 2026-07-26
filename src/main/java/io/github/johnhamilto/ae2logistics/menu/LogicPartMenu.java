package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

/**
 * One shared menu for every logic part. Static configuration travels in the menu-open
 * buffer; edits return via {@link ConfigurePartPayload}; the live output value streams
 * through two int data slots.
 */
public class LogicPartMenu extends AbstractContainerMenu {

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

    public LogicPartMenu(int containerId, Inventory inventory, LogicPart part) {
        super(AE2Logistics.LOGIC_PART_MENU.get(), containerId);
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
    }

    public LogicPartMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.LOGIC_PART_MENU.get(), containerId);
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
    }

    private static String toStringOrEmpty(@Nullable ResourceLocation id) {
        return id == null ? "" : id.toString();
    }

    public long outputValue() {
        return (long) outputHi << 32 | outputLo & 0xFFFFFFFFL;
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
