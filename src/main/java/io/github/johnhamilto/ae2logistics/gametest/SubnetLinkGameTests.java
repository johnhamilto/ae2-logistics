package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubnetLinkGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static SubnetLinkPart placeLink(GameTestHelper helper, BlockPos pos, Direction side) {
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null,
                AE2Logistics.SUBNET_LINK_PART.get());
        helper.assertTrue(part != null, "subnet link placement failed");
        return part;
    }

    private static int chestCount(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
        var be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof ChestBlockEntity, "no chest at " + pos);
        int total = 0;
        var chest = (ChestBlockEntity) be;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                total += chest.getItem(i).getCount();
            }
        }
        return total;
    }

    /** The face carries a REAL subnet: separate grid, powered through the link. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public void subnetLinkCarriesRealSubnet(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        var link = placeLink(helper, new BlockPos(1, 1, 1), Direction.UP);
        placeCable(helper, new BlockPos(1, 2, 1));
        helper.setBlock(new BlockPos(1, 3, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:interface")));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(link.subnetGrid() != null, "subnet grid missing");
            helper.assertTrue(link.subnetGrid() != link.mainGrid(),
                    "subnet must be a separate grid");
            var iface = helper.getBlockEntity(new BlockPos(1, 3, 1));
            helper.assertTrue(iface instanceof appeng.blockentity.misc.InterfaceBlockEntity i
                    && i.getMainNode().isOnline(),
                    "subnet interface must be powered THROUGH the link");
            helper.succeed();
        });
    }

    /** Default mode: the subnet sees the main network's storage through the window. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public void subnetLinkWindowsMainStorage(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        // Main-side storage: a storage bus onto a chest holding iron.
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.EAST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 1)))
                .setItem(0, new ItemStack(Items.IRON_INGOT, 10));

        var link = placeLink(helper, new BlockPos(1, 1, 1), Direction.UP);
        placeCable(helper, new BlockPos(1, 2, 1));

        helper.runAfterDelay(60, () -> {
            var subnet = link.subnetGrid();
            helper.assertTrue(subnet != null, "subnet grid missing");
            var inventory = subnet.getStorageService().getInventory();
            var counter = new KeyCounter();
            inventory.getAvailableStacks(counter);
            long seen = counter.get(AEItemKey.of(Items.IRON_INGOT));
            helper.assertTrue(seen == 10, "subnet must see main's 10 iron, saw " + seen);

            long extracted = inventory.extract(AEItemKey.of(Items.IRON_INGOT), 4,
                    Actionable.MODULATE, IActionSource.empty());
            helper.assertTrue(extracted == 4, "subnet must extract through the window, got " + extracted);
            helper.assertTrue(chestCount(helper, new BlockPos(3, 1, 1), Items.IRON_INGOT) == 6,
                    "main chest must drain to 6");
            helper.succeed();
        });
    }

    /** Flipped mode: the main network sees the subnet's storage. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public void subnetLinkExposesSubnetToMain(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        var link = placeLink(helper, new BlockPos(1, 1, 1), Direction.UP);
        placeCable(helper, new BlockPos(1, 2, 1));
        // Subnet-side storage: a REAL storage bus onto a chest.
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)),
                Direction.NORTH, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(1, 2, 0), Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0)))
                .setItem(0, new ItemStack(Items.GOLD_INGOT, 7));

        helper.runAfterDelay(20, () -> link.applyConfig(SubnetLinkPart.MODE_MAIN_SEES_SUBNET, 5));

        helper.runAfterDelay(60, () -> {
            var main = link.mainGrid();
            helper.assertTrue(main != null, "main grid missing");
            var counter = new KeyCounter();
            main.getStorageService().getInventory().getAvailableStacks(counter);
            long seen = counter.get(AEItemKey.of(Items.GOLD_INGOT));
            helper.assertTrue(seen == 7, "main must see the subnet's 7 gold, saw " + seen);
            helper.succeed();
        });
    }
}
