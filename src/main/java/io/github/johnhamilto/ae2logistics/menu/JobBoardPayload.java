package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Server-to-client board refresh for an open Job Monitor window: one row per
 * crafting CPU, streamed whenever a job starts, progresses, stalls, or a CPU is
 * renamed - the board tracks the network live instead of staying an open-time
 * snapshot.
 */
public record JobBoardPayload(BlockPos pos, byte side, List<JobMonitorMenu.JobRow> rows)
        implements CustomPacketPayload {

    public static final Type<JobBoardPayload> TYPE = new Type<>(AE2Logistics.id("job_board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobBoardPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeByte(payload.side);
                buffer.writeVarInt(payload.rows.size());
                for (var row : payload.rows) {
                    JobMonitorMenu.JobRow.write(buffer, row);
                }
            },
            buffer -> {
                var pos = buffer.readBlockPos();
                var side = buffer.readByte();
                int count = buffer.readVarInt();
                var rows = new ArrayList<JobMonitorMenu.JobRow>(count);
                for (int i = 0; i < count; i++) {
                    rows.add(JobMonitorMenu.JobRow.read(buffer));
                }
                return new JobBoardPayload(pos, side, List.copyOf(rows));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JobBoardPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof JobMonitorMenu menu
                && menu.matches(payload.pos, payload.side)) {
            menu.updateBoard(payload.rows);
        }
    }
}
