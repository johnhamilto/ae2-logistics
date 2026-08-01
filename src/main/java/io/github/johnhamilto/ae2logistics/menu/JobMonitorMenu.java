package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.JobMonitorPart;

public class JobMonitorMenu extends AEBaseMenu {

    /** How many board rows travel per sync; the GUI scrolls through them. */
    private static final int BOARD_LIMIT = 64;

    @Nullable
    private final JobMonitorPart part;
    @Nullable
    private final ServerPlayer serverPlayer;

    public final BlockPos pos;
    public final Direction side;
    public final String prefix;
    public final int stallSeconds;

    private int activeValue;
    private int stalledValue;
    private int pendingValue;

    /** One CPU per row; stalled jobs sort first, then running by remaining, then idle. */
    public record JobRow(String cpuName, ItemStack output, long remaining, boolean stalled,
            boolean busy) {

        public static void write(RegistryFriendlyByteBuf buffer, JobRow row) {
            buffer.writeUtf(row.cpuName);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, row.output);
            buffer.writeVarLong(row.remaining);
            buffer.writeBoolean(row.stalled);
            buffer.writeBoolean(row.busy);
        }

        public static JobRow read(RegistryFriendlyByteBuf buffer) {
            return new JobRow(buffer.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readVarLong(), buffer.readBoolean(), buffer.readBoolean());
        }
    }

    /** Live board state: streamed by the server, re-pushed whenever a row changes. */
    private List<JobRow> board = List.of();
    @Nullable
    private List<JobRow> lastSentBoard;
    private int boardTicks;

    public JobMonitorMenu(int containerId, Inventory inventory, JobMonitorPart part) {
        super(AE2Logistics.JOB_MONITOR_MENU.get(), containerId, inventory, part);
        this.part = part;
        this.serverPlayer = inventory.player instanceof ServerPlayer sp ? sp : null;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
        this.prefix = part.prefix();
        this.stallSeconds = part.stallSeconds();
        addLiveSlots();
    }

    public JobMonitorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.JOB_MONITOR_MENU.get(), containerId, inventory, null);
        this.part = null;
        this.serverPlayer = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        this.prefix = buffer.readUtf();
        this.stallSeconds = buffer.readVarInt();
        int count = buffer.readVarInt();
        var rows = new ArrayList<JobRow>(count);
        for (int i = 0; i < count; i++) {
            rows.add(JobRow.read(buffer));
        }
        this.board = List.copyOf(rows);
        addLiveSlots();
    }

    private void addLiveSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return part != null ? clamp(part.channelValue("active")) : activeValue;
            }

            @Override
            public void set(int value) {
                activeValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return part != null ? clamp(part.channelValue("stalled")) : stalledValue;
            }

            @Override
            public void set(int value) {
                stalledValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return part != null ? clamp(part.channelValue("pending")) : pendingValue;
            }

            @Override
            public void set(int value) {
                pendingValue = value;
            }
        });
    }

    private static int clamp(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, JobMonitorPart part) {
        var host = part.getHost().getBlockEntity();
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeByte(part.getSide().ordinal());
        buffer.writeUtf(part.prefix());
        buffer.writeVarInt(part.stallSeconds());
        var rows = buildBoard(part);
        buffer.writeVarInt(rows.size());
        for (var row : rows) {
            JobRow.write(buffer, row);
        }
    }

    /** Server-side board snapshot: one row per crafting CPU, worst news first. */
    public static List<JobRow> buildBoard(JobMonitorPart part) {
        var node = part.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return List.of();
        }
        var rows = new ArrayList<JobRow>();
        for (var cpu : node.getGrid().getCraftingService().getCpus()) {
            var name = cpu.getName() != null ? cpu.getName().getString() : "";
            var status = cpu.getJobStatus();
            if (status != null) {
                long remaining = Math.max(0, status.totalItems() - status.progress());
                var crafting = status.crafting();
                rows.add(new JobRow(name,
                        crafting != null ? GenericStack.wrapInItemStack(crafting) : ItemStack.EMPTY,
                        remaining, part.isStalledForDisplay(cpu), true));
            } else {
                rows.add(new JobRow(name, ItemStack.EMPTY, 0, false, false));
            }
        }
        rows.sort(Comparator.comparing((JobRow row) -> !row.stalled())
                .thenComparing(row -> !row.busy())
                .thenComparing(Comparator.comparingLong(JobRow::remaining).reversed())
                .thenComparing(JobRow::cpuName));
        return rows.size() <= BOARD_LIMIT ? rows : List.copyOf(rows.subList(0, BOARD_LIMIT));
    }

    /**
     * Live board streaming, the mesh-roster pattern: every half second rebuild and
     * re-push when anything changed - jobs starting, progressing, stalling, CPUs
     * renamed - so the open board tracks the network without reopening.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (part == null || serverPlayer == null || ++boardTicks % 10 != 0) {
            return;
        }
        var built = buildBoard(part);
        if (boardEquals(built, lastSentBoard)) {
            return;
        }
        lastSentBoard = built;
        PacketDistributor.sendToPlayer(serverPlayer,
                new JobBoardPayload(pos, (byte) side.ordinal(), built));
    }

    private static boolean boardEquals(List<JobRow> a, @Nullable List<JobRow> b) {
        if (b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            var fresh = a.get(i);
            var sent = b.get(i);
            if (!fresh.cpuName().equals(sent.cpuName()) || fresh.remaining() != sent.remaining()
                    || fresh.stalled() != sent.stalled() || fresh.busy() != sent.busy()
                    || !ItemStack.matches(fresh.output(), sent.output())) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(BlockPos payloadPos, byte payloadSide) {
        return pos.equals(payloadPos) && side.ordinal() == payloadSide;
    }

    /** Applied client-side when the server streams a fresh board. */
    public void updateBoard(List<JobRow> rows) {
        this.board = List.copyOf(rows);
    }

    public List<JobRow> board() {
        return board;
    }

    public int activeJobs() {
        return activeValue;
    }

    public int stalledJobs() {
        return stalledValue;
    }

    public int pendingItems() {
        return pendingValue;
    }
}
