package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.LogicCoreBlockEntity;

/**
 * SELECT binds the menu's ghost slot to an entry row; APPLY writes one entry's full
 * configuration. Watched keys travel via the menu's clicked() interception.
 */
public record ConfigureCoreEntryPayload(BlockPos pos, byte action, byte slot, byte entryType,
        String out, String inA, String inB, int op, long valueA, long valueB, boolean flag)
        implements CustomPacketPayload {

    public static final byte ACTION_SELECT = 0;
    public static final byte ACTION_APPLY = 1;

    public static final Type<ConfigureCoreEntryPayload> TYPE = new Type<>(AE2Logistics.id("configure_core_entry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureCoreEntryPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeByte(payload.action);
                        buffer.writeByte(payload.slot);
                        buffer.writeByte(payload.entryType);
                        buffer.writeUtf(payload.out);
                        buffer.writeUtf(payload.inA);
                        buffer.writeUtf(payload.inB);
                        buffer.writeVarInt(payload.op);
                        buffer.writeLong(payload.valueA);
                        buffer.writeLong(payload.valueB);
                        buffer.writeBoolean(payload.flag);
                    },
                    buffer -> new ConfigureCoreEntryPayload(
                            buffer.readBlockPos(),
                            buffer.readByte(),
                            buffer.readByte(),
                            buffer.readByte(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readVarInt(),
                            buffer.readLong(),
                            buffer.readLong(),
                            buffer.readBoolean()));

    public static ConfigureCoreEntryPayload select(BlockPos pos, int slot) {
        return new ConfigureCoreEntryPayload(pos, ACTION_SELECT, (byte) slot, (byte) -1,
                "", "", "", 0, 0, 0, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureCoreEntryPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.slot < 0 || payload.slot >= LogicCoreBlockEntity.ENTRIES
                || payload.pos.distToCenterSqr(player.position()) > 100
                || !(player.level().getBlockEntity(payload.pos) instanceof LogicCoreBlockEntity core)) {
            return;
        }
        if (payload.action == ACTION_SELECT) {
            if (player.containerMenu instanceof LogicCoreMenu menu && menu.pos.equals(payload.pos)) {
                menu.setSelected(payload.slot);
            }
        } else if (payload.action == ACTION_APPLY) {
            core.configureEntry(payload.slot, payload.entryType, payload.out, payload.inA,
                    payload.inB, payload.op, payload.valueA, payload.valueB, payload.flag);
            if (player.containerMenu instanceof LogicCoreMenu menu && menu.pos.equals(payload.pos)) {
                menu.noteApplied(payload);
            }
        }
    }
}
