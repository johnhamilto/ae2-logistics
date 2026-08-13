package io.github.johnhamilto.ae2logistics.parts;

import appeng.api.parts.IPartItem;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public class ThresholdPart extends LogicPart {

    public ThresholdPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.THRESHOLD;
    }

    @Override
    public void evaluate(LogicContext context) {
        long a = inA != null ? context.read(inA) : 0;
        long b = readB(context);
        boolean result = switch (op) {
            case 0 -> a < b;
            case 1 -> a <= b;
            case 2 -> a == b;
            case 3 -> a >= b;
            default -> a > b;
        };
        context.write(result ? 1 : 0);
    }
}
