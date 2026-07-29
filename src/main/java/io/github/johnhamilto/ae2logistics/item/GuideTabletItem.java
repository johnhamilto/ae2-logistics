package io.github.johnhamilto.ae2logistics.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import guideme.GuidesCommon;

import net.minecraft.resources.ResourceLocation;

public class GuideTabletItem extends Item {

    /** Our pages live inside AE2's own guide as the "AE2 Logistics" category. */
    private static final ResourceLocation AE2_GUIDE = ResourceLocation.parse("ae2:guide");

    public GuideTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            GuidesCommon.openGuide(player, AE2_GUIDE);
        }
        return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
    }
}
