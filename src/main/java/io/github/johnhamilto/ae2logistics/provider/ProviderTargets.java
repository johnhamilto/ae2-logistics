package io.github.johnhamilto.ae2logistics.provider;

import java.util.IdentityHashMap;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.AECapabilities;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;

/**
 * Resolves the block behind a face into a push target the same way a pattern provider
 * does: the ME_STORAGE capability first (accepts every key type), then the per-key-type
 * external storage strategies (items, fluids, and whatever companion mods register -
 * chemicals, flux) composed into one storage. This is what lets provider tunnels and
 * provider mesh endpoints deliver anything a pattern provider can push.
 */
public final class ProviderTargets {

    private ProviderTargets() {
    }

    @Nullable
    public static MEStorage resolve(ServerLevel level, BlockPos pos, Direction fromSide) {
        var meStorage = level.getCapability(AECapabilities.ME_STORAGE, pos, fromSide);
        if (meStorage != null) {
            return meStorage;
        }
        var strategies = StackWorldBehaviors.createExternalStorageStrategies(level, pos, fromSide);
        var externalStorages = new IdentityHashMap<AEKeyType, MEStorage>(strategies.size());
        for (var entry : strategies.entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, () -> {
            });
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }
        return externalStorages.isEmpty() ? null : new CompositeStorage(externalStorages);
    }

    /** Blocking-mode check: does the target still hold any of the batch's keys? */
    public static boolean containsAny(MEStorage storage, Set<AEKey> keys) {
        for (var stack : storage.getAvailableStacks()) {
            if (keys.contains(stack.getKey().dropSecondary())) {
                return true;
            }
        }
        return false;
    }
}
