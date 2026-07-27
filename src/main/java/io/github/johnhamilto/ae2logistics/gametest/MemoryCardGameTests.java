package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class MemoryCardGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    /** Memory-card settings round-trip: full logic config plus the sensor's watched key. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void memoryCardRoundTripsLogicConfig(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
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
            sourceConstant.applyConfig(ResourceLocation.parse("test:card"), null, null, 3, 42, 7, true);
            var constantSettings = sourceConstant.exportSettings(SettingsFrom.MEMORY_CARD);
            targetConstant.importSettings(SettingsFrom.MEMORY_CARD, constantSettings, null);
            helper.assertTrue(ResourceLocation.parse("test:card").equals(targetConstant.writtenChannelRaw()),
                    "output channel must round-trip");
            helper.assertTrue(targetConstant.valueARaw() == 42 && targetConstant.valueBRaw() == 7
                    && targetConstant.opRaw() == 3 && targetConstant.flagRaw(),
                    "op, values, and flag must round-trip");

            sourceSensor.applyConfig(ResourceLocation.parse("test:stock"), null, null, 0, 0, 0, false);
            sourceSensor.setWatchedKey(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
            var sensorSettings = sourceSensor.exportSettings(SettingsFrom.MEMORY_CARD);
            targetSensor.importSettings(SettingsFrom.MEMORY_CARD, sensorSettings, null);
            var watched = targetSensor.watchedKey();
            helper.assertTrue(watched != null && watched.what().equals(AEItemKey.of(Items.IRON_INGOT)),
                    "the sensor's watched key must round-trip, got " + watched);
            helper.succeed();
        });
    }

    /** Mesh endpoint settings incl. filters round-trip and re-register under the new frequency. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void memoryCardRoundTripsMeshConfig(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
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
            source.setFilterSlot(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
            source.setFilterSlot(4, new GenericStack(AEFluidKey.of(Fluids.WATER), 1));

            var settings = source.exportSettings(SettingsFrom.MEMORY_CARD);
            target.importSettings(SettingsFrom.MEMORY_CARD, settings, null);

            helper.assertTrue(target.frequency().equals("mc-mesh"), "frequency must round-trip");
            helper.assertTrue(target.role() == MeshEndpointPart.ROLE_OUT, "role must round-trip");
            helper.assertTrue(target.priority() == 7, "priority must round-trip");
            helper.assertTrue(target.capabilityMask() == (MeshRegistry.TYPE_ITEM | MeshRegistry.TYPE_FLUID),
                    "capabilities must round-trip");
            var slot0 = target.filterSlot(0);
            var slot4 = target.filterSlot(4);
            helper.assertTrue(slot0 != null && slot0.what().equals(AEItemKey.of(Items.IRON_INGOT)),
                    "item filter must round-trip");
            helper.assertTrue(slot4 != null && slot4.what().equals(AEFluidKey.of(Fluids.WATER)),
                    "fluid filter must round-trip");
            helper.assertTrue(target.filterSlot(1) == null, "empty slots must stay empty");
            helper.assertTrue(MeshRegistry.endpoints("mc-mesh").size() == 2,
                    "the pasted endpoint must register on the frequency");
            helper.succeed();
        });
    }
}
