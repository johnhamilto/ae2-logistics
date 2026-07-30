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
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.P2PFrequencyTerminalPart;
import io.github.johnhamilto.ae2logistics.parts.P2PNames;

/**
 * Terminal actions. Retune (action 0): pos/side identify the tunnel, value is the new
 * frequency; refuses to create a second input. Rename (action 1): pos/side identify the
 * terminal, name applies to frequency value. Mesh rename (action 2): pos/side identify
 * the terminal, extra is the old mesh frequency, name the new one - retags every loaded
 * endpoint of a frequency that touches the terminal's grid.
 */
public record P2PActionPayload(byte action, BlockPos pos, byte side, short value, String name, String extra)
        implements CustomPacketPayload {

    public static final byte ACTION_RETUNE = 0;
    public static final byte ACTION_RENAME = 1;
    public static final byte ACTION_MESH_RENAME = 2;

    public static final Type<P2PActionPayload> TYPE = new Type<>(AE2Logistics.id("p2p_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, P2PActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeByte(payload.action);
                buffer.writeBlockPos(payload.pos);
                buffer.writeByte(payload.side);
                buffer.writeShort(payload.value);
                buffer.writeUtf(payload.name);
                buffer.writeUtf(payload.extra);
            },
            buffer -> new P2PActionPayload(buffer.readByte(), buffer.readBlockPos(), buffer.readByte(),
                    buffer.readShort(), buffer.readUtf(), buffer.readUtf()));

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

        if (payload.action == ACTION_RENAME || payload.action == ACTION_MESH_RENAME) {
            if (!(part instanceof P2PFrequencyTerminalPart terminal)) {
                return;
            }
            var node = terminal.getMainNode().getNode();
            if (node == null || node.getGrid() == null) {
                return;
            }
            var grid = node.getGrid();
            if (payload.action == ACTION_RENAME) {
                P2PNames.rename(grid, payload.value, payload.name);
                return;
            }
            var newFrequency = payload.name.trim();
            if (newFrequency.isBlank() || payload.extra.isBlank()) {
                return;
            }
            // Frequencies are network-scoped: rename only this network's endpoints.
            MeshRegistry.renameFrequency(payload.extra, newFrequency, grid);
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
        var grid = node.getGrid();
        var service = P2PService.get(grid);
        if (!tunnel.isOutput() && frequency != 0) {
            var existingInput = service.getInput(frequency);
            if (existingInput != null && existingInput != tunnel) {
                return false;
            }
        }
        service.updateFreq(tunnel, frequency);
        // The name belongs to the frequency: drop the old one first (so it cannot be
        // resolved back from this tunnel's own attachment), then adopt the new one's.
        P2PNames.write(tunnel, "");
        P2PNames.write(tunnel, P2PNames.resolve(grid, frequency));
        return true;
    }
}
