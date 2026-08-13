package io.github.johnhamilto.ae2logistics.signal;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

public final class SignalKey extends AEKey {

    public static final MapCodec<SignalKey> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("channel").forGetter(SignalKey::channel))
            .apply(instance, SignalKey::of));

    private final Identifier channel;

    private SignalKey(Identifier channel) {
        this.channel = channel;
    }

    public static SignalKey of(Identifier channel) {
        return new SignalKey(Objects.requireNonNull(channel));
    }

    public Identifier channel() {
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
    public void toTag(net.minecraft.world.level.storage.ValueOutput output) {
        output.store(MAP_CODEC, this);
    }

    @Override
    public Object getPrimaryKey() {
        return channel;
    }

    @Override
    public Identifier getId() {
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
        data.writeIdentifier(channel);
    }

    public static SignalKey fromPacket(RegistryFriendlyByteBuf data) {
        return new SignalKey(data.readIdentifier());
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
