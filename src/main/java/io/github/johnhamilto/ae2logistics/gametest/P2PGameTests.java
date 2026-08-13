package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.P2PActionPayload;

public class P2PGameTests {

    static void register() {
        LogisticsTestInstance.add("retuneAppliesAndGuardsInputCollision", "empty5", P2PGameTests::retuneAppliesAndGuardsInputCollision);
    }

    public static void retuneAppliesAndGuardsInputCollision(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        var busPos = helper.absolutePos(new BlockPos(2, 1, 1));
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), busPos, null, null, (IPartItem<?>) cable);

        var tunnelItem = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:me_p2p_tunnel"));
        helper.assertTrue(tunnelItem instanceof IPartItem<?>, "me p2p tunnel must be a part item");
        var tunnelA = (P2PTunnelPart<?>) PartHelper.setPart(helper.getLevel(), busPos, Direction.UP, null,
                (IPartItem<?>) tunnelItem);
        var tunnelB = (P2PTunnelPart<?>) PartHelper.setPart(helper.getLevel(), busPos, Direction.NORTH, null,
                (IPartItem<?>) tunnelItem);

        helper.runAfterDelay(10, () -> {
            var node = tunnelA.getMainNode().getNode();
            helper.assertTrue(node != null && node.getGrid() != null, "tunnel A has no grid");
            var service = P2PService.get(node.getGrid());

            short first = service.newFrequency();
            service.updateFreq(tunnelA, first);
            helper.assertTrue(service.getInput(first) == tunnelA, "tunnel A should be the input on its frequency");

            helper.assertTrue(!P2PActionPayload.retune(tunnelB, first),
                    "retuning a second input onto an occupied frequency must be refused");

            short second = service.newFrequency();
            helper.assertTrue(P2PActionPayload.retune(tunnelB, second), "retune to a fresh frequency must succeed");
            helper.assertTrue(tunnelB.getFrequency() == second,
                    "tunnel B frequency should be " + second + ", got " + tunnelB.getFrequency());
            helper.assertTrue(service.getInput(second) == tunnelB, "tunnel B should be the input on its frequency");
            helper.succeed();
        });
    }
}
