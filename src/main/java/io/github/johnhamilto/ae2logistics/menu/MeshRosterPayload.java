package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Server-to-client roster refresh for an open mesh endpoint window: sent after the
 * server applies a config edit, so the Linked Endpoints list follows frequency, role,
 * and priority changes live instead of staying an open-time snapshot.
 */
public record MeshRosterPayload(BlockPos pos, byte side, int total,
        List<MeshEndpointMenu.EndpointInfo> rows) implements CustomPacketPayload {

    public static final Type<MeshRosterPayload> TYPE = new Type<>(AE2Logistics.id("mesh_roster"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MeshRosterPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeByte(payload.side);
                buffer.writeVarInt(payload.total);
                buffer.writeVarInt(payload.rows.size());
                for (var row : payload.rows) {
                    MeshEndpointMenu.EndpointInfo.write(buffer, row);
                }
            },
            buffer -> {
                var pos = buffer.readBlockPos();
                var side = buffer.readByte();
                var total = buffer.readVarInt();
                int count = buffer.readVarInt();
                var rows = new ArrayList<MeshEndpointMenu.EndpointInfo>(count);
                for (int i = 0; i < count; i++) {
                    rows.add(MeshEndpointMenu.EndpointInfo.read(buffer));
                }
                return new MeshRosterPayload(pos, side, total, List.copyOf(rows));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MeshRosterPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof MeshEndpointMenu menu
                && menu.matches(payload.pos, payload.side)) {
            menu.updateRoster(payload.rows, payload.total);
        }
    }
}
