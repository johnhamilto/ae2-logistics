package io.github.johnhamilto.ae2logistics.item;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.PlayerSource;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Pattern Import Card: while it sits anywhere in a player's inventory and a
 * pattern encoding window is open, the window's blank-pattern slot restocks from
 * network storage - encoding never runs dry. Detection is instanceof on the open
 * menu, so every host wearing AE2's encoding window works: the cable part, the
 * wireless forms, and ExtendedAE's extended terminal (their menus subclass it).
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
        if (!hasCard(player)) {
            return 0;
        }
        var slots = menu.getSlots(SlotSemantics.BLANK_PATTERN);
        if (slots.isEmpty() || !slots.get(0).getItem().isEmpty()) {
            return 0;
        }
        var host = menu.getHost();
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

    private static boolean hasCard(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(AE2Logistics.PATTERN_IMPORT_CARD.get())) {
                return true;
            }
        }
        return false;
    }
}
