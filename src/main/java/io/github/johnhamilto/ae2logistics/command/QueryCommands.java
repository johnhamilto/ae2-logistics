package io.github.johnhamilto.ae2logistics.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;

import io.github.johnhamilto.ae2logistics.query.CompiledQuery;
import io.github.johnhamilto.ae2logistics.query.QueryParser;
import io.github.johnhamilto.ae2logistics.query.QueryService;

/** {@code /ae2logistics query <expression>} - evaluate a query on the network you face. */
public final class QueryCommands {

    private static final SimpleCommandExceptionType NOT_LOOKING_AT_GRID = new SimpleCommandExceptionType(
            Component.literal("You must be looking at a block on an ME network"));

    private QueryCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2logistics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("query")
                        .then(Commands.literal("card")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(QueryCommands::giveCard)))
                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                .executes(QueryCommands::run))));
    }

    private static int giveCard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var name = StringArgumentType.getString(context, "name");
        player.getInventory().placeItemBackInInventory(
                io.github.johnhamilto.ae2logistics.item.QueryCardItem.bound(name));
        context.getSource().sendSuccess(() -> Component.literal("Query Card bound to @" + name), false);
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var grid = targetedGrid(context.getSource());
        var expression = StringArgumentType.getString(context, "expression");

        var parsed = QueryParser.parse(expression);
        if (!parsed.ok()) {
            context.getSource().sendFailure(Component.literal(
                    "Syntax error: " + (parsed.error() == null ? "?" : parsed.error())));
            return 0;
        }
        var service = grid.getService(QueryService.class);
        var query = service.compiled(expression);
        var queryContext = service.context();
        var stacks = queryContext.stacks();
        if (query == null || stacks == null) {
            context.getSource().sendFailure(Component.literal("Could not evaluate the query"));
            return 0;
        }

        int kinds = 0;
        long total = 0;
        int shown = 0;
        for (var entry : stacks) {
            if (entry.getLongValue() <= 0 || !CompiledQuery.isQueryableKey(entry.getKey())
                    || !query.matches(entry.getKey(), queryContext)) {
                continue;
            }
            kinds++;
            total += entry.getLongValue();
            if (shown < 8) {
                shown++;
                var line = entry.getLongValue() + " x " + entry.getKey().getDisplayName().getString();
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        int finalKinds = kinds;
        long finalTotal = total;
        context.getSource().sendSuccess(() -> Component.literal(
                finalKinds + " kinds, " + finalTotal + " total"
                        + (finalKinds > 8 ? " (showing first 8)" : "")), false);
        return kinds;
    }

    private static IGrid targetedGrid(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var from = player.getEyePosition();
        var to = from.add(player.getLookAngle().scale(10));
        var hit = player.level().clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            for (var direction : Direction.values()) {
                var node = GridHelper.getExposedNode(player.level(), hit.getBlockPos(), direction);
                if (node != null && node.getGrid() != null) {
                    return node.getGrid();
                }
            }
        }
        throw NOT_LOOKING_AT_GRID.create();
    }
}
