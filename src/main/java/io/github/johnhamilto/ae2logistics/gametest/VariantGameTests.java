package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.GatedStorageBusPart;
import io.github.johnhamilto.ae2logistics.parts.VariantExportBusPart;
import io.github.johnhamilto.ae2logistics.parts.VariantMatching;

/**
 * DESIGN F13: a configured item is a variant TEMPLATE - same item, and every
 * component the template carries must agree; components it does not carry are
 * ignored. One card, four hosts: the Gated Storage Bus and Subnet Link partition
 * by template, the Variant Import and Export Buses filter and expand by it.
 */
@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class VariantGameTests {

    private static ItemStack enchantedBook(GameTestHelper helper, String enchantId, int level) {
        var registry = helper.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var enchant = registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ENCHANTMENT, ResourceLocation.parse(enchantId)));
        return net.minecraft.world.item.EnchantedBookItem
                .createForEnchantment(new EnchantmentInstance(enchant, level));
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public void templateSemantics(GameTestHelper helper) {
        var mending = AEItemKey.of(enchantedBook(helper, "minecraft:mending", 1));
        var sharpness = AEItemKey.of(enchantedBook(helper, "minecraft:sharpness", 5));
        var plainTemplate = AEItemKey.of(new ItemStack(Items.ENCHANTED_BOOK));

        helper.assertTrue(VariantMatching.matches(plainTemplate, mending),
                "a plain template must match any variant of its item");
        helper.assertTrue(VariantMatching.matches(plainTemplate, sharpness),
                "a plain template must match every variant");
        helper.assertTrue(!VariantMatching.matches(mending, sharpness),
                "a component the template carries must agree");
        helper.assertTrue(VariantMatching.matches(mending, mending),
                "exact keys always match");

        var named = new ItemStack(Items.PAPER);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Ticket"));
        var namedAndMore = named.copy();
        namedAndMore.set(DataComponents.RARITY, net.minecraft.world.item.Rarity.EPIC);
        helper.assertTrue(VariantMatching.matches(AEItemKey.of(named), AEItemKey.of(namedAndMore)),
                "components absent from the template are ignored");
        helper.assertTrue(!VariantMatching.matches(AEItemKey.of(named), AEItemKey.of(new ItemStack(Items.PAPER))),
                "a template component missing on the candidate must refuse");
        helper.assertTrue(!VariantMatching.matches(plainTemplate, AEItemKey.of(new ItemStack(Items.BOOK))),
                "different items never match");
        helper.succeed();
    }

    private static GatedStorageBusPart gatedBusOnChest(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.NORTH, null, AE2Logistics.GATED_STORAGE_BUS_PART.get());
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        return part;
    }

    /** Gated bus + Variant Card: the partition slots hold templates, not exact keys. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void gatedBusPartitionsByTemplate(GameTestHelper helper) {
        var part = gatedBusOnChest(helper);
        part.getUpgrades().addItems(new ItemStack(AE2Logistics.VARIANT_CARD.get()));
        part.getConfig().setStack(0, new GenericStack(
                AEItemKey.of(new ItemStack(Items.ENCHANTED_BOOK)), 1));

        helper.runAfterDelay(40, () -> {
            var storage = part.getMainNode().getGrid().getStorageService().getInventory();
            var mending = AEItemKey.of(enchantedBook(helper, "minecraft:mending", 1));
            var sharpness = AEItemKey.of(enchantedBook(helper, "minecraft:sharpness", 5));
            helper.assertTrue(storage.insert(mending, 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                    "a mending book must pass the plain-book template");
            helper.assertTrue(storage.insert(sharpness, 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                    "a sharpness book must pass the plain-book template");
            helper.assertTrue(storage.insert(AEItemKey.of(Items.IRON_INGOT), 1,
                    Actionable.MODULATE, IActionSource.empty()) == 0,
                    "iron is not templated, the partition must refuse it");
            helper.succeed();
        });
    }

    /** Variant Export Bus: one template slot exports every matching stored variant. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void exportBusExpandsTemplates(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        // Network storage: a stock storage bus over a source chest holding two variants.
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.UP, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 2, 1)) instanceof ChestBlockEntity source) {
            source.setItem(0, enchantedBook(helper, "minecraft:mending", 1));
            source.setItem(1, enchantedBook(helper, "minecraft:sharpness", 5));
            source.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
        }
        // The variant export bus pushes into a target chest.
        var exportBus = (VariantExportBusPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH, null,
                AE2Logistics.VARIANT_EXPORT_BUS_PART.get());
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        exportBus.getUpgrades().addItems(new ItemStack(AE2Logistics.VARIANT_CARD.get()));
        exportBus.getConfig().setStack(0, new GenericStack(
                AEItemKey.of(new ItemStack(Items.ENCHANTED_BOOK)), 1));

        helper.runAfterDelay(200, () -> {
            var target = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 0));
            int books = 0;
            int other = 0;
            for (int i = 0; i < target.getContainerSize(); i++) {
                var stack = target.getItem(i);
                if (stack.is(Items.ENCHANTED_BOOK)) {
                    books += stack.getCount();
                } else if (!stack.isEmpty()) {
                    other += stack.getCount();
                }
            }
            helper.assertTrue(books == 2,
                    "one plain-book template must export BOTH stored variants, got " + books);
            helper.assertTrue(other == 0, "nothing outside the template may export, got " + other);
            helper.succeed();
        });
    }

    /** Variant Import Bus: the filter admits every variant of the template's item. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void importBusFiltersByTemplate(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        // Network storage target: stock storage bus over an empty chest.
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.UP, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        // The variant import bus pulls from a source chest with mixed contents.
        var importBus = PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH, null,
                AE2Logistics.VARIANT_IMPORT_BUS_PART.get());
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 0)) instanceof ChestBlockEntity source) {
            source.setItem(0, enchantedBook(helper, "minecraft:mending", 1));
            source.setItem(1, enchantedBook(helper, "minecraft:sharpness", 5));
            source.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
        }
        importBus.getUpgrades().addItems(new ItemStack(AE2Logistics.VARIANT_CARD.get()));
        importBus.getConfig().setStack(0, new GenericStack(
                AEItemKey.of(new ItemStack(Items.ENCHANTED_BOOK)), 1));

        helper.runAfterDelay(200, () -> {
            var source = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 0));
            int booksLeft = 0;
            int ironLeft = 0;
            for (int i = 0; i < source.getContainerSize(); i++) {
                var stack = source.getItem(i);
                if (stack.is(Items.ENCHANTED_BOOK)) {
                    booksLeft += stack.getCount();
                } else if (stack.is(Items.IRON_INGOT)) {
                    ironLeft += stack.getCount();
                }
            }
            helper.assertTrue(booksLeft == 0,
                    "both book variants must import through the template, " + booksLeft + " left");
            helper.assertTrue(ironLeft == 8, "iron is outside the template and must stay");
            helper.succeed();
        });
    }

    /** The card sockets in all four hosts and stays out of AE2's stock buses. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void variantCardSockets(GameTestHelper helper) {
        var card = AE2Logistics.VARIANT_CARD.get();
        helper.assertTrue(appeng.api.upgrades.Upgrades.getMaxInstallable(card,
                AE2Logistics.GATED_STORAGE_BUS_PART.get()) == 1, "gated bus must take the card");
        helper.assertTrue(appeng.api.upgrades.Upgrades.getMaxInstallable(card,
                AE2Logistics.SUBNET_LINK_PART.get()) == 1, "subnet link must take the card");
        helper.assertTrue(appeng.api.upgrades.Upgrades.getMaxInstallable(card,
                AE2Logistics.VARIANT_IMPORT_BUS_PART.get()) == 1, "variant import bus must take the card");
        helper.assertTrue(appeng.api.upgrades.Upgrades.getMaxInstallable(card,
                AE2Logistics.VARIANT_EXPORT_BUS_PART.get()) == 1, "variant export bus must take the card");
        helper.assertTrue(appeng.api.upgrades.Upgrades.getMaxInstallable(card,
                AEItems.ITEM_CELL_1K) == 0, "the card must stay out of stock hosts");
        helper.succeed();
    }
}
