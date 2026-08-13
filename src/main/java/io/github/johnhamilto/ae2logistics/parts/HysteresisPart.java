package io.github.johnhamilto.ae2logistics.parts;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.parts.IPartItem;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Two-setpoint latch: output latches to 1 when the input drops below the low setpoint
 * (valueA) and to 0 when it rises above the high setpoint (valueB). The classic
 * "start the farm below 1k, stop it above 10k" behavior.
 */
public class HysteresisPart extends LogicPart {

    private boolean latched;

    public HysteresisPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.HYSTERESIS;
    }

    @Override
    public void evaluate(LogicContext context) {
        long a = inA != null ? context.read(inA) : 0;
        boolean previous = latched;
        if (a < valueA) {
            latched = true;
        } else if (a > valueB) {
            latched = false;
        }
        if (previous != latched) {
            getHost().markForSave();
        }
        context.write(latched ? 1 : 0);
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        data.putBoolean("latched", latched);
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        latched = data.getBooleanOr("latched", false);
    }
}
