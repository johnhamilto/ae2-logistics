package io.github.johnhamilto.ae2logistics.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Binds a NAMED query (the per-network library) the way a Signal Card binds a
 * channel. Installed in a gated storage bus family part, the bus's partition
 * becomes live query membership - including data: rules over component trees.
 */
public class QueryCardItem extends Item {

    public QueryCardItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static String getQueryName(ItemStack stack) {
        return stack.get(AE2Logistics.QUERY_NAME.get());
    }

    public static ItemStack bound(String name) {
        var stack = new ItemStack(AE2Logistics.QUERY_CARD.get());
        stack.set(AE2Logistics.QUERY_NAME.get(), name);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        var name = getQueryName(stack);
        if (name != null) {
            tooltip.accept(Component.literal("@" + name).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.accept(Component.translatable("tooltip.ae2logistics.query_card.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
