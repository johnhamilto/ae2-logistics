package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.parts.PartHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.P2PFrequencyTerminalPart;

/**
 * Terminal-driven mesh retune: move one endpoint of {@code from} onto {@code to}.
 * The TERMINAL (pos/side) anchors authorization and supplies the carrier grid - the
 * endpoint itself may be anywhere on that network, including another dimension, so
 * it is identified by position + side + level id among the frequency's loaded
 * endpoints rather than resolved through the player's world.
 */
public record MeshRetunePayload(BlockPos terminalPos, byte terminalSide, String from,
        BlockPos endpointPos, byte endpointSide, String dimension, String to)
        implements CustomPacketPayload {

    public static final Type<MeshRetunePayload> TYPE = new Type<>(AE2Logistics.id("mesh_retune"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MeshRetunePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.terminalPos);
                        buffer.writeByte(payload.terminalSide);
                        buffer.writeUtf(payload.from);
                        buffer.writeBlockPos(payload.endpointPos);
                        buffer.writeByte(payload.endpointSide);
                        buffer.writeUtf(payload.dimension);
                        buffer.writeUtf(payload.to);
                    },
                    buffer -> new MeshRetunePayload(buffer.readBlockPos(), buffer.readByte(),
                            buffer.readUtf(), buffer.readBlockPos(), buffer.readByte(),
                            buffer.readUtf(), buffer.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MeshRetunePayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player.level() instanceof ServerLevel level)
                || payload.terminalPos.distToCenterSqr(player.position()) > 1024) {
            return;
        }
        var direction = payload.terminalSide >= 0 && payload.terminalSide < 6
                ? Direction.values()[payload.terminalSide] : null;
        if (!(PartHelper.getPart(level, payload.terminalPos, direction)
                instanceof P2PFrequencyTerminalPart terminal)) {
            return;
        }
        var node = terminal.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        MeshRegistry.retuneEndpoint(node.getGrid(), payload.from, payload.endpointPos,
                payload.endpointSide, payload.dimension, payload.to);
    }
}
