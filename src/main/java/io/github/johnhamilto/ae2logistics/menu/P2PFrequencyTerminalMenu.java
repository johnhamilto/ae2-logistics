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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.P2PFrequencyTerminalPart;

public class P2PFrequencyTerminalMenu extends AbstractContainerMenu {

    @Nullable
    private final P2PFrequencyTerminalPart part;
    @Nullable
    private final ServerPlayer serverPlayer;

    public final BlockPos pos;
    public final Direction side;

    private long ticks;

    public record Row(BlockPos pos, byte side, short frequency, boolean output, String itemId, String name,
            String dimension) {
    }

    /** One mesh endpoint, server-wide, for a frequency that touches this grid. */
    public record MeshRow(String frequency, byte side, byte role, int capabilities, boolean sameGrid,
            byte status, BlockPos pos, String dimension) {
    }

    // Client-side state, fed by P2PDataPayload.
    public List<Row> rows = new ArrayList<>();
    public List<MeshRow> meshRows = new ArrayList<>();

    public P2PFrequencyTerminalMenu(int containerId, Inventory inventory, P2PFrequencyTerminalPart part) {
        super(AE2Logistics.P2P_TERMINAL_MENU.get(), containerId);
        this.part = part;
        this.serverPlayer = inventory.player instanceof ServerPlayer sp ? sp : null;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
    }

    public P2PFrequencyTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.P2P_TERMINAL_MENU.get(), containerId);
        this.part = null;
        this.serverPlayer = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (part == null || serverPlayer == null || ticks++ % 20 != 0) {
            return;
        }
        var node = part.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        part.migrateLegacyNames();

        // Names live on the tunnels; a frequency's display name is the first non-blank
        // one among its tunnels, so a tunnel retuned in later still shows the label.
        var names = new java.util.HashMap<Short, String>();
        var tunnels = new ArrayList<P2PTunnelPart<?>>();
        for (var gridNode : node.getGrid().getNodes()) {
            if (gridNode.getOwner() instanceof P2PTunnelPart<?> tunnel) {
                tunnels.add(tunnel);
                var name = io.github.johnhamilto.ae2logistics.parts.P2PNames.nameOn(tunnel);
                if (!name.isBlank()) {
                    names.putIfAbsent(tunnel.getFrequency(), name);
                }
            }
        }

        var collected = new ArrayList<Row>();
        for (var tunnel : tunnels) {
            var host = tunnel.getHost().getBlockEntity();
            var tunnelSide = tunnel.getSide();
            collected.add(new Row(
                    host.getBlockPos(),
                    (byte) (tunnelSide == null ? 6 : tunnelSide.ordinal()),
                    tunnel.getFrequency(),
                    tunnel.isOutput(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(tunnel.getPartItem().asItem()).toString(),
                    names.getOrDefault(tunnel.getFrequency(), ""),
                    host.getLevel() != null ? host.getLevel().dimension().location().toString() : "?"));
        }
        collected.sort(Comparator.comparingInt((Row row) -> row.frequency() & 0xFFFF)
                .thenComparing(Row::output));

        // Frequencies are network-scoped, so list exactly this network's mesh
        // endpoints - the same set AE2's own P2P rows would show.
        var mesh = new ArrayList<MeshRow>();
        var grid = node.getGrid();
        for (var entry : io.github.johnhamilto.ae2logistics.mesh.MeshRegistry.allFrequencies().entrySet()) {
            var endpoints = new ArrayList<>(entry.getValue());
            endpoints.sort(Comparator.comparingLong(
                    io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart::stableKey));
            for (var endpoint : endpoints) {
                var endpointNode = endpoint.getMainNode().getNode();
                if (endpointNode == null || endpointNode.getGrid() != grid) {
                    continue;
                }
                var host = endpoint.getHost().getBlockEntity();
                var endpointSide = endpoint.getSide();
                mesh.add(new MeshRow(
                        entry.getKey(),
                        (byte) (endpointSide == null ? 6 : endpointSide.ordinal()),
                        endpoint.role(),
                        endpoint.capabilityMask(),
                        true,
                        io.github.johnhamilto.ae2logistics.mesh.MeshRegistry.statusOf(endpoint),
                        host.getBlockPos(),
                        host.getLevel() != null ? host.getLevel().dimension().location().toString() : "?"));
            }
        }
        PacketDistributor.sendToPlayer(serverPlayer, new P2PDataPayload(containerId, collected, mesh));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
