package io.github.johnhamilto.ae2logistics.parts;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.JobMonitorMenu;
import io.github.johnhamilto.ae2logistics.signal.ILogicNode;
import io.github.johnhamilto.ae2logistics.signal.SignalMath;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * Polls the network's crafting CPUs once per tick and drives job telemetry onto signal
 * channels: {@code <prefix>:active}, {@code <prefix>:idle}, {@code <prefix>:stalled}
 * (busy but no progress for the configured window) and {@code <prefix>:pending}
 * (items left across all jobs), plus {@code <prefix>:<name>/remaining} and
 * {@code <prefix>:<name>/stalled} for every CPU cluster you have given a custom name.
 * One monitor per network; a second one on the same prefix doubles every count.
 */
public class JobMonitorPart extends AEBasePart implements ILogicNode {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/job_monitor"));

    private String prefix = "craft";
    private int stallSeconds = 10;

    private final Map<Object, Tracker> trackers = new IdentityHashMap<>();
    private Set<Identifier> channels = Set.of();
    private long tick;

    private static final class Tracker {
        long lastProgress = -1;
        long lastChangeTick;
        @Nullable
        GenericStack lastWhat;
    }

    public JobMonitorPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(0.5)
                .addService(ILogicNode.class, this);
    }

    public String prefix() {
        return prefix;
    }

    public int stallSeconds() {
        return stallSeconds;
    }

    public void applyMonitorConfig(String newPrefix, int newStallSeconds) {
        var cleaned = sanitize(newPrefix);
        this.prefix = cleaned.isEmpty() ? "craft" : cleaned;
        this.stallSeconds = Math.max(1, Math.min(600, newStallSeconds));
        getHost().markForSave();
        getMainNode().ifPresent(grid -> grid.getService(SignalService.class).invalidateGraph());
    }

    /** Lowercases and squeezes to legal resource-location characters. */
    private static String sanitize(String raw) {
        var out = new StringBuilder();
        for (char ch : raw.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            boolean legal = ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9'
                    || ch == '_' || ch == '.' || ch == '-';
            out.append(legal ? ch : '_');
        }
        return out.length() > 24 ? out.substring(0, 24) : out.toString();
    }

    private Identifier channel(String path) {
        return Identifier.fromNamespaceAndPath(prefix, path);
    }

    /** Live value of one of this monitor's channels, for the menu readout. */
    /** Read-only stall check for the GUI board; tracker state advances only in evaluate(). */
    public boolean isStalledForDisplay(Object cpu) {
        var tracker = trackers.get(cpu);
        return tracker != null && tracker.lastProgress >= 0
                && tick - tracker.lastChangeTick >= stallSeconds * 20L;
    }

    public long channelValue(String path) {
        var node = getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return 0;
        }
        return node.getGrid().getService(SignalService.class).get(channel(path));
    }

    // --- ILogicNode ---

    @Override
    public Set<Identifier> readChannels() {
        return Set.of();
    }

    @Nullable
    @Override
    public Identifier writtenChannel() {
        return null;
    }

    @Override
    public Set<Identifier> writtenChannels() {
        return channels;
    }

    @Override
    public long stableKey() {
        var host = getHost().getBlockEntity();
        return host.getBlockPos().asLong() * 31 + (getSide() == null ? 6 : getSide().ordinal());
    }

    @Override
    public void evaluate(LogicContext context) {
        tick++;
        var node = getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        var crafting = node.getGrid().getCraftingService();

        long active = 0;
        long idle = 0;
        long stalled = 0;
        long pending = 0;
        var next = new HashSet<Identifier>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var cpu : crafting.getCpus()) {
            seen.add(cpu);
            var tracker = trackers.computeIfAbsent(cpu, c -> new Tracker());
            var status = cpu.getJobStatus();
            boolean busy = status != null;
            boolean cpuStalled = false;
            long remaining = 0;

            if (busy) {
                active++;
                remaining = Math.max(0, status.totalItems() - status.progress());
                pending = SignalMath.add(pending, remaining);
                if (status.progress() != tracker.lastProgress
                        || !Objects.equals(status.crafting(), tracker.lastWhat)) {
                    tracker.lastProgress = status.progress();
                    tracker.lastWhat = status.crafting();
                    tracker.lastChangeTick = tick;
                } else if (tick - tracker.lastChangeTick >= stallSeconds * 20L) {
                    cpuStalled = true;
                    stalled++;
                }
            } else {
                idle++;
                tracker.lastProgress = -1;
                tracker.lastWhat = null;
                tracker.lastChangeTick = tick;
            }

            var name = cpu.getName();
            if (name != null) {
                var slug = sanitize(name.getString());
                if (!slug.isEmpty()) {
                    var remainingChannel = channel(slug + "/remaining");
                    var stalledChannel = channel(slug + "/stalled");
                    next.add(remainingChannel);
                    next.add(stalledChannel);
                    context.write(remainingChannel, remaining);
                    context.write(stalledChannel, cpuStalled ? 1 : 0);
                }
            }
        }
        trackers.keySet().retainAll(seen);

        var activeChannel = channel("active");
        var idleChannel = channel("idle");
        var stalledChannel = channel("stalled");
        var pendingChannel = channel("pending");
        next.add(activeChannel);
        next.add(idleChannel);
        next.add(stalledChannel);
        next.add(pendingChannel);
        context.write(activeChannel, active);
        context.write(idleChannel, idle);
        context.write(stalledChannel, stalled);
        context.write(pendingChannel, pending);

        if (!next.equals(channels)) {
            channels = Set.copyOf(next);
            node.getGrid().getService(SignalService.class).invalidateGraph();
        }
    }

    // --- part boilerplate ---

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
                            (id, inventory, p) -> new JobMonitorMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> JobMonitorMenu.writeOpenData(buffer, this));
        }
        return true;
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
            net.minecraft.core.component.DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);
        if (mode == appeng.util.SettingsFrom.MEMORY_CARD) {
            var tag = new CompoundTag();
            tag.putString("prefix", prefix);
            tag.putInt("stallSeconds", stallSeconds);
            builder.set(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get(), tag);
        }
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
            net.minecraft.core.component.DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (isClientSide()) {
            return;
        }
        var tag = input.get(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get());
        if (tag != null && tag.contains("prefix")) {
            applyMonitorConfig(tag.getString("prefix"), tag.getInt("stallSeconds"));
        }
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putString("prefix", prefix);
        data.putInt("stallSeconds", stallSeconds);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        if (data.contains("prefix")) {
            prefix = data.getString("prefix");
        }
        if (data.contains("stallSeconds")) {
            stallSeconds = data.getInt("stallSeconds");
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
