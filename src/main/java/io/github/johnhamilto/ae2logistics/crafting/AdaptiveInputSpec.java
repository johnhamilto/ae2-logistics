package io.github.johnhamilto.ae2logistics.crafting;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * How one pattern input matches candidates: exactly (vanilla behavior), by item identity
 * ignoring components ("any damage, any enchantment"), or by item tag membership.
 */
public record AdaptiveInputSpec(Mode mode, Optional<ResourceLocation> tag) {

    public static final AdaptiveInputSpec EXACT = new AdaptiveInputSpec(Mode.EXACT, Optional.empty());

    public static final Codec<AdaptiveInputSpec> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Mode.CODEC.fieldOf("mode").forGetter(AdaptiveInputSpec::mode),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(AdaptiveInputSpec::tag))
            .apply(builder, AdaptiveInputSpec::new));

    public static final Codec<List<AdaptiveInputSpec>> LIST_CODEC = CODEC.listOf();

    public static AdaptiveInputSpec fuzzy() {
        return new AdaptiveInputSpec(Mode.FUZZY, Optional.empty());
    }

    public static AdaptiveInputSpec ofTag(ResourceLocation tag) {
        return new AdaptiveInputSpec(Mode.TAG, Optional.of(tag));
    }

    public enum Mode implements StringRepresentable {
        EXACT("exact"),
        FUZZY("fuzzy"),
        TAG("tag");

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
