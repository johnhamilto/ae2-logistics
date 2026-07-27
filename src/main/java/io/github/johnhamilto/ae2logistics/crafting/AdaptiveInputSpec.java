package io.github.johnhamilto.ae2logistics.crafting;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.GenericStack;

/**
 * How one pattern input matches candidates: exactly (vanilla behavior), by item identity
 * with a fuzzy damage band, by item tag membership, or against an explicit ordered list
 * of alternatives. Independently, an input can be flagged as a catalyst: required and
 * pushed, but credited back rather than net-consumed (AE2's container-item mechanism).
 */
public record AdaptiveInputSpec(Mode mode, Optional<ResourceLocation> tag, Optional<FuzzyMode> fuzzyMode,
        List<GenericStack> alternatives, boolean catalyst) {

    public static final AdaptiveInputSpec EXACT = new AdaptiveInputSpec(
            Mode.EXACT, Optional.empty(), Optional.empty(), List.of(), false);

    public static final Codec<AdaptiveInputSpec> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Mode.CODEC.fieldOf("mode").forGetter(AdaptiveInputSpec::mode),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(AdaptiveInputSpec::tag),
            FuzzyMode.CODEC.optionalFieldOf("fuzzyMode").forGetter(AdaptiveInputSpec::fuzzyMode),
            GenericStack.FAULT_TOLERANT_LIST_CODEC.optionalFieldOf("alternatives", List.of())
                    .forGetter(AdaptiveInputSpec::alternatives),
            Codec.BOOL.optionalFieldOf("catalyst", false).forGetter(AdaptiveInputSpec::catalyst))
            .apply(builder, AdaptiveInputSpec::new));

    public static final Codec<List<AdaptiveInputSpec>> LIST_CODEC = CODEC.listOf();

    public static AdaptiveInputSpec fuzzy() {
        return new AdaptiveInputSpec(Mode.FUZZY, Optional.empty(), Optional.empty(), List.of(), false);
    }

    public static AdaptiveInputSpec fuzzy(FuzzyMode band) {
        return new AdaptiveInputSpec(Mode.FUZZY, Optional.empty(), Optional.of(band), List.of(), false);
    }

    public static AdaptiveInputSpec ofTag(ResourceLocation tag) {
        return new AdaptiveInputSpec(Mode.TAG, Optional.of(tag), Optional.empty(), List.of(), false);
    }

    public static AdaptiveInputSpec anyOf(List<GenericStack> alternatives) {
        return new AdaptiveInputSpec(Mode.ANY_OF, Optional.empty(), Optional.empty(),
                List.copyOf(alternatives), false);
    }

    public AdaptiveInputSpec withMatch(AdaptiveInputSpec match) {
        return new AdaptiveInputSpec(match.mode, match.tag, match.fuzzyMode, match.alternatives, catalyst);
    }

    public AdaptiveInputSpec withCatalyst(boolean catalyst) {
        return new AdaptiveInputSpec(mode, tag, fuzzyMode, alternatives, catalyst);
    }

    public AdaptiveInputSpec withAlternative(GenericStack stack) {
        if (alternatives.stream().anyMatch(alt -> alt.what().equals(stack.what())) || alternatives.size() >= 8) {
            return this;
        }
        var next = new java.util.ArrayList<>(alternatives);
        next.add(new GenericStack(stack.what(), 1));
        return new AdaptiveInputSpec(Mode.ANY_OF, Optional.empty(), Optional.empty(),
                List.copyOf(next), catalyst);
    }

    public enum Mode implements StringRepresentable {
        EXACT("exact"),
        FUZZY("fuzzy"),
        TAG("tag"),
        ANY_OF("any_of");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
