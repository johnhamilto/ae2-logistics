package io.github.johnhamilto.ae2logistics.signal;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.item.SignalCardItem;

/**
 * Signal Cards are filter chips, not containers: they expose a signal key to config slots
 * but never transfer anything.
 */
public class SignalCardContainerStrategy implements ContainerItemStrategy<SignalKey, Void> {

    @Override
    @Nullable
    public GenericStack getContainedStack(ItemStack stack) {
        var channel = SignalCardItem.getChannel(stack);
        return channel != null ? new GenericStack(SignalKey.of(channel), 1) : null;
    }

    @Override
    @Nullable
    public Void findCarriedContext(Player player, AbstractContainerMenu menu) {
        return null;
    }

    @Override
    @Nullable
    public Void findPlayerSlotContext(Player player, int slot) {
        return null;
    }

    @Override
    public long extract(Void context, SignalKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public long insert(Void context, SignalKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public void playFillSound(Player player, SignalKey what) {
    }

    @Override
    public void playEmptySound(Player player, SignalKey what) {
    }

    @Override
    @Nullable
    public GenericStack getExtractableContent(Void context) {
        return null;
    }
}
