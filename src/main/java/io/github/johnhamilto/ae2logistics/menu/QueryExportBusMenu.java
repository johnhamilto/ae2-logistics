package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.QueryExportBusPart;

public class QueryExportBusMenu extends AbstractContainerMenu {

    @Nullable
    private final QueryExportBusPart part;

    public final BlockPos pos;
    public final Direction side;
    public final String source;

    private int movedValue;
    private int validValue;

    public QueryExportBusMenu(int containerId, Inventory inventory, QueryExportBusPart part) {
        super(AE2Logistics.QUERY_EXPORT_BUS_MENU.get(), containerId);
        this.part = part;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
        this.source = part.source();
        addLiveSlots();
    }

    public QueryExportBusMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.QUERY_EXPORT_BUS_MENU.get(), containerId);
        this.part = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        this.source = buffer.readUtf();
        addLiveSlots();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, QueryExportBusPart part) {
        var host = part.getHost().getBlockEntity();
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeByte(part.getSide().ordinal());
        buffer.writeUtf(part.source());
    }

    private void addLiveSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return part != null ? part.movedLastOperation() : movedValue;
            }

            @Override
            public void set(int value) {
                movedValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return part != null ? (part.sourceValid() ? 1 : 0) : validValue;
            }

            @Override
            public void set(int value) {
                validValue = value;
            }
        });
    }

    public int movedLastOperation() {
        return movedValue;
    }

    public boolean sourceValid() {
        return validValue != 0;
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
