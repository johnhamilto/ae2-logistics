package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEItems;
import appeng.parts.storagebus.StorageBusPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.GatedStorageBusPart;

/**
 * DESIGN F12: the Gated Storage Bus applies input cards at its mount. Conform Card
 * accepts only keys the target already holds (live, fuzzy widens, inverter flips);
 * Stack Limiter Card delivers items one at a time into an item-empty target. The
 * stock storage bus deliberately does NOT take our cards (its card handling is
 * hardcoded upstream; an inert socketed card would look like a working one).
 */
public class InputCardGameTests {

    static void register() {
        LogisticsTestInstance.add("conformGatesBySeededContents", "empty5", 200, InputCardGameTests::conformGatesBySeededContents);
        LogisticsTestInstance.add("conformInverterCollectsNewOnly", "empty5", 200, InputCardGameTests::conformInverterCollectsNewOnly);
        LogisticsTestInstance.add("conformFuzzyWidens", "empty5", 200, InputCardGameTests::conformFuzzyWidens);
        LogisticsTestInstance.add("limiterDripFeedsSingles", "empty5", 200, InputCardGameTests::limiterDripFeedsSingles);
        LogisticsTestInstance.add("conformIntersectsPartition", "empty5", 200, InputCardGameTests::conformIntersectsPartition);
        LogisticsTestInstance.add("cardsSocketViaSlotValidation", "empty5", 200, InputCardGameTests::cardsSocketViaSlotValidation);
    }

