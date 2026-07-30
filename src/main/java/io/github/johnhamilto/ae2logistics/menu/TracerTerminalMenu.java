package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.AEBaseMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.TracerTerminalPart;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

public class TracerTerminalMenu extends AEBaseMenu {

    @Nullable
    private final TracerTerminalPart part;
    @Nullable
    private final ServerPlayer serverPlayer;

    public final BlockPos pos;
    public final Direction side;

    @Nullable
    private ResourceLocation selected;
    private long ticks;

    // Client-side state, fed by TracerDataPayload.
    public record Entry(ResourceLocation channel, long value) {
    }

    public List<Entry> entries = new ArrayList<>();
    @Nullable
    public ResourceLocation clientSelected;
    public long[] samples = new long[0];

    public TracerTerminalMenu(int containerId, Inventory inventory, TracerTerminalPart part) {
        super(AE2Logistics.TRACER_TERMINAL_MENU.get(), containerId, inventory, part);
        this.part = part;
        this.serverPlayer = inventory.player instanceof ServerPlayer sp ? sp : null;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
    }

    public TracerTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.TRACER_TERMINAL_MENU.get(), containerId, inventory, null);
        this.part = null;
        this.serverPlayer = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
    }

    public void setSelected(@Nullable ResourceLocation channel) {
        this.selected = channel;
        this.ticks = 0;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (part == null || serverPlayer == null) {
            return;
        }
        if (ticks++ % SignalService.SAMPLE_INTERVAL_TICKS != 0) {
            return;
        }
        var service = part.service();
        if (service == null) {
            return;
        }

        var channels = new ArrayList<>(service.committed().entrySet());
        channels.sort(java.util.Map.Entry.comparingByKey());
        var entries = new ArrayList<Entry>(channels.size());
        for (var entry : channels) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        var samples = selected != null ? service.history(selected) : new long[0];
        PacketDistributor.sendToPlayer(serverPlayer,
                new TracerDataPayload(containerId, entries, selected, samples));
    }

}
