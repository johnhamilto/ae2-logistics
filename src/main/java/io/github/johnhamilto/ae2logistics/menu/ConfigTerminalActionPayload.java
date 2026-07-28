package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public record ConfigTerminalActionPayload(int containerId, byte action, int index, String text, long value)
        implements CustomPacketPayload {

    public static final byte ACTION_REFRESH = 0;
    public static final byte ACTION_SELECT = 1;
    public static final byte ACTION_CYCLE = 2;
    public static final byte ACTION_SET_PRIORITY = 3;
    public static final byte ACTION_COPY = 4;
    public static final byte ACTION_PASTE = 5;
    public static final byte ACTION_PASTE_ALL = 6;
    public static final byte ACTION_SNAPSHOT = 7;

    public static final Type<ConfigTerminalActionPayload> TYPE = new Type<>(AE2Logistics.id("config_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigTerminalActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.containerId);
                        buffer.writeByte(payload.action);
                        buffer.writeVarInt(payload.index);
                        buffer.writeUtf(payload.text);
                        buffer.writeLong(payload.value);
                    },
                    buffer -> new ConfigTerminalActionPayload(buffer.readVarInt(), buffer.readByte(),
                            buffer.readVarInt(), buffer.readUtf(), buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigTerminalActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ConfigTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.handleAction(player, payload.action, payload.index, payload.text, payload.value);
        }
    }
}
