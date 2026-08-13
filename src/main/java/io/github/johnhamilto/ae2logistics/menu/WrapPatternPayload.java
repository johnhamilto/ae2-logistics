package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.crafting.PatternDetailsHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.PatternWorkbenchBlockEntity;
import io.github.johnhamilto.ae2logistics.crafting.GuardedPattern;

/** Wraps the workbench's pattern behind a signal guard, or unwraps it again. */
public record WrapPatternPayload(BlockPos pos, byte action, String channel, int op, long value)
        implements CustomPacketPayload {

    public static final byte ACTION_WRAP = 0;
    public static final byte ACTION_UNWRAP = 1;

    public static final Type<WrapPatternPayload> TYPE = new Type<>(AE2Logistics.id("wrap_pattern"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WrapPatternPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeByte(payload.action);
                buffer.writeUtf(payload.channel);
                buffer.writeVarInt(payload.op);
                buffer.writeLong(payload.value);
            },
            buffer -> new WrapPatternPayload(buffer.readBlockPos(), buffer.readByte(), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WrapPatternPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || !(player.level().getBlockEntity(payload.pos) instanceof PatternWorkbenchBlockEntity workbench)) {
            return;
        }
        var inventory = workbench.inventory();
        var stack = inventory.getItem(0);
        if (stack.isEmpty()) {
            return;
        }

        if (payload.action == ACTION_WRAP) {
            var channel = Identifier.tryParse(payload.channel.trim());
            if (channel == null || stack.is(AE2Logistics.GUARDED_PATTERN.get())
                    || !PatternDetailsHelper.isEncodedPattern(stack)) {
                return;
            }
            inventory.setItem(0, GuardedPattern.wrap(stack, channel, payload.op, payload.value));
        } else if (payload.action == ACTION_UNWRAP && stack.is(AE2Logistics.GUARDED_PATTERN.get())) {
            var inner = GuardedPattern.unwrap(stack);
            if (inner != null) {
                inventory.setItem(0, inner);
            }
        }
    }
}
