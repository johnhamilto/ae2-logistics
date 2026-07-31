package io.github.johnhamilto.ae2logistics.command;

import java.util.Comparator;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import appeng.server.testplots.TestPlots;

/**
 * {@code /ae2logistics testworld} - builds ONLY our plots in a sorted, signposted row
 * in front of the player. AE2's {@code /ae2 setuptestworld} interleaves everything in
 * hash order with its own ~40 plots, which makes ours nearly impossible to find.
 */
public final class TestWorldCommands {

    private TestWorldCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2logistics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("testworld")
                        .executes(TestWorldCommands::build)));
    }

    private static int build(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Players only - the plots build around you"));
            return 0;
        }
        var level = source.getLevel();
        var base = player.blockPosition();

        var ids = TestPlots.getPlotIds().stream()
                .filter(id -> id.getPath().startsWith("logistics_"))
                .sorted(Comparator.comparing(net.minecraft.resources.ResourceLocation::getPath))
                .toList();
        if (ids.isEmpty()) {
            source.sendFailure(Component.literal("No logistics_* plots registered"));
            return 0;
        }

        int cursorX = base.getX() + 3;
        int frontZ = base.getZ() + 3;
        for (var id : ids) {
            var plot = TestPlots.getById(id);
            var bounds = plot.getBounds();
            var origin = new BlockPos(cursorX - bounds.minX(), base.getY() - bounds.minY(),
                    frontZ - bounds.minZ());

            int floorY = base.getY() - 1;
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    level.setBlockAndUpdate(new BlockPos(origin.getX() + x, floorY, origin.getZ() + z),
                            Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
            plot.build(level, player, origin);

            var signPos = new BlockPos(cursorX, base.getY(), frontZ - 2);
            level.setBlockAndUpdate(signPos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            level.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());
            if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
                var label = Component.literal(id.getPath().substring("logistics_".length()));
                sign.setText(sign.getFrontText().setMessage(1, label), true);
                sign.setText(sign.getBackText().setMessage(1, label), false);
            }

            var where = origin.getX() + bounds.minX() + "," + base.getY() + ","
                    + (origin.getZ() + bounds.minZ());
            source.sendSuccess(() -> Component.literal(id.getPath() + " at " + where), false);
            cursorX += bounds.getXSpan() + 4;
        }
        return ids.size();
    }
}
