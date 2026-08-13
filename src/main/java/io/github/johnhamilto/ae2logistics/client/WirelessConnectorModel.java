package io.github.johnhamilto.ae2logistics.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.model.data.ModelData;

import appeng.api.util.AEColor;
import appeng.client.api.model.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.WirelessConnectorPart;

/**
 * One baked model per AE color, picked at collect time from the part's COLOR_DATA
 * model property - the 26.1 replacement for the 17 static IPartModels.
 */
public record WirelessConnectorModel(Map<AEColor, BlockStateModelPart> models) implements PartModel {

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, ModelData partModelData,
            RandomSource random, List<BlockStateModelPart> parts) {
        var color = Objects.requireNonNullElse(
                partModelData.get(WirelessConnectorPart.COLOR_DATA), AEColor.TRANSPARENT);
        parts.add(models.get(color));
    }

    @Override
    public Material.Baked particleMaterial() {
        return models.get(AEColor.TRANSPARENT).particleMaterial();
    }

    public record Unbaked() implements PartModel.Unbaked {
        public static final Identifier ID = AE2Logistics.id("wireless_connector");
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<? extends PartModel.Unbaked> codec() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {
            for (var color : AEColor.values()) {
                resolver.markDependency(
                        AE2Logistics.id("part/wireless_connector_" + color.registryPrefix));
            }
        }

        @Override
        public PartModel bake(ModelBaker baker, ModelState modelState) {
            var models = new EnumMap<AEColor, BlockStateModelPart>(AEColor.class);
            for (var color : AEColor.values()) {
                models.put(color, SimpleModelWrapper.bake(baker,
                        AE2Logistics.id("part/wireless_connector_" + color.registryPrefix),
                        modelState));
            }
            return new WirelessConnectorModel(models);
        }
    }
}
