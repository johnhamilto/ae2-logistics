package io.github.johnhamilto.ae2logistics.crafting;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * A pattern that wraps another encoded pattern behind a signal guard. Every crafting
 * behavior delegates to the inner pattern; the guard itself is data that the Guarded
 * Pattern Provider consults when deciding whether to offer or push the pattern. A plain
 * pattern provider crafts it unconditionally.
 */
public class GuardedPattern implements IPatternDetails {

    public static final String[] OPS = {"<", "<=", "=", ">=", ">"};

    private final AEItemKey definition;
    private final IPatternDetails inner;
    private final Identifier channel;
    private final int op;
    private final long value;

    public GuardedPattern(AEItemKey definition, Level level) {
        this.definition = definition;
        var data = definition.get(AE2Logistics.GUARDED_PATTERN_DATA.get());
        if (data == null) {
            throw new IllegalArgumentException("Given item does not encode a guarded pattern: " + definition);
        }
        this.inner = PatternDetailsHelper.decodePattern(data.inner(), level);
        if (this.inner == null) {
            throw new IllegalArgumentException("Guarded pattern wraps an undecodable pattern");
        }
        this.channel = data.channel();
        this.op = data.op();
        this.value = data.value();
    }

    /** Builds the wrapper item around an already-encoded pattern. */
    public static ItemStack wrap(ItemStack innerPattern, Identifier channel, int op, long value) {
        var stack = new ItemStack(AE2Logistics.GUARDED_PATTERN.get());
        stack.set(AE2Logistics.GUARDED_PATTERN_DATA.get(),
                new GuardedPatternData(innerPattern.copyWithCount(1), channel,
                        Math.floorMod(op, OPS.length), value));
        return stack;
    }

    @Nullable
    public static ItemStack unwrap(ItemStack guarded) {
        var data = guarded.get(AE2Logistics.GUARDED_PATTERN_DATA.get());
        return data == null ? null : data.inner().copy();
    }

    public static boolean test(long signal, int op, long constant) {
        return switch (Math.floorMod(op, OPS.length)) {
            case 0 -> signal < constant;
            case 1 -> signal <= constant;
            case 2 -> signal == constant;
            case 3 -> signal >= constant;
            default -> signal > constant;
        };
    }

    public Identifier guardChannel() {
        return channel;
    }

    public int guardOp() {
        return op;
    }

    public long guardValue() {
        return value;
    }

    public boolean passes(SignalService service) {
        return test(service.get(channel), op, value);
    }

    // --- delegation ---

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inner.getInputs();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return inner.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return inner.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        inner.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        var tooltip = inner.getTooltip(level, flags);
        tooltip.addProperty(Component.literal("Guard"),
                Component.literal(channel + " " + OPS[Math.floorMod(op, OPS.length)] + " " + value));
        return tooltip;
    }

    public static PatternDetailsTooltip getInvalidPatternTooltip(ItemStack stack, Level level,
            @Nullable Exception cause, TooltipFlag flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_CRAFTS);
        var data = stack.get(AE2Logistics.GUARDED_PATTERN_DATA.get());
        if (data != null) {
            tooltip.addProperty(Component.literal("Guard"),
                    Component.literal(data.channel() + " " + OPS[Math.floorMod(data.op(), OPS.length)]
                            + " " + data.value()));
        }
        return tooltip;
    }

    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == getClass()
                && ((GuardedPattern) other).definition.equals(definition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition);
    }
}
