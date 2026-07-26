package io.github.johnhamilto.ae2logistics.crafting;

import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import appeng.api.stacks.GenericStack;

/**
 * The data component carried by an Adaptive Processing Pattern: the sparse input/output
 * lists exactly as AE2 encodes them, plus one {@link AdaptiveInputSpec} per sparse input
 * slot (index-aligned, nulls included).
 */
public record EncodedAdaptivePattern(
        List<GenericStack> sparseInputs,
        List<GenericStack> sparseOutputs,
        List<AdaptiveInputSpec> specs) {

    public EncodedAdaptivePattern {
        sparseInputs = Collections.unmodifiableList(sparseInputs);
        sparseOutputs = Collections.unmodifiableList(sparseOutputs);
        specs = Collections.unmodifiableList(specs);
    }

    public static final Codec<EncodedAdaptivePattern> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            GenericStack.FAULT_TOLERANT_NULLABLE_LIST_CODEC.fieldOf("sparseInputs")
                    .forGetter(EncodedAdaptivePattern::sparseInputs),
            GenericStack.FAULT_TOLERANT_NULLABLE_LIST_CODEC.fieldOf("sparseOutputs")
                    .forGetter(EncodedAdaptivePattern::sparseOutputs),
            AdaptiveInputSpec.LIST_CODEC.fieldOf("specs")
                    .forGetter(EncodedAdaptivePattern::specs))
            .apply(builder, EncodedAdaptivePattern::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncodedAdaptivePattern> STREAM_CODEC = ByteBufCodecs
            .fromCodecWithRegistries(CODEC);

    public AdaptiveInputSpec specFor(int sparseIndex) {
        if (sparseIndex < 0 || sparseIndex >= specs.size()) {
            return AdaptiveInputSpec.EXACT;
        }
        return specs.get(sparseIndex);
    }
}
