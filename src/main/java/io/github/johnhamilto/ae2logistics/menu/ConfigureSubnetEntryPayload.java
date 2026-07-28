package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.SubnetCoreBlockEntity;

/** SELECT binds the ghost filter slot to a row; APPLY writes one entry's type/face/priority. */
public record ConfigureSubnetEntryPayload(BlockPos pos, byte action, byte slot, byte entryType,
        byte face, int priority) implements CustomPacketPayload {

    public static final byte ACTION_SELECT = 0;
    public static final byte ACTION_APPLY = 1;

    public static final Type<ConfigureSubnetEntryPayload> TYPE = new Type<>(AE2Logistics.id("configure_subnet_entry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureSubnetEntryPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeByte(payload.action);
                        buffer.writeByte(payload.slot);
                        buffer.writeByte(payload.entryType);
                        buffer.writeByte(payload.face);
                        buffer.writeVarInt(payload.priority);
                    },
                    buffer -> new ConfigureSubnetEntryPayload(
                            buffer.readBlockPos(),
                            buffer.readByte(),
                            buffer.readByte(),
                            buffer.readByte(),
                            buffer.readByte(),
                            buffer.readVarInt()));

    public static ConfigureSubnetEntryPayload select(BlockPos pos, int slot) {
        return new ConfigureSubnetEntryPayload(pos, ACTION_SELECT, (byte) slot, (byte) -1, (byte) 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureSubnetEntryPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.slot < 0 || payload.slot >= SubnetCoreBlockEntity.ENTRIES
                || payload.pos.distToCenterSqr(player.position()) > 100
                || !(player.level().getBlockEntity(payload.pos) instanceof SubnetCoreBlockEntity core)) {
            return;
        }
        if (payload.action == ACTION_SELECT) {
            if (player.containerMenu instanceof SubnetCoreMenu menu && menu.pos.equals(payload.pos)) {
                menu.setSelected(payload.slot);
            }
        } else if (payload.action == ACTION_APPLY) {
            core.configureEntry(payload.slot, payload.entryType, payload.face, payload.priority);
            if (player.containerMenu instanceof SubnetCoreMenu menu && menu.pos.equals(payload.pos)) {
                menu.noteApplied(payload);
            }
        }
    }
}
