package io.github.johnhamilto.ae2logistics.signal;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

public final class SignalKey extends AEKey {

    public static final MapCodec<SignalKey> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("channel").forGetter(SignalKey::channel))
            .apply(instance, SignalKey::of));

    private final ResourceLocation channel;

    private SignalKey(ResourceLocation channel) {
        this.channel = channel;
    }

    public static SignalKey of(ResourceLocation channel) {
        return new SignalKey(Objects.requireNonNull(channel));
    }

    public ResourceLocation channel() {
        return channel;
    }

    @Override
    public AEKeyType getType() {
        return SignalKeyType.TYPE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) MAP_CODEC.codec().encodeStart(ops, this).getOrThrow();
    }

    @Override
    public Object getPrimaryKey() {
        return channel;
    }

    @Override
    public ResourceLocation getId() {
        return channel;
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
    }

    @Override
    protected Component computeDisplayName() {
        return Component.literal(channel.toString());
    }

    @Override
    public <T> @Nullable T get(DataComponentType<T> type) {
        return null;
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeResourceLocation(channel);
    }

    public static SignalKey fromPacket(RegistryFriendlyByteBuf data) {
        return new SignalKey(data.readResourceLocation());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return channel.equals(((SignalKey) o).channel);
    }

    @Override
    public int hashCode() {
        return channel.hashCode();
    }

    @Override
    public String toString() {
        return "SignalKey{" + channel + "}";
    }
}
