package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public record SelectTracerChannelPayload(int containerId, String channel) implements CustomPacketPayload {

    public static final Type<SelectTracerChannelPayload> TYPE = new Type<>(AE2Logistics.id("select_tracer_channel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectTracerChannelPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeUtf(payload.channel);
            },
            buffer -> new SelectTracerChannelPayload(buffer.readVarInt(), buffer.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectTracerChannelPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof TracerTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.setSelected(payload.channel.isBlank() ? null : ResourceLocation.tryParse(payload.channel));
        }
    }
}
