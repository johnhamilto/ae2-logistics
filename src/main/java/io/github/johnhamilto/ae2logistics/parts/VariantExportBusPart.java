package io.github.johnhamilto.ae2logistics.parts;

import com.google.common.collect.ImmutableList;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartItem;
import appeng.core.definitions.AEItems;
import appeng.parts.automation.ExportBusPart;
import appeng.util.prioritylist.DefaultPriorityList;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * An export bus that takes the Variant Card: with it installed, every config
 * slot exports ALL stored variants that match its template (see
 * {@link VariantMatching}) - one plain enchanted book empties the network of
 * enchanted books; a Mending-only template exports exactly those. Without the
 * card it is the export bus it extends. The Crafting Card is ignored while the
 * Variant Card is in: crafting produces exact keys, and "craft me any variant"
 * has no honest meaning.
 */
public class VariantExportBusPart extends ExportBusPart {


    public VariantExportBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    private boolean variantMode() {
        return isUpgradedWith(AE2Logistics.VARIANT_CARD.get());
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        if (!variantMode()) {
            return super.doBusWork(grid);
        }
        var storageService = grid.getStorageService();
        var schedulingMode = getConfigManager().getSetting(Settings.SCHEDULING_MODE);
        var context = VariantMatching.transferContext(storageService, grid.getEnergyService(),
                this.source, getOperationsPerTick(), DefaultPriorityList.INSTANCE);

        int x = 0;
        for (x = 0; x < availableSlots() && context.hasOperationsLeft(); x++) {
            int slotToExport = getStartingSlot(schedulingMode, x);
            var what = getConfig().getKey(slotToExport);
            if (what == null) {
                continue;
            }
            // findFuzzy(IGNORE_ALL) yields every stored variant of the template's
            // item (all components, all damage); the template then narrows it.
            for (var candidate : ImmutableList
                    .copyOf(storageService.getCachedInventory().findFuzzy(what, FuzzyMode.IGNORE_ALL))) {
                var key = candidate.getKey();
                if (!VariantMatching.matches(what, key)) {
                    continue;
                }
                var transferFactor = key.getAmountPerOperation();
                long amount = (long) context.getOperationsRemaining() * transferFactor;
                amount = getExportStrategy().transfer(context, key, amount);
                if (amount > 0) {
                    context.reduceOperationsRemaining(Math.max(1, amount / transferFactor));
                }
                if (!context.hasOperationsLeft()) {
                    break;
                }
            }
        }

        if (context.hasDoneWork()) {
            updateSchedulingMode(schedulingMode, x);
        }
        return context.hasDoneWork();
    }

    @Override
    public net.minecraft.world.inventory.MenuType<?> getMenuType() {
        return AE2Logistics.VARIANT_EXPORT_BUS_MENU.get();
    }

}
