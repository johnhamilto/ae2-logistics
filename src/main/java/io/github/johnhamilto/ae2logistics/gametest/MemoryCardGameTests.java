package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.util.SettingsFrom;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public class MemoryCardGameTests {

    static void register() {
        LogisticsTestInstance.add("memoryCardRoundTripsLogicConfig", "empty5", 200, MemoryCardGameTests::memoryCardRoundTripsLogicConfig);
        LogisticsTestInstance.add("memoryCardRoundTripsMeshConfig", "empty5", 200, MemoryCardGameTests::memoryCardRoundTripsMeshConfig);
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    /** Memory-card settings round-trip: full logic config plus the sensor's watched key. */
    public static void memoryCardRoundTripsLogicConfig(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 1));

        var sourceConstant = (LogicPart) PartHelper.setPart(level, pos, Direction.UP, null,
                AE2Logistics.CONSTANT_PART.get());
        var targetConstant = (LogicPart) PartHelper.setPart(level, pos, Direction.NORTH, null,
                AE2Logistics.CONSTANT_PART.get());
        var sourceSensor = (LogicPart) PartHelper.setPart(level, pos, Direction.SOUTH, null,
                AE2Logistics.STOCK_SENSOR_PART.get());
        var targetSensor = (LogicPart) PartHelper.setPart(level, pos, Direction.EAST, null,
                AE2Logistics.STOCK_SENSOR_PART.get());

        helper.runAfterDelay(10, () -> {
            sourceConstant.applyConfig(Identifier.parse("test:card"), null, null, 3, 42, 7, true);
            var constantSettings = sourceConstant.exportSettings(SettingsFrom.MEMORY_CARD);
            targetConstant.importSettings(SettingsFrom.MEMORY_CARD, constantSettings, null);
            helper.assertTrue(Identifier.parse("test:card").equals(targetConstant.writtenChannelRaw()),
                    "output channel must round-trip");
            helper.assertTrue(targetConstant.valueARaw() == 42 && targetConstant.valueBRaw() == 7
                    && targetConstant.opRaw() == 3 && targetConstant.flagRaw(),
                    "op, values, and flag must round-trip");

            sourceSensor.applyConfig(Identifier.parse("test:stock"), null, null, 0, 0, 0, false);
            sourceSensor.setWatchedKey(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
            var sensorSettings = sourceSensor.exportSettings(SettingsFrom.MEMORY_CARD);
            targetSensor.importSettings(SettingsFrom.MEMORY_CARD, sensorSettings, null);
            var watched = targetSensor.watchedKey();
            helper.assertTrue(watched != null && watched.what().equals(AEItemKey.of(Items.IRON_INGOT)),
                    "the sensor's watched key must round-trip, got " + watched);
            helper.succeed();
        });
    }

    /** Mesh endpoint settings round-trip and re-register under the new frequency. */
    public static void memoryCardRoundTripsMeshConfig(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 1));

        var source = (MeshEndpointPart) PartHelper.setPart(level, pos, Direction.UP, null,
                AE2Logistics.MESH_ENDPOINT_PART.get());
        var target = (MeshEndpointPart) PartHelper.setPart(level, pos, Direction.NORTH, null,
                AE2Logistics.MESH_ENDPOINT_PART.get());

        helper.runAfterDelay(10, () -> {
            source.applyMeshConfig("mc-mesh", MeshEndpointPart.ROLE_OUT, 7,
                    MeshRegistry.TYPE_ITEM | MeshRegistry.TYPE_FLUID);

            var settings = source.exportSettings(SettingsFrom.MEMORY_CARD);
            target.importSettings(SettingsFrom.MEMORY_CARD, settings, null);

            helper.assertTrue(target.frequency().equals("mc-mesh"), "frequency must round-trip");
            helper.assertTrue(target.role() == MeshEndpointPart.ROLE_OUT, "role must round-trip");
            helper.assertTrue(target.priority() == 7, "priority must round-trip");
            helper.assertTrue(target.capabilityMask() == (MeshRegistry.TYPE_ITEM | MeshRegistry.TYPE_FLUID),
                    "capabilities must round-trip");
            helper.assertTrue(MeshRegistry.endpoints("mc-mesh").size() == 2,
                    "the pasted endpoint must register on the frequency");
            helper.succeed();
        });
    }
}
