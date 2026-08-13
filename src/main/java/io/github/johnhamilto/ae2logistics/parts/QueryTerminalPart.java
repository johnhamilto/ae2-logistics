package io.github.johnhamilto.ae2logistics.parts;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.QueryTerminalMenu;

/**
 * Editor and browser for the network's named queries. Every terminal carries the full
 * library (edits write to all of them via the grid service), so any one survivor
 * preserves the network's saved searches.
 */
public class QueryTerminalPart extends AEBasePart {

    private final TreeMap<String, String> savedQueries = new TreeMap<>();

    public QueryTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(0.5);
    }

    public Map<String, String> savedQueries() {
        return Collections.unmodifiableMap(savedQueries);
    }

    public void putQuery(String name, String source) {
        savedQueries.put(name, source);
        getHost().markForSave();
    }

    public void removeQuery(String name) {
        if (savedQueries.remove(name) != null) {
            getHost().markForSave();
        }
    }

    public long stableKey() {
        var host = getHost().getBlockEntity();
        return host.getBlockPos().asLong() * 31 + (getSide() == null ? 6 : getSide().ordinal());
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
                            (id, inventory, p) -> new QueryTerminalMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> QueryTerminalMenu.writeOpenData(buffer, this));
        }
        return true;
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        var list = data.childrenList("queries");
        for (var entry : savedQueries.entrySet()) {
            var tag = list.addChild();
            tag.putString("name", entry.getKey());
            tag.putString("source", entry.getValue());
        }
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        savedQueries.clear();
        for (var tag : data.childrenListOrEmpty("queries")) {
            savedQueries.put(tag.getStringOr("name", ""), tag.getStringOr("source", ""));
        }
    }
}
