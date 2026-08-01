package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.StorageJanitorBlockEntity;

/** Start/stop for the Storage Janitor's GUI button. */
public record JanitorTogglePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<JanitorTogglePayload> TYPE = new Type<>(AE2Logistics.id("janitor_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JanitorTogglePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBlockPos(payload.pos),
                    buffer -> new JanitorTogglePayload(buffer.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JanitorTogglePayload payload, IPayloadContext context) {
        var player = context.player();
        if (player.level() instanceof ServerLevel level
                && payload.pos.distToCenterSqr(player.position()) <= 1024
                && level.getBlockEntity(payload.pos) instanceof StorageJanitorBlockEntity janitor) {
            janitor.toggle();
        }
    }
}
