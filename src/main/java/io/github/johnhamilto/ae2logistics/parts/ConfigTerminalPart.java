package io.github.johnhamilto.ae2logistics.parts;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.ConfigTerminalMenu;

/**
 * One terminal, every configurable device on the network: audit generic settings and
 * priorities, edit them in place, and copy/paste memory-card settings across same-type
 * devices - one at a time or fleet-wide.
 */
public class ConfigTerminalPart extends AEBasePart {

    private static final Codec<java.util.Map<String, String>> SNAPSHOT_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.STRING);

    /** Persistent snapshot: device key -> settings state at snapshot time. */
    private final java.util.HashMap<String, String> snapshot = new java.util.HashMap<>();

    public ConfigTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(0.5);
    }

    public java.util.Map<String, String> snapshot() {
        return snapshot;
    }

    public void takeSnapshot(java.util.List<io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex.Device> devices) {
        snapshot.clear();
        for (var device : devices) {
            if (device.valid()) {
                snapshot.put(io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex.snapshotKey(device),
                        io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex.snapshotValue(device));
            }
        }
        getHost().markForSave();
    }

    @Override
    public void writeToNBT(net.minecraft.world.level.storage.ValueOutput data) {
        super.writeToNBT(data);
        data.store("snapshot", SNAPSHOT_CODEC, snapshot);
    }

    @Override
    public void readFromNBT(net.minecraft.world.level.storage.ValueInput data) {
        super.readFromNBT(data);
        snapshot.clear();
        data.read("snapshot", SNAPSHOT_CODEC).ifPresent(snapshot::putAll);
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
                            (id, inventory, p) -> new ConfigTerminalMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> {
                        var host = getHost().getBlockEntity();
                        buffer.writeBlockPos(host.getBlockPos());
                        buffer.writeByte(getSide().ordinal());
                    });
        }
        return true;
    }
}
