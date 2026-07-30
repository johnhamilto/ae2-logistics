package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.AEBaseMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex;
import io.github.johnhamilto.ae2logistics.parts.ConfigTerminalPart;

public class ConfigTerminalMenu extends AEBaseMenu {

    public static final int MAX_ROWS = 256;

    public record Row(String itemId, String name, boolean hasPos, BlockPos pos, String dimension,
            String summary, boolean hasPriority, int priority, byte diff) {
    }

    public record SettingLine(String name, String value) {
    }

    @Nullable
    private final ConfigTerminalPart part;
    @Nullable
    private final ServerPlayer serverPlayer;

    public final BlockPos pos;
    public final Direction side;

    // Server-side session state.
    private List<ConfigDeviceIndex.Device> devices = List.of();
    private int selected = -1;
    @Nullable
    private DataComponentMap clipboard;
    private String clipboardType = "";
    private String notice = "";
    private long ticks;
    private boolean built;

    // Client-side state.
    public List<Row> rows = new ArrayList<>();
    public List<SettingLine> detailSettings = new ArrayList<>();
    public int selectedIndex = -1;
    public boolean detailHasPriority;
    public int detailPriority;
    public String clientClipboardType = "";
    public String clientNotice = "";

    public ConfigTerminalMenu(int containerId, Inventory inventory, ConfigTerminalPart part) {
        super(AE2Logistics.CONFIG_TERMINAL_MENU.get(), containerId, inventory, part);
        this.part = part;
        this.serverPlayer = inventory.player instanceof ServerPlayer sp ? sp : null;
        var host = part.getHost().getBlockEntity();
        this.pos = host.getBlockPos();
        this.side = part.getSide();
    }

    public ConfigTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.CONFIG_TERMINAL_MENU.get(), containerId, inventory, null);
        this.part = null;
        this.serverPlayer = null;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readByte()];
    }

    private void rebuildDevices() {
        if (part == null) {
            return;
        }
        var node = part.getMainNode().getNode();
        devices = node != null && node.getGrid() != null
                ? ConfigDeviceIndex.enumerate(node.getGrid())
                : List.of();
        selected = -1;
    }

    @Nullable
    private ConfigDeviceIndex.Device device(int index) {
        if (index < 0 || index >= devices.size()) {
            return null;
        }
        var device = devices.get(index);
        return device.valid() ? device : null;
    }

    public void handleAction(ServerPlayer player, byte action, int index, String text, long value) {
        var mayEdit = ConfigDeviceIndex.mayEdit(player, pos);
        switch (action) {
            case ConfigTerminalActionPayload.ACTION_REFRESH -> rebuildDevices();
            case ConfigTerminalActionPayload.ACTION_SELECT -> selected = index;
            case ConfigTerminalActionPayload.ACTION_CYCLE -> {
                var device = device(index);
                if (!mayEdit) {
                    notice = "no permission";
                } else if (device != null && ConfigDeviceIndex.cycleSetting(device, text)) {
                    notice = "";
                }
            }
            case ConfigTerminalActionPayload.ACTION_SET_PRIORITY -> {
                var device = device(index);
                if (!mayEdit) {
                    notice = "no permission";
                } else if (device != null && device.priorityHost() != null) {
                    device.priorityHost().setPriority((int) value);
                    notice = "";
                }
            }
            case ConfigTerminalActionPayload.ACTION_COPY -> {
                var device = device(index);
                if (device != null && device.canTransfer()) {
                    clipboard = device.export(player);
                    clipboardType = device.typeId();
                    notice = "copied";
                }
            }
            case ConfigTerminalActionPayload.ACTION_PASTE -> {
                var device = device(index);
                if (!mayEdit) {
                    notice = "no permission";
                } else if (clipboard != null && device != null
                        && device.typeId().equals(clipboardType)) {
                    device.importFrom(clipboard, player);
                    notice = "pasted";
                } else if (clipboard != null) {
                    notice = "type mismatch";
                }
            }
            case ConfigTerminalActionPayload.ACTION_PASTE_ALL -> {
                if (!mayEdit) {
                    notice = "no permission";
                } else if (clipboard != null) {
                    int applied = 0;
                    for (var device : devices) {
                        if (device.valid() && device.typeId().equals(clipboardType)) {
                            device.importFrom(clipboard, player);
                            applied++;
                        }
                    }
                    notice = "applied to " + applied;
                }
            }
            case ConfigTerminalActionPayload.ACTION_SNAPSHOT -> {
                if (part != null) {
                    part.takeSnapshot(devices);
                    notice = "snapshot: " + part.snapshot().size() + " devices";
                }
            }
            default -> {
            }
        }
        sendState();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (part == null || serverPlayer == null) {
            return;
        }
        if (!built) {
            built = true;
            rebuildDevices();
        }
        if (ticks++ % 20 == 0) {
            sendState();
        }
    }

    private void sendState() {
        if (serverPlayer == null) {
            return;
        }
        var snapshot = part != null ? part.snapshot() : java.util.Map.<String, String>of();
        var diff = ConfigDeviceIndex.computeDiff(snapshot, devices);
        var outRows = new ArrayList<Row>();
        for (var device : devices) {
            if (outRows.size() >= MAX_ROWS) {
                break;
            }
            if (!device.valid()) {
                outRows.add(new Row("?", "(removed)", false, BlockPos.ZERO, "?", "", false, 0,
                        ConfigDeviceIndex.DIFF_SAME));
                continue;
            }
            var icon = device.icon();
            var priorityHost = device.priorityHost();
            var devicePos = device.pos();
            var key = ConfigDeviceIndex.snapshotKey(device);
            outRows.add(new Row(
                    device.typeId(),
                    icon.isEmpty() ? device.typeId() : icon.getHoverName().getString(),
                    devicePos != null,
                    devicePos != null ? devicePos : BlockPos.ZERO,
                    device.dimension(),
                    device.settingsSummary(),
                    priorityHost != null,
                    priorityHost != null ? priorityHost.getPriority() : 0,
                    snapshot.isEmpty() ? ConfigDeviceIndex.DIFF_SAME
                            : diff.getOrDefault(key, ConfigDeviceIndex.DIFF_NEW)));
        }
        for (var entry : diff.entrySet()) {
            if (entry.getValue() == ConfigDeviceIndex.DIFF_GONE && outRows.size() < MAX_ROWS) {
                var key = entry.getKey();
                var typeId = key.contains("@") ? key.substring(0, key.indexOf('@')) : key;
                outRows.add(new Row(typeId, "(missing) " + typeId, false, BlockPos.ZERO, "?",
                        snapshot.getOrDefault(key, ""), false, 0, ConfigDeviceIndex.DIFF_GONE));
            }
        }

        var settings = new ArrayList<SettingLine>();
        boolean hasPriority = false;
        int priority = 0;
        var selectedDevice = device(selected);
        if (selectedDevice != null) {
            var manager = selectedDevice.configManager();
            if (manager != null) {
                for (var entry : new java.util.TreeMap<>(manager.exportSettings()).entrySet()) {
                    settings.add(new SettingLine(entry.getKey(), entry.getValue()));
                }
            }
            var priorityHost = selectedDevice.priorityHost();
            if (priorityHost != null) {
                hasPriority = true;
                priority = priorityHost.getPriority();
            }
        }
        PacketDistributor.sendToPlayer(serverPlayer, new ConfigTerminalDataPayload(containerId,
                outRows, selected, settings, hasPriority, priority, clipboardType, notice));
        notice = "";
    }

}
