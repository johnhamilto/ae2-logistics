package io.github.johnhamilto.ae2logistics.parts;

import java.util.Set;

import net.minecraft.resources.Identifier;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public class ConstantPart extends LogicPart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/constant"));

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

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
