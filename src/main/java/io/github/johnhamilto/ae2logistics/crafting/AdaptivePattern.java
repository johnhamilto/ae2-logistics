package io.github.johnhamilto.ae2logistics.crafting;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.helpers.ItemComparisonHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * A processing pattern whose inputs match by spec instead of exact key identity. Because
 * the first possible input stays the original ("canonical") item and substitutes are
 * never autocrafted, the planner does not branch (DESIGN.md F9's resolution rule).
 */
public class AdaptivePattern implements IPatternDetails {

    private final AEItemKey definition;
    private final EncodedAdaptivePattern encoded;
    private final Input[] inputs;
    private final List<GenericStack> outputs;

    public AdaptivePattern(AEItemKey definition, Level level) {
        this.definition = definition;

        var encodedPattern = definition.get(AE2Logistics.ENCODED_ADAPTIVE_PATTERN.get());
        if (encodedPattern == null) {
            throw new IllegalArgumentException("Given item does not encode an adaptive pattern: " + definition);
        }
        this.encoded = encodedPattern;

        var inputList = new ArrayList<Input>();
        var sparse = encodedPattern.sparseInputs();
        for (int i = 0; i < sparse.size(); i++) {
            var stack = sparse.get(i);
            if (stack != null) {
                inputList.add(new Input(stack, encodedPattern.specFor(i)));
            }
        }
        if (inputList.isEmpty()) {
            throw new IllegalArgumentException("Adaptive pattern has no inputs");
        }
        this.inputs = inputList.toArray(Input[]::new);

        this.outputs = encodedPattern.sparseOutputs().stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Adaptive pattern has no outputs");
        }
    }

    public static void encode(ItemStack stack, List<GenericStack> sparseInputs,
            List<GenericStack> sparseOutputs, List<AdaptiveInputSpec> specs) {
        stack.set(AE2Logistics.ENCODED_ADAPTIVE_PATTERN.get(),
                new EncodedAdaptivePattern(sparseInputs, sparseOutputs, specs));
    }

    public EncodedAdaptivePattern encoded() {
        return encoded;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_PRODUCES);
        tooltip.addInputsAndOutputs(this);
        for (var input : inputs) {
            var spec = input.spec;
            if (spec.mode() == AdaptiveInputSpec.Mode.EXACT && !spec.catalyst()) {
                continue;
            }
            var name = input.possibleInputs[0].what().getDisplayName().copy();
            var description = switch (spec.mode()) {
                case EXACT -> Component.literal("exact");
                case FUZZY -> Component.literal(spec.fuzzyMode()
                        .map(mode -> "fuzzy, damage band " + mode.getSerializedName())
                        .orElse("fuzzy, any variant"));
                case TAG -> Component.literal("#" + spec.tag().map(Object::toString).orElse("?"));
                case ANY_OF -> Component.literal("any of " + (spec.alternatives().size() + 1) + " items");
            };
            if (spec.catalyst()) {
                description = description.copy().append(", catalyst");
            }
            tooltip.addProperty(name, description);
        }
        return tooltip;
    }

    public static PatternDetailsTooltip getInvalidPatternTooltip(net.minecraft.world.item.ItemStack stack,
            Level level, @Nullable Exception cause, TooltipFlag flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_PRODUCES);
        var encoded = stack.get(AE2Logistics.ENCODED_ADAPTIVE_PATTERN.get());
        if (encoded != null) {
            encoded.sparseInputs().stream().filter(java.util.Objects::nonNull).forEach(tooltip::addInput);
            encoded.sparseOutputs().stream().filter(java.util.Objects::nonNull).forEach(tooltip::addOutput);
        }
        return tooltip;
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass() && ((AdaptivePattern) obj).definition.equals(definition);
    }

    static class Input implements IInput {
        final GenericStack[] possibleInputs;
        private final long multiplier;
        final AdaptiveInputSpec spec;
        @Nullable
        private final TagKey<net.minecraft.world.item.Item> tagKey;

        Input(GenericStack stack, AdaptiveInputSpec spec) {
            this.multiplier = stack.amount();
            this.spec = spec;

            var primary = new GenericStack(stack.what(), 1);
            if (spec.mode() == AdaptiveInputSpec.Mode.TAG
                    && spec.tag().isPresent()
                    && stack.what() instanceof AEItemKey) {
                this.tagKey = TagKey.create(Registries.ITEM, spec.tag().get());
                var candidates = new ArrayList<GenericStack>();
                candidates.add(primary);
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                    var key = AEItemKey.of(holder.value());
                    if (key != null && !key.equals(stack.what())) {
                        candidates.add(new GenericStack(key, 1));
                    }
                }
                this.possibleInputs = candidates.toArray(GenericStack[]::new);
            } else if (spec.mode() == AdaptiveInputSpec.Mode.ANY_OF && !spec.alternatives().isEmpty()) {
                this.tagKey = null;
                var candidates = new ArrayList<GenericStack>();
                candidates.add(primary);
                for (var alternative : spec.alternatives()) {
                    if (!alternative.what().equals(stack.what())) {
                        candidates.add(new GenericStack(alternative.what(), 1));
                    }
                }
                this.possibleInputs = candidates.toArray(GenericStack[]::new);
            } else {
                this.tagKey = null;
                this.possibleInputs = new GenericStack[] { primary };
            }
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            var primary = possibleInputs[0].what();
            return switch (spec.mode()) {
                case EXACT -> input.matches(possibleInputs[0]);
                case FUZZY -> {
                    if (!(input instanceof AEItemKey candidate) || !(primary instanceof AEItemKey primaryItem)
                            || candidate.getItem() != primaryItem.getItem()) {
                        yield false;
                    }
                    var band = spec.fuzzyMode().orElse(FuzzyMode.IGNORE_ALL);
                    yield band == FuzzyMode.IGNORE_ALL
                            || ItemComparisonHelper.isFuzzyEqualItem(
                                    primaryItem.toStack(), candidate.toStack(), band);
                }
                case TAG -> tagKey != null && input.isTagged(tagKey);
                case ANY_OF -> {
                    for (var candidate : possibleInputs) {
                        if (input.matches(candidate)) {
                            yield true;
                        }
                    }
                    yield false;
                }
            };
        }

        /** Catalysts use AE2's container-item mechanism: pushed, then credited back. */
        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return spec.catalyst() ? template : null;
        }
    }

    /**
     * The spec cycle used by the Pattern Workbench: EXACT, FUZZY (plus damage bands for
     * damageable items), then each item tag. The catalyst flag is preserved across the
     * cycle; cycling away from ANY_OF discards the alternatives list.
     */
    public static AdaptiveInputSpec nextSpec(GenericStack input, AdaptiveInputSpec current) {
        if (!(input.what() instanceof AEItemKey itemKey)) {
            return AdaptiveInputSpec.EXACT.withCatalyst(current.catalyst());
        }
        var tags = BuiltInRegistries.ITEM.wrapAsHolder(itemKey.getItem()).tags()
                .map(TagKey::location)
                .sorted()
                .limit(8)
                .toList();

        var sequence = new ArrayList<AdaptiveInputSpec>();
        sequence.add(AdaptiveInputSpec.EXACT);
        sequence.add(AdaptiveInputSpec.fuzzy());
        if (itemKey.toStack().getMaxDamage() > 0) {
            sequence.add(AdaptiveInputSpec.fuzzy(FuzzyMode.PERCENT_99));
            sequence.add(AdaptiveInputSpec.fuzzy(FuzzyMode.PERCENT_75));
            sequence.add(AdaptiveInputSpec.fuzzy(FuzzyMode.PERCENT_50));
            sequence.add(AdaptiveInputSpec.fuzzy(FuzzyMode.PERCENT_25));
        }
        for (Identifier tag : tags) {
            sequence.add(AdaptiveInputSpec.ofTag(tag));
        }

        var currentMatch = current.withCatalyst(false);
        int index = sequence.indexOf(currentMatch);
        var next = sequence.get((index + 1) % sequence.size());
        return next.withCatalyst(current.catalyst());
    }
}
