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
            var line = new StringBuilder("entry " + (i + 1) + ": " + typeLabel(entry.type()));
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

        // Import/export entries move items to/from the SUBNET's storage; without a
        // storage-capable entry the subnet has nowhere to put or take anything.
        boolean movesItems = false;
        boolean hasSubnetStorage = false;
        boolean visibleFromMain = false;
        for (int i = 0; i < SubnetCoreBlockEntity.ENTRIES; i++) {
            var type = core.entry(i).type();
            if (type == null) {
                continue;
            }
            switch (type) {
                case IMPORT_BUS, EXPORT_BUS -> movesItems = true;
                case STORAGE_BUS, UPLINK -> hasSubnetStorage = true;
                case DOWNLINK -> visibleFromMain = true;
            }
        }
        if (movesItems && !hasSubnetStorage) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "hint: the subnet has NO storage - import/export entries move items"
                            + " to/from the SUBNET, not the main network. Add a FROM-MAIN"
                            + " entry (main's storage appears inside the subnet) or a"
                            + " STORAGE entry (a faced inventory becomes subnet storage)."), false);
        }
        if (hasSubnetStorage && !movesItems && !visibleFromMain) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "hint: nothing reads this subnet - add import/export entries to move"
                            + " items, or a TO-MAIN entry so the subnet's storage appears"
                            + " on the main network."), false);
        }
        return configured;
    }

    /** Named for whose storage appears where; enum names stay for NBT compat. */
    private static String typeLabel(io.github.johnhamilto.ae2logistics.block.SubnetCoreEntry.Type type) {
        return switch (type) {
            case STORAGE_BUS -> "storage";
            case IMPORT_BUS -> "import";
            case EXPORT_BUS -> "export";
            case UPLINK -> "from-main";
            case DOWNLINK -> "to-main";
        };
    }
}
