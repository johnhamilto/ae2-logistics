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

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity;
import io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity;
import io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;
import io.github.johnhamilto.ae2logistics.item.ConfigBlueprintItem;
import io.github.johnhamilto.ae2logistics.parts.ConfigTerminalPart;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class SchedulerPolicyGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static ItemStack tablePattern() {
        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(pattern,
                java.util.List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                java.util.List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                java.util.List.of(AdaptiveInputSpec.EXACT));
        return pattern;
    }

    /**
     * Admission control end to end: with a complete plan but no CPU nothing is
     * submitted; when a CPU appears the bulk rule runs on it, while the maintenance
     * rule stays deferred because no "maint" CPU exists, and a guarded rule holds.
     */
    @GameTest(template = "empty5", timeoutTicks = 900)
    public void schedulerAdmissionClassPoolsAndGuard(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 32));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST,
                null, (IPartItem<?>) storageBus);
        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 1)), Direction.DOWN, null,
                AE2Logistics.CONSTANT_PART.get());
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.BARREL);
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.JOB_SCHEDULER.get());

        var scheduler = new Object() {
            JobSchedulerBlockEntity be;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    if (helper.getBlockEntity(new BlockPos(2, 1, 3)) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity provider) {
                        provider.getLogic().getPatternInv().setItemDirect(0, tablePattern());
                        provider.getLogic().updatePatterns();
                    } else {
                        helper.fail("no provider");
                    }
                    scheduler.be = (JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2));
                    constant.applyConfig(ResourceLocation.parse("g:sched"), null, null, 0, 0, 0, false);

                    var table = new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1);
                    scheduler.be.setRuleTarget(0, table);
                    scheduler.be.applyRuleConfig(0, 2, 1, JobSchedulerBlockEntity.CLASS_BULK, null);
                    scheduler.be.setRuleTarget(1, table);
                    scheduler.be.applyRuleConfig(1, 2, 1, JobSchedulerBlockEntity.CLASS_MAINT, null);
                    scheduler.be.setRuleTarget(2, table);
                    scheduler.be.applyRuleConfig(2, 2, 1, JobSchedulerBlockEntity.CLASS_BULK,
                            ResourceLocation.parse("g:sched"));
                })
                .thenExecuteAfter(120, () -> {
                    helper.assertTrue(scheduler.be.ruleState(0) == JobSchedulerBlockEntity.STATE_NO_CPU,
                            "bulk rule must defer with no CPU, state " + scheduler.be.ruleState(0));
                    helper.assertTrue(scheduler.be.ruleState(1) == JobSchedulerBlockEntity.STATE_NO_CPU,
                            "maint rule must defer with no CPU, state " + scheduler.be.ruleState(1));
                    helper.assertTrue(scheduler.be.ruleState(2) == JobSchedulerBlockEntity.STATE_GUARD_HOLD,
                            "guarded rule must hold, state " + scheduler.be.ruleState(2));
                    var grid = scheduler.be.getMainNode().getGrid();
                    for (var cpu : grid.getCraftingService().getCpus()) {
                        helper.assertTrue(!cpu.isBusy(), "nothing may be submitted without admission");
                    }
                    helper.setBlock(new BlockPos(2, 2, 1),
                            BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
                })
                .thenWaitUntil(() -> {
                    if (scheduler.be.ruleState(0) != JobSchedulerBlockEntity.STATE_RUNNING) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException(
                                "waiting for bulk rule to run, state " + scheduler.be.ruleState(0));
                    }
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(scheduler.be.ruleState(1) != JobSchedulerBlockEntity.STATE_RUNNING,
                            "maint rule must never take the unnamed CPU");
                    helper.assertTrue(scheduler.be.ruleState(2) == JobSchedulerBlockEntity.STATE_GUARD_HOLD,
                            "guarded rule must still hold");
                    var grid = scheduler.be.getMainNode().getGrid();
                    int busy = 0;
                    for (var cpu : grid.getCraftingService().getCpus()) {
                        if (cpu.isBusy()) {
                            busy++;
                        }
                    }
                    helper.assertTrue(busy == 1, "exactly the bulk job may run, busy=" + busy);
                    helper.succeed();
                })
                .thenSucceed();
    }

    /** The blueprint captures a region's device settings and reapplies them elsewhere. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void blueprintRoundTripsRegionConfig(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeAePart(helper, new BlockPos(1, 1, 1), Direction.UP, "ae2:export_bus");
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.GUARDED_PROVIDER.get());

        helper.setBlock(new BlockPos(3, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(3, 1, 1));
        placeAePart(helper, new BlockPos(3, 1, 1), Direction.UP, "ae2:export_bus");
        helper.setBlock(new BlockPos(3, 1, 2), AE2Logistics.GUARDED_PROVIDER.get());

        helper.runAfterDelay(80, () -> {
            var level = helper.getLevel();

            // Diverge the source region from defaults.
            var sourceHost = (appeng.api.parts.IPartHost) helper.getBlockEntity(new BlockPos(1, 1, 1));
            var sourceBus = (appeng.parts.AEBasePart) sourceHost.getPart(Direction.UP);
            var busConfigurable = (appeng.api.util.IConfigurableObject) sourceBus;
            for (var setting : busConfigurable.getConfigManager().getSettings()) {
                setting.setFromString(busConfigurable.getConfigManager(),
                        nextValue(busConfigurable.getConfigManager(), setting));
            }
            var sourceProvider = (GuardedPatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2));
            sourceProvider.applyGuardConfig(ResourceLocation.parse("bp:guard"), 3, 77, false,
                    ResourceLocation.parse("bp:prio"), 9);

            var entries = ConfigBlueprintItem.capture(level,
                    helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(1, 1, 2)));
            helper.assertTrue(entries.size() >= 2,
                    "capture must include the bus and the provider, got " + entries.size());

            var result = ConfigBlueprintItem.apply(level, helper.absolutePos(new BlockPos(3, 1, 1)),
                    entries, null);
            helper.assertTrue(result[0] >= 2, "apply must configure both devices, applied " + result[0]);

            var targetHost = (appeng.api.parts.IPartHost) helper.getBlockEntity(new BlockPos(3, 1, 1));
            var targetBus = (appeng.api.util.IConfigurableObject) targetHost.getPart(Direction.UP);
            helper.assertTrue(busConfigurable.getConfigManager().exportSettings()
                    .equals(targetBus.getConfigManager().exportSettings()),
                    "export bus settings must round-trip through the blueprint");

            var targetProvider = (GuardedPatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 2));
            helper.assertTrue(ResourceLocation.parse("bp:guard").equals(targetProvider.guardChannel()),
                    "guard channel must round-trip, got " + targetProvider.guardChannel());
            helper.assertTrue(targetProvider.guardOp() == 3 && targetProvider.guardValue() == 77
                    && !targetProvider.gateExecution(),
                    "guard details must round-trip");
            helper.assertTrue(ResourceLocation.parse("bp:prio").equals(targetProvider.priorityChannel()),
                    "priority channel must round-trip");
            helper.succeed();
        });
    }

    private static String nextValue(appeng.api.util.IConfigManager manager,
            appeng.api.config.Setting<?> setting) {
        var values = new java.util.ArrayList<>(setting.getValues());
        var current = manager.exportSettings().get(setting.getName());
        int index = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).name().equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }
        return values.get((index + 1) % values.size()).name();
    }

    private static void placeAePart(GameTestHelper helper, BlockPos pos, Direction side, String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null, (IPartItem<?>) item);
    }

    /** Snapshots flag changed devices; untouched devices stay clean. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public void snapshotDiffFlagsChanges(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeAePart(helper, new BlockPos(1, 1, 1), Direction.UP, "ae2:export_bus");
        placeAePart(helper, new BlockPos(1, 1, 1), Direction.NORTH, "ae2:storage_bus");
        var terminal = (ConfigTerminalPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.SOUTH, null,
                AE2Logistics.CONFIG_TERMINAL_PART.get());

        helper.runAfterDelay(60, () -> {
            var grid = terminal.getMainNode().getGrid();
            var devices = ConfigDeviceIndex.enumerate(grid);
            terminal.takeSnapshot(devices);

            var clean = ConfigDeviceIndex.computeDiff(terminal.snapshot(), devices);
            helper.assertTrue(clean.values().stream()
                    .allMatch(code -> code == ConfigDeviceIndex.DIFF_SAME),
                    "fresh snapshot must diff clean");

            ConfigDeviceIndex.Device exportBus = null;
            ConfigDeviceIndex.Device storageBus = null;
            for (var device : devices) {
                if (device.typeId().equals("ae2:export_bus")) {
                    exportBus = device;
                }
                if (device.typeId().equals("ae2:storage_bus")) {
                    storageBus = device;
                }
            }
            helper.assertTrue(exportBus != null && storageBus != null, "devices missing");
            var settingName = exportBus.configManager().getSettings().iterator().next().getName();
            ConfigDeviceIndex.cycleSetting(exportBus, settingName, 1);

            var diff = ConfigDeviceIndex.computeDiff(terminal.snapshot(), devices);
            helper.assertTrue(diff.get(ConfigDeviceIndex.snapshotKey(exportBus))
                    == ConfigDeviceIndex.DIFF_CHANGED, "cycled bus must flag CHANGED");
            helper.assertTrue(diff.get(ConfigDeviceIndex.snapshotKey(storageBus))
                    == ConfigDeviceIndex.DIFF_SAME, "untouched bus must stay SAME");
            helper.succeed();
        });
    }
}
