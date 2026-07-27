package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.parts.IPartHost;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public record ConfigureMeshPayload(BlockPos pos, byte side, String frequency, byte role, int priority,
        int capabilities) implements CustomPacketPayload {

    public static final Type<ConfigureMeshPayload> TYPE = new Type<>(AE2Logistics.id("configure_mesh"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureMeshPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeByte(payload.side);
                buffer.writeUtf(payload.frequency);
                buffer.writeByte(payload.role);
                buffer.writeVarInt(payload.priority);
                buffer.writeVarInt(payload.capabilities);
            },
            buffer -> new ConfigureMeshPayload(buffer.readBlockPos(), buffer.readByte(), buffer.readUtf(),
                    buffer.readByte(), buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureMeshPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || payload.side < 0 || payload.side >= 6
                || !(player.level().getBlockEntity(payload.pos) instanceof IPartHost host)
                || !(host.getPart(Direction.values()[payload.side]) instanceof MeshEndpointPart endpoint)) {
            return;
        }
        endpoint.applyMeshConfig(
                payload.frequency.trim(),
                (byte) Math.floorMod(payload.role, 3),
                payload.priority,
                payload.capabilities & 63);
    }
}
