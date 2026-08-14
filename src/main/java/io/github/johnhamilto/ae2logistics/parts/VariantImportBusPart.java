package io.github.johnhamilto.ae2logistics.parts;

import net.minecraft.server.level.ServerLevel;

import appeng.api.networking.IGrid;
import appeng.api.parts.IPartItem;
import appeng.core.definitions.AEItems;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.automation.StackWorldBehaviors;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * An import bus that takes the Variant Card: with it installed, the config slots
 * become variant TEMPLATES (see {@link VariantMatching}) instead of exact keys,
 * so one plain enchanted book pulls every enchanted book. Without the card it is
 * the import bus it extends - AE2's whole pipeline runs via super.
 */
public class VariantImportBusPart extends ImportBusPart {


    public VariantImportBusPart(IPartItem<?> partItem) {
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
        // Mirror of the stock body with our filter; the facade is rebuilt per pass
        // because the stock strategy cache is private (allocation-only, no lookups).
        var self = getHost().getBlockEntity();
        var fromPos = self.getBlockPos().relative(getSide());
        var fromSide = getSide().getOpposite();
        var strategy = StackWorldBehaviors.createImportFacade((ServerLevel) getLevel(), fromPos,
                fromSide, getKeyTypeSelection().enabledPredicate());

        var context = VariantMatching.transferContext(grid.getStorageService(),
                grid.getEnergyService(), this.source, getOperationsPerTick(),
                VariantMatching.partition(getConfig()));
        context.setInverted(isUpgradedWith(AEItems.INVERTER_CARD));
        strategy.transfer(context);
        return context.hasDoneWork();
    }

    @Override
    public net.minecraft.world.inventory.MenuType<?> getMenuType() {
        return AE2Logistics.VARIANT_IMPORT_BUS_MENU.get();
    }

}
