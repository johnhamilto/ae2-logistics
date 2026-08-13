package io.github.johnhamilto.ae2logistics;

import com.mojang.serialization.MapCodec;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Recipe condition that only passes in a development environment. Gates devices whose
 * art or GUI is not release-ready (the creative tab applies the same
 * {@code FMLEnvironment.isProduction()} check): they stay registered everywhere so worlds
 * and structure templates keep loading, but production players cannot craft or find them.
 */
public record DevOnlyCondition() implements ICondition {

    public static final DevOnlyCondition INSTANCE = new DevOnlyCondition();
    public static final MapCodec<DevOnlyCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(IContext context) {
        return !FMLEnvironment.isProduction();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
