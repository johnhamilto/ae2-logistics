package io.github.johnhamilto.ae2logistics.gametest;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.MachineSource;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;
import io.github.johnhamilto.ae2logistics.crafting.GuardedPattern;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class GuardedCraftingGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static ItemStack plainPattern(ItemStack input, int inputCount, ItemStack output) {
        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(pattern,
                List.of(new GenericStack(AEItemKey.of(input.getItem()), inputCount)),
                List.of(new GenericStack(AEItemKey.of(output.getItem()), 1)),
                List.of(AdaptiveInputSpec.EXACT));
        return pattern;
    }

    private static int countItems(GameTestHelper helper, BlockPos pos) {
        int count = 0;
        if (helper.getBlockEntity(pos) instanceof BaseContainerBlockEntity container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                count += container.getItem(i).getCount();
            }
        }
        return count;
    }

    /** Charged certus + redstone + glowstone dropped into water become Regulus. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void regulusFormsInWater(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);
        var charged = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:charged_certus_quartz_crystal"));
        helper.spawnItem(charged, 2.5f, 1.5f, 2.5f);
        helper.spawnItem(Items.REDSTONE, 2.5f, 1.5f, 2.5f);
        helper.spawnItem(Items.GLOWSTONE_DUST, 2.5f, 1.5f, 2.5f);

        helper.succeedWhen(() -> helper.assertItemEntityPresent(
                AE2Logistics.REGULUS_CRYSTAL.get(), new BlockPos(2, 1, 2), 2.0));
    }

    /**
     * Standard guarded-provider plot: energy, cable spine with storage bus over a source
     * chest, crafting storage, a Constant part driving the guard channel, and the guarded
     * provider with a barrel push target. Returns the provider BE.
     */
    private record Plot(GuardedPatternProviderBlockEntity provider, LogicPart constant) {
    }

    private Plot buildPlot(GameTestHelper helper, ItemStack sourceItems) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, sourceItems);
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST,
                null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));

        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 1)), Direction.DOWN, null,
                AE2Logistics.CONSTANT_PART.get());

        helper.setBlock(new BlockPos(2, 1, 2), AE2Logistics.GUARDED_PROVIDER.get());
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.BARREL);
        var be = helper.getBlockEntity(new BlockPos(2, 1, 2));
        helper.assertTrue(be instanceof GuardedPatternProviderBlockEntity, "guarded provider BE missing");
        return new Plot((GuardedPatternProviderBlockEntity) be, constant);
    }

    private static ICraftingPlan awaitPlan(Future<ICraftingPlan> future) {
        try {
            return future.get(0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new GameTestAssertException("planning");
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** A false guard hides patterns from the planner; flipping it true re-indexes them. */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void guardHidesPatternsFromPlanning(GameTestHelper helper) {
        var plot = buildPlot(helper, new ItemStack(Items.OAK_PLANKS, 8));
        var state = new Object() {
            Future<ICraftingPlan> future;
            boolean firstChecked;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    plot.constant().applyConfig(ResourceLocation.parse("g:enable"), null, null, 0, 0, 0, false);
                    plot.provider().getLogic().getPatternInv().setItemDirect(0,
                            plainPattern(new ItemStack(Items.OAK_PLANKS), 4, new ItemStack(Items.CRAFTING_TABLE)));
                    plot.provider().applyGuardConfig(ResourceLocation.parse("g:enable"),
                            4, 0, true, null, 0);
                })
                .thenExecuteAfter(40, () -> {
                    var grid = plot.constant().getMainNode().getGrid();
                    state.future = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            () -> new MachineSource(plot.constant()), AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    if (state.future == null) {
                        throw new GameTestAssertException("waiting for recalculation to start");
                    }
                    var plan = awaitPlan(state.future);
                    if (!state.firstChecked) {
                        if (!plan.simulation()) {
                            helper.fail("guarded-off pattern must be invisible to the planner");
                        }
                        state.firstChecked = true;
                        // Open the guard and re-plan.
                        plot.constant().applyConfig(ResourceLocation.parse("g:enable"), null, null, 0, 1, 0,
                                false);
                        var grid = plot.constant().getMainNode().getGrid();
                        state.future = null;
                        helper.runAfterDelay(40, () -> state.future = grid.getCraftingService()
                                .beginCraftingCalculation(helper.getLevel(),
                                        () -> new MachineSource(plot.constant()),
                                        AEItemKey.of(Items.CRAFTING_TABLE), 1,
                                        CalculationStrategy.REPORT_MISSING_ITEMS));
                        throw new GameTestAssertException("waiting for second plan");
                    }
                    if (plan.simulation()) {
                        throw new GameTestAssertException("guard open, plan should succeed");
                    }
                })
                .thenExecute(helper::succeed)
                .thenSucceed();
    }

    /** With execution gating on, a submitted job holds until the guard opens, then completes. */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void executionGateHoldsPushesUntilGuardOpens(GameTestHelper helper) {
        var plot = buildPlot(helper, new ItemStack(Items.OAK_PLANKS, 8));
        var state = new Object() {
            Future<ICraftingPlan> future;
            boolean submitted;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    plot.constant().applyConfig(ResourceLocation.parse("g:go"), null, null, 0, 1, 0, false);
                    plot.provider().getLogic().getPatternInv().setItemDirect(0,
                            plainPattern(new ItemStack(Items.OAK_PLANKS), 4, new ItemStack(Items.CRAFTING_TABLE)));
                    plot.provider().applyGuardConfig(ResourceLocation.parse("g:go"), 4, 0, true, null, 0);
                })
                .thenExecuteAfter(40, () -> {
                    var grid = plot.constant().getMainNode().getGrid();
                    state.future = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            () -> new MachineSource(plot.constant()), AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    var plan = awaitPlan(state.future);
                    if (plan.simulation()) {
                        helper.fail("plan incomplete with open guard");
                    }
                    if (!state.submitted) {
                        // Close the guard BEFORE submitting: the plan already carries the
                        // pattern, so only the push gate can stop execution now.
                        plot.constant().applyConfig(ResourceLocation.parse("g:go"), null, null, 0, 0, 0,
                                false);
                        state.submitted = true;
                        var grid = plot.constant().getMainNode().getGrid();
                        helper.runAfterDelay(30, () -> {
                            var result = grid.getCraftingService().submitJob(plan, null, null, true,
                                    new MachineSource(plot.constant()));
                            helper.assertTrue(result.successful(),
                                    "submit should succeed, got " + result.errorCode());
                        });
                    }
                })
                .thenExecuteAfter(90, () -> {
                    helper.assertTrue(countItems(helper, new BlockPos(2, 1, 3)) == 0,
                            "no push may happen while the guard is closed");
                    plot.constant().applyConfig(ResourceLocation.parse("g:go"), null, null, 0, 1, 0, false);
                })
                .thenExecuteAfter(80, () -> {
                    int pushed = countItems(helper, new BlockPos(2, 1, 3));
                    helper.assertTrue(pushed == 4,
                            "batch must push once the guard opens, barrel has " + pushed);
                    helper.succeed();
                })
                .thenSucceed();
    }

    /** A guarded pattern filters itself while its sibling in the same provider still plans. */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void perPatternGuardFiltersWithinProvider(GameTestHelper helper) {
        var plot = buildPlot(helper, new ItemStack(Items.IRON_INGOT, 8));
        var state = new Object() {
            Future<ICraftingPlan> future;
            boolean firstChecked;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    plot.constant().applyConfig(ResourceLocation.parse("g:alt"), null, null, 0, 0, 0, false);
                    // Pattern A needs planks (absent). Pattern B crafts from iron but is
                    // guarded behind g:alt > 0.
                    plot.provider().getLogic().getPatternInv().setItemDirect(0,
                            plainPattern(new ItemStack(Items.OAK_PLANKS), 4, new ItemStack(Items.CRAFTING_TABLE)));
                    plot.provider().getLogic().getPatternInv().setItemDirect(1,
                            GuardedPattern.wrap(
                                    plainPattern(new ItemStack(Items.IRON_INGOT), 2,
                                            new ItemStack(Items.CRAFTING_TABLE)),
                                    ResourceLocation.parse("g:alt"), 4, 0));
                })
                .thenExecuteAfter(40, () -> {
                    var grid = plot.constant().getMainNode().getGrid();
                    state.future = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            () -> new MachineSource(plot.constant()), AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    if (state.future == null) {
                        throw new GameTestAssertException("waiting for recalculation to start");
                    }
                    var plan = awaitPlan(state.future);
                    if (!state.firstChecked) {
                        if (!plan.simulation()) {
                            helper.fail("with the per-pattern guard closed, planning must fail");
                        }
                        state.firstChecked = true;
                        plot.constant().applyConfig(ResourceLocation.parse("g:alt"), null, null, 0, 1, 0,
                                false);
                        var grid = plot.constant().getMainNode().getGrid();
                        state.future = null;
                        helper.runAfterDelay(40, () -> state.future = grid.getCraftingService()
                                .beginCraftingCalculation(helper.getLevel(),
                                        () -> new MachineSource(plot.constant()),
                                        AEItemKey.of(Items.CRAFTING_TABLE), 1,
                                        CalculationStrategy.REPORT_MISSING_ITEMS));
                        throw new GameTestAssertException("waiting for second plan");
                    }
                    if (plan.simulation()) {
                        throw new GameTestAssertException("open per-pattern guard should let the plan succeed");
                    }
                })
                .thenExecute(helper::succeed)
                .thenSucceed();
    }

    /** Priority channels reorder which provider receives the push, live. */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void priorityChannelSteersPushes(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST,
                null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 1)), Direction.DOWN, null,
                AE2Logistics.CONSTANT_PART.get());

        helper.setBlock(new BlockPos(2, 1, 2), AE2Logistics.GUARDED_PROVIDER.get());
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(3, 1, 1), AE2Logistics.GUARDED_PROVIDER.get());
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.BARREL);

        // Provider priority orders PATTERN CHOICE at plan time (identical patterns in
        // several providers round-robin at push time), so each provider gets a different
        // recipe for the same output and the signal decides which recipe wins.
        var state = new Object() {
            GuardedPatternProviderBlockEntity first;
            GuardedPatternProviderBlockEntity second;
            Future<ICraftingPlan> future;
            boolean submitted;
            boolean secondPhase;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    state.first = (GuardedPatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
                    state.second = (GuardedPatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 1));
                    if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity chest) {
                        chest.setItem(1, new ItemStack(Items.IRON_INGOT, 8));
                    }
                    constant.applyConfig(ResourceLocation.parse("p:favor"), null, null, 0, 10, 0, false);
                    state.first.getLogic().getPatternInv().setItemDirect(0,
                            plainPattern(new ItemStack(Items.OAK_PLANKS), 4, new ItemStack(Items.CRAFTING_TABLE)));
                    state.second.getLogic().getPatternInv().setItemDirect(0,
                            plainPattern(new ItemStack(Items.IRON_INGOT), 2, new ItemStack(Items.CRAFTING_TABLE)));
                    // First provider follows the signal, second sits at a fixed 5.
                    state.first.applyGuardConfig(null, 4, 0, true, ResourceLocation.parse("p:favor"), 0);
                    state.second.applyGuardConfig(null, 4, 0, true, null, 5);
                })
                .thenExecuteAfter(40, () -> {
                    var grid = constant.getMainNode().getGrid();
                    state.future = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            () -> new MachineSource(constant), AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    var plan = awaitPlan(state.future);
                    if (plan.simulation()) {
                        helper.fail("plan incomplete");
                    }
                    if (!state.submitted) {
                        var grid = constant.getMainNode().getGrid();
                        var result = grid.getCraftingService().submitJob(plan, null, null, true,
                                new MachineSource(constant));
                        if (!result.successful()) {
                            throw new GameTestAssertException("submit failed: " + result.errorCode());
                        }
                        state.submitted = true;
                    }
                })
                .thenExecuteAfter(80, () -> {
                    int planks = countItems(helper, new BlockPos(2, 1, 3));
                    int iron = countItems(helper, new BlockPos(4, 1, 1));
                    helper.assertTrue(planks == 4 && iron == 0,
                            "the signal-boosted recipe must win at priority 10, got " + planks + "/" + iron);
                    // Free the CPU, drop the signal below the fixed priority, re-plan.
                    var grid = constant.getMainNode().getGrid();
                    for (var cpu : grid.getCraftingService().getCpus()) {
                        cpu.cancelJob();
                    }
                    constant.applyConfig(ResourceLocation.parse("p:favor"), null, null, 0, 1, 0, false);
                    state.submitted = false;
                    state.secondPhase = true;
                })
                .thenExecuteAfter(40, () -> {
                    var grid = constant.getMainNode().getGrid();
                    state.future = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            () -> new MachineSource(constant), AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    var plan = awaitPlan(state.future);
                    if (plan.simulation()) {
                        helper.fail("second plan incomplete");
                    }
                    if (!state.submitted && state.secondPhase) {
                        var grid = constant.getMainNode().getGrid();
                        var result = grid.getCraftingService().submitJob(plan, null, null, true,
                                new MachineSource(constant));
                        if (!result.successful()) {
                            throw new GameTestAssertException("second submit failed: " + result.errorCode());
                        }
                        state.submitted = true;
                    }
                })
                .thenExecuteAfter(80, () -> {
                    int iron = countItems(helper, new BlockPos(4, 1, 1));
                    helper.assertTrue(iron == 2,
                            "with the signal at 1 the fixed-5 recipe must win, iron barrel has " + iron);
                    helper.succeed();
                })
                .thenSucceed();
    }

    /** In a vanilla pattern provider the wrapper crafts unconditionally - the guard is inert. */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void wrapperIsInertInVanillaProvider(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST,
                null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 1)), Direction.DOWN, null,
                AE2Logistics.CONSTANT_PART.get());
        helper.setBlock(new BlockPos(2, 1, 2),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.BARREL);

        var wrapped = GuardedPattern.wrap(
                plainPattern(new ItemStack(Items.OAK_PLANKS), 4, new ItemStack(Items.CRAFTING_TABLE)),
                ResourceLocation.parse("g:never"), 4, 0);

        var state = new Object() {
            Future<ICraftingPlan> future;
            boolean submitted;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    if (helper.getBlockEntity(new BlockPos(2, 1, 2)) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity vanilla) {
                        vanilla.getLogic().getPatternInv().setItemDirect(0, wrapped);
                    } else {
                        helper.fail("no vanilla provider");
                    }
                })
                .thenExecuteAfter(40, () -> {
                    var grid = constant.getMainNode().getGrid();
                    state.future = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            () -> new MachineSource(constant), AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    var plan = awaitPlan(state.future);
                    if (plan.simulation()) {
                        helper.fail("vanilla provider must plan the wrapped pattern regardless of guard");
                    }
                    if (!state.submitted) {
                        var grid = constant.getMainNode().getGrid();
                        var result = grid.getCraftingService().submitJob(plan, null, null, true,
                                new MachineSource(constant));
                        if (!result.successful()) {
                            throw new GameTestAssertException("submit failed: " + result.errorCode());
                        }
                        state.submitted = true;
                    }
                })
                .thenExecuteAfter(80, () -> {
                    int pushed = countItems(helper, new BlockPos(2, 1, 3));
                    helper.assertTrue(pushed == 4,
                            "vanilla provider must push the wrapped pattern, barrel has " + pushed);
                    helper.succeed();
                })
                .thenSucceed();
    }
}
