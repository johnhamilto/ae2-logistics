package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.DenseWapBlockEntity;
import io.github.johnhamilto.ae2logistics.block.WirelessBridgeBlockEntity;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class WirelessGameTests {

    private static final String EMPTY = "empty5";

    private static WirelessBridgeBlockEntity bridge(GameTestHelper helper, BlockPos pos) {
        var be = helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class);
        helper.assertTrue(be instanceof WirelessBridgeBlockEntity, "no bridge at " + pos);
        return (WirelessBridgeBlockEntity) be;
    }

    private static DenseWapBlockEntity wap(GameTestHelper helper, BlockPos pos) {
        var be = helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class);
        helper.assertTrue(be instanceof DenseWapBlockEntity, "no dense WAP at " + pos);
        return (DenseWapBlockEntity) be;
    }

    private static void anchor(GameTestHelper helper, BlockPos bridgePos, BlockPos wapPos) {
        bridge(helper, bridgePos).setAnchor(
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(wapPos)));
    }

    /**
     * The headline flow: a chest behind a storage bus on the bridge's cable segment,
     * with no cable path to the main network, becomes part of the main network's
     * storage purely through WAP coverage.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void bridgeCarriesStorageThroughCoverage(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(0, 1, 1), AE2Logistics.DENSE_WAP.get());

        helper.setBlock(new BlockPos(3, 1, 3), AE2Logistics.WIRELESS_BRIDGE.get());
        var cablePos = helper.absolutePos(new BlockPos(2, 1, 3));
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), cablePos, null, null, (IPartItem<?>) cable);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 3), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), cablePos, Direction.WEST, null, (IPartItem<?>) storageBus);

        helper.startSequence()
                .thenExecuteAfter(20, () -> anchor(helper, new BlockPos(3, 1, 3), new BlockPos(0, 1, 1)))
                .thenExecuteAfter(120, () -> {
                    var bridge = bridge(helper, new BlockPos(3, 1, 3));
                    var wap = wap(helper, new BlockPos(0, 1, 1));
                    helper.assertTrue(bridge.isLinked(), "bridge must link inside coverage");
                    helper.assertTrue(bridge.grid() == wap.getGrid(),
                            "bridge segment must join the WAP's grid");
                    var stored = wap.getGrid().getStorageService().getCachedInventory()
                            .get(AEItemKey.of(Items.IRON_INGOT));
                    helper.assertTrue(stored == 10,
                            "main network must see 10 iron through the bridge, got " + stored);
                })
                .thenSucceed();
    }

    /** Coverage is binary: out of range is dark, and range changes take effect live. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void bridgeRespectsRangeBinary(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(0, 1, 1), AE2Logistics.DENSE_WAP.get());
        helper.setBlock(new BlockPos(4, 1, 4), AE2Logistics.WIRELESS_BRIDGE.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    wap(helper, new BlockPos(0, 1, 1)).setRange(2);
                    anchor(helper, new BlockPos(4, 1, 4), new BlockPos(0, 1, 1));
                })
                .thenExecuteAfter(60, () -> {
                    var bridge = bridge(helper, new BlockPos(4, 1, 4));
                    helper.assertTrue(!bridge.isLinked(), "bridge must be dark out of range");
                    helper.assertTrue(bridge.grid() != wap(helper, new BlockPos(0, 1, 1)).getGrid(),
                            "grids must stay separate out of coverage");
                    wap(helper, new BlockPos(0, 1, 1)).setRange(32);
                })
                .thenExecuteAfter(60, () -> {
                    var bridge = bridge(helper, new BlockPos(4, 1, 4));
                    helper.assertTrue(bridge.isLinked(), "bridge must link when range covers it");
                    helper.assertTrue(bridge.grid() == wap(helper, new BlockPos(0, 1, 1)).getGrid(),
                            "grids must fuse in coverage");
                    wap(helper, new BlockPos(0, 1, 1)).setRange(2);
                })
                .thenExecuteAfter(60, () -> {
                    var bridge = bridge(helper, new BlockPos(4, 1, 4));
                    helper.assertTrue(!bridge.isLinked(), "bridge must go dark when coverage shrinks");
                })
                .thenSucceed();
    }

    /** Losing the serving access point re-associates the bridge to another in range. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void bridgeHandsOverBetweenAccessPoints(GameTestHelper helper) {
        var cablePos = helper.absolutePos(new BlockPos(0, 1, 2));
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), cablePos, null, null, (IPartItem<?>) cable);
        helper.setBlock(new BlockPos(1, 1, 2),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(0, 1, 1), AE2Logistics.DENSE_WAP.get());
        helper.setBlock(new BlockPos(0, 1, 3), AE2Logistics.DENSE_WAP.get());
        helper.setBlock(new BlockPos(2, 1, 1), AE2Logistics.WIRELESS_BRIDGE.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> anchor(helper, new BlockPos(2, 1, 1), new BlockPos(0, 1, 3)))
                .thenExecuteAfter(60, () -> {
                    var bridge = bridge(helper, new BlockPos(2, 1, 1));
                    helper.assertTrue(bridge.isLinked(), "bridge must link");
                    helper.assertTrue(helper.absolutePos(new BlockPos(0, 1, 1)).equals(bridge.linkedApPos()),
                            "bridge must pick the nearest access point, got " + bridge.linkedApPos());
                    helper.setBlock(new BlockPos(0, 1, 1), Blocks.AIR);
                })
                .thenExecuteAfter(60, () -> {
                    var bridge = bridge(helper, new BlockPos(2, 1, 1));
                    helper.assertTrue(bridge.isLinked(), "bridge must hand over after AP loss");
                    helper.assertTrue(helper.absolutePos(new BlockPos(0, 1, 3)).equals(bridge.linkedApPos()),
                            "bridge must re-associate to the surviving AP, got " + bridge.linkedApPos());
                    helper.assertTrue(bridge.grid() == wap(helper, new BlockPos(0, 1, 3)).getGrid(),
                            "bridge must be on the surviving AP's grid");
                })
                .thenSucceed();
    }

    /** AE2's own Wireless Access Point serves bridges - existing towers become logistics. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void ae2AccessPointServesBridge(GameTestHelper helper) {
        // Surround the WAP with energy cells so its orientation-dependent back face
        // always touches a grid; the bridge follows whichever grid it joined.
        var wapPos = new BlockPos(1, 2, 1);
        helper.setBlock(wapPos,
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:wireless_access_point")));
        for (var dir : Direction.values()) {
            helper.setBlock(wapPos.relative(dir),
                    BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        }
        helper.setBlock(new BlockPos(3, 1, 3), AE2Logistics.WIRELESS_BRIDGE.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> anchor(helper, new BlockPos(3, 1, 3), wapPos))
                .thenExecuteAfter(80, () -> {
                    var be = helper.getBlockEntity(wapPos, net.minecraft.world.level.block.entity.BlockEntity.class);
                    helper.assertTrue(be instanceof IWirelessAccessPoint, "no AE2 WAP");
                    var wap = (IWirelessAccessPoint) be;
                    helper.assertTrue(wap.isActive(), "AE2 WAP must be active");
                    var bridge = bridge(helper, new BlockPos(3, 1, 3));
                    helper.assertTrue(bridge.isLinked(), "bridge must link through AE2's WAP");
                    helper.assertTrue(bridge.grid() == wap.getGrid(),
                            "bridge must join the AE2 WAP's grid");
                })
                .thenSucceed();
    }
}
