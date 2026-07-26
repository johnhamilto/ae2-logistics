package io.github.johnhamilto.ae2logistics.signal;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public final class SignalKeyType extends AEKeyType {

    public static final SignalKeyType TYPE = new SignalKeyType();

    private SignalKeyType() {
        super(AE2Logistics.id("signal"), SignalKey.class, Component.literal("Signals"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return SignalKey.MAP_CODEC;
    }

    @Override
    public SignalKey readFromPacket(RegistryFriendlyByteBuf input) {
        return SignalKey.fromPacket(input);
    }
}
