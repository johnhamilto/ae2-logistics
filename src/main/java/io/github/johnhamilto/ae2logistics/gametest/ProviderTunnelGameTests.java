package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.ids.AEComponents;
import appeng.me.service.P2PService;
import appeng.util.SettingsFrom;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;
import io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart;

public class ProviderTunnelGameTests {

    static void register() {
        LogisticsTestInstance.add("providerTunnelDistributesBatchesAcrossMachines", "empty5", 400, ProviderTunnelGameTests::providerTunnelDistributesBatchesAcrossMachines);
        LogisticsTestInstance.add("providerTunnelPushesWithoutBlocking", "empty5", 400, ProviderTunnelGameTests::providerTunnelPushesWithoutBlocking);
        LogisticsTestInstance.add("providerTunnelMirrorsPatternsAndPushesFluids", "empty5", 300, ProviderTunnelGameTests::providerTunnelMirrorsPatternsAndPushesFluids);
        LogisticsTestInstance.add("providerMeshEndpointRoutesAnyKey", "empty5", 200, ProviderTunnelGameTests::providerMeshEndpointRoutesAnyKey);
        LogisticsTestInstance.add("providerTunnelReturnsResultsThroughOutputFace", "empty5", 200, ProviderTunnelGameTests::providerTunnelReturnsResultsThroughOutputFace);
        LogisticsTestInstance.add("providerTunnelGenericReturnSurface", "empty5", 200, ProviderTunnelGameTests::providerTunnelGenericReturnSurface);
        LogisticsTestInstance.add("meshProviderReturnsResultsThroughOutputFace", "empty5", 200, ProviderTunnelGameTests::meshProviderReturnsResultsThroughOutputFace);
        LogisticsTestInstance.add("meshProviderGenericReturnSurface", "empty5", 200, ProviderTunnelGameTests::meshProviderGenericReturnSurface);
        LogisticsTestInstance.add("meshProviderReturnsFollowInputPriority", "empty5", 200, ProviderTunnelGameTests::meshProviderReturnsFollowInputPriority);
        LogisticsTestInstance.add("assemblerPatternsCrossTheTunnel", "empty5", 400, ProviderTunnelGameTests::assemblerPatternsCrossTheTunnel);
        LogisticsTestInstance.add("assemblerCraftsChainThroughTheTunnel", "empty5", 600, ProviderTunnelGameTests::assemblerCraftsChainThroughTheTunnel);
        LogisticsTestInstance.add("returnBufferHoldsFlushesAndBackpressures", "empty5", 200, ProviderTunnelGameTests::returnBufferHoldsFlushesAndBackpressures);
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static ProviderP2PTunnelPart placeTunnel(GameTestHelper helper, BlockPos pos, Direction side) {
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null,
                AE2Logistics.PROVIDER_P2P_TUNNEL_PART.get());
        helper.assertTrue(part != null, "provider tunnel placement failed at " + pos + " " + side);
        return part;
    }

    /** Links tunnels like a memory card would: the input keeps the freq, outputs import it. */
    private static void linkTunnels(GameTestHelper helper, ProviderP2PTunnelPart input,
            ProviderP2PTunnelPart... outputs) {
        var grid = input.getMainNode().getGrid();
        helper.assertTrue(grid != null, "input tunnel must be on a grid");
        var freq = P2PService.get(grid).newFrequency();
        P2PService.get(grid).updateFreq(input, freq);
        var settings = DataComponentMap.builder()
                .set(AEComponents.EXPORTED_P2P_FREQUENCY, freq).build();
        for (var output : outputs) {
            output.importSettings(SettingsFrom.MEMORY_CARD, settings, null);
        }
    }

