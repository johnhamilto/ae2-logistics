package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.DevOnlyCondition;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class DevGateGameTests {

    /**
     * The gametest server is a dev environment, so this can only cover the dev half of
     * the gate: the dev_only condition passes and the gated devices' recipes are loaded.
     * The production half is the same single {@code FMLEnvironment.isProduction()} read
     * inverted, unreachable from any test environment; a wiring regression (condition
     * codec unregistered, recipe JSON malformed) fails here as missing recipes.
     */
    @GameTest(template = "empty5")
    public void devGatedDevicesAvailableInDev(GameTestHelper helper) {
        helper.assertFalse(FMLEnvironment.isProduction(), "gametests must run in a dev environment");
        helper.assertTrue(DevOnlyCondition.INSTANCE.test(ICondition.IContext.EMPTY),
                "dev_only condition must pass in dev");
        var recipes = helper.getLevel().getServer().getRecipeManager();
        helper.assertTrue(recipes.byKey(AE2Logistics.id("storage_janitor")).isPresent(),
                "storage_janitor recipe must be loaded in dev");
        helper.assertTrue(recipes.byKey(AE2Logistics.id("trace_panel")).isPresent(),
                "trace_panel recipe must be loaded in dev");
        helper.succeed();
    }
}
