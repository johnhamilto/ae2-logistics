package io.github.johnhamilto.ae2logistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalMath;

/**
 * Windowed production rate: writes how much input channel A grew per second, averaged
 * over the window (valueA seconds, clamped 1-60). Shrinking values report 0.
 */
public class RatePart extends LogicPart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/rate"));

    private long[] samples = new long[0];
    private int cursor;
    private int filled;

    public RatePart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.RATE;
    }

    @Override
    public void evaluate(LogicContext context) {
        int windowSeconds = (int) SignalMath.clamp(valueA, 1, 60);
        int windowTicks = windowSeconds * 20;
        if (samples.length != windowTicks) {
            samples = new long[windowTicks];
            cursor = 0;
            filled = 0;
        }

        long current = inA != null ? context.read(inA) : 0;
        long oldest = samples[cursor];
        samples[cursor] = current;
        cursor = (cursor + 1) % windowTicks;
        if (filled < windowTicks) {
            filled++;
            context.write(0);
            return;
        }

        long delta = current - oldest;
        context.write(delta <= 0 ? 0 : delta / windowSeconds);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
