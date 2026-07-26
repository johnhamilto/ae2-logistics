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
import io.github.johnhamilto.ae2logistics.signal.SignalKey;

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
                                        .executes(SignalCommands::getSignal)))));
    }

    private static int setSignal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var bank = targetedBank(context.getSource());
        var key = SignalKey.of(ResourceLocationArgument.getId(context, "channel"));
        var value = LongArgumentType.getLong(context, "value");
        bank.setSignal(key, value);
        context.getSource().sendSuccess(
                () -> Component.literal(key.channel() + " = " + value), false);
        return 1;
    }

    private static int getSignal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var bank = targetedBank(context.getSource());
        var key = SignalKey.of(ResourceLocationArgument.getId(context, "channel"));
        var value = bank.getSignal(key);
        context.getSource().sendSuccess(
                () -> Component.literal(key.channel() + " = " + value), false);
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
