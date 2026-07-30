package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Sets a ghost/filter slot from a dragged ingredient (JEI ghost drag) instead of the
 * carried stack. Menus opt in via {@link GhostSlotTarget}.
 */
public record GhostSlotPayload(int containerId, int slotIndex, ItemStack stack)
        implements CustomPacketPayload {

    public interface GhostSlotTarget {
        boolean acceptsGhost(int slotIndex);

        void setGhost(int slotIndex, ItemStack stack);
    }

    public static final Type<GhostSlotPayload> TYPE = new Type<>(AE2Logistics.id("ghost_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GhostSlotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeVarInt(payload.slotIndex);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.stack);
            },
            buffer -> new GhostSlotPayload(buffer.readVarInt(), buffer.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GhostSlotPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof GhostSlotTarget target
                && context.player().containerMenu.containerId == payload.containerId
                && target.acceptsGhost(payload.slotIndex)) {
            var stack = payload.stack.copy();
            if (!stack.isEmpty()) {
                stack.setCount(1);
            }
            target.setGhost(payload.slotIndex, stack);
        }
    }
}
