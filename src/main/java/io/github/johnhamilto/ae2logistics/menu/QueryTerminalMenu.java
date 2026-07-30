package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;

import appeng.menu.AEBaseMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.QueryTerminalPart;
import io.github.johnhamilto.ae2logistics.query.QueryParser;
import io.github.johnhamilto.ae2logistics.query.QueryService;

public class QueryTerminalMenu extends AEBaseMenu {

    public static final int PREVIEW_ROWS = 6;

    @Nullable
    private final QueryTerminalPart part;
    @Nullable
    private final ServerPlayer serverPlayer;

    public final BlockPos pos;
    public final Direction side;

    // Client-side state.
    public Map<String, String> library = new LinkedHashMap<>();
    public List<ItemStack> previewStacks = new ArrayList<>();
    public List<Long> previewAmounts = new ArrayList<>();
    public long previewTotal;
    public int previewMatches;
    public String previewError = "";

    // Server-side state.
    private String requestedSource = "";
    private long ticks;

    public QueryTerminalMenu(int containerId, Inventory inventory, QueryTerminalPart part) {
        super(AE2Logistics.QUERY_TERMINAL_MENU.get(), containerId, inventory, part);
        this.part = part;
        this.serverPlayer = inventory.player instanceof ServerPlayer sp ? sp : null;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
    }

    public QueryTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.QUERY_TERMINAL_MENU.get(), containerId, inventory, null);
        this.part = null;
        this.serverPlayer = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            library.put(buffer.readUtf(), buffer.readUtf());
        }
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, QueryTerminalPart part) {
        var host = part.getHost().getBlockEntity();
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeByte(part.getSide().ordinal());
        var node = part.getMainNode().getNode();
        var service = node != null && node.getGrid() != null
                ? node.getGrid().getService(QueryService.class)
                : null;
        var library = service != null ? service.library() : part.savedQueries();
        buffer.writeVarInt(library.size());
        for (var entry : library.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public void setRequestedSource(String source) {
        this.requestedSource = source;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (part == null || serverPlayer == null || ticks++ % 20 != 0 || requestedSource.isBlank()) {
            return;
        }
        var node = part.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        var service = node.getGrid().getService(QueryService.class);

        var parsed = QueryParser.parse(requestedSource);
        if (!parsed.ok()) {
            PacketDistributor.sendToPlayer(serverPlayer, new QueryPreviewPayload(containerId,
                    parsed.error() == null ? "syntax error" : parsed.error(),
                    0, 0, List.of(), List.of(), service.library()));
            return;
        }
        var query = service.compiled(requestedSource);
        var context = service.context();
        var stacks = context.stacks();

        int matches = 0;
        long total = 0;
        var displayStacks = new ArrayList<ItemStack>(PREVIEW_ROWS);
        var displayAmounts = new ArrayList<Long>(PREVIEW_ROWS);
        if (query != null && stacks != null) {
            for (var entry : stacks) {
                if (entry.getLongValue() <= 0
                        || !io.github.johnhamilto.ae2logistics.query.CompiledQuery
                                .isQueryableKey(entry.getKey())
                        || !query.matches(entry.getKey(), context)) {
                    continue;
                }
                matches++;
                total = io.github.johnhamilto.ae2logistics.signal.SignalMath.add(total,
                        entry.getLongValue());
                if (displayStacks.size() < PREVIEW_ROWS) {
                    displayStacks.add(displayStack(entry.getKey()));
                    displayAmounts.add(entry.getLongValue());
                }
            }
        }
        PacketDistributor.sendToPlayer(serverPlayer, new QueryPreviewPayload(containerId,
                "", matches, total, displayStacks, displayAmounts, service.library()));
    }

    private static ItemStack displayStack(appeng.api.stacks.AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return itemKey.toStack();
        }
        if (key instanceof AEFluidKey fluidKey
                && fluidKey.getFluid().getBucket() != net.minecraft.world.item.Items.AIR) {
            return new ItemStack(fluidKey.getFluid().getBucket());
        }
        return ItemStack.EMPTY;
    }

}
