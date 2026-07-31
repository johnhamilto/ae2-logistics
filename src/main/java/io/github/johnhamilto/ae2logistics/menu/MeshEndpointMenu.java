package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.AEBaseMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public class MeshEndpointMenu extends AEBaseMenu {

    /** How many roster rows travel per sync; the GUI scrolls through them. */
    private static final int ROSTER_LIMIT = 64;

    @Nullable
    private final MeshEndpointPart part;
    @Nullable
    private final ServerPlayer serverPlayer;

    /** Live-stream state: the roster as last pushed to this menu's player. */
    @Nullable
    private List<EndpointInfo> lastSentRows;
    private int lastSentTotal;
    private int rosterTicks;

    public final BlockPos pos;
    public final Direction side;
    public final String frequency;
    public final byte role;
    public final int priority;
    public final int capabilities;
    public final boolean capabilitiesLocked;

    /** Same-network endpoints on this frequency; re-pushed by the server on config edits. */
    private List<EndpointInfo> roster = List.of();
    private int rosterTotal;

    /** One roster row; {@code connected} is the block on the endpoint's face, as an item. */
    public record EndpointInfo(BlockPos pos, byte role, int priority, byte status, byte meState,
            boolean self, ItemStack connected) {

        public static void write(RegistryFriendlyByteBuf buffer, EndpointInfo info) {
            buffer.writeBlockPos(info.pos);
            buffer.writeByte(info.role);
            buffer.writeVarInt(info.priority);
            buffer.writeByte(info.status);
            buffer.writeByte(info.meState);
            buffer.writeBoolean(info.self);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, info.connected);
        }

        public static EndpointInfo read(RegistryFriendlyByteBuf buffer) {
            return new EndpointInfo(buffer.readBlockPos(), buffer.readByte(), buffer.readVarInt(),
                    buffer.readByte(), buffer.readByte(), buffer.readBoolean(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
    }

    public record Roster(List<EndpointInfo> rows, int total) {
    }

    public MeshEndpointMenu(int containerId, Inventory inventory, MeshEndpointPart part) {
        super(AE2Logistics.MESH_ENDPOINT_MENU.get(), containerId, inventory, part);
        this.part = part;
        this.serverPlayer = inventory.player instanceof ServerPlayer sp ? sp : null;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
        this.frequency = part.frequency();
        this.role = part.role();
        this.priority = part.priority();
        this.capabilities = part.capabilityMask();
        this.capabilitiesLocked = part.capabilityLocked();
    }

    public MeshEndpointMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.MESH_ENDPOINT_MENU.get(), containerId, inventory, null);
        this.part = null;
        this.serverPlayer = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
        this.frequency = buffer.readUtf();
        this.role = buffer.readByte();
        this.priority = buffer.readVarInt();
        this.capabilities = buffer.readVarInt();
        this.capabilitiesLocked = buffer.readBoolean();
        this.rosterTotal = buffer.readVarInt();
        int sent = buffer.readVarInt();
        var list = new ArrayList<EndpointInfo>(sent);
        for (int i = 0; i < sent; i++) {
            list.add(EndpointInfo.read(buffer));
        }
        this.roster = List.copyOf(list);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, MeshEndpointPart part) {
        var host = part.getHost().getBlockEntity();
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeByte(part.getSide().ordinal());
        buffer.writeUtf(part.frequency());
        buffer.writeByte(part.role());
        buffer.writeVarInt(part.priority());
        buffer.writeVarInt(part.capabilityMask());
        buffer.writeBoolean(part.capabilityLocked());

        var built = buildRoster(part);
        buffer.writeVarInt(built.total());
        buffer.writeVarInt(built.rows().size());
        for (var info : built.rows()) {
            EndpointInfo.write(buffer, info);
        }
    }

    /** Server-side roster snapshot for this part's frequency, capped at the wire limit. */
    public static Roster buildRoster(MeshEndpointPart part) {
        var linked = part.frequency().isBlank()
                ? List.<MeshEndpointPart>of()
                : MeshRegistry.carrierEndpoints(part);
        int sent = Math.min(linked.size(), ROSTER_LIMIT);
        var rows = new ArrayList<EndpointInfo>(sent);
        for (int i = 0; i < sent; i++) {
            var endpoint = linked.get(i);
            var endpointHost = endpoint.getHost().getBlockEntity();
            rows.add(new EndpointInfo(endpointHost.getBlockPos(), endpoint.role(),
                    endpoint.priority(), MeshRegistry.statusOf(endpoint), endpoint.meLinkState(),
                    endpoint == part, connectedDisplay(endpointHost, endpoint)));
        }
        return new Roster(List.copyOf(rows), linked.size());
    }

    public List<EndpointInfo> roster() {
        return roster;
    }

    public int rosterTotal() {
        return rosterTotal;
    }

    public boolean matches(BlockPos payloadPos, byte payloadSide) {
        return pos.equals(payloadPos) && side.ordinal() == payloadSide;
    }

    /** Applied client-side when the server re-pushes the roster after a config edit. */
    public void updateRoster(List<EndpointInfo> rows, int total) {
        this.roster = List.copyOf(rows);
        this.rosterTotal = total;
    }

    /**
     * Live roster streaming: every half second the server rebuilds the roster and
     * re-pushes it when anything changed - status flips, priority edits by OTHER
     * players, machines placed against endpoint faces. Config edits through this menu
     * still push instantly via {@link ConfigureMeshPayload}.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (part == null || serverPlayer == null || ++rosterTicks % 10 != 0) {
            return;
        }
        var built = buildRoster(part);
        if (built.total() == lastSentTotal && rosterEquals(built.rows(), lastSentRows)) {
            return;
        }
        lastSentRows = built.rows();
        lastSentTotal = built.total();
        PacketDistributor.sendToPlayer(serverPlayer,
                new MeshRosterPayload(pos, (byte) side.ordinal(), built.total(), built.rows()));
    }

    private static boolean rosterEquals(List<EndpointInfo> a, @Nullable List<EndpointInfo> b) {
        if (b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            var fresh = a.get(i);
            var sent = b.get(i);
            if (!fresh.pos().equals(sent.pos()) || fresh.role() != sent.role()
                    || fresh.priority() != sent.priority() || fresh.status() != sent.status()
                    || fresh.meState() != sent.meState() || fresh.self() != sent.self()
                    || !ItemStack.matches(fresh.connected(), sent.connected())) {
                return false;
            }
        }
        return true;
    }

    /**
     * What the endpoint's face touches, as a display item: the part on the facing side
     * of a cable bus (or its center cable), else the plain block.
     */
    private static ItemStack connectedDisplay(
            net.minecraft.world.level.block.entity.BlockEntity endpointHost, MeshEndpointPart endpoint) {
        var level = endpointHost.getLevel();
        if (level == null) {
            return ItemStack.EMPTY;
        }
        var facePos = endpointHost.getBlockPos().relative(endpoint.getSide());
        if (level.getBlockEntity(facePos) instanceof appeng.api.parts.IPartHost partHost) {
            var part = partHost.getPart(endpoint.getSide().getOpposite());
            if (part == null) {
                part = partHost.getPart(null);
            }
            if (part != null) {
                return new ItemStack(part.getPartItem().asItem());
            }
        }
        return new ItemStack(level.getBlockState(facePos).getBlock());
    }
}
