package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public record P2PDataPayload(int containerId, List<P2PFrequencyTerminalMenu.Row> rows)
        implements CustomPacketPayload {

    public static final Type<P2PDataPayload> TYPE = new Type<>(AE2Logistics.id("p2p_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, P2PDataPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeVarInt(payload.rows.size());
                for (var row : payload.rows) {
                    buffer.writeBlockPos(row.pos());
                    buffer.writeByte(row.side());
                    buffer.writeShort(row.frequency());
                    buffer.writeBoolean(row.output());
                    buffer.writeUtf(row.itemId());
                    buffer.writeUtf(row.name());
                    buffer.writeUtf(row.dimension());
                }
            },
            buffer -> {
                int containerId = buffer.readVarInt();
                int count = buffer.readVarInt();
                var rows = new ArrayList<P2PFrequencyTerminalMenu.Row>(count);
                for (int i = 0; i < count; i++) {
                    rows.add(new P2PFrequencyTerminalMenu.Row(
                            buffer.readBlockPos(),
                            buffer.readByte(),
                            buffer.readShort(),
                            buffer.readBoolean(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf()));
                }
                return new P2PDataPayload(containerId, rows);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(P2PDataPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof P2PFrequencyTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.rows = payload.rows;
        }
    }
}
