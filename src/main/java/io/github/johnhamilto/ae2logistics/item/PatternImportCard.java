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
 * Pattern Import Card: a normal upgrade card, installed in a terminal's upgrade
 * slots. While an encoding window whose host carries the card is open, the
 * blank-pattern slot restocks from network storage.
 *
 * <p>Upgrade slots are the ONLY install surface - that is AE2WTLib's wireless
 * encoding terminal and universal terminal (associations registered when the mod
 * is present). The cable-part terminal has no upgrade slots upstream, so the card
 * deliberately does nothing there rather than invent an off-convention socket;
 * revisit if AE2 ever gives terminals upgrade inventories.
 */
public final class PatternImportCard {

    /** Blanks pulled per refill; the slot refills only when it runs empty. */
    private static final int BATCH = 8;

    private PatternImportCard() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (!player.level().isClientSide() && player.tickCount % 10 == 0) {
            topUp(player);
        }
    }

    /** The whole behavior, callable directly by gametests. Returns blanks delivered. */
    public static int topUp(Player player) {
        if (!(player.containerMenu instanceof PatternEncodingTermMenu menu)) {
            return 0;
        }
        var host = menu.getHost();
        if (host.getUpgrades()
                .getInstalledUpgrades(AE2Logistics.PATTERN_IMPORT_CARD.get()) == 0) {
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
}
