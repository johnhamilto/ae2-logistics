package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.parts.p2p.P2PTunnelPart;

import mekanism.api.Action;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismChemicals;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

/**
 * Typed Mekanism/AppMek test bodies, in their own class so CompatGameTests never
 * classloads the Mekanism API when the suite runs bare - callers gate on CompatMods.
 */
final class AppMekCompatHooks {

    private AppMekCompatHooks() {
    }

    /**
     * Chemical into the output endpoint's native cap, out of the pattern provider
     * behind the input face - the receptacle real returns use. The provider side
     * rides AppMek's own bridge (its chemical-handler view over AE2 generic
     * inventories), so this covers both directions of the compat story.
     */
    static void chemicalReturnRoundTrip(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        var input = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.NORTH, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        var output = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)),
                Direction.UP, null, AE2Logistics.MESH_ENDPOINT_PROVIDER_PART.get());
        helper.assertTrue(input != null && output != null, "provider endpoint placement failed");
        input.applyMeshConfig("appmek-ret", MeshEndpointPart.ROLE_IN, 0, 0);
        output.applyMeshConfig("appmek-ret", MeshEndpointPart.ROLE_OUT, 0, 0);
        helper.setBlock(new BlockPos(1, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));

        helper.runAfterDelay(30, () -> {
            var inputCap = helper.getLevel().getCapability(Capabilities.CHEMICAL.block(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH);
            helper.assertTrue(inputCap == null,
                    "input endpoint faces must not expose the chemical return");
            var handler = helper.getLevel().getCapability(Capabilities.CHEMICAL.block(),
                    helper.absolutePos(new BlockPos(2, 1, 1)), Direction.UP);
            helper.assertTrue(handler != null,
                    "output endpoint must expose a native chemical return handler");
            var remainder = handler.insertChemical(MekanismChemicals.HYDROGEN.asStack(1000),
                    Action.EXECUTE);
            helper.assertTrue(remainder.isEmpty(),
                    "chemical return must be accepted, remainder " + remainder.getAmount());
        });

        helper.runAfterDelay(50, () -> {
            if (!(helper.getBlockEntity(new BlockPos(1, 1, 0))
                    instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe)) {
                helper.fail("no pattern provider behind the input face");
                return;
            }
            var returnInv = providerBe.getLogic().getReturnInv();
            long stored = 0;
            for (int i = 0; i < returnInv.size(); i++) {
                if (returnInv.getKey(i) instanceof me.ramidzkh.mekae2.ae2.MekanismKey key
                        && key.getStack().getChemical() == MekanismChemicals.HYDROGEN.get()) {
                    stored += returnInv.getAmount(i);
                }
            }
            helper.assertTrue(stored == 1000,
                    "1000 mB hydrogen must land in the provider's return slots, got " + stored);
            helper.succeed();
        });
    }

    /** The tunnel side of the registration: outputs expose the chemical cap, inputs never. */
    static void tunnelOutputExposesChemicalReturn(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        var tunnel = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.UP, null, AE2Logistics.PROVIDER_P2P_TUNNEL_PART.get());
        helper.assertTrue(tunnel != null, "provider tunnel placement failed");

        helper.runAfterDelay(20, () -> {
            var inputCap = helper.getLevel().getCapability(Capabilities.CHEMICAL.block(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP);
            helper.assertTrue(inputCap == null, "input tunnels must not expose the chemical return");
            makeOutput(tunnel);
            var outputCap = helper.getLevel().getCapability(Capabilities.CHEMICAL.block(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP);
            helper.assertTrue(outputCap != null, "output tunnels must expose the chemical return");
            helper.succeed();
        });
    }

    private static void makeOutput(P2PTunnelPart<?> tunnel) {
        try {
            var method = P2PTunnelPart.class.getDeclaredMethod("setOutput", boolean.class);
            method.setAccessible(true);
            method.invoke(tunnel, true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }
}
