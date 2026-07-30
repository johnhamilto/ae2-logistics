package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.ids.AEComponents;
import appeng.me.service.P2PService;
import appeng.util.SettingsFrom;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;
import io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProviderTunnelGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
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

    /** Simulate-then-modulate, the way a pattern provider pushes one batch entry. */
    private static long push(ProviderP2PTunnelPart input, appeng.api.stacks.AEKey what, long amount) {
        var storage = input.exposedStorage();
        if (storage == null) {
            return 0;
        }
        long simulated = storage.insert(what, amount, Actionable.SIMULATE, IActionSource.empty());
        if (simulated < amount) {
            return 0;
        }
        return storage.insert(what, amount, Actionable.MODULATE, IActionSource.empty());
    }

    /**
     * Two batches through one input tunnel must land whole on DIFFERENT machines: the
     * first machine still holds its batch, so it reports busy like a blocking provider.
     */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void providerTunnelDistributesBatchesAcrossMachines(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(3, 1, 1));

        var input = placeTunnel(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        var outputA = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.UP);
        var outputB = placeTunnel(helper, new BlockPos(3, 1, 1), Direction.UP);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.CHEST);

        helper.runAfterDelay(30, () -> {
            linkTunnels(helper, input, outputA, outputB);
        });

        helper.runAfterDelay(40, () -> {
            long iron = push(input, AEItemKey.of(Items.IRON_INGOT), 8);
            helper.assertTrue(iron == 8, "first batch must be accepted whole, got " + iron);
            long gold = push(input, AEItemKey.of(Items.GOLD_INGOT), 4);
            helper.assertTrue(gold == 4, "second batch must be accepted whole, got " + gold);
        });

        helper.runAfterDelay(50, () -> {
            int chestsWithOneKind = 0;
            int total = 0;
            for (var chestPos : new BlockPos[] {new BlockPos(2, 2, 1), new BlockPos(3, 2, 1)}) {
                if (helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                    var kinds = new java.util.HashSet<net.minecraft.world.item.Item>();
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        var stack = chest.getItem(i);
                        if (!stack.isEmpty()) {
                            kinds.add(stack.getItem());
                            total += stack.getCount();
                        }
                    }
                    if (kinds.size() == 1) {
                        chestsWithOneKind++;
                    }
                }
            }
            helper.assertTrue(total == 12, "all 12 items must arrive, got " + total);
            helper.assertTrue(chestsWithOneKind == 2,
                    "each machine must hold exactly one complete batch, got " + chestsWithOneKind);
            helper.succeed();
        });
    }

    /**
     * The push path is key-type agnostic: a fluid rides the external-storage strategies
     * into a cauldron, the same registry companion mods use for chemicals.
     */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void providerTunnelPushesFluidsGenerically(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = placeTunnel(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        var output = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.UP);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CAULDRON);

        helper.runAfterDelay(30, () -> {
            linkTunnels(helper, input, output);
        });

        helper.runAfterDelay(40, () -> {
            long filled = push(input, AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER), 1000);
            helper.assertTrue(filled == 1000, "the tunnel must accept a full bucket, took " + filled);
        });

        helper.runAfterDelay(50, () -> {
            var state = helper.getBlockState(new BlockPos(2, 2, 1));
            helper.assertTrue(state.is(Blocks.WATER_CAULDRON)
                    && state.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    "cauldron must be full of water, state " + state);
            helper.succeed();
        });
    }

    /** The provider mesh transport delivers any key type too, via the typed provider part. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void providerMeshEndpointRoutesAnyKey(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.NORTH, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        var output = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.UP, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        helper.assertTrue(input != null && output != null, "typed provider endpoint placement failed");
        input.applyMeshConfig("prov-typed", MeshEndpointPart.ROLE_IN, 0, 0);
        output.applyMeshConfig("prov-typed", MeshEndpointPart.ROLE_OUT, 0, 0);
        helper.assertTrue(input.capabilityMask() == MeshRegistry.TYPE_PROVIDER,
                "typed provider endpoint must lock to the provider transport");
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CAULDRON);

        helper.runAfterDelay(30, () -> {
            var storage = input.exposedMeStorage();
            helper.assertTrue(storage != null, "provider endpoint must expose ME storage");
            long simulated = storage.insert(AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER),
                    1000, Actionable.SIMULATE, IActionSource.empty());
            helper.assertTrue(simulated == 1000, "simulate must accept a bucket, took " + simulated);
            long filled = storage.insert(AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER),
                    1000, Actionable.MODULATE, IActionSource.empty());
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
}
