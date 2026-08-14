package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.parts.encoding.PatternEncodingTerminalPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.item.PatternImportCard;

/**
 * Pattern Import Card: installed in a host's UPGRADE SLOTS, the only surface.
 * The cable part has no upgrade slots upstream, so a menu on the real part never
 * feeds (pinned below); a host WITH the card in its upgrade inventory - the shape
 * AE2WTLib's wireless terminals provide - restocks the blank slot from storage.
 * The upgraded host here wraps the real part (real grid, logic, and storage) and
 * fakes only the upgrade inventory, exactly the delta a wireless terminal adds.
 */
public class PatternImportGameTests {

    static void register() {
        LogisticsTestInstance.add("cardRestocksBlankPatterns", "empty5", 300, PatternImportGameTests::cardRestocksBlankPatterns);
    }

    /**
     * The real part with a one-slot upgrade inventory holding the import card.
     * Implements IPart by delegation because AEBaseMenu requires its host to be a
     * part, block entity, or item host.
     */
    private record UpgradedHost(PatternEncodingTerminalPart part, IUpgradeInventory upgrades)
            implements IPatternTerminalMenuHost, appeng.api.parts.IPart {
        @Override
        public appeng.api.parts.IPartItem<?> getPartItem() {
            return part.getPartItem();
        }

        @Override
        public appeng.api.networking.IGridNode getGridNode() {
            return part.getGridNode();
        }

        @Override
        public void setPartHostInfo(Direction side, appeng.api.parts.IPartHost host,
                net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        }

        @Override
        public float getCableConnectionLength(appeng.api.util.AECableType cable) {
            return part.getCableConnectionLength(cable);
        }

        @Override
        public void getBoxes(appeng.api.parts.IPartCollisionHelper bch) {
            part.getBoxes(bch);
        }

        @Override
        public PatternEncodingLogic getLogic() {
            return part.getLogic();
        }

        @Override
        public MEStorage getInventory() {
            return part.getInventory();
        }

        @Override
        public IConfigManager getConfigManager() {
            return part.getConfigManager();
        }

        @Override
        public IUpgradeInventory getUpgrades() {
            return upgrades;
        }

        @Override
        public appeng.api.storage.ILinkStatus getLinkStatus() {
            return appeng.api.storage.ILinkStatus.ofConnected();
        }

        @Override
        public void returnToMainMenu(Player player, ISubMenu subMenu) {
        }

        @Override
        public ItemStack getMainMenuIcon() {
            return new ItemStack(AE2Logistics.PATTERN_IMPORT_CARD.get());
        }
    }

    /** One-slot upgrade inventory holding the import card (BaseInternalInventory supplies the platform wrapper). */
    private static final class CardInstalled extends appeng.api.inventories.BaseInternalInventory
            implements IUpgradeInventory {
        private ItemStack card = new ItemStack(AE2Logistics.PATTERN_IMPORT_CARD.get());

        @Override
        public net.minecraft.world.level.ItemLike getUpgradableItem() {
            return AE2Logistics.PATTERN_IMPORT_CARD.get();
        }

        @Override
        public int getMaxInstalled(net.minecraft.world.level.ItemLike upgradeCard) {
            return upgradeCard.asItem() == AE2Logistics.PATTERN_IMPORT_CARD.get() ? 1 : 0;
        }

        @Override
        public int getInstalledUpgrades(net.minecraft.world.level.ItemLike upgradeCard) {
            return !card.isEmpty() && card.is(upgradeCard.asItem()) ? 1 : 0;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return card;
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            card = stack;
        }

        @Override
        public void readFromNBT(net.minecraft.world.level.storage.ValueInput input, String subtag) {
        }

        @Override
        public void writeToNBT(net.minecraft.world.level.storage.ValueOutput output, String subtag) {
        }
    }

    private static IUpgradeInventory cardInstalled() {
        return new CardInstalled();
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

            // The real part host has NO upgrade slots: never feeds, pocketed cards or not.
            var bareMenu = new PatternEncodingTermMenu(1, player.getInventory(), terminal);
            player.containerMenu = bareMenu;
            player.getInventory().setItem(0,
                    new ItemStack(AE2Logistics.PATTERN_IMPORT_CARD.get()));
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "the cable part has no upgrade slots, it must never feed");
            helper.assertTrue(bareMenu.getSlots(SlotSemantics.BLANK_PATTERN).get(0)
                    .getItem().isEmpty(), "slot must still be empty");

            // The same part behind a host WITH the card in its upgrade slots feeds.
            var upgraded = new UpgradedHost(terminal, cardInstalled());
            var menu = new PatternEncodingTermMenu(2, player.getInventory(), upgraded);
            player.containerMenu = menu;
            var blankSlot = menu.getSlots(SlotSemantics.BLANK_PATTERN).get(0);
            int delivered = PatternImportCard.topUp(player);
            helper.assertTrue(delivered == 8, "the card must pull a batch of 8, got " + delivered);
            helper.assertTrue(AEItems.BLANK_PATTERN.is(blankSlot.getItem())
                    && blankSlot.getItem().getCount() == 8,
                    "the blank slot must hold the pulled batch");

            // A stocked slot is left alone.
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "a stocked slot must not be topped up");

            // Drain the network: the card can only deliver what storage holds.
            var chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class);
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.STICK));
            blankSlot.set(ItemStack.EMPTY);
            helper.assertTrue(PatternImportCard.topUp(player) == 0,
                    "an empty network must deliver nothing");
            helper.succeed();
        });
    }
}
