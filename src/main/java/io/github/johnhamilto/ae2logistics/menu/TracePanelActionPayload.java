package io.github.johnhamilto.ae2logistics.menu;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity;

/** Trace panel GUI actions: remove one bound channel, or clear them all (empty). */
public record TracePanelActionPayload(BlockPos pos, Optional<Identifier> channel)
        implements CustomPacketPayload {

    public static final Type<TracePanelActionPayload> TYPE =
            new Type<>(AE2Logistics.id("trace_panel_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TracePanelActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeOptional(payload.channel,
                                (b, id) -> ((RegistryFriendlyByteBuf) b).writeIdentifier(id));
                    },
                    buffer -> new TracePanelActionPayload(buffer.readBlockPos(),
                            buffer.readOptional(b -> ((RegistryFriendlyByteBuf) b).readIdentifier())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TracePanelActionPayload payload, IPayloadContext context) {
        var player = context.player();
        if (player.level() instanceof ServerLevel level
                && payload.pos.distToCenterSqr(player.position()) <= 1024
                && level.getBlockEntity(payload.pos) instanceof TracePanelBlockEntity panel) {
            payload.channel.ifPresentOrElse(
                    channel -> panel.bind(channel, true),
                    panel::clearBindings);
        }
    }
}
