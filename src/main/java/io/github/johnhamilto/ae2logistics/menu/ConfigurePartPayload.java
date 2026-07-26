package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.parts.IPartHost;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;

public record ConfigurePartPayload(BlockPos pos, Direction side, String outChannel, String inA,
        String inB, int op, long valueA, long valueB, boolean flag) implements CustomPacketPayload {

    public static final Type<ConfigurePartPayload> TYPE = new Type<>(AE2Logistics.id("configure_part"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigurePartPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeByte(payload.side.ordinal());
                buffer.writeUtf(payload.outChannel);
                buffer.writeUtf(payload.inA);
                buffer.writeUtf(payload.inB);
                buffer.writeVarInt(payload.op);
                buffer.writeLong(payload.valueA);
                buffer.writeLong(payload.valueB);
                buffer.writeBoolean(payload.flag);
            },
            buffer -> new ConfigurePartPayload(
                    buffer.readBlockPos(),
                    Direction.values()[buffer.readByte()],
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigurePartPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || !(level.getBlockEntity(payload.pos) instanceof IPartHost host)
                || !(host.getPart(payload.side) instanceof LogicPart part)) {
            return;
        }
        part.applyConfig(
                parse(payload.outChannel),
                parse(payload.inA),
                parse(payload.inB),
                payload.op,
                Math.max(0, payload.valueA),
                Math.max(0, payload.valueB),
                payload.flag);
    }

    private static ResourceLocation parse(String text) {
        return text.isBlank() ? null : ResourceLocation.tryParse(text.trim());
    }
}
