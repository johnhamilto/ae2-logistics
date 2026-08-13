package io.github.johnhamilto.ae2logistics.parts;

import appeng.api.parts.IPartItem;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public class BooleanPart extends LogicPart {

    public BooleanPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.BOOLEAN;
    }

    @Override
    public void evaluate(LogicContext context) {
        boolean a = inA != null && context.read(inA) != 0;
        boolean b = readB(context) != 0;
        boolean result = switch (op) {
            case 0 -> a && b;
            case 1 -> a || b;
            case 2 -> a ^ b;
            default -> !a;
        };
        context.write(result ? 1 : 0);
    }
}
