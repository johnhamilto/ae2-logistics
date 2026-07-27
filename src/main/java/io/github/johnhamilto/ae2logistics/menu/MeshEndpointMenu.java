package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public class MeshEndpointMenu extends AbstractContainerMenu {

    public final BlockPos pos;
    public final Direction side;
    public final String frequency;
    public final byte role;
    public final int priority;
    public final int capabilities;

    public MeshEndpointMenu(int containerId, Inventory inventory, MeshEndpointPart part) {
        super(AE2Logistics.MESH_ENDPOINT_MENU.get(), containerId);
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
        this.frequency = part.frequency();
        this.role = part.role();
        this.priority = part.priority();
        this.capabilities = part.capabilityMask();
    }

    public MeshEndpointMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.MESH_ENDPOINT_MENU.get(), containerId);
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        this.frequency = buffer.readUtf();
        this.role = buffer.readByte();
        this.priority = buffer.readVarInt();
        this.capabilities = buffer.readVarInt();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, MeshEndpointPart part) {
        var host = part.getHost().getBlockEntity();
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeByte(part.getSide().ordinal());
        buffer.writeUtf(part.frequency());
        buffer.writeByte(part.role());
        buffer.writeVarInt(part.priority());
        buffer.writeVarInt(part.capabilityMask());
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
