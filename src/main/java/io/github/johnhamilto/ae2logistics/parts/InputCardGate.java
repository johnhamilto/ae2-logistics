package io.github.johnhamilto.ae2logistics.parts;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.parts.storagebus.StorageBusPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Insert gate wrapped around a storage bus mount, applying our input cards
 * (DESIGN F12). Cards are read live from the part on every insert, so
 * adding or removing one needs no remount.
 *
 * <p>Conform Card: accept only keys the target already contains - live contents,
 * not a snapshot; an empty target accepts nothing. AE2's Fuzzy Card widens the
 * contains-check exactly as it widens partitions; the Inverter Card flips it
 * (accept only what is NOT yet present - a self-deduplicating collection chest).
 *
 * <p>Stack Limiter Card: item inserts deliver a single item, and only while the
 * target holds no items at all - whole-inventory scoping, the only one that
 * yields strict one-at-a-time delivery downstream. Non-item keys pass unchanged.
 *
 * <p>Both read the target through the configured handler's stack report, which
 * is empty under FILTER_ON_EXTRACT with a write-only access mode - the gate then
 * refuses everything ("can't see it, won't conform to it").
 */
public final class InputCardGate implements MEStorage {

    private final MEStorage delegate;
    private final StorageBusPart part;
    /**
     * The Variant Card swaps the wrapped handler's partition for a template-matching
     * one (see {@link VariantMatching}). Stock code re-applies the exact partition on
     * its own updates, so the swap is re-asserted before every delegated operation:
     * the network only ever reaches the handler through this wrapper, making the
     * partition state at call time ours. On card removal a remount restores stock.
     */
    private Boolean lastVariantState;

    public InputCardGate(MEStorage delegate, StorageBusPart part) {
        this.delegate = delegate;
        this.part = part;
    }

    private boolean ensureVariantPartition() {
        var upgrades = part.getUpgrades();
        // Precedence: a bound Query Card owns the partition outright; the Variant
        // Card templates the config slots; else stock's own exact partition stands.
        String queryName = null;
        if (upgrades.getInstalledUpgrades(AE2Logistics.QUERY_CARD.get()) > 0) {
            for (var stack : upgrades) {
                if (stack.is(AE2Logistics.QUERY_CARD.get())) {
                    queryName = io.github.johnhamilto.ae2logistics.item.QueryCardItem.getQueryName(stack);
                    break;
                }
            }
        }
        boolean variant = upgrades.getInstalledUpgrades(AE2Logistics.VARIANT_CARD.get()) > 0;
        boolean overriding = queryName != null || variant;
        if (overriding && delegate instanceof appeng.me.storage.MEInventoryHandler handler) {
            handler.setPartitionList(queryName != null
                    ? queryPartition(queryName)
                    : VariantMatching.partition(part.getConfig()));
        } else if (lastVariantState != null && lastVariantState && !overriding) {
            // Cards just left: force a remount so stock reconfigures its own partition.
            appeng.api.storage.IStorageProvider.requestUpdate(part.getMainNode());
        }
        lastVariantState = overriding;
        return variant;
    }

    /** Partition = live membership in the named query from the grid's library. */
    private appeng.util.prioritylist.IPartitionList queryPartition(String name) {
        return new appeng.util.prioritylist.IPartitionList() {
            @Override
            public boolean isListed(AEKey input) {
                var node = part.getMainNode().getNode();
                var grid = node == null ? null : node.getGrid();
                if (grid == null) {
                    return false;
                }
                var service = grid.getService(io.github.johnhamilto.ae2logistics.query.QueryService.class);
                if (service == null) {
                    return false;
                }
                java.util.function.Function<String, io.github.johnhamilto.ae2logistics.query.CompiledQuery> resolver =
                        queryName -> {
                            var source = service.library().get(queryName);
                            return source == null ? null : service.compiled(source);
                        };
                var query = resolver.apply(name);
                if (query == null
                        || !io.github.johnhamilto.ae2logistics.query.CompiledQuery.isQueryableKey(input)) {
                    return false;
                }
                return query.matches(input,
                        io.github.johnhamilto.ae2logistics.query.QueryContext.of(grid, resolver));
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Iterable<AEKey> getItems() {
                return java.util.List.of();
            }
        };
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        boolean variant = ensureVariantPartition();
        var upgrades = part.getUpgrades();
        boolean conform = upgrades.getInstalledUpgrades(AE2Logistics.CONFORM_CARD.get()) > 0;
        boolean limiter = upgrades.getInstalledUpgrades(AE2Logistics.STACK_LIMITER_CARD.get()) > 0;
        if (!conform && !limiter) {
            return delegate.insert(what, amount, mode, source);
        }

        KeyCounter contents = new KeyCounter();
        delegate.getAvailableStacks(contents);

        if (conform) {
            boolean present = variant
                    ? containsSameItem(contents, what)
                    : contains(contents, what, upgrades
                            .getInstalledUpgrades(appeng.core.definitions.AEItems.FUZZY_CARD.asItem()) > 0);
            boolean inverted = upgrades
                    .getInstalledUpgrades(appeng.core.definitions.AEItems.INVERTER_CARD.asItem()) > 0;
            if (inverted ? present : !present) {
                return 0;
            }
        }

        if (limiter && what instanceof AEItemKey) {
            for (var entry : contents) {
                if (entry.getKey() instanceof AEItemKey && entry.getLongValue() > 0) {
                    return 0;
                }
            }
            amount = Math.min(amount, 1);
        }

        return delegate.insert(what, amount, mode, source);
    }

    private boolean contains(KeyCounter contents, AEKey what, boolean fuzzy) {
        if (contents.get(what) > 0) {
            return true;
        }
        if (fuzzy && what.supportsFuzzyRangeSearch()) {
            FuzzyMode fuzzyMode = part.getConfigManager().getSetting(Settings.FUZZY_MODE);
            for (var entry : contents.findFuzzy(what, fuzzyMode)) {
                if (entry.getLongValue() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Conform + Variant: accept any variant of items already present. */
    private static boolean containsSameItem(KeyCounter contents, AEKey what) {
        for (var entry : contents) {
            if (entry.getLongValue() > 0 && VariantMatching.sameItem(entry.getKey(), what)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        ensureVariantPartition();
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        ensureVariantPartition();
        delegate.getAvailableStacks(out);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }
}
