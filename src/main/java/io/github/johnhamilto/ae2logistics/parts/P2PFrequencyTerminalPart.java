package io.github.johnhamilto.ae2logistics.parts;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import appeng.api.networking.IGridNodeListener;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.P2PFrequencyTerminalMenu;

/**
 * A network-wide table of every P2P tunnel: frequency, type, role, position. The terminal
 * itself is stateless; frequency names live on the tunnels (see {@link P2PNames}), so any
 * number of terminals share one set of names. Older worlds stored names in this part's
 * NBT - those migrate onto the tunnels the first time the part sees its grid.
 */
public class P2PFrequencyTerminalPart extends AEBasePart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/p2p_frequency_terminal"));

    private final Map<Short, String> legacyNames = new HashMap<>();

    public P2PFrequencyTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(0.5);
    }

    /**
     * Moves pre-0.6 per-terminal names onto the tunnels that carry those frequencies.
     * Entries whose frequency has no tunnels on the grid right now (for example, tunnels
     * in unloaded chunks) stay here and retry on the next grid state change or menu open.
     */
    public void migrateLegacyNames() {
        if (legacyNames.isEmpty() || isClientSide()) {
            return;
        }
        var node = getMainNode().getNode();
        if (node == null) {
            return;
        }
        boolean changed = false;
        var iterator = legacyNames.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            boolean found = false;
            for (var gridNode : node.getGrid().getNodes()) {
                if (gridNode.getOwner() instanceof P2PTunnelPart<?> tunnel
                        && tunnel.getFrequency() == entry.getKey()) {
                    found = true;
                    if (P2PNames.nameOn(tunnel).isBlank()) {
                        P2PNames.write(tunnel, entry.getValue());
                    }
                }
            }
            if (found) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            getHost().markForSave();
        }
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        migrateLegacyNames();
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 16;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new P2PFrequencyTerminalMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> {
                        var host = getHost().getBlockEntity();
                        buffer.writeBlockPos(host.getBlockPos());
                        buffer.writeByte(getSide().ordinal());
                    });
        }
        return true;
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        if (!legacyNames.isEmpty()) {
            var list = data.childrenList("frequencyNames");
            for (var entry : legacyNames.entrySet()) {
                var tag = list.addChild();
                tag.putShort("freq", entry.getKey());
                tag.putString("name", entry.getValue());
            }
        }
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        legacyNames.clear();
        for (var tag : data.childrenListOrEmpty("frequencyNames")) {
            legacyNames.put((short) tag.getShortOr("freq", (short) 0), tag.getStringOr("name", ""));
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
