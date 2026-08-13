package io.github.johnhamilto.ae2logistics.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.IPriorityHost;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;

/**
 * The Config Terminal's model: every device on the grid that exposes generic settings
 * ({@link IConfigurableObject}), a priority ({@link IPriorityHost}), or memory-card
 * settings transfer (AE2 base parts and block entities - which includes all of ours).
 */
public final class ConfigDeviceIndex {

    private ConfigDeviceIndex() {
    }

    public record Device(IGridNode node, Object owner) {

        public boolean valid() {
            return node.getGrid() != null
                    && !(owner instanceof BlockEntity be && be.isRemoved());
        }

        public ItemStack icon() {
            var representation = node.getVisualRepresentation();
            return representation == null ? ItemStack.EMPTY : representation.toStack();
        }

        public String typeId() {
            var stack = icon();
            return stack.isEmpty() ? "?"
                    : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        @Nullable
        public BlockPos pos() {
            if (owner instanceof AEBasePart part) {
                return part.getHost().getBlockEntity().getBlockPos();
            }
            if (owner instanceof BlockEntity blockEntity) {
                return blockEntity.getBlockPos();
            }
            return null;
        }

        public String dimension() {
            var level = node.getLevel();
            return level == null ? "?" : level.dimension().identifier().toString();
        }

        @Nullable
        public IConfigManager configManager() {
            return owner instanceof IConfigurableObject configurable
                    ? configurable.getConfigManager()
                    : null;
        }

        @Nullable
        public IPriorityHost priorityHost() {
            return owner instanceof IPriorityHost host ? host : null;
        }

        public boolean canTransfer() {
            return owner instanceof AEBasePart || owner instanceof AEBaseBlockEntity
                    || owner instanceof TransferableSettings;
        }

        @Nullable
        public DataComponentMap export(@Nullable Player player) {
            if (owner instanceof TransferableSettings transferable) {
                return transferable.exportTransferSettings(player);
            }
            if (owner instanceof AEBasePart part) {
                return part.exportSettings(SettingsFrom.MEMORY_CARD);
            }
            if (owner instanceof AEBaseBlockEntity blockEntity) {
                return blockEntity.exportSettings(SettingsFrom.MEMORY_CARD, player);
            }
            return null;
        }

        public void importFrom(DataComponentMap settings, @Nullable Player player) {
            if (owner instanceof TransferableSettings transferable) {
                transferable.importTransferSettings(settings, player);
            } else if (owner instanceof AEBasePart part) {
                part.importSettings(SettingsFrom.MEMORY_CARD, settings, player);
            } else if (owner instanceof AEBaseBlockEntity blockEntity) {
                blockEntity.importSettings(SettingsFrom.MEMORY_CARD, settings, player);
            }
        }

        /** Short "key=value,..." summary of generic settings, empty when none. */
        public String settingsSummary() {
            var manager = configManager();
            if (manager == null) {
                return "";
            }
            var entries = new ArrayList<String>();
            for (var entry : manager.exportSettings().entrySet()) {
                entries.add(entry.getKey() + "=" + entry.getValue().toLowerCase(Locale.ROOT));
            }
            Collections.sort(entries);
            return String.join(",", entries);
        }
    }

    // Diff codes against a snapshot.
    public static final byte DIFF_SAME = 0;
    public static final byte DIFF_CHANGED = 1;
    public static final byte DIFF_NEW = 2;
    public static final byte DIFF_GONE = 3;

    /** Stable identity across sessions: type + dimension + position. */
    public static String snapshotKey(Device device) {
        var pos = device.pos();
        return device.typeId() + "@" + device.dimension() + ":"
                + (pos == null ? "?" : pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    /** The compared state: generic settings plus priority. */
    public static String snapshotValue(Device device) {
        var priorityHost = device.priorityHost();
        return device.settingsSummary()
                + (priorityHost != null ? "|p=" + priorityHost.getPriority() : "");
    }

    /** Per-device diff code; snapshot keys with no live device map to {@link #DIFF_GONE}. */
    public static java.util.Map<String, Byte> computeDiff(java.util.Map<String, String> snapshot,
            List<Device> devices) {
        var diff = new java.util.HashMap<String, Byte>();
        var seen = new java.util.HashSet<String>();
        for (var device : devices) {
            if (!device.valid()) {
                continue;
            }
            var key = snapshotKey(device);
            seen.add(key);
            var recorded = snapshot.get(key);
            if (recorded == null) {
                diff.put(key, DIFF_NEW);
            } else {
                diff.put(key, recorded.equals(snapshotValue(device)) ? DIFF_SAME : DIFF_CHANGED);
            }
        }
        for (var key : snapshot.keySet()) {
            if (!seen.contains(key)) {
                diff.put(key, DIFF_GONE);
            }
        }
        return diff;
    }

    public static List<Device> enumerate(IGrid grid) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var devices = new ArrayList<Device>();
        for (var node : grid.getNodes()) {
            var owner = node.getOwner();
            if (owner == null || !seen.add(owner)) {
                continue;
            }
            var device = new Device(node, owner);
            if (device.configManager() != null || device.priorityHost() != null
                    || device.canTransfer()) {
                devices.add(device);
            }
        }
        devices.sort(Comparator.comparing(Device::typeId)
                .thenComparing(device -> {
                    var pos = device.pos();
                    return pos == null ? Long.MAX_VALUE : pos.asLong();
                }));
        return devices;
    }

    /** Advances the named setting to its next value; returns false when not applicable. */
    public static boolean cycleSetting(Device device, String settingName, int direction) {
        var manager = device.configManager();
        if (manager == null) {
            return false;
        }
        for (var setting : manager.getSettings()) {
            if (!setting.getName().equals(settingName)) {
                continue;
            }
            var values = new ArrayList<>(setting.getValues());
            if (values.isEmpty()) {
                return false;
            }
            var current = manager.exportSettings().get(settingName);
            int index = 0;
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i).name().equalsIgnoreCase(current)) {
                    index = i;
                    break;
                }
            }
            var next = values.get(Math.floorMod(index + direction, values.size()));
            setting.setFromString(manager, next.name());
            return true;
        }
        return false;
    }

    /**
     * AE2 19.2 has no security station, so remote writes gate on the surfaces that do
     * exist: the player may build (not adventure/spectator) and may interact with the
     * terminal's position (respects protection mods hooking mayInteract).
     */
    public static boolean mayEdit(ServerPlayer player, BlockPos terminalPos) {
        return player.mayBuild() && player.level().mayInteract(player, terminalPos);
    }
}
