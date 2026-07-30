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
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class MeshGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static MeshEndpointPart placeEndpoint(GameTestHelper helper, BlockPos pos, Direction side,
            String frequency, byte role, int mask) {
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null,
                AE2Logistics.MESH_ENDPOINT_PART.get());
        helper.assertTrue(part != null, "mesh endpoint placement failed at " + pos + " " + side);
        part.applyMeshConfig(frequency, role, 0, mask);
        return part;
    }

    private static void placeDenseCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_smart_dense_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    @GameTest(template = "empty5", timeoutTicks = 200)
    public void redstoneMeshIsWiredOr(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "rs-mesh",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_REDSTONE);
        placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "rs-mesh",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_REDSTONE);

        helper.setBlock(new BlockPos(1, 2, 1), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.REDSTONE_LAMP);

        helper.runAfterDelay(30, () -> {
            var lamp = helper.getBlockState(new BlockPos(2, 2, 1));
            helper.assertTrue(lamp.getValue(RedstoneLampBlock.LIT),
                    "lamp should be lit through the redstone mesh");
            helper.succeed();
        });
    }

    /** Two stacks inserted in one tick must land in ONE chest - the provider-batch guarantee. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void itemMeshKeepsBatchesTogether(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(3, 1, 1));

        var input = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.NORTH, "item-mesh",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "item-mesh",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        placeEndpoint(helper, new BlockPos(3, 1, 1), Direction.UP, "item-mesh",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.CHEST);

        helper.runAfterDelay(30, () -> {
            var handler = input.exposedItemHandler();
            helper.assertTrue(handler != null, "input endpoint must expose an item handler");
            var restIron = handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 8), false);
            var restGold = handler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 4), false);
            helper.assertTrue(restIron.isEmpty() && restGold.isEmpty(),
                    "both batch stacks must be accepted by the mesh");
        });

        helper.runAfterDelay(40, () -> {
            int chestsWithItems = 0;
            int total = 0;
            for (var chestPos : new BlockPos[] {new BlockPos(2, 2, 1), new BlockPos(3, 2, 1)}) {
                if (helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                    int count = 0;
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        count += chest.getItem(i).getCount();
                    }
                    if (count > 0) {
                        chestsWithItems++;
                    }
                    total += count;
                }
            }
            helper.assertTrue(total == 12, "all 12 items must arrive, got " + total);
            helper.assertTrue(chestsWithItems == 1,
                    "batch must stay in one chest, found items in " + chestsWithItems);
            helper.succeed();
        });
    }

    /**
     * True provider P2P: a pattern provider pushing two batches through one mesh input
     * must land each complete batch on a DIFFERENT machine, because the first machine
     * still holds its batch (per-machine blocking through the mesh).
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void providerP2PDistributesBatchesAcrossMachines(GameTestHelper helper) {
        var level = helper.getLevel();

        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));

        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity source) {
            source.setItem(0, new ItemStack(Items.BIRCH_PLANKS, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(level, helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST, null,
                (IPartItem<?>) storageBus);

        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));
        // The provider's push face points at the mesh endpoint, which occupies that cable
        // face - so its grid connection comes from a separate cable path above.
        placeCable(helper, new BlockPos(2, 2, 2));
        placeCable(helper, new BlockPos(2, 2, 3));

        var input = placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.SOUTH, "prov-mesh",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.WEST, "prov-mesh",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.EAST, "prov-mesh",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);

        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern.encode(pattern,
                java.util.List.of(new appeng.api.stacks.GenericStack(
                        appeng.api.stacks.AEItemKey.of(Items.OAK_PLANKS), 4)),
                java.util.List.of(new appeng.api.stacks.GenericStack(
                        appeng.api.stacks.AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                java.util.List.of(io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec
                        .ofTag(ResourceLocation.parse("minecraft:planks"))));
        if (helper.getBlockEntity(new BlockPos(2, 1, 3)) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe) {
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
                    var grid = input.getMainNode().getGrid();
                    var source = new appeng.me.helpers.MachineSource(input);
                    job.future = grid.getCraftingService().beginCraftingCalculation(level,
                            () -> source, appeng.api.stacks.AEItemKey.of(Items.CRAFTING_TABLE), 2,
                            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    try {
                        var plan = job.future.get(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (plan.simulation()) {
                            helper.fail("plan incomplete");
                        }
                        if (!job.submitted) {
                            var grid = input.getMainNode().getGrid();
                            var result = grid.getCraftingService().submitJob(plan, null, null, true,
                                    new appeng.me.helpers.MachineSource(input));
                            if (!result.successful()) {
                                throw new net.minecraft.gametest.framework.GameTestAssertException(
                                        "submit failed: " + result.errorCode());
                            }
                            job.submitted = true;
                        }
                    } catch (java.util.concurrent.TimeoutException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("planning");
                    } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenExecuteAfter(80, () -> {
                    int chestA = countItems(helper, new BlockPos(1, 1, 2));
                    int chestB = countItems(helper, new BlockPos(3, 1, 2));
                    helper.assertTrue(chestA + chestB == 8,
                            "both batches must be pushed, got " + chestA + "+" + chestB);
                    helper.assertTrue(chestA == 4 && chestB == 4,
                            "each machine must get one complete batch, got " + chestA + "/" + chestB);
                    helper.succeed();
                })
                .thenSucceed();
    }

    private static int countItems(GameTestHelper helper, BlockPos pos) {
        int count = 0;
        if (helper.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                count += chest.getItem(i).getCount();
            }
        }
        return count;
    }

    /** A mesh delivery aimed at another mesh input must be refused, not looped. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void meshDeliveryCannotEnterAnotherMesh(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var firstIn = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.NORTH, "loop-a",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        // loop-a's output faces EAST into the cable hosting loop-b's input on its WEST face.
        placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.EAST, "loop-a",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.WEST, "loop-b",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);

        helper.runAfterDelay(30, () -> {
            var handler = firstIn.exposedItemHandler();
            helper.assertTrue(handler != null, "input must expose a handler");
            var rest = handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
            helper.assertTrue(rest.getCount() == 4,
                    "delivery into another mesh must be refused whole, got back " + rest.getCount());
            helper.succeed();
        });
    }

    /** With a single machine still holding its batch, the provider must push nothing more. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void providerP2PBlocksWhenAllMachinesBusy(GameTestHelper helper) {
        var level = helper.getLevel();

        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity source) {
            source.setItem(0, new ItemStack(Items.BIRCH_PLANKS, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(level, helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST, null,
                (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));
        placeCable(helper, new BlockPos(2, 2, 2));
        placeCable(helper, new BlockPos(2, 2, 3));

        var input = placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.SOUTH, "busy-mesh",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.EAST, "busy-mesh",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);

        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern.encode(pattern,
                java.util.List.of(new appeng.api.stacks.GenericStack(
                        appeng.api.stacks.AEItemKey.of(Items.OAK_PLANKS), 4)),
                java.util.List.of(new appeng.api.stacks.GenericStack(
                        appeng.api.stacks.AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                java.util.List.of(io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec
                        .ofTag(ResourceLocation.parse("minecraft:planks"))));
        if (helper.getBlockEntity(new BlockPos(2, 1, 3)) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe) {
            providerBe.getLogic().getPatternInv().setItemDirect(0, pattern);
        }

        var job = new Object() {
            java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> future;
            boolean submitted;
        };
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    var grid = input.getMainNode().getGrid();
                    var source = new appeng.me.helpers.MachineSource(input);
                    job.future = grid.getCraftingService().beginCraftingCalculation(level,
                            () -> source, appeng.api.stacks.AEItemKey.of(Items.CRAFTING_TABLE), 2,
                            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    try {
                        var plan = job.future.get(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (!job.submitted) {
                            input.getMainNode().getGrid().getCraftingService()
                                    .submitJob(plan, null, null, true, new appeng.me.helpers.MachineSource(input));
                            job.submitted = true;
                        }
                    } catch (java.util.concurrent.TimeoutException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("planning");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenExecuteAfter(80, () -> {
                    int chest = countItems(helper, new BlockPos(3, 1, 2));
                    helper.assertTrue(chest == 4,
                            "only one batch may push while the machine is busy, chest has " + chest);
                    helper.succeed();
                })
                .thenSucceed();
    }

    /** Removing an endpoint from the frequency must split the carried grids again. */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public void meMeshSplitsWhenEndpointLeaves(GameTestHelper helper) {
        // One backbone network carries the frequency (frequencies never cross networks).
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(3, 1, 1));
        placeCable(helper, new BlockPos(4, 1, 1));
        // The networks FED into the endpoints' faces - these are what the mesh carries.
        helper.setBlock(new BlockPos(1, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(4, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));

        var first = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "me-split",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);
        var second = placeEndpoint(helper, new BlockPos(4, 1, 1), Direction.UP, "me-split",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(first.carriedNode() != null && second.carriedNode() != null,
                    "carried nodes missing");
            helper.assertTrue(first.carriedNode().getGrid() == second.carriedNode().getGrid(),
                    "carried grids should fuse first");
            second.applyMeshConfig("", MeshEndpointPart.ROLE_BOTH, 0, 0);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(second.carriedNode() == null,
                    "un-attuning ME must destroy the carried node");
            var carried = first.carriedNode();
            helper.assertTrue(carried != null && carried.getGrid() != null, "carried node missing");
            helper.assertTrue(carried.getGrid().size() == 2,
                    "first carried grid must shrink to endpoint + its fed cell, got "
                            + carried.getGrid().size());
            helper.succeed();
        });
    }

    /**
     * ME tunneling is true P2P: the networks fed INTO the endpoints' faces fuse across
     * the mesh, carried by the host network - which itself is never fused into them.
     */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void meMeshCarriesFedNetworks(GameTestHelper helper) {
        // One backbone network carries the frequency (frequencies never cross networks).
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(3, 1, 1));
        placeCable(helper, new BlockPos(4, 1, 1));
        // Fed networks touching the endpoint faces (endpoints face UP).
        helper.setBlock(new BlockPos(1, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(4, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));

        var first = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "me-link",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);
        var second = placeEndpoint(helper, new BlockPos(4, 1, 1), Direction.UP, "me-link",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);

        helper.runAfterDelay(40, () -> {
            var firstCarried = first.carriedNode();
            var secondCarried = second.carriedNode();
            helper.assertTrue(firstCarried != null && secondCarried != null, "carried nodes missing");
            helper.assertTrue(firstCarried.getGrid() == secondCarried.getGrid(),
                    "carried grids must fuse into one");
            helper.assertTrue(firstCarried.getGrid().size() == 4,
                    "carried grid = 2 carried nodes + 2 fed cells, got "
                            + firstCarried.getGrid().size());
            var firstHost = first.getMainNode().getNode();
            var secondHost = second.getMainNode().getNode();
            helper.assertTrue(firstHost.getGrid() == secondHost.getGrid(),
                    "both endpoints ride one backbone network");
            helper.assertTrue(firstHost.getGrid() != firstCarried.getGrid(),
                    "host and carried networks must stay separate");
            helper.succeed();
        });
    }

    /**
     * Frequencies never cross networks: two endpoints on the same frequency but
     * different host networks must NOT link - the host network is the carrier,
     * exactly like AE2's own P2P tunnels.
     */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void meshFrequenciesAreNetworkScoped(GameTestHelper helper) {
        // Two DISJOINT host networks (gap at x=2), each with a powered cable.
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(4, 1, 1));
        // Fed networks on the faces.
        helper.setBlock(new BlockPos(1, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(4, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));

        var first = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "net-scope",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME | MeshRegistry.TYPE_ITEM);
        var second = placeEndpoint(helper, new BlockPos(4, 1, 1), Direction.UP, "net-scope",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME | MeshRegistry.TYPE_ITEM);

        helper.runAfterDelay(20, () -> MeshRegistry.forceRelink("net-scope"));

        helper.runAfterDelay(60, () -> {
            var firstHost = first.getMainNode().getNode();
            var secondHost = second.getMainNode().getNode();
            helper.assertTrue(firstHost != null && secondHost != null
                    && firstHost.getGrid() != secondHost.getGrid(),
                    "test requires two separate host networks");
            var firstCarried = first.carriedNode();
            var secondCarried = second.carriedNode();
            helper.assertTrue(firstCarried != null && secondCarried != null, "carried nodes missing");
            helper.assertTrue(firstCarried.getGrid() != secondCarried.getGrid(),
                    "frequencies must not link across host networks");
            helper.assertTrue(first.meLinkState() == MeshRegistry.ME_STATE_WAITING
                    && second.meLinkState() == MeshRegistry.ME_STATE_WAITING,
                    "endpoints without a same-network peer must report waiting");
            helper.assertTrue(MeshRegistry.outputs("net-scope", MeshRegistry.TYPE_ITEM, first).isEmpty(),
                    "item transport must not target endpoints on another network");
            helper.succeed();
        });
    }

    /**
     * Channel bundling: one lane caps at a dense node's 32 channels, so two fed inputs
     * must become two LANES (disjoint pairs, not a star) for 24 + 9 = 33 channels of
     * interfaces to path across the mesh. Every interface must come online.
     */
    @GameTest(template = "empty12", timeoutTicks = 400)
    public void meMeshBundlesChannelsAcrossLanes(GameTestHelper helper) {
        var controllerBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:controller"));
        var creativeCell = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell"));
        var interfaceBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:interface"));

        // Controller column feeding the mesh: 32 channels per controller face.
        helper.setBlock(new BlockPos(1, 1, 4), controllerBlock);
        helper.setBlock(new BlockPos(1, 1, 5), controllerBlock);
        helper.setBlock(new BlockPos(1, 2, 5), creativeCell);

        // The host network the four endpoints sit on; its faces do the carrying.
        placeCable(helper, new BlockPos(2, 1, 4));
        placeCable(helper, new BlockPos(2, 1, 5));
        helper.setBlock(new BlockPos(2, 0, 4), creativeCell);

        var feedA = placeEndpoint(helper, new BlockPos(2, 1, 4), Direction.WEST, "bundle",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);
        var feedB = placeEndpoint(helper, new BlockPos(2, 1, 5), Direction.WEST, "bundle",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);
        var outC = placeEndpoint(helper, new BlockPos(2, 1, 4), Direction.EAST, "bundle",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);
        var outD = placeEndpoint(helper, new BlockPos(2, 1, 5), Direction.SOUTH, "bundle",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);

        var interfaces = new java.util.ArrayList<BlockPos>();
        // Farm C behind outC's face: dense spine east with 24 interfaces around it.
        for (int x = 3; x <= 10; x++) {
            placeDenseCable(helper, new BlockPos(x, 1, 4));
            interfaces.add(new BlockPos(x, 2, 4));
            interfaces.add(new BlockPos(x, 1, 3));
            if (x >= 4) {
                // x=3 at z=5 would touch the host cable and fuse the farm into it.
                interfaces.add(new BlockPos(x, 1, 5));
            }
        }
        interfaces.add(new BlockPos(11, 1, 4));
        // Farm D behind outD's face: dense spine south with 9 interfaces.
        for (int z = 6; z <= 9; z++) {
            placeDenseCable(helper, new BlockPos(2, 1, z));
            interfaces.add(new BlockPos(2, 2, z));
            interfaces.add(new BlockPos(3, 1, z));
        }
        interfaces.add(new BlockPos(2, 1, 10));
        for (var pos : interfaces) {
            helper.setBlock(pos, interfaceBlock);
        }

        // Lane building can race the carried nodes' in-world connections; force the
        // documented re-check once everything is connected, then let pathing settle.
        helper.runAfterDelay(40, () -> MeshRegistry.forceRelink("bundle"));

        helper.runAfterDelay(200, () -> {
            for (var endpoint : new MeshEndpointPart[] {feedA, feedB, outC, outD}) {
                helper.assertTrue(endpoint.carriedNode() != null, "carried node missing");
                helper.assertTrue(endpoint.carriedNode().getGrid() == feedA.carriedNode().getGrid(),
                        "all carried grids must fuse into one");
            }
            int online = 0;
            for (var pos : interfaces) {
                if (helper.getBlockEntity(pos) instanceof appeng.blockentity.misc.InterfaceBlockEntity iface
                        && iface.getMainNode().isOnline()) {
                    online++;
                }
            }
            helper.assertTrue(online == 33,
                    "33 channels must cross the mesh on two lanes (one lane ceils at 32), online: "
                            + online);
            helper.succeed();
        });
    }

    /**
     * Signals bridge subnets THROUGH the mesh: the input face reads the subnet touching
     * it, the output face injects into its subnet, and the host network carries.
     */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void signalMeshBridgesNetworks(GameTestHelper helper) {
        // Backbone network carrying both endpoints.
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(3, 1, 1));
        placeCable(helper, new BlockPos(4, 1, 1));
        // Subnet A above the input face: its cable holds the constant writing 77.
        placeCable(helper, new BlockPos(1, 2, 1));
        helper.setBlock(new BlockPos(1, 3, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        // Subnet B above the output face receives the bridged channel.
        placeCable(helper, new BlockPos(4, 2, 1));
        helper.setBlock(new BlockPos(4, 3, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));

        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 2, 1)), Direction.NORTH, null,
                AE2Logistics.CONSTANT_PART.get());
        constant.applyConfig(ResourceLocation.parse("test:bridged"), null, null, 0, 77, 0, false);

        placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "sig-mesh",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_SIGNAL);
        var receiver = placeEndpoint(helper, new BlockPos(4, 1, 1), Direction.UP, "sig-mesh",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_SIGNAL);

        helper.runAfterDelay(40, () -> {
            var sourceNode = constant.getMainNode().getNode();
            var receiverHost = receiver.getMainNode().getNode();
            helper.assertTrue(sourceNode != null && receiverHost != null, "nodes missing");
            helper.assertTrue(sourceNode.getGrid() != receiverHost.getGrid(),
                    "subnet A must be separate from the backbone");
            var subnetB = receiver.signalService();
            helper.assertTrue(subnetB != null, "output face must resolve subnet B's signal service");
            helper.assertTrue(subnetB != sourceNode.getGrid().getService(SignalService.class),
                    "subnet B must be separate from subnet A");
            long value = subnetB.get(ResourceLocation.parse("test:bridged"));
            helper.assertTrue(value == 77, "bridged signal should be 77 on subnet B, got " + value);
            helper.succeed();
        });
    }

    /** Typed part items lock the capability mask no matter what config arrives. */
    @GameTest(template = "empty5", timeoutTicks = 100)
    public void typedEndpointLocksCapabilityMask(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var typed = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.UP, null, AE2Logistics.MESH_ENDPOINT_ITEM_PART.get());
        helper.assertTrue(typed != null, "typed endpoint placement failed");
        helper.assertTrue(typed.capabilityLocked(), "typed endpoint must report a locked mask");
        helper.assertTrue(typed.capabilityMask() == MeshRegistry.TYPE_ITEM,
                "typed endpoint must place pre-attuned to its transport");
        typed.applyMeshConfig("locked", MeshEndpointPart.ROLE_IN, 0, 63);
        helper.assertTrue(typed.capabilityMask() == MeshRegistry.TYPE_ITEM,
                "typed endpoint must ignore mask edits, got " + typed.capabilityMask());

        var universal = placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "locked",
                MeshEndpointPart.ROLE_OUT, 63);
        helper.assertTrue(!universal.capabilityLocked() && universal.capabilityMask() == 63,
                "universal endpoint must keep an editable full mask");
        helper.succeed();
    }

    /** A typed input/output pair forwards items even when the config carries mask 0. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void typedEndpointsFormTunnel(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.NORTH, null, AE2Logistics.MESH_ENDPOINT_ITEM_PART.get());
        var output = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.UP, null, AE2Logistics.MESH_ENDPOINT_ITEM_PART.get());
        helper.assertTrue(input != null && output != null, "typed endpoint placement failed");
        input.applyMeshConfig("typed-tunnel", MeshEndpointPart.ROLE_IN, 0, 0);
        output.applyMeshConfig("typed-tunnel", MeshEndpointPart.ROLE_OUT, 0, 0);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);

        helper.runAfterDelay(30, () -> {
            var handler = input.exposedItemHandler();
            helper.assertTrue(handler != null,
                    "typed input endpoint must expose an item handler despite mask-0 config");
            var rest = handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 8), false);
            helper.assertTrue(rest.isEmpty(), "typed tunnel must accept the stack");
        });

        helper.runAfterDelay(40, () -> {
            int count = 0;
            if (helper.getBlockEntity(new BlockPos(2, 2, 1)) instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    count += chest.getItem(i).getCount();
                }
            }
            helper.assertTrue(count == 8, "all 8 items must arrive through the typed tunnel, got " + count);
            helper.succeed();
        });
    }
}
