package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity;

public record ConfigureGuardPayload(BlockPos pos, String guardChannel, int guardOp, long guardValue,
        boolean gateExecution, String priorityChannel, int basePriority) implements CustomPacketPayload {

    public static final Type<ConfigureGuardPayload> TYPE = new Type<>(AE2Logistics.id("configure_guard"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureGuardPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeUtf(payload.guardChannel);
                        buffer.writeVarInt(payload.guardOp);
                        buffer.writeLong(payload.guardValue);
                        buffer.writeBoolean(payload.gateExecution);
                        buffer.writeUtf(payload.priorityChannel);
                        buffer.writeVarInt(payload.basePriority);
                    },
                    buffer -> new ConfigureGuardPayload(buffer.readBlockPos(), buffer.readUtf(),
                            buffer.readVarInt(), buffer.readLong(), buffer.readBoolean(), buffer.readUtf(),
                            buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureGuardPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || !(player.level().getBlockEntity(payload.pos) instanceof GuardedPatternProviderBlockEntity provider)) {
            return;
        }
        var channelText = payload.guardChannel.trim();
        var priorityText = payload.priorityChannel.trim();
        provider.applyGuardConfig(
                channelText.isEmpty() ? null : ResourceLocation.tryParse(channelText),
                payload.guardOp,
                payload.guardValue,
                payload.gateExecution,
                priorityText.isEmpty() ? null : ResourceLocation.tryParse(priorityText),
                payload.basePriority);
    }
}
