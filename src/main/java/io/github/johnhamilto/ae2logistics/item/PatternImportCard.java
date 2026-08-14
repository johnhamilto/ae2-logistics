package io.github.johnhamilto.ae2logistics.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.me.helpers.PlayerSource;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Pattern Import Card: INSTALLED per terminal, never a player-wide aura. While an
 * encoding window whose terminal carries the card is open, the blank-pattern slot
 * restocks from network storage.
 *
 * <p>Two install surfaces. Terminals with real upgrade slots (AE2WTLib's wireless
 * encoding terminal and the universal terminal; associations registered when the
 * mod is present) take the card as a normal upgrade. The cable-part terminal has
 * no upgrade slots upstream, so the card is built in by hand: sneak-click the
 * part with the card to install (consumes it), sneak-click with an empty hand to
 * pop it back out. The install lives on the cable side under the part - wrenching
 * the terminal off leaves it for the next terminal placed there; breaking the
 * cable scraps it.
 */
public final class PatternImportCard {

    /** Blanks pulled per refill; the slot refills only when it runs empty. */
    private static final int BATCH = 8;

    private PatternImportCard() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (!player.level().isClientSide && player.tickCount % 10 == 0) {
            topUp(player);
        }
    }

    /** The whole behavior, callable directly by gametests. Returns blanks delivered. */
    public static int topUp(Player player) {
        if (!(player.containerMenu instanceof PatternEncodingTermMenu menu)) {
            return 0;
        }
        var host = menu.getHost();
        boolean installed = host.getUpgrades()
                .getInstalledUpgrades(AE2Logistics.PATTERN_IMPORT_CARD.get()) > 0
                || (host instanceof appeng.parts.AEBasePart part && installedOnPart(part));
        if (!installed) {
            return 0;
        }
        var slots = menu.getSlots(SlotSemantics.BLANK_PATTERN);
        if (slots.isEmpty() || !slots.get(0).getItem().isEmpty()) {
            return 0;
        }
        var storage = host.getInventory();
        if (storage == null) {
            return 0;
        }
        var source = new PlayerSource(player, host instanceof IActionHost actionHost ? actionHost : null);
        long got = storage.extract(AEItemKey.of(AEItems.BLANK_PATTERN.asItem()), BATCH,
                Actionable.MODULATE, source);
        if (got > 0) {
            slots.get(0).set(AEItems.BLANK_PATTERN.stack((int) got));
        }
        return (int) got;
    }

    // --- the cable-part install, stored per (cable bus, side) like P2P names ---

    public static boolean installedOnPart(appeng.parts.AEBasePart part) {
        var host = part.getHost().getBlockEntity();
        var installs = host.getExistingDataOrNull(AE2Logistics.PATTERN_IMPORT_INSTALLS);
        return installs != null && installs.contains(sideKey(part));
    }

    public static void installOnPart(appeng.parts.AEBasePart part) {
        var host = part.getHost().getBlockEntity();
        host.getData(AE2Logistics.PATTERN_IMPORT_INSTALLS).add(sideKey(part));
        host.setChanged();
    }

    public static void uninstallFromPart(appeng.parts.AEBasePart part) {
        var host = part.getHost().getBlockEntity();
        var installs = host.getExistingDataOrNull(AE2Logistics.PATTERN_IMPORT_INSTALLS);
        if (installs != null) {
            installs.remove(sideKey(part));
            host.setChanged();
        }
    }

    private static String sideKey(appeng.parts.AEBasePart part) {
        var side = part.getSide();
        return side == null ? "center" : side.getName();
    }

    /** Sneak-click install/uninstall on cable-part encoding terminals. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }
        var level = event.getLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof IPartHost partHost)) {
            return;
        }
        var selected = partHost.selectPartWorld(event.getHitVec().getLocation());
        if (!(selected.part instanceof IPatternTerminalMenuHost)
                || !(selected.part instanceof appeng.parts.AEBasePart part)) {
            return;
        }
        var held = event.getItemStack();
        if (held.is(AE2Logistics.PATTERN_IMPORT_CARD.get())) {
            // Unambiguous gesture: cancel on both sides so the hand does not swing twice.
            event.setCanceled(true);
            event.setCancellationResult(
                    net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide()));
            if (level.isClientSide()) {
                return;
            }
            if (installedOnPart(part)) {
                player.displayClientMessage(
                        Component.literal("Pattern Import Card already installed"), true);
                return;
            }
            held.shrink(1);
            installOnPart(part);
            player.displayClientMessage(Component.literal("Pattern Import Card installed"), true);
        } else if (held.isEmpty() && !level.isClientSide() && installedOnPart(part)) {
            // Only the server knows the install state for the empty-hand gesture.
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            uninstallFromPart(part);
            var card = new ItemStack(AE2Logistics.PATTERN_IMPORT_CARD.get());
            if (!player.addItem(card)) {
                player.drop(card, false);
            }
            player.displayClientMessage(Component.literal("Pattern Import Card removed"), true);
        }
    }
}
