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
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.JobMonitorPart;

public class JobMonitorGameTests {

    static void register() {
        LogisticsTestInstance.add("jobMonitorTracksActiveStallAndCancel", "empty5", 600, JobMonitorGameTests::jobMonitorTracksActiveStallAndCancel);
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    /**
     * A job whose provider has nowhere to push sits busy at zero progress: the monitor
     * must report it active, then stalled once the window passes, then idle after cancel.
     */
    public static void jobMonitorTracksActiveStallAndCancel(GameTestHelper helper) {
        var level = helper.getLevel();

        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(level, helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST, null,
                (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:1k_crafting_storage")));
        // The provider has no adjacent inventory, so pushes fail forever - a stall.
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:pattern_provider")));

        var monitor = (JobMonitorPart) PartHelper.setPart(level,
                helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, null,
                AE2Logistics.JOB_MONITOR_PART.get());
        monitor.applyMonitorConfig("craft", 1);

        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern.encode(pattern,
                java.util.List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                java.util.List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                java.util.List.of(io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec.EXACT));
        if (helper.getBlockEntity(new BlockPos(2, 1, 3), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe) {
            providerBe.getLogic().getPatternInv().setItemDirect(0, pattern);
        } else {
            helper.fail("no pattern provider");
        }

        var job = new Object() {
            java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> future;
            boolean submitted;
        };
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    helper.assertTrue(monitor.channelValue("active") == 0,
                            "no job yet: craft:active must be 0");
                    var grid = monitor.getMainNode().getGrid();
                    var source = new appeng.me.helpers.MachineSource(monitor);
                    job.future = grid.getCraftingService().beginCraftingCalculation(level,
                            () -> source, AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    try {
                        var plan = job.future.get(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (plan.simulation()) {
                            helper.fail("plan incomplete");
                        }
                        if (!job.submitted) {
                            var grid = monitor.getMainNode().getGrid();
                            var result = grid.getCraftingService().submitJob(plan, null, null, true,
                                    new appeng.me.helpers.MachineSource(monitor));
                            if (!result.successful()) {
                                throw helper.assertionException(
                                        "submit failed: " + result.errorCode());
                            }
                            job.submitted = true;
                        }
                    } catch (java.util.concurrent.TimeoutException e) {
                        throw helper.assertionException("planning");
                    } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(monitor.channelValue("active") == 1,
                            "craft:active must be 1 while the job runs, got "
                                    + monitor.channelValue("active"));
                    helper.assertTrue(monitor.channelValue("pending") >= 1,
                            "craft:pending must count the outstanding items");
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(monitor.channelValue("stalled") == 1,
                            "craft:stalled must flag the stuck job, got "
                                    + monitor.channelValue("stalled"));
                    var grid = monitor.getMainNode().getGrid();
                    for (var cpu : grid.getCraftingService().getCpus()) {
                        cpu.cancelJob();
                    }
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(monitor.channelValue("active") == 0,
                            "craft:active must drop to 0 after cancel, got "
                                    + monitor.channelValue("active"));
                    helper.assertTrue(monitor.channelValue("stalled") == 0,
                            "craft:stalled must clear after cancel");
                    helper.succeed();
                })
                .thenSucceed();
    }
}
