package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import io.github.johnhamilto.ae2logistics.block.DenseWapBlockEntity;
import io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity;
import io.github.johnhamilto.ae2logistics.block.LogicCoreBlockEntity;
import io.github.johnhamilto.ae2logistics.block.RegisterBankBlockEntity;
import io.github.johnhamilto.ae2logistics.block.WirelessBridgeBlockEntity;
import io.github.johnhamilto.ae2logistics.parts.JobMonitorPart;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class TestDebtGameTests {

    /**
     * Names a crafting cluster the way an anvil rename would: the private customName on
     * the storage's block entity feeds CraftingCPUCluster.updateName().
     */
    private static void nameCluster(GameTestHelper helper, BlockPos storagePos, String name) {
        var be = helper.getBlockEntity(storagePos, net.minecraft.world.level.block.entity.BlockEntity.class);
        try {
            var field = appeng.blockentity.AEBaseBlockEntity.class.getDeclaredField("customName");
            field.setAccessible(true);
            field.set(be, Component.literal(name));
        } catch (ReflectiveOperationException e) {
            helper.fail("cannot set custom name: " + e);
        }
    }

    /**
     * Closes two untested-list items at once: a maintenance rule actually RUNS on a
     * "maint" CPU (only the deferral half was testable before there was a way to name
     * a cluster), and the Job Monitor publishes per-named-CPU channels.
     */
    @GameTest(template = "empty5", timeoutTicks = 900)
    public void namedCpuPoolsAndMonitorChannels(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        for (var pos : new BlockPos[] {new BlockPos(2, 1, 1), new BlockPos(2, 1, 2)}) {
            PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null,
                    (IPartItem<?>) cable);
        }
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 32));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.WEST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:pattern_provider")));
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.JOB_SCHEDULER.get());
        var monitor = (JobMonitorPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, null,
                AE2Logistics.JOB_MONITOR_PART.get());
        monitor.applyMonitorConfig("craft", 5);

        var table = new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1);
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    nameCluster(helper, new BlockPos(2, 2, 1), "maint 1");
                    var scheduler = (JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2), net.minecraft.world.level.block.entity.BlockEntity.class);
                    var grid = scheduler.getMainNode().getGrid();
                    for (var cpu : grid.getCraftingService().getCpus()) {
                        if (cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster cluster) {
                            cluster.updateName();
                        }
                        helper.assertTrue(cpu.getName() != null
                                && cpu.getName().getString().equals("maint 1"),
                                "cluster must carry the custom name");
                    }
                    if (helper.getBlockEntity(new BlockPos(2, 1, 3), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity provider) {
                        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
                        io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern.encode(pattern,
                                java.util.List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                                java.util.List.of(table),
                                java.util.List.of(io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec.EXACT));
                        provider.getLogic().getPatternInv().setItemDirect(0, pattern);
                        provider.getLogic().updatePatterns();
                    } else {
                        helper.fail("no provider");
                    }
                    scheduler.setRuleTarget(0, table);
                    scheduler.applyRuleConfig(0, 2, 1, JobSchedulerBlockEntity.CLASS_MAINT, null,
                            0, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        ((JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2), net.minecraft.world.level.block.entity.BlockEntity.class))
                                .ruleState(0) == JobSchedulerBlockEntity.STATE_RUNNING,
                        "maint rule must run on the maint-named CPU"))
                .thenWaitUntil(() -> {
                    var scheduler = (JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2), net.minecraft.world.level.block.entity.BlockEntity.class);
                    var signals = scheduler.getMainNode().getGrid().getService(SignalService.class);
                    long remaining = signals.get(Identifier.parse("craft:maint_1/remaining"));
                    helper.assertTrue(remaining >= 1,
                            "monitor must publish craft:maint_1/remaining, got " + remaining);
                })
                .thenSucceed();
    }

    /**
     * Save/load coverage for every block entity's configuration: serialize a configured
     * BE, load the tag into a freshly placed one (before its first tick), and compare -
     * the same code path a world reload exercises.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void blockEntitiesRoundTripNbt(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.setBlock(new BlockPos(0, 1, 0), AE2Logistics.LOGIC_CORE.get());
        helper.setBlock(new BlockPos(0, 1, 2), AE2Logistics.JOB_SCHEDULER.get());
        helper.setBlock(new BlockPos(0, 1, 4), AE2Logistics.REGISTER_BANK.get());
        helper.setBlock(new BlockPos(0, 3, 0), AE2Logistics.WIRELESS_BRIDGE.get());
        helper.setBlock(new BlockPos(0, 3, 2), AE2Logistics.DENSE_WAP.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var core = (LogicCoreBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class);
                    core.configureEntry(0, LogicPartType.CONSTANT.ordinal(), "t:a", "", "", 0, 42, 0, false);
                    core.configureEntry(1, LogicPartType.STOCK_SENSOR.ordinal(), "t:w", "", "", 0, 0, 0, false);
                    core.setEntryWatched(1, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));

                    var scheduler = (JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 2), net.minecraft.world.level.block.entity.BlockEntity.class);
                    scheduler.setRuleTarget(0, new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1));
                    scheduler.applyRuleConfig(0, 5, 2, JobSchedulerBlockEntity.CLASS_MAINT,
                            Identifier.parse("g:x"), 30, true);

                    var bank = (RegisterBankBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 4), net.minecraft.world.level.block.entity.BlockEntity.class);
                    bank.setSignal(Identifier.parse("t:x"), 9);

                    var bridge = (WirelessBridgeBlockEntity) helper.getBlockEntity(new BlockPos(0, 3, 0), net.minecraft.world.level.block.entity.BlockEntity.class);
                    bridge.setAnchor(GlobalPos.of(helper.getLevel().dimension(),
                            helper.absolutePos(new BlockPos(0, 3, 2))));

                    var wap = (DenseWapBlockEntity) helper.getBlockEntity(new BlockPos(0, 3, 2), net.minecraft.world.level.block.entity.BlockEntity.class);
                    wap.setRange(7);

                    // Save each, place a twin, and load before the twin's first tick.
                    var coreTag = core.saveWithFullMetadata(registries);
                    var schedulerTag = scheduler.saveWithFullMetadata(registries);
                    var bankTag = bank.saveWithFullMetadata(registries);
                    var bridgeTag = bridge.saveWithFullMetadata(registries);
                    var wapTag = wap.saveWithFullMetadata(registries);

                    helper.setBlock(new BlockPos(4, 1, 0), AE2Logistics.LOGIC_CORE.get());
                    helper.setBlock(new BlockPos(4, 1, 2), AE2Logistics.JOB_SCHEDULER.get());
                    helper.setBlock(new BlockPos(4, 1, 4), AE2Logistics.REGISTER_BANK.get());
                    helper.setBlock(new BlockPos(4, 3, 0), AE2Logistics.WIRELESS_BRIDGE.get());
                    helper.setBlock(new BlockPos(4, 3, 2), AE2Logistics.DENSE_WAP.get());

                    helper.getBlockEntity(new BlockPos(4, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class).loadWithComponents(coreTag, registries);
                    helper.getBlockEntity(new BlockPos(4, 1, 2), net.minecraft.world.level.block.entity.BlockEntity.class).loadWithComponents(schedulerTag, registries);
                    helper.getBlockEntity(new BlockPos(4, 1, 4), net.minecraft.world.level.block.entity.BlockEntity.class).loadWithComponents(bankTag, registries);
                    helper.getBlockEntity(new BlockPos(4, 3, 0), net.minecraft.world.level.block.entity.BlockEntity.class).loadWithComponents(bridgeTag, registries);
                    helper.getBlockEntity(new BlockPos(4, 3, 2), net.minecraft.world.level.block.entity.BlockEntity.class).loadWithComponents(wapTag, registries);
                })
                .thenExecuteAfter(20, () -> {
                    var core = (LogicCoreBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class);
                    helper.assertTrue(core.entry(0).type() == LogicPartType.CONSTANT
                            && core.entry(0).valueARaw() == 42, "core entry 0 must round-trip");
                    var watched = core.entry(1).watchedKey();
                    helper.assertTrue(core.entry(1).type() == LogicPartType.STOCK_SENSOR
                            && watched != null && watched.what().equals(AEItemKey.of(Items.IRON_INGOT)),
                            "core entry 1 + watched key must round-trip");

                    var scheduler = (JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 2), net.minecraft.world.level.block.entity.BlockEntity.class);
                    var rule = scheduler.rule(0);
                    helper.assertTrue(rule.target != null
                            && rule.target.what().equals(AEItemKey.of(Items.CRAFTING_TABLE))
                            && rule.floor == 5 && rule.batch == 2
                            && rule.jobClass == JobSchedulerBlockEntity.CLASS_MAINT
                            && Identifier.parse("g:x").equals(rule.guard)
                            && rule.deadlineSeconds == 30 && rule.preempt,
                            "scheduler rule must round-trip");

                    var bank = (RegisterBankBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4), net.minecraft.world.level.block.entity.BlockEntity.class);
                    helper.assertTrue(bank.getSignal(Identifier.parse("t:x")) == 9,
                            "bank signal must round-trip");

                    var bridge = (WirelessBridgeBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 0), net.minecraft.world.level.block.entity.BlockEntity.class);
                    helper.assertTrue(bridge.anchor() != null
                            && bridge.anchor().pos().equals(helper.absolutePos(new BlockPos(0, 3, 2))),
                            "bridge anchor must round-trip");

                    var wap = (DenseWapBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 2), net.minecraft.world.level.block.entity.BlockEntity.class);
                    helper.assertTrue(wap.getRange() == 7, "WAP range must round-trip");
                })
                .thenSucceed();
    }
}
