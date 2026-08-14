package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingTerminalPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.item.PatternImportCard;

/**
 * Pattern Import Card: with the card in the player's inventory and an encoding
 * window open, the blank-pattern slot restocks from network storage. The test
 * drives the same {@code topUp} the player-tick hook calls.
 */
@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class PatternImportGameTests {

    @GameTest(template = "empty5", timeoutTicks = 300)
    public void cardRestocksBlankPatterns(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        var terminalItem = BuiltInRegistries.ITEM
                .get(ResourceLocation.parse("ae2:pattern_encoding_terminal"));
        var terminal = (PatternEncodingTerminalPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH, null,
                (IPartItem<?>) terminalItem);
        helper.assertTrue(terminal != null, "encoding terminal placement failed");
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.EAST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 1)))
                .setItem(0, AEItems.BLANK_PATTERN.stack(32));

        helper.runAfterDelay(60, () -> {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new PatternEncodingTermMenu(1, player.getInventory(), terminal);
            player.containerMenu = menu;
            var blankSlot = menu.getSlots(SlotSemantics.BLANK_PATTERN).get(0);

            // No card: nothing happens.
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "without the card the slot must stay empty");
            helper.assertTrue(blankSlot.getItem().isEmpty(), "slot must still be empty");

            player.getInventory().setItem(0, new ItemStack(AE2Logistics.PATTERN_IMPORT_CARD.get()));
            int delivered = PatternImportCard.topUp(player);
            helper.assertTrue(delivered == 8, "the card must pull a batch of 8, got " + delivered);
            helper.assertTrue(AEItems.BLANK_PATTERN.is(blankSlot.getItem())
                    && blankSlot.getItem().getCount() == 8,
                    "the blank slot must hold the pulled batch");

            // A stocked slot is left alone - no topping up past the batch.
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "a stocked slot must not be topped up");

            // Drain the network: the card can only deliver what storage holds.
            var chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 1));
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.STICK));
            blankSlot.set(ItemStack.EMPTY);
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "an empty network must deliver nothing");
            helper.succeed();
        });
    }
}
