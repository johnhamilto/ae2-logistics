package io.github.johnhamilto.ae2logistics.gametest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * 26.1 gametests are registry entries, not annotations (AE2's GameTestPlotAdapter is
 * the pattern): every test class lists its scenes in a register() method via
 * {@link #add}, and this one instance type replays the stored consumer when the
 * framework runs it. Structure ids resolve to our committed templates
 * (empty5/empty12/empty20); the event only fires when gametests are enabled, so
 * nothing registers in normal play.
 */
public class LogisticsTestInstance extends GameTestInstance {

    private record Def(String template, int maxTicks, int padding, Consumer<GameTestHelper> test) {
    }

    private static final Map<Identifier, Def> DEFS = new LinkedHashMap<>();

    public static final MapCodec<LogisticsTestInstance> CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder.group(
                    TestData.CODEC.fieldOf("testData").forGetter(i -> i.info()),
                    Identifier.CODEC.fieldOf("testId").forGetter(i -> i.testId))
                    .apply(builder, LogisticsTestInstance::new));

    private final Identifier testId;

    private LogisticsTestInstance(TestData<Holder<TestEnvironmentDefinition<?>>> testData,
            Identifier testId) {
        super(testData);
        this.testId = testId;
    }

    /** Default timeout matches the old annotation default of 100 ticks. */
    static void add(String name, String template, Consumer<GameTestHelper> test) {
        add(name, template, 100, test);
    }

    static void add(String name, String template, int maxTicks, Consumer<GameTestHelper> test) {
        add(name, template, maxTicks, 0, test);
    }

    /**
     * Padding spaces the structure away from batch neighbors - wireless connector
     * scenes need more than the 16-block base range, or fluix connectors link
     * ACROSS test scenes and fuse unrelated test networks.
     */
    static void add(String name, String template, int maxTicks, int padding,
            Consumer<GameTestHelper> test) {
        DEFS.put(AE2Logistics.id(name.toLowerCase(Locale.ROOT)),
                new Def(template, maxTicks, padding, test));
    }

    public static void registerAll(RegisterGameTestsEvent event) {
        CompatGameTests.register();
        ConfigTerminalGameTests.register();
        DevGateGameTests.register();
        GuardedCraftingGameTests.register();
        HardeningGameTests.register();
        InputCardGameTests.register();
        JanitorGameTests.register();
        JobMonitorGameTests.register();
        LogicCoreGameTests.register();
        MemoryCardGameTests.register();
        MeshGameTests.register();
        MeshPolishGameTests.register();
        PatternImportGameTests.register();
        P2PGameTests.register();
        PatternGameTests.register();
        ProviderTunnelGameTests.register();
        QueryGameTests.register();
        SchedulerGameTests.register();
        SchedulerPolicyGameTests.register();
        SchedulerStretchGameTests.register();
        SubnetLinkGameTests.register();
        TestDebtGameTests.register();
        TestPlotGameTests.register();
        TracePanelGameTests.register();
        TreeMatrixGameTests.register();
        WirelessConnectorGameTests.register();
        WirelessGameTests.register();

        for (var entry : DEFS.entrySet()) {
            var def = entry.getValue();
            var testData = new TestData<>(
                    Holder.<TestEnvironmentDefinition<?>>direct(new TestEnvironmentDefinition.AllOf()),
                    AE2Logistics.id(def.template()),
                    def.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, false, def.padding());
            event.registerTest(entry.getKey(), new LogisticsTestInstance(testData, entry.getKey()));
        }
    }

    @Override
    public void run(GameTestHelper helper) {
        var def = DEFS.get(testId);
        if (def == null) {
            throw helper.assertionException("Test " + testId + " is not registered");
        }
        def.test().accept(helper);
    }

    @Override
    public MapCodec<? extends GameTestInstance> codec() {
        return CODEC;
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.literal("AE2 Logistics " + testId.getPath());
    }
}
