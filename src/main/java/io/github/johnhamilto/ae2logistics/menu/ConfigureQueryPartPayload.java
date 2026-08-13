package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.parts.IPartHost;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.QueryExportBusPart;
import io.github.johnhamilto.ae2logistics.parts.QuerySensorPart;

/** Applies channel/expression config to a query sensor or query export bus. */
public record ConfigureQueryPartPayload(BlockPos pos, byte side, String channel, String source)
        implements CustomPacketPayload {

    public static final Type<ConfigureQueryPartPayload> TYPE = new Type<>(AE2Logistics.id("configure_query_part"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureQueryPartPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeByte(payload.side);
                        buffer.writeUtf(payload.channel);
                        buffer.writeUtf(payload.source);
                    },
                    buffer -> new ConfigureQueryPartPayload(buffer.readBlockPos(), buffer.readByte(),
                            buffer.readUtf(), buffer.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureQueryPartPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || payload.side < 0 || payload.side >= 6
                || !(player.level().getBlockEntity(payload.pos) instanceof IPartHost host)) {
            return;
        }
        var part = host.getPart(Direction.values()[payload.side]);
        if (part instanceof QuerySensorPart sensor) {
            var channelText = payload.channel.trim();
            sensor.applySensorConfig(
                    channelText.isEmpty() ? null : Identifier.tryParse(channelText),
                    payload.source);
        } else if (part instanceof QueryExportBusPart bus) {
            bus.applyBusConfig(payload.source);
        }
    }
}
