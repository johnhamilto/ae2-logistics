package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import appeng.api.stacks.AEKeyTypes;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.compat.CompatMods;
import io.github.johnhamilto.ae2logistics.provider.ProviderTargets;

/**
 * Integration coverage against the compat suite in the dev runtime. Every test treats
 * an absent mod as a skip (succeed immediately), so the suite stays green in a bare
 * environment and gains assertions in the modded one.
 */
public class CompatGameTests {

    static void register() {
        LogisticsTestInstance.add("extendedAeProviderResolvesForVirtualization", "empty5", CompatGameTests::extendedAeProviderResolvesForVirtualization);
        LogisticsTestInstance.add("appliedMekanisticsChemicalKeyTypeRegisters", "empty5", CompatGameTests::appliedMekanisticsChemicalKeyTypeRegisters);
        LogisticsTestInstance.add("wtlibWirelessEncodingTerminalSocketsImportCard", "empty5", CompatGameTests::wtlibWirelessEncodingTerminalSocketsImportCard);
    }

    /**
     * ExtendedAE's providers subclass AE2's provider host, so the virtual-provider
     * tunnel path must resolve them and map their blocking mode like AE2's own.
     */
    public static void extendedAeProviderResolvesForVirtualization(GameTestHelper helper) {
        if (!CompatMods.loaded(CompatMods.EXTENDED_AE)) {
            helper.succeed();
            return;
        }
        helper.setBlock(new BlockPos(1, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("extendedae:ex_pattern_provider")));

        helper.runAfterDelay(10, () -> {
            var hostPos = helper.absolutePos(new BlockPos(1, 1, 1));
            var host = ProviderTargets.providerHostAt(helper.getLevel(), hostPos, Direction.NORTH);
            helper.assertTrue(host != null,
                    "ExtendedAE provider must resolve as a pattern provider host");
            host.getLogic().getConfigManager().putSetting(
                    appeng.api.config.Settings.BLOCKING_MODE, appeng.api.config.YesNo.YES);
            helper.assertTrue(
                    ProviderTargets.blockingModeAt(helper.getLevel(), hostPos, Direction.NORTH),
                    "blocking mode must map from the ExtendedAE provider");
            helper.succeed();
        });
    }

    /**
     * Applied Mekanistics registers its chemical key type with AE2; our mesh, tunnel,
     * and return surfaces accept it by construction (they are key-type generic), so
     * registration is the wiring that matters.
     */
    public static void appliedMekanisticsChemicalKeyTypeRegisters(GameTestHelper helper) {
        if (!CompatMods.loaded(CompatMods.APPLIED_MEKANISTICS)) {
            helper.succeed();
            return;
        }
        boolean found = false;
        for (var type : AEKeyTypes.getAll()) {
            if (type.getId().getNamespace().equals(CompatMods.APPLIED_MEKANISTICS)) {
                found = true;
            }
        }
        helper.assertTrue(found, "Applied Mekanistics must register an AE key type");
        helper.succeed();
    }

    // The two AppMekCompatHooks round-trip tests live on main only: the bridge and its
    // compile-time Mekanism dependency return when the compat suite has 26.1 ports.

    /**
     * The Pattern Import Card installs as a REAL upgrade in AE2WTLib's wireless
     * encoding terminals: the association is registered for both the dedicated
     * terminal and the universal one, and the card passes the item's own
     * upgrade-slot validation. The menu-side behavior needs no compat code at all -
     * their WETMenu subclasses AE2's encoding menu, so the instanceof already holds.
     */
    public static void wtlibWirelessEncodingTerminalSocketsImportCard(GameTestHelper helper) {
        if (!CompatMods.loaded(CompatMods.AE2WTLIB)) {
            helper.succeed();
            return;
        }
        var card = AE2Logistics.PATTERN_IMPORT_CARD.get();
        for (var id : new String[] {"ae2wtlib:wireless_pattern_encoding_terminal",
                "ae2wtlib:wireless_universal_terminal"}) {
            var terminalItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            helper.assertTrue(appeng.api.upgrades.Upgrades.getMaxInstallable(card, terminalItem) == 1,
                    id + " must accept exactly one import card");
        }
        // Physical insertion only on the dedicated terminal: the universal one sizes
        // its upgrade inventory by the terminals merged into it, and a bare stack has none.
        var wet = BuiltInRegistries.ITEM
                .getValue(Identifier.parse("ae2wtlib:wireless_pattern_encoding_terminal"));
        var upgrades = ((appeng.api.upgrades.IUpgradeableItem) wet)
                .getUpgrades(new net.minecraft.world.item.ItemStack(wet));
        var leftover = upgrades.addItems(new net.minecraft.world.item.ItemStack(card));
        helper.assertTrue(leftover.isEmpty() && upgrades.getInstalledUpgrades(card) == 1,
                "the wireless encoding terminal's upgrade slots must take the card");
        helper.succeed();
    }
}
