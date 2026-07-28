package io.github.johnhamilto.ae2logistics.gametest;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class ConfigTerminalGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static void placeAePart(GameTestHelper helper, BlockPos pos, Direction side, String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null, (IPartItem<?>) item);
    }

    @org.jetbrains.annotations.Nullable
    private static ConfigDeviceIndex.Device find(java.util.List<ConfigDeviceIndex.Device> devices,
            String typeId, int skip) {
        for (var device : devices) {
            if (device.typeId().equals(typeId) && skip-- == 0) {
                return device;
            }
        }
        return null;
    }

    /** The index finds AE2 devices and ours, cycles generic settings, and sets priorities. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public void configIndexEnumeratesAndEdits(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeAePart(helper, new BlockPos(1, 1, 1), Direction.UP, "ae2:export_bus");
        placeAePart(helper, new BlockPos(1, 1, 1), Direction.NORTH, "ae2:storage_bus");
        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.DOWN, null,
                AE2Logistics.CONSTANT_PART.get());
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.GUARDED_PROVIDER.get());

        helper.runAfterDelay(60, () -> {
            var grid = constant.getMainNode().getGrid();
            helper.assertTrue(grid != null, "no grid");
            var devices = ConfigDeviceIndex.enumerate(grid);

            var exportBus = find(devices, "ae2:export_bus", 0);
            var storageBus = find(devices, "ae2:storage_bus", 0);
            var provider = find(devices, "ae2logistics:guarded_pattern_provider", 0);
            var logicPart = find(devices, "ae2logistics:constant", 0);
            helper.assertTrue(exportBus != null, "export bus must be indexed");
            helper.assertTrue(storageBus != null, "storage bus must be indexed");
            helper.assertTrue(provider != null, "our provider must be indexed");
            helper.assertTrue(logicPart != null, "our logic part must be indexed (transferable)");

            var manager = exportBus.configManager();
            helper.assertTrue(manager != null && !manager.getSettings().isEmpty(),
                    "export bus must expose generic settings");
            var settingName = manager.getSettings().iterator().next().getName();
            var before = manager.exportSettings().get(settingName);
            helper.assertTrue(ConfigDeviceIndex.cycleSetting(exportBus, settingName),
                    "cycling must apply");
            var after = manager.exportSettings().get(settingName);
            helper.assertTrue(!Objects.equals(before, after),
                    "setting '" + settingName + "' must change, still " + after);

            helper.assertTrue(storageBus.priorityHost() != null, "storage bus has priority");
            storageBus.priorityHost().setPriority(42);
            helper.assertTrue(storageBus.priorityHost().getPriority() == 42, "priority must stick");
            helper.succeed();
        });
    }

    /** Copy one device's memory-card settings and fan them out to every same-type device. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public void configCopyPasteAllPropagates(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeAePart(helper, new BlockPos(1, 1, 1), Direction.UP, "ae2:export_bus");
        placeAePart(helper, new BlockPos(1, 1, 1), Direction.NORTH, "ae2:export_bus");
        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.DOWN, null,
                AE2Logistics.CONSTANT_PART.get());

        helper.runAfterDelay(60, () -> {
            var grid = constant.getMainNode().getGrid();
            var devices = ConfigDeviceIndex.enumerate(grid);
            var source = find(devices, "ae2:export_bus", 0);
            var target = find(devices, "ae2:export_bus", 1);
            helper.assertTrue(source != null && target != null, "both buses must be indexed");

            // Make the source differ from defaults on every cycleable setting.
            var manager = source.configManager();
            for (var setting : manager.getSettings()) {
                ConfigDeviceIndex.cycleSetting(source, setting.getName());
            }
            var expected = manager.exportSettings();
            helper.assertTrue(!expected.equals(target.configManager().exportSettings()),
                    "buses must differ before the paste");

            var clipboard = source.export(null);
            helper.assertTrue(clipboard != null, "export must produce settings");
            for (var device : devices) {
                if (device.valid() && device.typeId().equals(source.typeId())) {
                    device.importFrom(clipboard, null);
                }
            }
            helper.assertTrue(expected.equals(target.configManager().exportSettings()),
                    "paste-all must propagate settings to the second bus");
            helper.succeed();
        });
    }
}
