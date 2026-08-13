package io.github.johnhamilto.ae2logistics.parts;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.parts.IPartItem;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalMath;

/**
 * Counts rising edges of input channel A. A nonzero value on input channel B resets the
 * count; valueA is an optional wrap modulus (0 = no wrap). The count persists.
 */
public class CounterPart extends LogicPart {

    private long count;
    private boolean lastInput;

    public CounterPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.COUNTER;
    }

    @Override
    public Set<Identifier> readChannels() {
        var channels = new HashSet<Identifier>(2);
        if (inA != null) {
            channels.add(inA);
        }
        if (inB != null) {
            channels.add(inB);
        }
        return channels;
    }

    @Override
    public void evaluate(LogicContext context) {
        if (inB != null && context.read(inB) != 0) {
            setCount(0);
        }

        boolean input = inA != null && context.read(inA) != 0;
        if (input && !lastInput) {
            long next = SignalMath.add(count, 1);
            if (valueA > 0) {
                next = next % valueA;
            }
            setCount(next);
        }
        lastInput = input;
        context.write(count);
    }

    private void setCount(long value) {
        if (count != value) {
            count = value;
            getHost().markForSave();
        }
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        data.putLong("count", count);
        data.putBoolean("lastInput", lastInput);
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        count = data.getLongOr("count", 0L);
        lastInput = data.getBooleanOr("lastInput", false);
    }
}
