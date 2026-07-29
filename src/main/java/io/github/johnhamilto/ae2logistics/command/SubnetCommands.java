package io.github.johnhamilto.ae2logistics.command;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.johnhamilto.ae2logistics.block.SubnetCoreBlockEntity;

public final class SubnetCommands {

    private SubnetCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2logistics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("subnet")
                        .then(Commands.literal("status")
                                .executes(SubnetCommands::status))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        var player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }
        if (!(player.pick(8, 0, false) instanceof BlockHitResult hit)
                || !(player.level().getBlockEntity(hit.getBlockPos())
                        instanceof SubnetCoreBlockEntity core)) {
            context.getSource().sendFailure(Component.literal("Look at an ME Subnet Core"));
            return 0;
        }
        var main = core.mainGrid();
        var internal = core.internalGrid();
        context.getSource().sendSuccess(() -> Component.literal(
                "core: " + (core.coreActive() ? "online" : "OFFLINE (needs main-grid power + a channel)")
                        + " - main grid " + (main == null ? 0 : main.size()) + " nodes, internal grid "
                        + (internal == null ? 0 : internal.size()) + " nodes"),
                false);
        int configured = 0;
        for (int i = 0; i < SubnetCoreBlockEntity.ENTRIES; i++) {
            var entry = core.entry(i);
            if (entry.type() == null) {
                continue;
            }
            configured++;
            var line = new StringBuilder("entry " + (i + 1) + ": " + entry.type().name().toLowerCase(java.util.Locale.ROOT));
            if (entry.type().faceBound()) {
                line.append(" face=").append(entry.face().getName());
                int targets = core.externalStoragesFor(entry).size();
                line.append(targets > 0 ? " target=inventory found" : " target=NO INVENTORY on that face");
            }
            line.append(" p=").append(entry.priority());
            if (entry.filter() != null) {
                line.append(" filter=").append(entry.filter().what());
            }
            line.append(entry.isActive() ? " [active]" : " [DARK - no internal channel or power]");
            var text = line.toString();
            context.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        if (configured == 0) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "no entries configured - open the GUI, click a row, cycle its type, Apply"), false);
        }
        return configured;
    }
}
