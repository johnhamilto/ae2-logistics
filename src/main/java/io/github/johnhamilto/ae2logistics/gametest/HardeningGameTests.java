package io.github.johnhamilto.ae2logistics.gametest;

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
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class HardeningGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static MeshEndpointPart placeEndpoint(GameTestHelper helper, BlockPos pos, Direction side,
            String frequency, byte role, int mask) {
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null,
                AE2Logistics.MESH_ENDPOINT_PART.get());
        part.applyMeshConfig(frequency, role, 0, mask);
        return part;
    }

    /** Fluid forwarding end to end: mesh input to a cauldron behind an output endpoint. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void fluidMeshFillsCauldron(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.NORTH, "fluid-e2e",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_FLUID);
        placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "fluid-e2e",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_FLUID);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CAULDRON);

        helper.runAfterDelay(30, () -> {
            var handler = input.exposedFluidHandler();
            helper.assertTrue(handler != null, "input must expose a fluid handler");
            int filled = handler.fill(new FluidStack(Fluids.WATER, 1000),
                    IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(filled == 1000, "the mesh must accept a full bucket, took " + filled);
        });
        helper.runAfterDelay(40, () -> {
            var state = helper.getBlockState(new BlockPos(2, 2, 1));
            helper.assertTrue(state.is(Blocks.WATER_CAULDRON)
                    && state.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    "cauldron must be full of water, state " + state);
            helper.succeed();
        });
    }

    /** Energy forwarding crosses to a machine on a DIFFERENT grid via its FE capability. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void energyMeshPowersForeignAcceptor(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));

        var input = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.NORTH, "energy-e2e",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ENERGY);
        // The OUT endpoint occupies the EAST face, so the acceptor next door can NOT
        // grid-connect through it - it forms its own network and receives FE instead.
        placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.EAST, "energy-e2e",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ENERGY);
        helper.setBlock(new BlockPos(2, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:energy_acceptor")));
        // Somewhere for the converted power to go on the foreign grid.
        helper.setBlock(new BlockPos(3, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:energy_cell")));

        helper.runAfterDelay(40, () -> {
            var acceptorNode = GridHelper.getExposedNode(helper.getLevel(),
                    helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST);
            helper.assertTrue(acceptorNode != null, "acceptor must have a grid node");
            var meshNode = input.getMainNode().getNode();
            helper.assertTrue(meshNode != null && acceptorNode.getGrid() != meshNode.getGrid(),
                    "the acceptor must be on its own grid");

            var handler = input.exposedEnergyHandler();
            helper.assertTrue(handler != null, "input must expose an energy handler");
            int accepted = handler.receiveEnergy(100000, false);
            helper.assertTrue(accepted > 0, "the acceptor must take FE through the mesh");
        });
        // getStoredPower caches for 90 ticks; assert well past the refresh window.
        helper.runAfterDelay(160, () -> {
            var acceptorNode = GridHelper.getExposedNode(helper.getLevel(),
                    helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST);
            double stored = acceptorNode.getGrid().getEnergyService().getStoredPower();
            helper.assertTrue(stored > 0,
                    "the foreign grid must hold converted power, stored " + stored);
            helper.succeed();
        });
    }

    /** The scheduler's full loop: run, product returns through the provider, rule idles. */
    @GameTest(template = "empty5", timeoutTicks = 900)
    public void schedulerRuleCompletesAndGoesIdle(GameTestHelper helper) {
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
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.BARREL);
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.JOB_SCHEDULER.get());

        var refs = new Object() {
            JobSchedulerBlockEntity scheduler;
            appeng.blockentity.crafting.PatternProviderBlockEntity provider;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    refs.provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) helper
                            .getBlockEntity(new BlockPos(2, 1, 3));
                    var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
                    AdaptivePattern.encode(pattern,
                            java.util.List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                            java.util.List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                            java.util.List.of(AdaptiveInputSpec.EXACT));
                    refs.provider.getLogic().getPatternInv().setItemDirect(0, pattern);
                    refs.provider.getLogic().updatePatterns();

                    refs.scheduler = (JobSchedulerBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2));
                    refs.scheduler.setRuleTarget(0,
                            new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1));
                    refs.scheduler.applyRuleConfig(0, 1, 1, JobSchedulerBlockEntity.CLASS_BULK, null);
                })
                .thenWaitUntil(() -> {
                    if (refs.scheduler.ruleState(0) != JobSchedulerBlockEntity.STATE_RUNNING) {
                        throw new GameTestAssertException(
                                "waiting for the rule to run, state " + refs.scheduler.ruleState(0));
                    }
                })
                .thenExecuteAfter(40, () -> {
                    // The "machine" finishes: the product enters through the provider's
                    // return inventory, which is the path that credits the job.
                    long inserted = refs.provider.getLogic().getReturnInv()
                            .insert(0, AEItemKey.of(Items.CRAFTING_TABLE), 1, Actionable.MODULATE);
                    helper.assertTrue(inserted == 1, "return inventory must accept the product");
                })
                .thenWaitUntil(() -> {
                    if (refs.scheduler.ruleState(0) != JobSchedulerBlockEntity.STATE_IDLE) {
                        throw new GameTestAssertException(
                                "waiting for the rule to idle, state " + refs.scheduler.ruleState(0));
                    }
                })
                .thenExecuteAfter(10, () -> {
                    var grid = refs.scheduler.getMainNode().getGrid();
                    for (var cpu : grid.getCraftingService().getCpus()) {
                        helper.assertTrue(!cpu.isBusy(), "the CPU must be free after completion");
                    }
                    long stored = grid.getStorageService().getCachedInventory()
                            .get(AEItemKey.of(Items.CRAFTING_TABLE));
                    helper.assertTrue(stored >= 1, "the crafted table must be in storage, got " + stored);
                    helper.succeed();
                })
                .thenSucceed();
    }

    /** Catalyst execution phase: the tool ships with the batch and is credited back. */
    @GameTest(template = "empty5", timeoutTicks = 900)
    public void catalystExecutionCreditsToolBack(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            chest.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(level, helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST, null,
                (IPartItem<?>) storageBus);
        var constant = (LogicPart) PartHelper.setPart(level,
                helper.absolutePos(new BlockPos(2, 1, 1)), Direction.DOWN, null,
                AE2Logistics.CONSTANT_PART.get());
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.BARREL);

        var refs = new Object() {
            appeng.blockentity.crafting.PatternProviderBlockEntity provider;
            java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> future;
            appeng.api.networking.crafting.ICraftingLink link;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    refs.provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) helper
                            .getBlockEntity(new BlockPos(2, 1, 3));
                    var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
                    AdaptivePattern.encode(pattern,
                            java.util.List.of(
                                    new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4),
                                    new GenericStack(AEItemKey.of(Items.IRON_PICKAXE), 1)),
                            java.util.List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                            java.util.List.of(
                                    AdaptiveInputSpec.EXACT,
                                    AdaptiveInputSpec.EXACT.withCatalyst(true)));
                    refs.provider.getLogic().getPatternInv().setItemDirect(0, pattern);
                    refs.provider.getLogic().updatePatterns();
                })
                .thenExecuteAfter(40, () -> {
                    var grid = constant.getMainNode().getGrid();
                    refs.future = grid.getCraftingService().beginCraftingCalculation(level,
                            () -> new appeng.me.helpers.MachineSource(constant),
                            AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    try {
                        var plan = refs.future.get(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (plan.simulation()) {
                            helper.fail("catalyst plan must be complete");
                        }
                        if (refs.link == null) {
                            var grid = constant.getMainNode().getGrid();
                            var result = grid.getCraftingService().submitJob(plan, null, null, true,
                                    new appeng.me.helpers.MachineSource(constant));
                            if (!result.successful()) {
                                throw new GameTestAssertException("submit failed: " + result.errorCode());
                            }
                            refs.link = result.link();
                        }
                    } catch (java.util.concurrent.TimeoutException e) {
                        throw new GameTestAssertException("planning");
                    } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenExecuteAfter(60, () -> {
                    int pickaxes = 0;
                    if (helper.getBlockEntity(new BlockPos(2, 1, 4)) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
                        for (int i = 0; i < barrel.getContainerSize(); i++) {
                            if (barrel.getItem(i).is(Items.IRON_PICKAXE)) {
                                pickaxes += barrel.getItem(i).getCount();
                            }
                        }
                    }
                    helper.assertTrue(pickaxes == 1, "the catalyst must ship with the batch, found "
                            + pickaxes);
                    // The machine finishes: product AND tool come back through the return inv.
                    var returnInv = refs.provider.getLogic().getReturnInv();
                    returnInv.insert(0, AEItemKey.of(Items.CRAFTING_TABLE), 1, Actionable.MODULATE);
                    returnInv.insert(1, AEItemKey.of(Items.IRON_PICKAXE), 1, Actionable.MODULATE);
                })
                .thenWaitUntil(() -> {
                    var grid = constant.getMainNode().getGrid();
                    for (var cpu : grid.getCraftingService().getCpus()) {
                        if (cpu.isBusy()) {
                            throw new GameTestAssertException("waiting for the job to complete");
                        }
                    }
                })
                .thenExecuteAfter(20, () -> {
                    var grid = constant.getMainNode().getGrid();
                    long pickaxe = grid.getStorageService().getCachedInventory()
                            .get(AEItemKey.of(Items.IRON_PICKAXE));
                    long table = grid.getStorageService().getCachedInventory()
                            .get(AEItemKey.of(Items.CRAFTING_TABLE));
                    helper.assertTrue(pickaxe == 1,
                            "the catalyst must be back in storage, got " + pickaxe);
                    helper.assertTrue(table >= 1, "the product must be in storage, got " + table);
                    helper.succeed();
                })
                .thenSucceed();
    }
}
