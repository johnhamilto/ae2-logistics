package io.github.johnhamilto.ae2logistics.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Component payload of a Guarded Pattern: the wrapped encoded pattern plus the guard
 * condition (signal channel OP constant). The guard is enforced by the Guarded Pattern
 * Provider; in any other provider the pattern behaves exactly like its inner pattern.
 */
public record GuardedPatternData(ItemStack inner, ResourceLocation channel, int op, long value) {

    public static final Codec<GuardedPatternData> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            ItemStack.CODEC.fieldOf("inner").forGetter(GuardedPatternData::inner),
            ResourceLocation.CODEC.fieldOf("channel").forGetter(GuardedPatternData::channel),
            Codec.INT.fieldOf("op").forGetter(GuardedPatternData::op),
            Codec.LONG.fieldOf("value").forGetter(GuardedPatternData::value))
            .apply(builder, GuardedPatternData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuardedPatternData> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, GuardedPatternData::inner,
                    ResourceLocation.STREAM_CODEC, GuardedPatternData::channel,
                    ByteBufCodecs.VAR_INT, GuardedPatternData::op,
                    ByteBufCodecs.VAR_LONG, GuardedPatternData::value,
                    GuardedPatternData::new);

    // ItemStack has identity equality; component change-detection needs value equality.
    @Override
    public boolean equals(Object other) {
        return other instanceof GuardedPatternData that
                && ItemStack.matches(inner, that.inner)
                && channel.equals(that.channel)
                && op == that.op
                && value == that.value;
    }

    @Override
    public int hashCode() {
        int hash = ItemStack.hashItemAndComponents(inner);
        hash = 31 * hash + channel.hashCode();
        hash = 31 * hash + op;
        hash = 31 * hash + Long.hashCode(value);
        return hash;
    }
}
