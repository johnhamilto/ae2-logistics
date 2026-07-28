package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public record ConfigTerminalDataPayload(int containerId, List<ConfigTerminalMenu.Row> rows,
        int selected, List<ConfigTerminalMenu.SettingLine> settings, boolean hasPriority,
        int priority, String clipboardType, String notice) implements CustomPacketPayload {

    public static final Type<ConfigTerminalDataPayload> TYPE = new Type<>(AE2Logistics.id("config_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigTerminalDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.containerId);
                        buffer.writeVarInt(payload.rows.size());
                        for (var row : payload.rows) {
                            buffer.writeUtf(row.itemId());
                            buffer.writeUtf(row.name());
                            buffer.writeBoolean(row.hasPos());
                            buffer.writeBlockPos(row.pos());
                            buffer.writeUtf(row.dimension());
                            buffer.writeUtf(row.summary());
                            buffer.writeBoolean(row.hasPriority());
                            buffer.writeVarInt(row.priority());
                        }
                        buffer.writeVarInt(payload.selected);
                        buffer.writeVarInt(payload.settings.size());
                        for (var line : payload.settings) {
                            buffer.writeUtf(line.name());
                            buffer.writeUtf(line.value());
                        }
                        buffer.writeBoolean(payload.hasPriority);
                        buffer.writeVarInt(payload.priority);
                        buffer.writeUtf(payload.clipboardType);
                        buffer.writeUtf(payload.notice);
                    },
                    buffer -> {
                        int containerId = buffer.readVarInt();
                        int rowCount = buffer.readVarInt();
                        var rows = new ArrayList<ConfigTerminalMenu.Row>(rowCount);
                        for (int i = 0; i < rowCount; i++) {
                            rows.add(new ConfigTerminalMenu.Row(
                                    buffer.readUtf(), buffer.readUtf(), buffer.readBoolean(),
                                    buffer.readBlockPos(), buffer.readUtf(), buffer.readUtf(),
                                    buffer.readBoolean(), buffer.readVarInt()));
                        }
                        int selected = buffer.readVarInt();
                        int settingCount = buffer.readVarInt();
                        var settings = new ArrayList<ConfigTerminalMenu.SettingLine>(settingCount);
                        for (int i = 0; i < settingCount; i++) {
                            settings.add(new ConfigTerminalMenu.SettingLine(buffer.readUtf(),
                                    buffer.readUtf()));
                        }
                        return new ConfigTerminalDataPayload(containerId, rows, selected, settings,
                                buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(),
                                buffer.readUtf());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigTerminalDataPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof ConfigTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.rows = payload.rows;
            menu.selectedIndex = payload.selected;
            menu.detailSettings = payload.settings;
            menu.detailHasPriority = payload.hasPriority;
            menu.detailPriority = payload.priority;
            menu.clientClipboardType = payload.clipboardType;
            if (!payload.notice.isEmpty()) {
                menu.clientNotice = payload.notice;
            }
        }
    }
}
