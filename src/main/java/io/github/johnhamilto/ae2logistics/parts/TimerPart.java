package io.github.johnhamilto.ae2logistics.parts;

import java.util.Set;

import net.minecraft.resources.Identifier;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalMath;

/**
 * Clock: emits 1 for the first valueB ticks of every valueA-tick period (period clamped
 * 2-72000, pulse clamped 1 to period-1).
 */
public class TimerPart extends LogicPart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/timer"));

    private long ticks;

    public TimerPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.TIMER;
    }

    @Override
    public Set<Identifier> readChannels() {
        return Set.of();
    }

    @Override
    public void evaluate(LogicContext context) {
        long period = SignalMath.clamp(valueA, 2, 72000);
        long pulse = SignalMath.clamp(valueB, 1, period - 1);
        context.write(ticks % period < pulse ? 1 : 0);
        ticks++;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
