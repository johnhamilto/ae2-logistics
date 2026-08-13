package io.github.johnhamilto.ae2logistics.command;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.johnhamilto.ae2logistics.block.WirelessBridgeBlockEntity;

public final class WirelessCommands {

    private WirelessCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2logistics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("wireless")
                        .then(Commands.literal("status")
                                .executes(WirelessCommands::status))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        var player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }
        if (!(player.pick(8, 0, false) instanceof BlockHitResult hit)
                || !(player.level().getBlockEntity(hit.getBlockPos())
                        instanceof WirelessBridgeBlockEntity bridge)) {
            context.getSource().sendFailure(Component.literal("Look at an ME Wireless Bridge"));
            return 0;
        }
        var anchor = bridge.anchor();
        context.getSource().sendSuccess(() -> Component.literal(
                "anchor: " + (anchor == null ? "none - place after clicking an access point"
                        : anchor.pos().toShortString() + " in " + anchor.dimension().identifier())),
                false);
        var linked = bridge.linkedApPos();
        context.getSource().sendSuccess(() -> Component.literal(
                linked != null ? "linked via access point at " + linked.toShortString()
                        : "out of coverage - no active access point in range"),
                false);
        var grid = bridge.grid();
        context.getSource().sendSuccess(() -> Component.literal(
                "grid nodes: " + (grid == null ? 0 : grid.size())), false);
        return bridge.isLinked() ? 1 : 0;
    }
}
