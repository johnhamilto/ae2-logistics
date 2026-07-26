package io.github.johnhamilto.ae2logistics.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public class SignalCardItem extends Item {

    public SignalCardItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static ResourceLocation getChannel(ItemStack stack) {
        return stack.get(AE2Logistics.SIGNAL_CHANNEL.get());
    }

    public static ItemStack bound(ResourceLocation channel) {
        var stack = new ItemStack(AE2Logistics.SIGNAL_CARD.get());
        stack.set(AE2Logistics.SIGNAL_CHANNEL.get(), channel);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var channel = getChannel(stack);
        if (channel != null) {
            tooltip.add(Component.literal(channel.toString()).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.ae2logistics.signal_card.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
