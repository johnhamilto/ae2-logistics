package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.TracePanelBlock;
import io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class TracePanelGameTests {

    private static void placePanel(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, AE2Logistics.TRACE_PANEL.get().defaultBlockState()
                .setValue(TracePanelBlock.FACING, Direction.NORTH));
    }

    private static TracePanelBlockEntity panel(GameTestHelper helper, BlockPos pos) {
        return (TracePanelBlockEntity) helper.getBlockEntity(pos);
    }

    /**
     * Formation is placement-shaped: a filled 2x2 merges into one group under the
     * min-corner master, every member agreeing; breaking a corner drops the
     * survivors back to standalone 1x1 panels.
     */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void panelsFormRectanglesByPlacement(GameTestHelper helper) {
        // North-facing plane: viewer-right is counterclockwise of north = west (-X).
        placePanel(helper, new BlockPos(2, 1, 2));
        placePanel(helper, new BlockPos(1, 1, 2));
        placePanel(helper, new BlockPos(2, 2, 2));
        placePanel(helper, new BlockPos(1, 2, 2));

        helper.runAfterDelay(20, () -> {
            var corner = panel(helper, new BlockPos(1, 1, 2));
            var origin = corner.groupOrigin();
            for (var pos : new BlockPos[] {new BlockPos(2, 1, 2), new BlockPos(1, 1, 2),
                    new BlockPos(2, 2, 2), new BlockPos(1, 2, 2)}) {
                var member = panel(helper, pos);
                helper.assertTrue(member.groupOrigin().equals(origin)
                        && member.groupWidth() == 2 && member.groupHeight() == 2,
                        "every member must agree on one 2x2 group, " + pos + " disagrees");
            }
            helper.destroyBlock(new BlockPos(2, 2, 2));
        });

        helper.runAfterDelay(40, () -> {
            for (var pos : new BlockPos[] {new BlockPos(2, 1, 2), new BlockPos(1, 1, 2),
                    new BlockPos(1, 2, 2)}) {
                var member = panel(helper, pos);
                helper.assertTrue(member.groupWidth() == 1 && member.groupHeight() == 1
                        && member.isMaster(),
                        "an L-shape must fall back to standalone panels, " + pos + " did not");
            }
            helper.succeed();
        });
    }

    /**
     * Binding through any member lands on the master, and the master samples the
     * bound channel from the grid's signal store once a second.
     */
    @GameTest(template = "empty5", timeoutTicks = 300)
    public void panelSamplesBoundChannel(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.REGISTER_BANK.get());
        placePanel(helper, new BlockPos(2, 1, 1));
        placePanel(helper, new BlockPos(3, 1, 1));

        var channel = ResourceLocation.parse("demo:panel");
        helper.runAfterDelay(20, () -> {
            var bank = (io.github.johnhamilto.ae2logistics.block.RegisterBankBlockEntity) helper
                    .getBlockEntity(new BlockPos(1, 1, 2));
            bank.setSignal(channel, 777);
            // Bind through the NON-master member: it must land on the master.
            var slave = panel(helper, new BlockPos(2, 1, 1)).isMaster()
                    ? panel(helper, new BlockPos(3, 1, 1)) : panel(helper, new BlockPos(2, 1, 1));
            helper.assertTrue(slave.bind(channel, false), "binding through a member must work");
            // groupOrigin is a WORLD position - resolve it through the level, not the helper.
            var master = (TracePanelBlockEntity) helper.getLevel().getBlockEntity(slave.groupOrigin());
            helper.assertTrue(master != null && master.boundChannels().contains(channel),
                    "the binding must live on the master");
        });

        helper.runAfterDelay(120, () -> {
            var any = panel(helper, new BlockPos(2, 1, 1));
            var master = (TracePanelBlockEntity) helper.getLevel().getBlockEntity(any.groupOrigin());
            helper.assertTrue(master != null, "master must exist");
            var bank = (io.github.johnhamilto.ae2logistics.block.RegisterBankBlockEntity) helper
                    .getBlockEntity(new BlockPos(1, 1, 2));
            var panelNode = master.getGridNode(null);
            var bankNode = bank.getGridNode(null);
            helper.assertTrue(panelNode != null, "probe: panel has no grid node");
            helper.assertTrue(bankNode != null, "probe: bank has no grid node");
            var slaveNode = panel(helper, new BlockPos(2, 1, 1)).getGridNode(null);
            helper.assertTrue(slaveNode != null && slaveNode.getGrid() == bankNode.getGrid(),
                    "probe: SLAVE panel is not on the bank grid");
            helper.assertTrue(panelNode.getGrid() == bankNode.getGrid(),
                    "probe: MASTER panel is not on the bank grid (panel-panel link failed)");
            var samples = master.samples(channel);
            helper.assertTrue(samples.length >= 2,
                    "master must have recorded samples, got " + samples.length);
            helper.assertTrue(samples[samples.length - 1] == 777,
                    "latest sample must carry the signal value, got "
                            + samples[samples.length - 1]);
            helper.succeed();
        });
    }
}