    private static int count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                total += chest.getItem(i).getCount();
            }
        }
        return total;
    }

    private static int chestTotal(GameTestHelper helper, BlockPos pos) {
        int total = 0;
        if (helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                total += chest.getItem(i).getCount();
            }
        }
        return total;
    }

    /**
     * The hall scene: a grid with planks in a chest (storage bus), a crafting CPU, and
     * an on-grid pattern provider whose only push face is the input tunnel. Two output
     * tunnels face machine chests. Returns the input tunnel; outputs are WEST and EAST
     * of the mesh cable, chests at (1,1,2) and (3,1,2).
     */
    private static ProviderP2PTunnelPart[] buildJobScene(GameTestHelper helper,
            appeng.api.config.YesNo blockingMode) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity source) {
            source.setItem(0, new ItemStack(Items.BIRCH_PLANKS, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.WEST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:pattern_provider")));
        // The provider's push face points at the input tunnel; its grid connection
        // comes from the cable path above - same grid, as the replicas require.
        placeCable(helper, new BlockPos(2, 2, 2));
        placeCable(helper, new BlockPos(2, 2, 3));

        var input = placeTunnel(helper, new BlockPos(2, 1, 2), Direction.SOUTH);
        var outputA = placeTunnel(helper, new BlockPos(2, 1, 2), Direction.WEST);
        var outputB = placeTunnel(helper, new BlockPos(2, 1, 2), Direction.EAST);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);

        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(pattern,
                java.util.List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                java.util.List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                java.util.List.of(AdaptiveInputSpec.ofTag(Identifier.parse("minecraft:planks"))));
        if (helper.getBlockEntity(new BlockPos(2, 1, 3), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe) {
            providerBe.getLogic().getPatternInv().setItemDirect(0, pattern);
            providerBe.getLogic().getConfigManager().putSetting(
                    appeng.api.config.Settings.BLOCKING_MODE, blockingMode);
        } else {
            helper.fail("no pattern provider");
        }
        return new ProviderP2PTunnelPart[] {input, outputA, outputB};
    }

    private static void submitJob(GameTestHelper helper, ProviderP2PTunnelPart input,
            Runnable thenCheck) {
        submitJob(helper, input, AEItemKey.of(Items.CRAFTING_TABLE), 2, 80, thenCheck);
    }

    private static void submitJob(GameTestHelper helper, ProviderP2PTunnelPart input,
            AEItemKey what, int amount, int settleTicks, Runnable thenCheck) {
        var level = helper.getLevel();
        var job = new Object() {
            java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> future;
            boolean submitted;
        };
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    var grid = input.getMainNode().getGrid();
                    var source = new appeng.me.helpers.MachineSource(input);
                    job.future = grid.getCraftingService().beginCraftingCalculation(level,
                            () -> source, what, amount,
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
                .thenExecuteAfter(settleTicks, thenCheck)
                .thenSucceed();
    }

    /**
     * A blocking-mode provider replicated onto two machine faces must land each
     * complete batch on a DIFFERENT machine: the crafting service skips the busy
     * replica whose machine still holds its batch.
     */
    public static void providerTunnelDistributesBatchesAcrossMachines(GameTestHelper helper) {
        var tunnels = buildJobScene(helper, appeng.api.config.YesNo.YES);
        helper.runAfterDelay(30, () -> linkTunnels(helper, tunnels[0], tunnels[1], tunnels[2]));
        submitJob(helper, tunnels[0], () -> {
            int chestA = chestTotal(helper, new BlockPos(1, 1, 2));
            int chestB = chestTotal(helper, new BlockPos(3, 1, 2));
            helper.assertTrue(chestA + chestB == 8,
                    "both batches must be pushed, got " + chestA + "+" + chestB);
            helper.assertTrue(chestA == 4 && chestB == 4,
                    "each machine must get one complete batch, got " + chestA + "/" + chestB);
        });
    }

    /** Without blocking mode, all batches still deliver - distribution is unconstrained. */
    public static void providerTunnelPushesWithoutBlocking(GameTestHelper helper) {
        var tunnels = buildJobScene(helper, appeng.api.config.YesNo.NO);
        helper.runAfterDelay(30, () -> linkTunnels(helper, tunnels[0], tunnels[1], tunnels[2]));
        submitJob(helper, tunnels[0], () -> {
            int chestA = chestTotal(helper, new BlockPos(1, 1, 2));
            int chestB = chestTotal(helper, new BlockPos(3, 1, 2));
            helper.assertTrue(chestA + chestB == 8,
                    "all ingredients must be pushed without blocking, got " + chestA + "+" + chestB);
        });
    }

    /**
     * The replicas mirror the real provider's patterns and push any key type: a fluid
     * pattern rides the external-storage strategies into a cauldron, the same registry
     * companion mods use for chemicals.
     */
    public static void providerTunnelMirrorsPatternsAndPushesFluids(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        // The provider's grid connection loops through a cable next to the cell.
        placeCable(helper, new BlockPos(0, 1, 0));
        helper.setBlock(new BlockPos(1, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:pattern_provider")));

        var input = placeTunnel(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        var output = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.UP);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CAULDRON);

        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(pattern,
                java.util.List.of(new GenericStack(AEFluidKey.of(
                        net.minecraft.world.level.material.Fluids.WATER), 1000)),
                java.util.List.of(new GenericStack(AEItemKey.of(Items.OBSIDIAN), 1)),
                java.util.List.of(AdaptiveInputSpec.EXACT));
        if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe) {
            providerBe.getLogic().getPatternInv().setItemDirect(0, pattern);
        } else {
            helper.fail("no pattern provider");
        }

        helper.runAfterDelay(30, () -> linkTunnels(helper, input, output));

        helper.runAfterDelay(80, () -> {
            var node = output.getMainNode().getNode();
            helper.assertTrue(node != null, "output tunnel must have a grid node");
            var virtual = node.getService(ICraftingProvider.class);
            helper.assertTrue(virtual != null, "output tunnel must register a virtual provider");
            var patterns = virtual.getAvailablePatterns();
            helper.assertTrue(patterns.size() == 1,
                    "replica must mirror the real provider's pattern, got " + patterns.size());
            var inputs = new KeyCounter();
            inputs.add(AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER), 1000);
            boolean pushed = virtual.pushPattern(patterns.get(0), new KeyCounter[] {inputs});
            helper.assertTrue(pushed, "replica must push the fluid batch");
        });

        helper.runAfterDelay(120, () -> {
            var state = helper.getBlockState(new BlockPos(2, 2, 1));
            helper.assertTrue(state.is(Blocks.WATER_CAULDRON)
                    && state.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    "cauldron must be full of water, state " + state);
            helper.succeed();
        });
    }

    /** The provider mesh transport delivers any key type too, via the typed provider part. */
    public static void providerMeshEndpointRoutesAnyKey(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.NORTH, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        var output = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.UP, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        helper.assertTrue(input != null && output != null, "typed provider endpoint placement failed");
        input.applyMeshConfig("prov-typed", io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart.ROLE_IN, 0, 0);
        output.applyMeshConfig("prov-typed", io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart.ROLE_OUT, 0, 0);
        helper.assertTrue(input.capabilityMask() == io.github.johnhamilto.ae2logistics.mesh.MeshRegistry.TYPE_PROVIDER,
                "typed provider endpoint must lock to the provider transport");
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CAULDRON);

        helper.runAfterDelay(30, () -> {
            var storage = input.exposedMeStorage();
            helper.assertTrue(storage != null, "provider endpoint must expose ME storage");
            long simulated = storage.insert(AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER),
                    1000, Actionable.SIMULATE, appeng.api.networking.security.IActionSource.empty());
            helper.assertTrue(simulated == 1000, "simulate must accept a bucket, took " + simulated);
            long filled = storage.insert(AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER),
                    1000, Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
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

    /**
     * Machines return results through their own face: an insert into the output
     * tunnel's item capability must land in whatever the input tunnel faces.
     */
    public static void providerTunnelReturnsResultsThroughOutputFace(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = placeTunnel(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        var output = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.UP);

        helper.runAfterDelay(30, () -> {
            linkTunnels(helper, input, output);
        });

        helper.runAfterDelay(40, () -> {
            var handler = helper.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    helper.absolutePos(new BlockPos(2, 1, 1)), Direction.UP);
            helper.assertTrue(handler != null, "output tunnel must expose a return item handler");
            int inserted;
            try (var tx = Transaction.openRoot()) {
                inserted = handler.insert(ItemResource.of(Items.CRAFTING_TABLE), 5, tx);
                tx.commit();
            }
            helper.assertTrue(inserted == 5, "return insert must be accepted, took " + inserted);
        });

        helper.runAfterDelay(50, () -> {
            int returned = 0;
            if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                returned = count(chest, Items.CRAFTING_TABLE);
            }
            helper.assertTrue(returned == 5,
                    "all 5 returned items must reach the input-side inventory, got " + returned);
            helper.succeed();
        });
    }

    /**
     * The generic-inventory return surface accepts any AE key type - it is the same
     * capability addons bridge chemicals and other custom keys through on AE2's own
     * providers and interfaces.
     */
    public static void providerTunnelGenericReturnSurface(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = placeTunnel(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        var output = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.UP);

        helper.runAfterDelay(30, () -> {
            linkTunnels(helper, input, output);
        });

        helper.runAfterDelay(40, () -> {
            var inv = helper.getLevel().getCapability(appeng.api.AECapabilities.GENERIC_INTERNAL_INV,
                    helper.absolutePos(new BlockPos(2, 1, 1)), Direction.UP);
            helper.assertTrue(inv != null, "output tunnel must expose the generic inventory");
            long inserted = inv.insert(0, AEItemKey.of(Items.CRAFTING_TABLE), 5, Actionable.MODULATE);
            helper.assertTrue(inserted == 5, "generic return must accept the stack, took " + inserted);
        });

        helper.runAfterDelay(50, () -> {
            int returned = 0;
            if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                returned = count(chest, Items.CRAFTING_TABLE);
            }
            helper.assertTrue(returned == 5,
                    "generic return must reach the input-side inventory, got " + returned);
            helper.succeed();
        });
    }

    /** Builds one provider mesh pair: input faces a chest at (1,1,0), output face on top of (2,1,1). */
    private static io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart[] meshReturnScene(
            GameTestHelper helper, String frequency) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.NORTH, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        var output = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.UP, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        helper.assertTrue(input != null && output != null, "provider endpoint placement failed");
        input.applyMeshConfig(frequency, io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart.ROLE_IN, 0, 0);
        output.applyMeshConfig(frequency, io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart.ROLE_OUT, 0, 0);
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        return new io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart[] {input, output};
    }

    /**
     * Mesh parity with the tunnel's returns: an insert into an OUTPUT provider
     * endpoint's item capability must land in whatever the input endpoint faces, and
     * input faces must never expose the generic inventory (a provider standing there
     * would chain into the return path instead of the push router).
     */
    public static void meshProviderReturnsResultsThroughOutputFace(GameTestHelper helper) {
        meshReturnScene(helper, "mesh-ret");

        helper.runAfterDelay(30, () -> {
            var inputGeneric = helper.getLevel().getCapability(
                    appeng.api.AECapabilities.GENERIC_INTERNAL_INV,
                    helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH);
            helper.assertTrue(inputGeneric == null,
                    "input endpoint faces must not expose the generic return inventory");
            var handler = helper.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    helper.absolutePos(new BlockPos(2, 1, 1)), Direction.UP);
            helper.assertTrue(handler != null, "output endpoint must expose a return item handler");
            int inserted;
            try (var tx = Transaction.openRoot()) {
                inserted = handler.insert(ItemResource.of(Items.CRAFTING_TABLE), 5, tx);
                tx.commit();
            }
            helper.assertTrue(inserted == 5, "return insert must be accepted, took " + inserted);
        });

        helper.runAfterDelay(50, () -> {
            int returned = 0;
            if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                returned = count(chest, Items.CRAFTING_TABLE);
            }
            helper.assertTrue(returned == 5,
                    "all 5 returned items must reach the input-side inventory, got " + returned);
            helper.succeed();
        });
    }

    /** The mesh return's generic-inventory surface accepts any AE key type, like the tunnel's. */
    public static void meshProviderGenericReturnSurface(GameTestHelper helper) {
        meshReturnScene(helper, "mesh-ret-gen");

        helper.runAfterDelay(30, () -> {
            var inv = helper.getLevel().getCapability(appeng.api.AECapabilities.GENERIC_INTERNAL_INV,
                    helper.absolutePos(new BlockPos(2, 1, 1)), Direction.UP);
            helper.assertTrue(inv != null, "output endpoint must expose the generic inventory");
            long inserted = inv.insert(0, AEItemKey.of(Items.CRAFTING_TABLE), 5, Actionable.MODULATE);
            helper.assertTrue(inserted == 5, "generic return must accept the stack, took " + inserted);
        });

        helper.runAfterDelay(50, () -> {
            int returned = 0;
            if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                returned = count(chest, Items.CRAFTING_TABLE);
            }
            helper.assertTrue(returned == 5,
                    "generic return must reach the input-side inventory, got " + returned);
            helper.succeed();
        });
    }

    /** With several inputs on the frequency, returns land at the highest-priority input first. */
    public static void meshProviderReturnsFollowInputPriority(GameTestHelper helper) {
        var parts = meshReturnScene(helper, "mesh-ret-prio");
        parts[0].applyMeshConfig("mesh-ret-prio",
                io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart.ROLE_IN, 5, 0);
        // A second, lower-priority input facing its own chest.
        placeCable(helper, new BlockPos(3, 1, 1));
        var second = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1)),
                Direction.NORTH, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        helper.assertTrue(second != null, "second input placement failed");
        second.applyMeshConfig("mesh-ret-prio",
                io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart.ROLE_IN, 0, 0);
        helper.setBlock(new BlockPos(3, 1, 0), Blocks.CHEST);

        helper.runAfterDelay(30, () -> {
            var handler = helper.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    helper.absolutePos(new BlockPos(2, 1, 1)), Direction.UP);
            helper.assertTrue(handler != null, "output endpoint must expose a return item handler");
            int inserted;
            try (var tx = Transaction.openRoot()) {
                inserted = handler.insert(ItemResource.of(Items.CRAFTING_TABLE), 5, tx);
                tx.commit();
            }
            helper.assertTrue(inserted == 5, "return insert must be accepted, took " + inserted);
        });

        helper.runAfterDelay(50, () -> {
            int high = 0;
            int low = 0;
            if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                high = count(chest, Items.CRAFTING_TABLE);
            }
            if (helper.getBlockEntity(new BlockPos(3, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                low = count(chest, Items.CRAFTING_TABLE);
            }
            helper.assertTrue(high == 5 && low == 0,
                    "returns must fill the priority-5 input first, high " + high + " low " + low);
            helper.succeed();
        });
    }

    /** Encodes a REAL vanilla crafting recipe as an AE2 crafting pattern (assembler kind). */
    @SuppressWarnings("unchecked")
    private static ItemStack craftingPattern(GameTestHelper helper, String recipeId,
            ItemStack[] grid, ItemStack out) {
        var holder = helper.getLevel().getServer().getRecipeManager()
                .byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse(recipeId))).orElseThrow();
        return appeng.api.crafting.PatternDetailsHelper.encodeCraftingPattern(
                (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>) holder,
                grid, out, false, false);
    }

    private static ItemStack[] craftingGrid(java.util.Map<Integer, ItemStack> slots) {
        var grid = new ItemStack[9];
        java.util.Arrays.fill(grid, ItemStack.EMPTY);
        slots.forEach((slot, stack) -> grid[slot] = stack);
        return grid;
    }

    /**
     * The assembler scene: oak logs in networked storage (chest + storage bus), a
     * crafting CPU, an on-grid pattern provider whose only push face is the input
     * tunnel, and a molecular assembler that touches the grid only for POWER (cable on
     * top) - the sole pattern path to it is the output tunnel on its west face.
     */
    private static ProviderP2PTunnelPart[] buildAssemblerScene(GameTestHelper helper,
            ItemStack... patterns) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity source) {
            source.setItem(0, new ItemStack(Items.OAK_LOG, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.WEST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:pattern_provider")));
        placeCable(helper, new BlockPos(2, 2, 2));
        placeCable(helper, new BlockPos(2, 2, 3));

        var input = placeTunnel(helper, new BlockPos(2, 1, 2), Direction.SOUTH);
        var output = placeTunnel(helper, new BlockPos(2, 1, 2), Direction.EAST);
        helper.setBlock(new BlockPos(3, 1, 2),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:molecular_assembler")));
        placeCable(helper, new BlockPos(3, 2, 2));

        if (helper.getBlockEntity(new BlockPos(2, 1, 3), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe) {
            for (int i = 0; i < patterns.length; i++) {
                providerBe.getLogic().getPatternInv().setItemDirect(i, patterns[i]);
            }
        } else {
            helper.fail("no pattern provider");
        }
        return new ProviderP2PTunnelPart[] {input, output};
    }

    /**
     * The load-bearing claim of the replica architecture, verified end to end: a
     * CRAFTING pattern (the molecular-assembler kind, which can never push into plain
     * inventories) reaches an assembler only through the tunnel, the assembler crafts,
     * and the result lands back in networked storage via the return path.
     */
    public static void assemblerPatternsCrossTheTunnel(GameTestHelper helper) {
        var planks = craftingPattern(helper, "minecraft:oak_planks",
                craftingGrid(java.util.Map.of(0, new ItemStack(Items.OAK_LOG))),
                new ItemStack(Items.OAK_PLANKS, 4));
        var tunnels = buildAssemblerScene(helper, planks);
        helper.runAfterDelay(30, () -> linkTunnels(helper, tunnels[0], tunnels[1]));

        submitJob(helper, tunnels[0], AEItemKey.of(Items.OAK_PLANKS), 4, 120, () -> {
            int crafted = 0;
            if (helper.getBlockEntity(new BlockPos(1, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                crafted = count(chest, Items.OAK_PLANKS);
            }
            helper.assertTrue(crafted >= 4,
                    "4 crafted planks must return to networked storage, got " + crafted);
        });
    }

    /**
     * Machines chain: a two-step craft (logs -> planks -> sticks) where the
     * intermediate result must return through the tunnel, re-enter storage, and be
     * pushed BACK through the same tunnel for the second assembler step.
     */
    public static void assemblerCraftsChainThroughTheTunnel(GameTestHelper helper) {
        var planks = craftingPattern(helper, "minecraft:oak_planks",
                craftingGrid(java.util.Map.of(0, new ItemStack(Items.OAK_LOG))),
                new ItemStack(Items.OAK_PLANKS, 4));
        var sticks = craftingPattern(helper, "minecraft:stick",
                craftingGrid(java.util.Map.of(0, new ItemStack(Items.OAK_PLANKS),
                        3, new ItemStack(Items.OAK_PLANKS))),
                new ItemStack(Items.STICK, 4));
        var tunnels = buildAssemblerScene(helper, planks, sticks);
        helper.runAfterDelay(30, () -> linkTunnels(helper, tunnels[0], tunnels[1]));

        submitJob(helper, tunnels[0], AEItemKey.of(Items.STICK), 4, 240, () -> {
            int crafted = 0;
            if (helper.getBlockEntity(new BlockPos(1, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                crafted = count(chest, Items.STICK);
            }
            helper.assertTrue(crafted >= 4,
                    "4 chained sticks must return to networked storage, got " + crafted);
        });
    }

    /**
     * The buffered return path: machine inserts land in the nine-slot buffer without
     * touching the network mid-insert, a flush moves everything onward, a refusing
     * destination keeps returns buffered instead of lost, a full buffer refuses
     * (backpressure lives at the buffer edge), and buffered returns survive NBT.
     */
    public static void returnBufferHoldsFlushesAndBackpressures(GameTestHelper helper) {
        var buffer = io.github.johnhamilto.ae2logistics.provider.ReturnAdapters.buffer(() -> {
        });
        var items = buffer.itemHandler();
        var iron = ItemResource.of(Items.IRON_INGOT);
        for (int slot = 0; slot < 9; slot++) {
            int inserted;
            try (var tx = Transaction.openRoot()) {
                inserted = items.insert(slot, iron, 64, tx);
                tx.commit();
            }
            helper.assertTrue(inserted == 64, "buffer slot " + slot + " must accept a stack");
        }
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(items.insert(0, iron, 1, tx) == 0,
                    "a full buffer must refuse the overflow");
        }

        var received = new java.util.concurrent.atomic.AtomicLong();
        helper.assertTrue(buffer.flush(sink(received, true)), "flush must report movement");
        helper.assertTrue(received.get() == 9L * 64,
                "flush must deliver all 576, delivered " + received.get());
        helper.assertTrue(buffer.isEmpty(), "buffer must drain fully");

        try (var tx = Transaction.openRoot()) {
            items.insert(0, iron, 64, tx);
            tx.commit();
        }
        buffer.flush(sink(new java.util.concurrent.atomic.AtomicLong(), false));
        helper.assertFalse(buffer.isEmpty(), "refused returns stay buffered, never lost");

        var registries = helper.getLevel().registryAccess();
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, registries);
        buffer.writeToNBT(out, "buf");
        var fresh = io.github.johnhamilto.ae2logistics.provider.ReturnAdapters.buffer(() -> {
        });
        fresh.readFromNBT(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, out.buildResult()), "buf");
        helper.assertFalse(fresh.isEmpty(), "buffered returns must survive NBT");
        helper.succeed();
    }

    /** Counting sink; accepts everything or nothing. */
    private static appeng.api.storage.MEStorage sink(
            java.util.concurrent.atomic.AtomicLong received, boolean accept) {
        return new appeng.api.storage.MEStorage() {
            @Override
            public long insert(appeng.api.stacks.AEKey what, long amount,
                    appeng.api.config.Actionable mode,
                    appeng.api.networking.security.IActionSource source) {
                if (!accept) {
                    return 0;
                }
                if (mode == appeng.api.config.Actionable.MODULATE) {
                    received.addAndGet(amount);
                }
                return amount;
            }

            @Override
            public long extract(appeng.api.stacks.AEKey what, long amount,
                    appeng.api.config.Actionable mode,
                    appeng.api.networking.security.IActionSource source) {
                return 0;
            }

            @Override
            public void getAvailableStacks(KeyCounter out) {
            }

            @Override
            public net.minecraft.network.chat.Component getDescription() {
                return net.minecraft.network.chat.Component.literal("sink");
            }
        };
    }
}
