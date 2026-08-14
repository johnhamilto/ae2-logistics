package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingTerminalPart;

import io.github.johnhamilto.ae2logistics.item.PatternImportCard;

/**
 * Pattern Import Card: installed PER TERMINAL, not carried. The cable part takes
 * the card via the sneak-click install (stored on the cable side, exercised here
 * through the same install/uninstall seam the click handler calls); with it
 * installed, the open encoding window's blank-pattern slot restocks from network
 * storage. Without it - even with cards in the player's pockets - nothing moves.
 */
public class PatternImportGameTests {

    static void register() {
        LogisticsTestInstance.add("cardRestocksBlankPatterns", "empty5", 300, PatternImportGameTests::cardRestocksBlankPatterns);
    }

    public static void cardRestocksBlankPatterns(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        var terminalItem = BuiltInRegistries.ITEM
                .getValue(Identifier.parse("ae2:pattern_encoding_terminal"));
        var terminal = (PatternEncodingTerminalPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH, null,
                (IPartItem<?>) terminalItem);
        helper.assertTrue(terminal != null, "encoding terminal placement failed");
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.EAST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class))
                .setItem(0, AEItems.BLANK_PATTERN.stack(32));

        helper.runAfterDelay(60, () -> {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new PatternEncodingTermMenu(1, player.getInventory(), terminal);
            player.containerMenu = menu;
            var blankSlot = menu.getSlots(SlotSemantics.BLANK_PATTERN).get(0);

            // A card in the player's pockets is NOT an install.
            player.getInventory().setItem(0, new ItemStack(
                    io.github.johnhamilto.ae2logistics.AE2Logistics.PATTERN_IMPORT_CARD.get()));
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "a pocketed card must not feed the terminal");
            helper.assertTrue(blankSlot.getItem().isEmpty(), "slot must still be empty");

            // Installed on the part (the sneak-click seam), the terminal feeds itself.
            helper.assertTrue(!PatternImportCard.installedOnPart(terminal),
                    "terminal must start uninstalled");
            PatternImportCard.installOnPart(terminal);
            helper.assertTrue(PatternImportCard.installedOnPart(terminal),
                    "install must stick on the cable side");
            int delivered = PatternImportCard.topUp(player);
            helper.assertTrue(delivered == 8, "the card must pull a batch of 8, got " + delivered);
            helper.assertTrue(AEItems.BLANK_PATTERN.is(blankSlot.getItem())
                    && blankSlot.getItem().getCount() == 8,
                    "the blank slot must hold the pulled batch");

            // A stocked slot is left alone.
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "a stocked slot must not be topped up");

            // Uninstall closes the tap even with an empty slot and a stocked network.
            blankSlot.set(ItemStack.EMPTY);
            PatternImportCard.uninstallFromPart(terminal);
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "an uninstalled terminal must not restock");
            helper.succeed();
        });
    }
}
