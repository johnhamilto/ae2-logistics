package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;
import io.github.johnhamilto.ae2logistics.parts.QueryExportBusPart;
import io.github.johnhamilto.ae2logistics.parts.QuerySensorPart;
import io.github.johnhamilto.ae2logistics.parts.QueryTerminalPart;
import io.github.johnhamilto.ae2logistics.query.CompiledQuery;
import io.github.johnhamilto.ae2logistics.query.QueryContext;
import io.github.johnhamilto.ae2logistics.query.QueryParser;
import io.github.johnhamilto.ae2logistics.query.QueryService;

public class QueryGameTests {

    static void register() {
        LogisticsTestInstance.add("parserEvaluatesCoreGrammar", "empty5", 100, QueryGameTests::parserEvaluatesCoreGrammar);
        LogisticsTestInstance.add("librariesReplicateAndSensorsResolve", "empty5", 400, QueryGameTests::librariesReplicateAndSensorsResolve);
        LogisticsTestInstance.add("queryExportBusMovesMatchingItems", "empty5", 400, QueryGameTests::queryExportBusMovesMatchingItems);
        LogisticsTestInstance.add("signalTermGatesSensor", "empty5", 400, QueryGameTests::signalTermGatesSensor);
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static boolean matches(String source, AEItemKey key, QueryContext context,
            GameTestHelper helper) {
        var query = CompiledQuery.compile(source);
        helper.assertTrue(query != null, "'" + source + "' must parse");
        return query.matches(key, context);
    }

    /** The grammar, evaluated against a hand-built context - no world machinery. */
    public static void parserEvaluatesCoreGrammar(GameTestHelper helper) {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var ironOre = AEItemKey.of(Items.IRON_ORE);
        var pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue((int) (pick.getMaxDamage() * 0.9));
        var damagedPick = AEItemKey.of(pick);

        var stacks = new KeyCounter();
        stacks.add(iron, 64);
        stacks.add(gold, 4);
        stacks.add(ironOre, 12);
        stacks.add(damagedPick, 1);

        var context = new QueryContext(stacks, null,
                channel -> channel.equals(Identifier.parse("t:x")) ? 7 : 0,
                name -> name.equals("ores") ? CompiledQuery.compile("tag:c:ores") : null);

        helper.assertTrue(matches("mod:minecraft", iron, context, helper), "mod: should match");
        helper.assertTrue(matches("name:iron", iron, context, helper), "name: should match");
        helper.assertTrue(!matches("name:iron", gold, context, helper), "name: should not match gold");
        helper.assertTrue(matches("tag:c:ingots", iron, context, helper), "tag: should match");
        helper.assertTrue(matches("count >= 60", iron, context, helper), "count on iron");
        helper.assertTrue(!matches("count >= 60", gold, context, helper), "count on gold");
        helper.assertTrue(matches("count >= 10k", iron, context, helper) == false, "10k suffix");
        helper.assertTrue(matches("stored", iron, context, helper), "stored");
        helper.assertTrue(matches("damage > 50", damagedPick, context, helper), "damage high");
        helper.assertTrue(!matches("damage > 95", damagedPick, context, helper), "damage bound");
        helper.assertTrue(matches("signal(t:x) >= 5", iron, context, helper), "signal ge");
        helper.assertTrue(!matches("signal(t:x) > 7", iron, context, helper), "signal gt");
        helper.assertTrue(matches("@ores", ironOre, context, helper), "@ref resolves");
        helper.assertTrue(!matches("@ores", iron, context, helper), "@ref excludes ingot");
        helper.assertTrue(matches("mod:minecraft name:iron", iron, context, helper), "implicit AND");
        helper.assertTrue(matches("name:gold OR name:iron", iron, context, helper), "OR");
        helper.assertTrue(matches("NOT name:gold", iron, context, helper), "NOT");
        helper.assertTrue(matches("(name:iron OR name:gold) AND count < 10", gold, context, helper),
                "parens");
        helper.assertTrue(!QueryParser.parse("count >").ok(), "missing number must error");
        helper.assertTrue(!QueryParser.parse("wibble").ok(), "unknown term must error");
        helper.assertTrue(!QueryParser.parse("name:iron AND (").ok(), "open paren must error");
        helper.succeed();
    }

    /**
     * A sensor with an @named query resolves it live through the replicated library:
     * saving the query flips the sensor from 0 to the matching total, editing it moves
     * the total, and both terminals carry the definition.
     */
    public static void librariesReplicateAndSensorsResolve(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
            chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 5));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH,
                null, (IPartItem<?>) storageBus);

        var firstTerminal = (QueryTerminalPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP, null,
                AE2Logistics.QUERY_TERMINAL_PART.get());
        var secondTerminal = (QueryTerminalPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 1)), Direction.NORTH, null,
                AE2Logistics.QUERY_TERMINAL_PART.get());
        var sensor = (QuerySensorPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 1)), Direction.UP, null,
                AE2Logistics.QUERY_SENSOR_PART.get());

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    sensor.applySensorConfig(Identifier.parse("q:metal"), "@metals");
                    helper.assertTrue(sensor.currentValue() == 0,
                            "unresolved @ref must count nothing");
                    var node = firstTerminal.getMainNode().getNode();
                    helper.assertTrue(node != null && node.getGrid() != null, "terminal has no grid");
                    node.getGrid().getService(QueryService.class).put("metals", "name:iron");
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(firstTerminal.savedQueries().containsKey("metals"),
                            "first terminal must carry the query");
                    helper.assertTrue(secondTerminal.savedQueries().containsKey("metals"),
                            "second terminal must carry the query");
                    long value = sensor.currentValue();
                    helper.assertTrue(value == 10,
                            "sensor must count iron through @metals, got " + value);
                    var node = firstTerminal.getMainNode().getNode();
                    node.getGrid().getService(QueryService.class).put("metals", "tag:c:ingots");
                })
                .thenExecuteAfter(20, () -> {
                    long value = sensor.currentValue();
                    helper.assertTrue(value == 15,
                            "editing the named query must retarget the sensor, got " + value);
                    helper.succeed();
                })
                .thenSucceed();
    }

    /** The export bus moves only matching items into the inventory it faces. */
    public static void queryExportBusMovesMatchingItems(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
            chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH,
                null, (IPartItem<?>) storageBus);
        var bus = (QueryExportBusPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.EAST, null,
                AE2Logistics.QUERY_EXPORT_BUS_PART.get());
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.BARREL);

        helper.runAfterDelay(100, () -> bus.applyBusConfig("name:iron"));
        helper.runAfterDelay(260, () -> {
            int barrel = 0;
            int barrelGold = 0;
            if (helper.getBlockEntity(new BlockPos(2, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof BaseContainerBlockEntity container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    var stack = container.getItem(i);
                    if (stack.is(Items.IRON_INGOT)) {
                        barrel += stack.getCount();
                    }
                    if (stack.is(Items.GOLD_INGOT)) {
                        barrelGold += stack.getCount();
                    }
                }
            }
            helper.assertTrue(barrel == 8, "all iron must be exported, barrel has " + barrel);
            helper.assertTrue(barrelGold == 0, "gold must stay in the network");
            helper.succeed();
        });
    }

    /** signal() terms gate a query live from the logic graph. */
    public static void signalTermGatesSensor(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 0), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH,
                null, (IPartItem<?>) storageBus);
        var constant = (LogicPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.DOWN, null,
                AE2Logistics.CONSTANT_PART.get());
        var sensor = (QuerySensorPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP, null,
                AE2Logistics.QUERY_SENSOR_PART.get());

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    constant.applyConfig(Identifier.parse("g:mode"), null, null, 0, 0, 0, false);
                    sensor.applySensorConfig(Identifier.parse("q:gated"),
                            "stored AND signal(g:mode) > 0");
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(sensor.currentValue() == 0,
                            "gated query must count nothing while the signal is 0");
                    constant.applyConfig(Identifier.parse("g:mode"), null, null, 0, 1, 0, false);
                })
                .thenExecuteAfter(20, () -> {
                    long value = sensor.currentValue();
                    helper.assertTrue(value == 10,
                            "gated query must count stored iron once the signal opens, got " + value);
                    helper.succeed();
                })
                .thenSucceed();
    }
}
