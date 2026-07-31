package io.github.johnhamilto.ae2logistics.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import appeng.server.testworld.BuildAction;
import appeng.server.testworld.PlotBuilder;

/**
 * Bridges hand-built scenes into test plots: pastes a saved structure template
 * ({@code data/ae2logistics/structure/*.nbt}, or a dev-time {@code /test export})
 * into a plot so it appears in the {@code /ae2 setuptestworld} grid.
 */
public final class PlotStructures {

    private PlotStructures() {
    }

    /**
     * Paste template {@code id} with its minimum corner at {@code origin} (plot-relative
     * "x y z"). Loads the template eagerly so the plot's bounds cover it; plots only
     * ever build on a running server, so the server lookup is safe here.
     */
    public static void structure(PlotBuilder plot, String origin, ResourceLocation id) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("test plots build server-side only");
        }
        var template = server.getStructureManager().get(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown structure template " + id));
        var size = template.getSize();
        var at = plot.bb(origin);
        var bounds = new BoundingBox(at.minX(), at.minY(), at.minZ(),
                at.minX() + size.getX() - 1, at.minY() + size.getY() - 1,
                at.minZ() + size.getZ() - 1);
        plot.addBuildAction(new PlaceStructure(bounds, template));
    }

    private record PlaceStructure(BoundingBox bounds, StructureTemplate template) implements BuildAction {

        @Override
        public BoundingBox getBoundingBox() {
            return bounds;
        }

        @Override
        public void build(ServerLevel level, Player player, BlockPos origin) {
            var pos = origin.offset(bounds.minX(), bounds.minY(), bounds.minZ());
            template.placeInWorld(level, pos, pos, new StructurePlaceSettings(),
                    level.getRandom(), Block.UPDATE_CLIENTS);
        }
    }
}
