package io.github.johnhamilto.ae2logistics.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.johnhamilto.ae2logistics.block.RegisterBankBlockEntity;
import io.github.johnhamilto.ae2logistics.item.SignalCardItem;

public final class SignalCommands {

    private static final SimpleCommandExceptionType NOT_LOOKING_AT_BANK = new SimpleCommandExceptionType(
            Component.literal("You must be looking at a Register Bank"));

    private SignalCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2logistics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("signal")
                        .then(Commands.literal("set")
                                .then(Commands.argument("channel", ResourceLocationArgument.id())
                                        .then(Commands.argument("value", LongArgumentType.longArg(0))
                                                .executes(SignalCommands::setSignal))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("channel", ResourceLocationArgument.id())
                                        .executes(SignalCommands::getSignal)))
                        .then(Commands.literal("list")
                                .executes(SignalCommands::listSignals))
                        .then(Commands.literal("card")
                                .then(Commands.argument("channel", ResourceLocationArgument.id())
                                        .executes(SignalCommands::giveCard)))));
    }

    private static int listSignals(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var bank = targetedBank(context.getSource());
        var signals = bank.signals();
        if (signals.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No signals set on this bank"), false);
            return 0;
        }
        for (var entry : signals.entrySet()) {
            context.getSource().sendSuccess(
                    () -> Component.literal(entry.getKey() + " = " + entry.getValue()), false);
        }
        return signals.size();
    }

    private static int giveCard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var channel = ResourceLocationArgument.getId(context, "channel");
        player.getInventory().placeItemBackInInventory(SignalCardItem.bound(channel));
        context.getSource().sendSuccess(() -> Component.literal("Signal Card bound to " + channel), false);
        return 1;
    }

    private static int setSignal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var bank = targetedBank(context.getSource());
        var channel = ResourceLocationArgument.getId(context, "channel");
        var value = LongArgumentType.getLong(context, "value");
        bank.setSignal(channel, value);
        context.getSource().sendSuccess(
                () -> Component.literal(channel + " = " + value), false);
        return 1;
    }

    private static int getSignal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var bank = targetedBank(context.getSource());
        var channel = ResourceLocationArgument.getId(context, "channel");
        var value = bank.getSignal(channel);
        context.getSource().sendSuccess(
                () -> Component.literal(channel + " = " + value), false);
        return 1;
    }

    private static RegisterBankBlockEntity targetedBank(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var from = player.getEyePosition();
        var to = from.add(player.getLookAngle().scale(10));
        var hit = player.level().clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(hit.getBlockPos()) instanceof RegisterBankBlockEntity bank) {
            return bank;
        }
        throw NOT_LOOKING_AT_BANK.create();
    }
}
