package io.github.johnhamilto.ae2logistics.parts;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.P2PFrequencyTerminalMenu;

/**
 * A network-wide table of every P2P tunnel: frequency, type, role, position. Frequencies
 * can be named (names persist in this part's NBT) and tunnels retuned from the table.
 */
public class P2PFrequencyTerminalPart extends AEBasePart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/p2p_frequency_terminal"));

    private final Map<Short, String> frequencyNames = new HashMap<>();

    public P2PFrequencyTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(0.5);
    }

    public String nameFor(short frequency) {
        return frequencyNames.getOrDefault(frequency, "");
    }

    public void setName(short frequency, String name) {
        if (name.isBlank()) {
            frequencyNames.remove(frequency);
        } else {
            frequencyNames.put(frequency, name.length() > 32 ? name.substring(0, 32) : name);
        }
        getHost().markForSave();
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
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        var list = new net.minecraft.nbt.ListTag();
        for (var entry : frequencyNames.entrySet()) {
            var tag = new CompoundTag();
            tag.putShort("freq", entry.getKey());
            tag.putString("name", entry.getValue());
            list.add(tag);
        }
        data.put("frequencyNames", list);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        frequencyNames.clear();
        for (Tag element : data.getList("frequencyNames", Tag.TAG_COMPOUND)) {
            if (element instanceof CompoundTag tag) {
                frequencyNames.put(tag.getShort("freq"), tag.getString("name"));
            }
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
