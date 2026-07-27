package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.parts.IPartHost;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.JobMonitorPart;

public record ConfigureJobMonitorPayload(BlockPos pos, byte side, String prefix, int stallSeconds)
        implements CustomPacketPayload {

    public static final Type<ConfigureJobMonitorPayload> TYPE = new Type<>(AE2Logistics.id("configure_job_monitor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureJobMonitorPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeByte(payload.side);
                        buffer.writeUtf(payload.prefix);
                        buffer.writeVarInt(payload.stallSeconds);
                    },
                    buffer -> new ConfigureJobMonitorPayload(buffer.readBlockPos(), buffer.readByte(),
                            buffer.readUtf(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureJobMonitorPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || payload.side < 0 || payload.side >= 6
                || !(player.level().getBlockEntity(payload.pos) instanceof IPartHost host)
                || !(host.getPart(Direction.values()[payload.side]) instanceof JobMonitorPart monitor)) {
            return;
        }
        monitor.applyMonitorConfig(payload.prefix, payload.stallSeconds);
    }
}
