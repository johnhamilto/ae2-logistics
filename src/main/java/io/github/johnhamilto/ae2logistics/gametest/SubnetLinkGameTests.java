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

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart;

public class SubnetLinkGameTests {

    static void register() {
        LogisticsTestInstance.add("subnetLinkCarriesRealSubnet", "empty5", 300, SubnetLinkGameTests::subnetLinkCarriesRealSubnet);
        LogisticsTestInstance.add("subnetLinkMountsSubnetOnMain", "empty5", 300, SubnetLinkGameTests::subnetLinkMountsSubnetOnMain);
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static SubnetLinkPart placeLink(GameTestHelper helper, BlockPos pos, Direction side) {
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null,
                AE2Logistics.SUBNET_LINK_PART.get());
        helper.assertTrue(part != null, "subnet link placement failed");
        return part;
    }

    private static int chestCount(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
        var be = helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class);
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
    public static void subnetLinkCarriesRealSubnet(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        var link = placeLink(helper, new BlockPos(1, 1, 1), Direction.UP);
        placeCable(helper, new BlockPos(1, 2, 1));
        helper.setBlock(new BlockPos(1, 3, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:interface")));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(link.subnetGrid() != null, "subnet grid missing");
            helper.assertTrue(link.subnetGrid() != link.mainGrid(),
                    "subnet must be a separate grid");
            var iface = helper.getBlockEntity(new BlockPos(1, 3, 1), net.minecraft.world.level.block.entity.BlockEntity.class);
            helper.assertTrue(iface instanceof appeng.blockentity.misc.InterfaceBlockEntity i
                    && i.getMainNode().isOnline(),
                    "subnet interface must be powered THROUGH the link");
            helper.succeed();
        });
    }

    /** The storage-bus half: the main network mounts the subnet's storage. */
    public static void subnetLinkMountsSubnetOnMain(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        var link = placeLink(helper, new BlockPos(1, 1, 1), Direction.UP);
        placeCable(helper, new BlockPos(1, 2, 1));
        // Subnet-side storage: a REAL storage bus onto a chest.
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)),
                Direction.NORTH, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(1, 2, 0), Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0), net.minecraft.world.level.block.entity.BlockEntity.class))
                .setItem(0, new ItemStack(Items.GOLD_INGOT, 7));

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
