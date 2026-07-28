package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.LogicCoreBlockEntity;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class LogicCoreGameTests {

    private static final String EMPTY = "empty5";

    private static LogicCoreBlockEntity core(GameTestHelper helper, BlockPos pos) {
        var be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof LogicCoreBlockEntity, "no logic core at " + pos);
        return (LogicCoreBlockEntity) be;
    }

    /** Entries chain through the scheduler exactly like physical parts would. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void logicCoreEvaluatesChain(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(1, 2, 1), AE2Logistics.LOGIC_CORE.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var core = core(helper, new BlockPos(1, 2, 1));
                    core.configureEntry(0, LogicPartType.CONSTANT.ordinal(),
                            "lc:a", "", "", 0, 42, 0, false);
                    core.configureEntry(1, LogicPartType.ARITHMETIC.ordinal(),
                            "lc:b", "lc:a", "", 0, 8, 0, false);
                    core.configureEntry(2, LogicPartType.THRESHOLD.ordinal(),
                            "lc:c", "lc:b", "", 3, 50, 0, false);
                })
                .thenExecuteAfter(40, () -> {
                    var core = core(helper, new BlockPos(1, 2, 1));
                    helper.assertTrue(core.coreActive(), "core must be active");
                    helper.assertTrue(core.activeEntries() == 3,
                            "3 entries must be active, got " + core.activeEntries());
                    helper.assertTrue(core.entryValue(0) == 42,
                            "constant must write 42, got " + core.entryValue(0));
                    helper.assertTrue(core.entryValue(1) == 50,
                            "arithmetic must write 50, got " + core.entryValue(1));
                    helper.assertTrue(core.entryValue(2) == 1,
                            "threshold must write 1, got " + core.entryValue(2));
                })
                .thenSucceed();
    }

    /** A sensor entry reads network storage through its own virtual node. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void logicCoreSensorReadsStorage(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var busPos = helper.absolutePos(new BlockPos(2, 1, 1));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), busPos, null, null, (IPartItem<?>) cable);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), busPos, Direction.WEST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1), AE2Logistics.LOGIC_CORE.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var core = core(helper, new BlockPos(2, 2, 1));
                    core.configureEntry(0, LogicPartType.STOCK_SENSOR.ordinal(),
                            "lc:iron", "", "", 0, 0, 0, false);
                    core.setEntryWatched(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
                })
                .thenExecuteAfter(80, () -> {
                    var core = core(helper, new BlockPos(2, 2, 1));
                    helper.assertTrue(core.entryValue(0) == 10,
                            "sensor must read 10 iron, got " + core.entryValue(0));
                })
                .thenSucceed();
    }

    /**
     * Entries genuinely consume channels: a full core plus its own node wants nine, so
     * behind an eight-channel glass segment something must starve, while a core on a
     * controller face (dense carrier, 32 channels) runs everything.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void logicCoreEntriesConsumeChannels(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:controller")));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                (IPartItem<?>) cable);
        helper.setBlock(new BlockPos(2, 1, 1), AE2Logistics.LOGIC_CORE.get());
        helper.setBlock(new BlockPos(0, 1, 2), AE2Logistics.LOGIC_CORE.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    for (var pos : new BlockPos[] {new BlockPos(2, 1, 1), new BlockPos(0, 1, 2)}) {
                        var core = core(helper, pos);
                        for (int i = 0; i < LogicCoreBlockEntity.ENTRIES; i++) {
                            core.configureEntry(i, LogicPartType.CONSTANT.ordinal(),
                                    "lc:" + pos.getZ() + "x" + i, "", "", 0, i + 1, 0, false);
                        }
                    }
                })
                .thenExecuteAfter(80, () -> {
                    var choked = core(helper, new BlockPos(2, 1, 1));
                    var direct = core(helper, new BlockPos(0, 1, 2));
                    helper.assertTrue(direct.coreActive() && direct.activeEntries() == 8,
                            "controller-face core must run all 8 entries, got "
                                    + direct.activeEntries());
                    helper.assertTrue(!(choked.coreActive() && choked.activeEntries() == 8),
                            "glass-fed core cannot satisfy core + 8 entries (9 channels through 8)");
                })
                .thenSucceed();
    }

    /** Entry configuration rides TransferableSettings, so blueprints can clone cores. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void logicCoreTransfersEntries(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(1, 2, 1), AE2Logistics.LOGIC_CORE.get());
        helper.setBlock(new BlockPos(1, 1, 2), AE2Logistics.LOGIC_CORE.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var source = core(helper, new BlockPos(1, 2, 1));
                    source.configureEntry(0, LogicPartType.CONSTANT.ordinal(),
                            "lc:t", "", "", 0, 7, 0, false);
                    source.configureEntry(1, LogicPartType.STOCK_SENSOR.ordinal(),
                            "lc:w", "", "", 0, 0, 0, false);
                    source.setEntryWatched(1, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));

                    var target = core(helper, new BlockPos(1, 1, 2));
                    target.importTransferSettings(source.exportTransferSettings(null), null);
                })
                .thenExecuteAfter(40, () -> {
                    var target = core(helper, new BlockPos(1, 1, 2));
                    helper.assertTrue(target.entry(0).type() == LogicPartType.CONSTANT,
                            "entry 0 type must transfer");
                    helper.assertTrue(target.entry(1).type() == LogicPartType.STOCK_SENSOR,
                            "entry 1 type must transfer");
                    helper.assertTrue(target.entry(0).valueARaw() == 7, "value must transfer");
                    var watched = target.entry(1).watchedKey();
                    helper.assertTrue(watched != null
                            && watched.what().equals(AEItemKey.of(Items.IRON_INGOT)),
                            "watched key must transfer");
                    // Both cores write lc:t; multi-writer channels sum, so 7 + 7.
                    helper.assertTrue(target.entryValue(0) == 14,
                            "both constants must sum on lc:t, got " + target.entryValue(0));
                })
                .thenSucceed();
    }
}