    private static GatedStorageBusPart buildGatedBusOnChest(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.NORTH, null, AE2Logistics.GATED_STORAGE_BUS_PART.get());
        helper.assertTrue(part != null, "gated storage bus placement failed");
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        return part;
    }

    private static ChestBlockEntity chest(GameTestHelper helper) {
        var be = helper.getBlockEntity(new BlockPos(1, 1, 0), ChestBlockEntity.class);
        helper.assertTrue(be != null, "no chest");
        return be;
    }

    private static MEStorage networkStorage(GameTestHelper helper, GatedStorageBusPart part) {
        var grid = part.getMainNode().getGrid();
        helper.assertTrue(grid != null, "bus must be on a grid");
        return grid.getStorageService().getInventory();
    }

    private static long insert(GameTestHelper helper, GatedStorageBusPart part,
            net.minecraft.world.item.Item item, long amount) {
        return networkStorage(helper, part).insert(AEItemKey.of(item), amount,
                Actionable.MODULATE, IActionSource.empty());
    }

    /** Conform Card: the chest's live contents ARE the filter; empty target accepts nothing. */
    public static void conformGatesBySeededContents(GameTestHelper helper) {
        var part = buildGatedBusOnChest(helper);
        part.getUpgrades().addItems(new ItemStack(AE2Logistics.CONFORM_CARD.get()));
        chest(helper).setItem(0, new ItemStack(Items.IRON_INGOT, 4));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(insert(helper, part, Items.GOLD_INGOT, 8) == 0,
                    "gold is not in the chest, conform must refuse it");
            helper.assertTrue(insert(helper, part, Items.IRON_INGOT, 8) == 8,
                    "iron is in the chest, conform must accept it");

            // Extract the seed type to zero and its door closes - live contents, no memory.
            var storage = networkStorage(helper, part);
            storage.extract(AEItemKey.of(Items.IRON_INGOT), 12, Actionable.MODULATE,
                    IActionSource.empty());
            helper.assertTrue(insert(helper, part, Items.IRON_INGOT, 1) == 0,
                    "emptied type must close its door");
            helper.succeed();
        });
    }

    /** Conform + Inverter: accept only what is NOT present - a self-deduplicating chest. */
    public static void conformInverterCollectsNewOnly(GameTestHelper helper) {
        var part = buildGatedBusOnChest(helper);
        part.getUpgrades().addItems(new ItemStack(AE2Logistics.CONFORM_CARD.get()));
        part.getUpgrades().addItems(new ItemStack(AEItems.INVERTER_CARD));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(insert(helper, part, Items.IRON_INGOT, 1) == 1,
                    "inverted conform on an empty chest must accept a new type");
            helper.assertTrue(insert(helper, part, Items.IRON_INGOT, 1) == 0,
                    "the type is now present, inverted conform must refuse seconds");
            helper.assertTrue(insert(helper, part, Items.GOLD_INGOT, 1) == 1,
                    "a different new type must still be accepted");
            helper.succeed();
        });
    }

    /** Conform + Fuzzy: the contains-check widens exactly as partitions widen. */
    public static void conformFuzzyWidens(GameTestHelper helper) {
        var part = buildGatedBusOnChest(helper);
        part.getUpgrades().addItems(new ItemStack(AE2Logistics.CONFORM_CARD.get()));
        part.getUpgrades().addItems(new ItemStack(AEItems.FUZZY_CARD));
        part.getConfigManager().putSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        var damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(50);
        chest(helper).setItem(0, damaged);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(insert(helper, part, Items.IRON_PICKAXE, 1) == 1,
                    "fuzzy conform must accept a pristine pickaxe next to a damaged one");
            helper.assertTrue(insert(helper, part, Items.GOLD_INGOT, 1) == 0,
                    "fuzzy widens matches, it does not open the gate");
            helper.succeed();
        });
    }

    /** Stack Limiter: one item at a time, only while the target holds no items. */
    public static void limiterDripFeedsSingles(GameTestHelper helper) {
        var part = buildGatedBusOnChest(helper);
        part.getUpgrades().addItems(new ItemStack(AE2Logistics.STACK_LIMITER_CARD.get()));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(insert(helper, part, Items.IRON_INGOT, 64) == 1,
                    "a stack offered to an empty chest must deliver exactly one item");
            helper.assertTrue(insert(helper, part, Items.IRON_INGOT, 64) == 0,
                    "an occupied chest must refuse until it drains");
            chest(helper).clearContent();
        });
        // The bus's view of DIRECT world edits refreshes on its tick (network-side
        // extractions show immediately - see conformGatesBySeededContents).
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(insert(helper, part, Items.IRON_INGOT, 64) == 1,
                    "a drained chest must accept the next single");
            helper.succeed();
        });
    }

    /** Conform intersects the partition slots; it never overrides them. */
    public static void conformIntersectsPartition(GameTestHelper helper) {
        var part = buildGatedBusOnChest(helper);
        part.getUpgrades().addItems(new ItemStack(AE2Logistics.CONFORM_CARD.get()));
        part.getConfig().addFilter(Items.IRON_INGOT);
        var c = chest(helper);
        c.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        c.setItem(1, new ItemStack(Items.GOLD_INGOT, 4));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(insert(helper, part, Items.GOLD_INGOT, 1) == 0,
                    "gold is present but outside the partition, must refuse");
            helper.assertTrue(insert(helper, part, Items.IRON_INGOT, 1) == 1,
                    "iron is present and partitioned, must accept");
            helper.succeed();
        });
    }

    /**
     * Card sockets validate against registered associations (the 0.37.0 fix: Subnet
     * Link never registered any, so no card could physically be inserted in-game).
     * Our cards deliberately stay OUT of the stock storage bus.
     */
    public static void cardsSocketViaSlotValidation(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        var link = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.UP, null, AE2Logistics.SUBNET_LINK_PART.get());
        var gated = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.NORTH, null, AE2Logistics.GATED_STORAGE_BUS_PART.get());
        var stockItem = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        var stock = (StorageBusPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.SOUTH, null,
                (IPartItem<?>) stockItem);

        helper.runAfterDelay(20, () -> {
            var leftover = link.getUpgrades().addItems(new ItemStack(AEItems.FUZZY_CARD));
            helper.assertTrue(leftover.isEmpty()
                    && link.getUpgrades().getInstalledUpgrades(AEItems.FUZZY_CARD) == 1,
                    "subnet link must accept AE2's fuzzy card through slot validation");
            leftover = gated.getUpgrades().addItems(new ItemStack(AE2Logistics.CONFORM_CARD.get()));
            helper.assertTrue(leftover.isEmpty()
                    && gated.getUpgrades().getInstalledUpgrades(AE2Logistics.CONFORM_CARD.get()) == 1,
                    "gated bus must accept the conform card");
            leftover = stock.getUpgrades().addItems(new ItemStack(AE2Logistics.CONFORM_CARD.get()));
            helper.assertTrue(!leftover.isEmpty()
                    && stock.getUpgrades().getInstalledUpgrades(AE2Logistics.CONFORM_CARD.get()) == 0,
                    "the STOCK storage bus must reject our cards - inert sockets lie");
            helper.succeed();
        });
    }
}
