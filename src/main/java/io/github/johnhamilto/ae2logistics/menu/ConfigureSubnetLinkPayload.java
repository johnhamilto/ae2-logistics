package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.parts.PartHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart;

public record ConfigureSubnetLinkPayload(BlockPos pos, byte side, byte mode, int priority)
        implements CustomPacketPayload {

    public static final Type<ConfigureSubnetLinkPayload> TYPE =
            new Type<>(AE2Logistics.id("configure_subnet_link"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureSubnetLinkPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeByte(payload.side);
                        buffer.writeByte(payload.mode);
                        buffer.writeVarInt(payload.priority);
                    },
                    buffer -> new ConfigureSubnetLinkPayload(buffer.readBlockPos(), buffer.readByte(),
                            buffer.readByte(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureSubnetLinkPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.pos.distToCenterSqr(player.position()) > 1024) {
            return;
        }
        var direction = payload.side >= 0 && payload.side < 6 ? Direction.values()[payload.side] : null;
        if (PartHelper.getPart(player.level(), payload.pos, direction) instanceof SubnetLinkPart part) {
            part.applyConfig(payload.mode, payload.priority);
        }
    }
}
