package io.github.johnhamilto.ae2logistics.gametest;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class SchedulerGameTests {

    private static final String EMPTY = "empty5";

    private static final ResourceLocation SRC = ResourceLocation.parse("test:src");
    private static final ResourceLocation FLAG = ResourceLocation.parse("test:flag");
    private static final ResourceLocation X = ResourceLocation.parse("test:x");
    private static final ResourceLocation Y = ResourceLocation.parse("test:y");

    /** Places an energy cell at (1,1,1) and a cable bus with a center cable at (2,1,1). */
    private static BlockPos setupNetwork(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var busPos = helper.absolutePos(new BlockPos(2, 1, 1));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        helper.assertTrue(cable instanceof IPartItem<?>, "fluix glass cable must be a part item");
        PartHelper.setPart(helper.getLevel(), busPos, null, null, (IPartItem<?>) cable);
        return busPos;
    }

    private static LogicPart place(GameTestHelper helper, BlockPos busPos, Direction side,
            IPartItem<? extends LogicPart> item) {
        var part = PartHelper.setPart(helper.getLevel(), busPos, side, null, item);
        helper.assertTrue(part != null, "part placement failed on side " + side);
        return part;
    }

    private static SignalService service(GameTestHelper helper, LogicPart part) {
        var node = part.getMainNode().getNode();
        helper.assertTrue(node != null && node.getGrid() != null, "part has no grid");
        return node.getGrid().getService(SignalService.class);
    }

    @GameTest(template = EMPTY)
    public void constantFeedsThreshold(GameTestHelper helper) {
        var busPos = setupNetwork(helper);
        var constant = place(helper, busPos, Direction.UP, AE2Logistics.CONSTANT_PART.get());
        var threshold = place(helper, busPos, Direction.NORTH, AE2Logistics.THRESHOLD_PART.get());

        constant.applyConfig(SRC, null, null, 0, 500, 0, false);
        threshold.applyConfig(FLAG, SRC, null, 4, 100, 0, false);

        helper.runAfterDelay(10, () -> {
            var service = service(helper, constant);
            helper.assertTrue(service.get(SRC) == 500, "expected src=500, got " + service.get(SRC));
            helper.assertTrue(service.get(FLAG) == 1, "expected flag=1, got " + service.get(FLAG));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void multipleWritersSum(GameTestHelper helper) {
        var busPos = setupNetwork(helper);
        var first = place(helper, busPos, Direction.UP, AE2Logistics.CONSTANT_PART.get());
        var second = place(helper, busPos, Direction.NORTH, AE2Logistics.CONSTANT_PART.get());

        first.applyConfig(SRC, null, null, 0, 200, 0, false);
        second.applyConfig(SRC, null, null, 0, 300, 0, false);

        helper.runAfterDelay(10, () -> {
            var service = service(helper, first);
            helper.assertTrue(service.get(SRC) == 500, "expected summed 500, got " + service.get(SRC));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void sameTickPropagationThroughChain(GameTestHelper helper) {
        var busPos = setupNetwork(helper);
        var constant = place(helper, busPos, Direction.UP, AE2Logistics.CONSTANT_PART.get());
        var doubler = place(helper, busPos, Direction.NORTH, AE2Logistics.ARITHMETIC_PART.get());
        var threshold = place(helper, busPos, Direction.SOUTH, AE2Logistics.THRESHOLD_PART.get());

        constant.applyConfig(SRC, null, null, 0, 21, 0, false);
        doubler.applyConfig(X, SRC, null, 2, 2, 0, false);
        threshold.applyConfig(FLAG, X, null, 2, 42, 0, false);

        helper.runAfterDelay(10, () -> {
            var service = service(helper, constant);
            helper.assertTrue(service.get(X) == 42, "expected x=42, got " + service.get(X));
            helper.assertTrue(service.get(FLAG) == 1, "expected flag=1, got " + service.get(FLAG));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void cycleAdvancesExactlyOncePerTick(GameTestHelper helper) {
        var busPos = setupNetwork(helper);
        var increment = place(helper, busPos, Direction.UP, AE2Logistics.ARITHMETIC_PART.get());
        var echo = place(helper, busPos, Direction.NORTH, AE2Logistics.ARITHMETIC_PART.get());

        // x = y + 1; y = x + 0 -- a feedback loop that should advance by exactly 1 per tick.
        increment.applyConfig(X, Y, null, 0, 1, 0, false);
        echo.applyConfig(Y, X, null, 0, 0, 0, false);

        var sampled = new AtomicLong();
        helper.runAfterDelay(40, () -> sampled.set(service(helper, increment).get(X)));
        helper.runAfterDelay(80, () -> {
            long later = service(helper, increment).get(X);
            long delta = later - sampled.get();
            helper.assertTrue(sampled.get() > 0, "cycle never started");
            helper.assertTrue(delta == 40, "expected +40 over 40 ticks, got +" + delta);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void redstoneOutputEmits(GameTestHelper helper) {
        var busPos = setupNetwork(helper);
        var constant = place(helper, busPos, Direction.UP, AE2Logistics.CONSTANT_PART.get());
        var port = place(helper, busPos, Direction.NORTH, AE2Logistics.REDSTONE_IO_PART.get());
        helper.setBlock(new BlockPos(2, 1, 0), Blocks.REDSTONE_LAMP);

        constant.applyConfig(SRC, null, null, 0, 15, 0, false);
        port.applyConfig(null, SRC, null, 0, 0, 0, true);

        helper.runAfterDelay(20, () -> {
            var lamp = helper.getBlockState(new BlockPos(2, 1, 0));
            helper.assertTrue(lamp.getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),
                    "lamp should be lit by the redstone port");
            helper.succeed();
        });
    }
}
