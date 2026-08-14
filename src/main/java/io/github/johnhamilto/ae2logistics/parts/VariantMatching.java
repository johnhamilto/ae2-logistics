package io.github.johnhamilto.ae2logistics.parts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.IPartitionList;

/**
 * Variant matching (DESIGN F13): a configured item is a TEMPLATE. A candidate
 * matches when it is the same item and agrees with every data component the
 * template carries; components the template does not carry are ignored. A plain
 * template therefore matches every variant of its item, and a template with one
 * deliberate component (an enchant set, a custom name) pins exactly that.
 *
 * <p>Non-item keys fall back to exact equality: component templating is an item
 * concept, and lying about fluids would be worse than declining.
 */
public final class VariantMatching {

    private VariantMatching() {
    }

    public static boolean matches(AEKey template, AEKey candidate) {
        if (template.equals(candidate)) {
            return true;
        }
        if (!(template instanceof AEItemKey templateItem)
                || !(candidate instanceof AEItemKey candidateItem)) {
            return false;
        }
        if (templateItem.getItem() != candidateItem.getItem()) {
            return false;
        }
        var patch = templateItem.getReadOnlyStack().getComponentsPatch();
        var actual = candidateItem.getReadOnlyStack().getComponents();
        for (var entry : patch.entrySet()) {
            var expected = entry.getValue();
            var value = actual.get(entry.getKey());
            if (expected.isEmpty() ? value != null : !expected.get().equals(value)) {
                return false;
            }
        }
        return true;
    }

    /** Same item, components ignored entirely (the Conform Card's widened contains-check). */
    public static boolean sameItem(AEKey a, AEKey b) {
        return a instanceof AEItemKey ka && b instanceof AEItemKey kb && ka.getItem() == kb.getItem();
    }

    /** A partition over live config templates; config slots are scanned per query. */
    public static IPartitionList partition(ConfigInventory config) {
        return new IPartitionList() {
            @Override
            public boolean isListed(AEKey input) {
                for (int i = 0; i < config.size(); i++) {
                    var template = config.getKey(i);
                    if (template != null && matches(template, input)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean isEmpty() {
                return config.keySet().isEmpty();
            }

            @Override
            public Iterable<AEKey> getItems() {
                var keys = new ArrayList<AEKey>();
                for (int i = 0; i < config.size(); i++) {
                    var template = config.getKey(i);
                    if (template != null) {
                        keys.add(template);
                    }
                }
                return List.copyOf(keys);
            }
        };
    }

    /**
     * Our own {@link StackTransferContext}: AE2's impl is package-private, and the
     * variant buses need a context carrying a variant partition. Mirrors the stock
     * impl faithfully (the interface is marked non-extendable, so an AE2 update that
     * grows it breaks us loudly at compile time - acceptable on a pinned version).
     */
    public static StackTransferContext transferContext(IStorageService internalStorage,
            IEnergySource energySource, IActionSource actionSource, int operations,
            IPartitionList filter) {
        return new StackTransferContext() {
            private final Set<AEKeyType> keyTypes = collectTypes(filter);
            private final int initialOperations = operations;
            private int operationsRemaining = operations;
            private boolean inverted;

            @Override
            public IStorageService getInternalStorage() {
                return internalStorage;
            }

            @Override
            public IEnergySource getEnergySource() {
                return energySource;
            }

            @Override
            public IActionSource getActionSource() {
                return actionSource;
            }

            @Override
            public int getOperationsRemaining() {
                return operationsRemaining;
            }

            @Override
            public void setOperationsRemaining(int value) {
                this.operationsRemaining = value;
            }

            @Override
            public boolean hasOperationsLeft() {
                return operationsRemaining > 0;
            }

            @Override
            public boolean hasDoneWork() {
                return initialOperations > operationsRemaining;
            }

            @Override
            public boolean isKeyTypeEnabled(AEKeyType space) {
                return keyTypes.isEmpty() || inverted || keyTypes.contains(space);
            }

            @Override
            public boolean isInFilter(AEKey key) {
                return filter.isEmpty() || filter.isListed(key);
            }

            @Override
            public IPartitionList getFilter() {
                return filter;
            }

            @Override
            public void setInverted(boolean value) {
                this.inverted = value;
            }

            @Override
            public boolean isInverted() {
                return !filter.isEmpty() && inverted;
            }

            @Override
            public boolean canInsert(AEItemKey what, long amount) {
                return internalStorage.getInventory().insert(what, amount, Actionable.SIMULATE,
                        actionSource) > 0;
            }

            @Override
            public void reduceOperationsRemaining(long inserted) {
                operationsRemaining -= inserted;
            }
        };
    }

    private static Set<AEKeyType> collectTypes(IPartitionList filter) {
        var types = new HashSet<AEKeyType>();
        for (var key : filter.getItems()) {
            types.add(key.getType());
        }
        return types;
    }
}
