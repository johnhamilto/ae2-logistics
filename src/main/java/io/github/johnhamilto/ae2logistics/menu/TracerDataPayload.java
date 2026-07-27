package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public record TracerDataPayload(int containerId, List<TracerTerminalMenu.Entry> entries,
        @Nullable ResourceLocation selected, long[] samples) implements CustomPacketPayload {

    public static final Type<TracerDataPayload> TYPE = new Type<>(AE2Logistics.id("tracer_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TracerDataPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeVarInt(payload.entries.size());
                for (var entry : payload.entries) {
                    buffer.writeResourceLocation(entry.channel());
                    buffer.writeVarLong(entry.value());
                }
                buffer.writeBoolean(payload.selected != null);
                if (payload.selected != null) {
                    buffer.writeResourceLocation(payload.selected);
                }
                buffer.writeVarInt(payload.samples.length);
                for (long sample : payload.samples) {
                    buffer.writeVarLong(sample);
                }
            },
            buffer -> {
                int containerId = buffer.readVarInt();
                int count = buffer.readVarInt();
                var entries = new ArrayList<TracerTerminalMenu.Entry>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(new TracerTerminalMenu.Entry(
                            buffer.readResourceLocation(), buffer.readVarLong()));
                }
                ResourceLocation selected = buffer.readBoolean() ? buffer.readResourceLocation() : null;
                var samples = new long[buffer.readVarInt()];
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = buffer.readVarLong();
                }
                return new TracerDataPayload(containerId, entries, selected, samples);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TracerDataPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof TracerTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.entries = payload.entries;
            menu.clientSelected = payload.selected;
            menu.samples = payload.samples;
        }
    }
}
