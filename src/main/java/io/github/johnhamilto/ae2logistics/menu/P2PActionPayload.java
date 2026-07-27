package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.parts.PartHelper;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.P2PFrequencyTerminalPart;

/**
 * Retune a tunnel (action 0: pos/side identify the tunnel, value is the new frequency)
 * or rename a frequency (action 1: pos/side identify the terminal, name applies to
 * value). Retuning refuses to create a second input on a frequency.
 */
public record P2PActionPayload(byte action, BlockPos pos, byte side, short value, String name)
        implements CustomPacketPayload {

    public static final byte ACTION_RETUNE = 0;
    public static final byte ACTION_RENAME = 1;

    public static final Type<P2PActionPayload> TYPE = new Type<>(AE2Logistics.id("p2p_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, P2PActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeByte(payload.action);
                buffer.writeBlockPos(payload.pos);
                buffer.writeByte(payload.side);
                buffer.writeShort(payload.value);
                buffer.writeUtf(payload.name);
            },
            buffer -> new P2PActionPayload(buffer.readByte(), buffer.readBlockPos(), buffer.readByte(),
                    buffer.readShort(), buffer.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(P2PActionPayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player.level() instanceof ServerLevel level)
                || payload.pos.distToCenterSqr(player.position()) > 1024) {
            return;
        }
        var direction = payload.side >= 0 && payload.side < 6 ? Direction.values()[payload.side] : null;
        var part = PartHelper.getPart(level, payload.pos, direction);

        if (payload.action == ACTION_RENAME) {
            if (part instanceof P2PFrequencyTerminalPart terminal) {
                terminal.setName(payload.value, payload.name);
            }
            return;
        }

        if (part instanceof P2PTunnelPart<?> tunnel) {
            retune(tunnel, payload.value);
        }
    }

    /** Applies the new frequency unless it would create a second input on that frequency. */
    public static boolean retune(P2PTunnelPart<?> tunnel, short frequency) {
        var node = tunnel.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return false;
        }
        var service = P2PService.get(node.getGrid());
        if (!tunnel.isOutput() && frequency != 0) {
            var existingInput = service.getInput(frequency);
            if (existingInput != null && existingInput != tunnel) {
                return false;
            }
        }
        service.updateFreq(tunnel, frequency);
        return true;
    }
}
