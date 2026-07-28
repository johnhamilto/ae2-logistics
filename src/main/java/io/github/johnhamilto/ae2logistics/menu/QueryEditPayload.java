package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.parts.IPartHost;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.QueryTerminalPart;
import io.github.johnhamilto.ae2logistics.query.QueryService;

/** Save (or delete) a named query; also carries live preview requests. */
public record QueryEditPayload(BlockPos pos, byte side, byte action, String name, String source)
        implements CustomPacketPayload {

    public static final byte ACTION_SAVE = 0;
    public static final byte ACTION_DELETE = 1;
    public static final byte ACTION_PREVIEW = 2;

    public static final Type<QueryEditPayload> TYPE = new Type<>(AE2Logistics.id("query_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QueryEditPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeByte(payload.side);
                        buffer.writeByte(payload.action);
                        buffer.writeUtf(payload.name);
                        buffer.writeUtf(payload.source);
                    },
                    buffer -> new QueryEditPayload(buffer.readBlockPos(), buffer.readByte(),
                            buffer.readByte(), buffer.readUtf(), buffer.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QueryEditPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || payload.side < 0 || payload.side >= 6
                || !(player.level().getBlockEntity(payload.pos) instanceof IPartHost host)
                || !(host.getPart(Direction.values()[payload.side]) instanceof QueryTerminalPart terminal)) {
            return;
        }
        if (payload.action == ACTION_PREVIEW) {
            if (player.containerMenu instanceof QueryTerminalMenu menu) {
                menu.setRequestedSource(payload.source);
            }
            return;
        }
        var node = terminal.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        var service = node.getGrid().getService(QueryService.class);
        if (payload.action == ACTION_SAVE && !payload.name.isBlank() && !payload.source.isBlank()) {
            service.put(payload.name, payload.source);
        } else if (payload.action == ACTION_DELETE) {
            service.remove(payload.name);
        }
    }
}
