package io.github.johnhamilto.ae2logistics.item;

import java.util.List;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Clicking any wireless access point (AE2's or a Dense WAP) anchors the bridge to that
 * access point's network before placing; placing elsewhere behaves like a normal block.
 */
public class WirelessBridgeItem extends BlockItem {

    public WirelessBridgeItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof IWirelessAccessPoint) {
            if (!level.isClientSide()) {
                context.getItemInHand().set(AE2Logistics.BRIDGE_ANCHOR.get(),
                        GlobalPos.of(level.dimension(), pos));
                if (context.getPlayer() != null) {
                    context.getPlayer().sendOverlayMessage(Component.literal(
                            "Bridge anchored to access point at " + pos.toShortString()));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        var anchor = stack.get(AE2Logistics.BRIDGE_ANCHOR.get());
        if (anchor != null) {
            tooltip.accept(Component.literal("Anchored: " + anchor.pos().toShortString())
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        } else {
            tooltip.accept(Component.literal("Click an access point to anchor")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
