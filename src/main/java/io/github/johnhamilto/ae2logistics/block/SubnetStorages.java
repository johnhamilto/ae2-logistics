package io.github.johnhamilto.ae2logistics.block;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

/** Storage wrappers for the ME Subnet Link. */
public final class SubnetStorages {

    private SubnetStorages() {
    }

    /**
     * Gates a storage behind its entry's channel/power state and an optional single-key
     * whitelist, so channel starvation genuinely darkens a virtual device.
     */
    /**
     * A view of another grid's network storage with a per-instance reentrancy latch:
     * when an uplink and a downlink form a cycle, the second visit to the same proxy on
     * the same thread is refused instead of recursing. Items still move exactly once.
     */
    public static final class GridProxy implements MEStorage {
        private final Supplier<IGrid> grid;
        private final BooleanSupplier active;
        private final String name;
        private final ThreadLocal<Boolean> entered = ThreadLocal.withInitial(() -> false);

        public GridProxy(Supplier<IGrid> grid, BooleanSupplier active, String name) {
            this.grid = grid;
            this.active = active;
            this.name = name;
        }

        @Nullable
        private MEStorage target() {
            if (!active.getAsBoolean()) {
                return null;
            }
            var resolved = grid.get();
            return resolved == null ? null : resolved.getStorageService().getInventory();
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (entered.get()) {
                return 0;
            }
            var target = target();
            if (target == null) {
                return 0;
            }
            entered.set(true);
            try {
                return target.insert(what, amount, mode, source);
            } finally {
                entered.set(false);
            }
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (entered.get()) {
                return 0;
            }
            var target = target();
            if (target == null) {
                return 0;
            }
            entered.set(true);
            try {
                return target.extract(what, amount, mode, source);
            } finally {
                entered.set(false);
            }
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (entered.get()) {
                return;
            }
            var target = target();
            if (target == null) {
                return;
            }
            entered.set(true);
            try {
                target.getAvailableStacks(out);
            } finally {
                entered.set(false);
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal(name);
        }
    }
}
