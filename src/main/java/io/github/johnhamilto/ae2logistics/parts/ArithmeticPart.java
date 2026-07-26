package io.github.johnhamilto.ae2logistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalMath;

public class ArithmeticPart extends LogicPart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/arithmetic"));

    public ArithmeticPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.ARITHMETIC;
    }

    @Override
    public void evaluate(LogicContext context) {
        long a = inA != null ? context.read(inA) : 0;
        long b = readB(context);
        long result = switch (op) {
            case 0 -> SignalMath.add(a, b);
            case 1 -> SignalMath.subtract(a, b);
            case 2 -> SignalMath.multiply(a, b);
            case 3 -> SignalMath.divide(a, b);
            case 4 -> Math.min(a, b);
            case 5 -> Math.max(a, b);
            default -> SignalMath.modulo(a, b);
        };
        context.write(result);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
