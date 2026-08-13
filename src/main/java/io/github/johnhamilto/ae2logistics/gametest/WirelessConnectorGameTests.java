package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.misc.InterfaceBlockEntity;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.WirelessConnectorPart;
import io.github.johnhamilto.ae2logistics.wireless.WirelessLinkRegistry;

public class WirelessConnectorGameTests {

    static void register() {
        LogisticsTestInstance.add("connectorColorsGateLinking", "empty5", 200, WirelessConnectorGameTests::connectorColorsGateLinking);
        LogisticsTestInstance.add("parallelTrunkLeavesWirelessIdle", "empty12", 400, WirelessConnectorGameTests::parallelTrunkLeavesWirelessIdle);
        LogisticsTestInstance.add("wirelessOnlyIslandCarriesChannels", "empty12", 400, WirelessConnectorGameTests::wirelessOnlyIslandCarriesChannels);
        LogisticsTestInstance.add("rangeIsMutualReach", "empty20", 400, WirelessConnectorGameTests::rangeIsMutualReach);
        LogisticsTestInstance.add("connectorStateSurvivesNbtRoundTrip", "empty5", WirelessConnectorGameTests::connectorStateSurvivesNbtRoundTrip);
    }

    private static void place(GameTestHelper helper, BlockPos pos, String blockId) {
        helper.setBlock(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)));
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos, String cableId) {
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse(cableId));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static WirelessConnectorPart placeConnector(GameTestHelper helper, BlockPos cablePos) {
        placeCable(helper, cablePos, "ae2:fluix_glass_cable");
        return (WirelessConnectorPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(cablePos), Direction.UP, null,
                AE2Logistics.WIRELESS_CONNECTOR_PART.get());
    }

    private static boolean sameGrid(WirelessConnectorPart a, WirelessConnectorPart b) {
        return a.node() != null && b.node() != null && a.node().getGrid() == b.node().getGrid();
    }

    /**
     * Color is the pairing: fluix links to anything, dyed connectors link only to their
     * own color (and fluix). Recoloring in place relinks and unlinks live - teardown
     * splits the grids, so identity flips are observable a tick later.
     */
    public static void connectorColorsGateLinking(GameTestHelper helper) {
        helper.assertTrue(WirelessLinkRegistry.colorsCompatible(AEColor.TRANSPARENT, AEColor.RED),
                "fluix must pair with any color");
        helper.assertTrue(WirelessLinkRegistry.colorsCompatible(AEColor.RED, AEColor.RED),
                "same color must pair");
        helper.assertFalse(WirelessLinkRegistry.colorsCompatible(AEColor.RED, AEColor.BLUE),
                "different colors must not pair");

        place(helper, new BlockPos(0, 1, 0), "ae2:creative_energy_cell");
        var a = placeConnector(helper, new BlockPos(0, 1, 1));
        place(helper, new BlockPos(4, 1, 0), "ae2:creative_energy_cell");
        var b = placeConnector(helper, new BlockPos(4, 1, 1));

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(sameGrid(a, b), "fluix pair in range must fuse into one grid");
            a.applyWirelessConfig(AEColor.RED, 0);
            b.applyWirelessConfig(AEColor.WHITE, 0);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertFalse(sameGrid(a, b), "red and white must not link");
            a.applyWirelessConfig(AEColor.WHITE, 0);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(sameGrid(a, b), "white pair must relink");
            helper.succeed();
        });
    }

    /**
     * The last-resort pathing bet (DESIGN F11.8): a wireless link parallel to a dense
     * trunk carries nothing, because plain nodes ride the third strict BFS tier and
     * every island the trunk can reach is claimed by the trunk first.
     */
    public static void parallelTrunkLeavesWirelessIdle(GameTestHelper helper) {
        place(helper, new BlockPos(0, 1, 0), "ae2:controller");
        place(helper, new BlockPos(0, 2, 0), "ae2:creative_energy_cell");
        for (int x = 1; x <= 8; x++) {
            placeCable(helper, new BlockPos(x, 1, 0), "ae2:fluix_smart_dense_cable");
        }
        placeCable(helper, new BlockPos(9, 1, 0), "ae2:fluix_glass_cable");
        place(helper, new BlockPos(10, 1, 0), "ae2:interface");
        place(helper, new BlockPos(11, 1, 0), "ae2:interface");
        place(helper, new BlockPos(10, 1, 1), "ae2:interface");

        var a = placeConnector(helper, new BlockPos(0, 1, 1));
        var b = placeConnector(helper, new BlockPos(9, 1, 1));

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(sameGrid(a, b), "connectors share the controller grid");
            for (var pos : new BlockPos[] { new BlockPos(10, 1, 0), new BlockPos(11, 1, 0),
                    new BlockPos(10, 1, 1) }) {
                if (helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class) instanceof InterfaceBlockEntity iface) {
                    helper.assertTrue(iface.getMainNode().isOnline(),
                            "interface at " + pos + " must be online via the trunk");
                } else {
                    helper.fail("expected an interface at " + pos);
                }
            }
            helper.assertTrue(a.node().getUsedChannels() == 0,
                    "wireless side A must carry 0 channels, carried " + a.node().getUsedChannels());
            helper.assertTrue(b.node().getUsedChannels() == 0,
                    "wireless side B must carry 0 channels, carried " + b.node().getUsedChannels());
            helper.succeed();
        });
    }

    /** The other half of the bet: an island only wireless can reach rides the link. */
    public static void wirelessOnlyIslandCarriesChannels(GameTestHelper helper) {
        place(helper, new BlockPos(0, 1, 0), "ae2:controller");
        place(helper, new BlockPos(0, 2, 0), "ae2:creative_energy_cell");
        var a = placeConnector(helper, new BlockPos(0, 1, 1));

        var b = placeConnector(helper, new BlockPos(8, 1, 1));
        place(helper, new BlockPos(8, 1, 2), "ae2:interface");
        place(helper, new BlockPos(8, 1, 3), "ae2:interface");

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(sameGrid(a, b), "island must join the controller grid wirelessly");
            for (var pos : new BlockPos[] { new BlockPos(8, 1, 2), new BlockPos(8, 1, 3) }) {
                if (helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class) instanceof InterfaceBlockEntity iface) {
                    helper.assertTrue(iface.getMainNode().isOnline(),
                            "island interface at " + pos + " must be online through the link");
                } else {
                    helper.fail("expected an interface at " + pos);
                }
            }
            helper.assertTrue(a.node().getUsedChannels() == 2,
                    "the wireless hop must carry the island's 2 channels, carried "
                            + a.node().getUsedChannels());
            helper.succeed();
        });
    }

    /**
     * Range is mutual reach (min of the two ranges): 18 blocks apart, base range 16 on
     * AE2's booster curve (16 + boosters^1.5). Nobody links at 0 boosters; boosting ONE
     * side to 18.8 still fails the mutual rule; boosting both links.
     */
    public static void rangeIsMutualReach(GameTestHelper helper) {
        place(helper, new BlockPos(0, 1, 0), "ae2:creative_energy_cell");
        var a = placeConnector(helper, new BlockPos(0, 1, 1));
        place(helper, new BlockPos(18, 1, 0), "ae2:creative_energy_cell");
        var b = placeConnector(helper, new BlockPos(18, 1, 1));

        helper.runAfterDelay(20, () -> {
            helper.assertFalse(sameGrid(a, b), "18 blocks exceeds the 16 base range");
            a.applyWirelessConfig(AEColor.TRANSPARENT, 2);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertFalse(sameGrid(a, b),
                    "one boosted side must not link: reach is min of the two ranges");
            b.applyWirelessConfig(AEColor.TRANSPARENT, 2);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(sameGrid(a, b), "both sides boosted to ~18.8 must link at 18");
            helper.succeed();
        });
    }

    /** Color and boosters survive the part NBT round-trip (cable-bus save path). */
    public static void connectorStateSurvivesNbtRoundTrip(GameTestHelper helper) {
        var part = placeConnector(helper, new BlockPos(1, 1, 1));
        part.applyWirelessConfig(AEColor.MAGENTA, 3);

        var registries = helper.getLevel().registryAccess();
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, registries);
        part.writeToNBT(out);
        var fresh = new WirelessConnectorPart(AE2Logistics.WIRELESS_CONNECTOR_PART.get());
        fresh.readFromNBT(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, out.buildResult()));

        helper.assertTrue(fresh.color() == AEColor.MAGENTA, "color must survive NBT");
        helper.assertTrue(fresh.boosters() == 3, "boosters must survive NBT");
        helper.succeed();
    }
}
