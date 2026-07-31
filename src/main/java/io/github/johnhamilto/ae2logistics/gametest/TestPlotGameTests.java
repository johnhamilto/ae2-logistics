package io.github.johnhamilto.ae2logistics.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.server.testplots.TestPlots;
import appeng.server.testworld.Plot;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.testplots.PlotStructures;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class TestPlotGameTests {

    /** AE2's cross-mod annotation scan must find our plots and build usable areas. */
    @GameTest(template = "empty5")
    public void plotsRegisterWithAe2Scan(GameTestHelper helper) {
        var ids = TestPlots.getPlotIds();
        for (var path : List.of("logistics_signal_chain", "logistics_mesh_hub",
                "logistics_provider_hall", "logistics_subnet_links",
                "logistics_compat_extendedae")) {
            var id = ResourceLocation.fromNamespaceAndPath("ae2", path);
            helper.assertTrue(ids.contains(id), "AE2 scan did not register " + path);
            var plot = TestPlots.getById(id);
            helper.assertTrue(plot.getBounds().getXSpan() >= 2,
                    "plot " + path + " built no meaningful area");
        }
        helper.succeed();
    }

    /** The structure paste helper must size plots from the committed template. */
    @GameTest(template = "empty5")
    public void structureHelperSizesPlotFromTemplate(GameTestHelper helper) {
        var templateId = ResourceLocation.parse("ae2logistics:empty5");
        var template = helper.getLevel().getServer().getStructureManager().get(templateId)
                .orElse(null);
        helper.assertTrue(template != null, "template ae2logistics:empty5 must load from resources");
        var plot = new Plot(ResourceLocation.parse("ae2logistics:structure_probe"));
        PlotStructures.structure(plot, "0 0 0", templateId);
        var bounds = plot.getBounds();
        var size = template.getSize();
        helper.assertTrue(bounds.getXSpan() == size.getX() && bounds.getYSpan() == size.getY()
                && bounds.getZSpan() == size.getZ(),
                "plot bounds must match template size, got " + bounds);
        helper.succeed();
    }
}
