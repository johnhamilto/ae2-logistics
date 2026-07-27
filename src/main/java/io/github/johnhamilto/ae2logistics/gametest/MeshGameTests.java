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

    /** ME-attuned endpoints on one frequency fuse their networks like a multi-point quantum bridge. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void meMeshBridgesGrids(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(4, 1, 1));

        var first = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "me-link",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);
        var second = placeEndpoint(helper, new BlockPos(4, 1, 1), Direction.UP, "me-link",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);

        helper.runAfterDelay(40, () -> {
            var firstNode = first.getMainNode().getNode();
            var secondNode = second.getMainNode().getNode();
            helper.assertTrue(firstNode != null && secondNode != null, "endpoint nodes missing");
            helper.assertTrue(firstNode.getGrid() == secondNode.getGrid(),
                    "ME mesh must fuse both networks into one grid");
            helper.assertTrue(firstNode.isActive() && secondNode.isActive(),
                    "both endpoints should be active on the fused grid");
            helper.succeed();
        });
    }

    /** A signal written on one network appears on a second, unconnected network via the mesh. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void signalMeshBridgesNetworks(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(4, 1, 1));

        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH, null,
                AE2Logistics.CONSTANT_PART.get());
        constant.applyConfig(ResourceLocation.parse("test:bridged"), null, null, 0, 77, 0, false);

        placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "sig-mesh",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_SIGNAL);
        var receiver = placeEndpoint(helper, new BlockPos(4, 1, 1), Direction.UP, "sig-mesh",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_SIGNAL);

        helper.runAfterDelay(40, () -> {
            var sourceNode = constant.getMainNode().getNode();
            var receiverNode = receiver.getMainNode().getNode();
            helper.assertTrue(sourceNode != null && receiverNode != null, "nodes missing");
            helper.assertTrue(sourceNode.getGrid() != receiverNode.getGrid(),
                    "test requires two separate networks");
            var service = receiverNode.getGrid().getService(SignalService.class);
            long value = service.get(ResourceLocation.parse("test:bridged"));
            helper.assertTrue(value == 77, "bridged signal should be 77 on the second network, got " + value);
            helper.succeed();
        });
    }
}
