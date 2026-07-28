package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/** Server -> client: live results for the expression being edited, plus the library. */
public record QueryPreviewPayload(int containerId, String error, int matches, long total,
        List<ItemStack> stacks, List<Long> amounts, Map<String, String> library)
        implements CustomPacketPayload {

    public static final Type<QueryPreviewPayload> TYPE = new Type<>(AE2Logistics.id("query_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QueryPreviewPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.containerId);
                        buffer.writeUtf(payload.error);
                        buffer.writeVarInt(payload.matches);
                        buffer.writeVarLong(payload.total);
                        buffer.writeVarInt(payload.stacks.size());
                        for (int i = 0; i < payload.stacks.size(); i++) {
                            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.stacks.get(i));
                            buffer.writeVarLong(payload.amounts.get(i));
                        }
                        buffer.writeVarInt(payload.library.size());
                        for (var entry : payload.library.entrySet()) {
                            buffer.writeUtf(entry.getKey());
                            buffer.writeUtf(entry.getValue());
                        }
                    },
                    buffer -> {
                        int containerId = buffer.readVarInt();
                        var error = buffer.readUtf();
                        int matches = buffer.readVarInt();
                        long total = buffer.readVarLong();
                        int rows = buffer.readVarInt();
                        var stacks = new ArrayList<ItemStack>(rows);
                        var amounts = new ArrayList<Long>(rows);
                        for (int i = 0; i < rows; i++) {
                            stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                            amounts.add(buffer.readVarLong());
                        }
                        int entries = buffer.readVarInt();
                        var library = new LinkedHashMap<String, String>();
                        for (int i = 0; i < entries; i++) {
                            library.put(buffer.readUtf(), buffer.readUtf());
                        }
                        return new QueryPreviewPayload(containerId, error, matches, total, stacks,
                                amounts, library);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QueryPreviewPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof QueryTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.previewError = payload.error;
            menu.previewMatches = payload.matches;
            menu.previewTotal = payload.total;
            menu.previewStacks = payload.stacks;
            menu.previewAmounts = payload.amounts;
            menu.library = payload.library;
        }
    }
}
