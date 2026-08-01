package io.github.johnhamilto.ae2logistics.command;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.johnhamilto.ae2logistics.block.StorageJanitorBlockEntity;

public final class JanitorCommands {

    private JanitorCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2logistics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("janitor")
                        .executes(JanitorCommands::toggle)));
    }

    /** Toggles the Storage Janitor the player is looking at. */
    private static int toggle(CommandContext<CommandSourceStack> context) {
        var player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        if (player.pick(8, 0, false) instanceof BlockHitResult hit
                && player.level().getBlockEntity(hit.getBlockPos())
                        instanceof StorageJanitorBlockEntity janitor) {
            janitor.toggle();
            context.getSource().sendSuccess(() -> Component.literal(
                    janitor.running() ? "Janitor pass started" : "Janitor stopped"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Look at a Storage Janitor"));
        return 0;
    }
}
