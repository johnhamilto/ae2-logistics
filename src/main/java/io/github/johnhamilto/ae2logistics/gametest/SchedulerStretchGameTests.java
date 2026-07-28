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
import io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class SchedulerStretchGameTests {

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
     * Stall plot: planks in storage, one CPU, a provider with nowhere to push, and the
     * scheduler at (1,1,2). Jobs submit fine and then stall forever.
     */
    private static void buildStallPlot(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 32));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.WEST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.JOB_SCHEDULER.get());
    }

    private static JobSchedulerBlockEntity scheduler(GameTestHelper helper) {
        var be = helper.getBlockEntity(new BlockPos(1, 1, 2));
        helper.assertTrue(be instanceof JobSchedulerBlockEntity, "no scheduler");
        return (JobSchedulerBlockEntity) be;
    }

    private static void armProvider(GameTestHelper helper) {
        if (helper.getBlockEntity(new BlockPos(2, 1, 3)) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity provider) {
            provider.getLogic().getPatternInv().setItemDirect(0, tablePattern());
            provider.getLogic().updatePatterns();
        } else {
            helper.fail("no provider");
        }
    }

    /** The watchdog evicts a job that overruns its deadline, freeing the CPU. */
    @GameTest(template = "empty5", timeoutTicks = 900)
    public void schedulerDeadlineEvictsStalledJob(GameTestHelper helper) {
        buildStallPlot(helper);

        var table = new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1);
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    armProvider(helper);
                    var scheduler = scheduler(helper);
                    scheduler.setRuleTarget(0, table);
                    scheduler.applyRuleConfig(0, 2, 1, JobSchedulerBlockEntity.CLASS_BULK, null,
                            2, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        scheduler(helper).ruleState(0) == JobSchedulerBlockEntity.STATE_RUNNING,
                        "waiting for submission"))
                .thenWaitUntil(() -> helper.assertTrue(
                        scheduler(helper).ruleState(0) == JobSchedulerBlockEntity.STATE_DEADLINE,
                        "waiting for the watchdog to evict"))
                .thenWaitUntil(() -> {
                    var grid = scheduler(helper).getMainNode().getGrid();
                    for (var cpu : grid.getCraftingService().getCpus()) {
                        helper.assertTrue(!cpu.isBusy(), "CPU must free after eviction");
                    }
                })
                .thenSucceed();
    }

    /**
     * A high-priority rule with preemption bumps the youngest same-class job of a
     * lower-priority rule off the only CPU.
     */
    @GameTest(template = "empty5", timeoutTicks = 900)
    public void schedulerPreemptsYoungerSameClass(GameTestHelper helper) {
        buildStallPlot(helper);

        var table = new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1);
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    armProvider(helper);
                    var scheduler = scheduler(helper);
                    scheduler.setRuleTarget(3, table);
                    scheduler.applyRuleConfig(3, 2, 1, JobSchedulerBlockEntity.CLASS_BULK, null,
                            0, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        scheduler(helper).ruleState(3) == JobSchedulerBlockEntity.STATE_RUNNING,
                        "low-priority rule must take the CPU first"))
                .thenExecute(() -> {
                    var scheduler = scheduler(helper);
                    scheduler.setRuleTarget(0, table);
                    scheduler.applyRuleConfig(0, 2, 1, JobSchedulerBlockEntity.CLASS_BULK, null,
                            0, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        scheduler(helper).ruleState(0) == JobSchedulerBlockEntity.STATE_RUNNING,
                        "preempting rule must end up running"))
                .thenExecute(() -> helper.assertTrue(
                        scheduler(helper).ruleState(3) == JobSchedulerBlockEntity.STATE_PREEMPTED,
                        "victim must show bumped, got " + scheduler(helper).ruleState(3)))
                .thenSucceed();
    }

    /** Rules ride TransferableSettings: Config Terminal copy/paste and blueprints. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void schedulerRulesTransferViaSettings(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(1, 2, 1), AE2Logistics.JOB_SCHEDULER.get());
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.JOB_SCHEDULER.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var source = (JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
                    var target = (JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2));
                    source.setRuleTarget(0, new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1));
                    source.applyRuleConfig(0, 5, 2, JobSchedulerBlockEntity.CLASS_MAINT,
                            ResourceLocation.parse("g:x"), 30, true);
                    target.importTransferSettings(source.exportTransferSettings(null), null);

                    var rule = target.rule(0);
                    helper.assertTrue(rule.target != null
                            && rule.target.what().equals(AEItemKey.of(Items.CRAFTING_TABLE)),
                            "target must transfer");
                    helper.assertTrue(rule.floor == 5 && rule.batch == 2, "floor/batch must transfer");
                    helper.assertTrue(rule.jobClass == JobSchedulerBlockEntity.CLASS_MAINT,
                            "class must transfer");
                    helper.assertTrue(ResourceLocation.parse("g:x").equals(rule.guard),
                            "guard must transfer");
                    helper.assertTrue(rule.deadlineSeconds == 30 && rule.preempt,
                            "deadline and preempt must transfer");
                })
                .thenSucceed();
    }
}
