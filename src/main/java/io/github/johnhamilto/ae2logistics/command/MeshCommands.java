package io.github.johnhamilto.ae2logistics.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public final class MeshCommands {

    private MeshCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2logistics")
                .requires(net.minecraft.commands.Commands.hasPermission(new net.minecraft.server.permissions.PermissionCheck.Require(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)))
                .then(Commands.literal("mesh")
                        .then(Commands.literal("list")
                                .executes(MeshCommands::list))
                        .then(Commands.literal("status")
                                .then(Commands.argument("frequency", StringArgumentType.greedyString())
                                        .executes(MeshCommands::status)))
                        .then(Commands.literal("relink")
                                .then(Commands.argument("frequency", StringArgumentType.greedyString())
                                        .executes(MeshCommands::relink)))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var frequencies = MeshRegistry.allFrequencies();
        if (frequencies.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No mesh frequencies"), false);
            return 0;
        }
        for (var entry : frequencies.entrySet()) {
            int mask = 0;
            int flagged = 0;
            var networks = new java.util.HashSet<Object>();
            for (var endpoint : entry.getValue()) {
                mask |= endpoint.capabilityMask();
                networks.add(networkTag(endpoint));
                if (MeshRegistry.statusOf(endpoint) != MeshRegistry.STATUS_OK) {
                    flagged++;
                }
            }
            var line = entry.getKey() + ": " + entry.getValue().size() + " endpoints ["
                    + MeshRegistry.describeTypes(mask) + "]"
                    + (networks.size() > 1
                            ? " across " + networks.size() + " networks (frequencies do not cross networks)"
                            : "")
                    + (flagged > 0 ? " - " + flagged + " flagged" : "");
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return frequencies.size();
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        var frequency = StringArgumentType.getString(context, "frequency");
        var endpoints = MeshRegistry.endpoints(frequency);
        if (endpoints.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("No endpoints on '" + frequency + "'"), false);
            return 0;
        }
        for (var endpoint : endpoints) {
            var host = endpoint.getHost().getBlockEntity();
            var pos = host.getBlockPos();
            var dimension = host.getLevel() != null
                    ? host.getLevel().dimension().identifier().toString()
                    : "?";
            var line = pos.getX() + "," + pos.getY() + "," + pos.getZ() + " (" + dimension + ") "
                    + "net " + networkTag(endpoint) + " "
                    + roleLabel(endpoint.role()) + " ["
                    + MeshRegistry.describeTypes(endpoint.capabilityMask()) + "] "
                    + statusLabel(endpoint);
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return endpoints.size();
    }

    private static int relink(CommandContext<CommandSourceStack> context) {
        var frequency = StringArgumentType.getString(context, "frequency");
        MeshRegistry.forceRelink(frequency);
        context.getSource().sendSuccess(
                () -> Component.literal("Rebuilding ME links for '" + frequency + "' next tick"), false);
        return 1;
    }

    /** Short stable-ish tag distinguishing host networks in command output. */
    private static String networkTag(MeshEndpointPart endpoint) {
        var node = endpoint.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return "-";
        }
        return "#" + Integer.toHexString(System.identityHashCode(node.getGrid()) & 0xFFFF);
    }

    private static String roleLabel(byte role) {
        return switch (role) {
            case MeshEndpointPart.ROLE_OUT -> "OUT";
            case MeshEndpointPart.ROLE_BOTH -> "BOTH";
            default -> "IN";
        };
    }

    private static String statusLabel(MeshEndpointPart endpoint) {
        var status = switch (MeshRegistry.statusOf(endpoint)) {
            case MeshRegistry.STATUS_OFFLINE -> "offline";
            case MeshRegistry.STATUS_ME_WAITING -> "waiting for ME peer";
            default -> "OK";
        };
        return switch (endpoint.meLinkState()) {
            case MeshRegistry.ME_STATE_LINKED -> status + ", ME lane";
            case MeshRegistry.ME_STATE_STANDBY -> status + ", ME standby";
            case MeshRegistry.ME_STATE_LOOP -> status + ", cabled (no ME lane needed)";
            default -> status;
        };
    }
}
