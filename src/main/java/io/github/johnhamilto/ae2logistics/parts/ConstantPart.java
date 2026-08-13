package io.github.johnhamilto.ae2logistics.parts;

import java.util.Set;

import net.minecraft.resources.Identifier;

import appeng.api.parts.IPartItem;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public class ConstantPart extends LogicPart {

    public ConstantPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public LogicPartType type() {
        return LogicPartType.CONSTANT;
    }

    @Override
    public Set<Identifier> readChannels() {
        return Set.of();
    }

    @Override
    public void evaluate(LogicContext context) {
        context.write(valueA);
    }
}
