package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

import appeng.menu.AEBaseMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.JobMonitorPart;

public class JobMonitorMenu extends AEBaseMenu {

    @Nullable
    private final JobMonitorPart part;

    public final BlockPos pos;
    public final Direction side;
    public final String prefix;
    public final int stallSeconds;

    private int activeValue;
    private int stalledValue;
    private int pendingValue;

    public JobMonitorMenu(int containerId, Inventory inventory, JobMonitorPart part) {
        super(AE2Logistics.JOB_MONITOR_MENU.get(), containerId, inventory, part);
        this.part = part;
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
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        this.prefix = buffer.readUtf();
        this.stallSeconds = buffer.readVarInt();
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
