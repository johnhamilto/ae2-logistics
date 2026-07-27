package io.github.johnhamilto.ae2logistics.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import guideme.GuidesCommon;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public class GuideTabletItem extends Item {

    public GuideTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            GuidesCommon.openGuide(player, AE2Logistics.id("guide"));
        }
        return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
    }
}
