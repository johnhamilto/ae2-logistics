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

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.SubnetCoreBlockEntity;
import io.github.johnhamilto.ae2logistics.block.SubnetCoreEntry;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubnetCoreGameTests {

    private static SubnetCoreBlockEntity core(GameTestHelper helper, BlockPos pos) {
        var be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof SubnetCoreBlockEntity, "no subnet core at " + pos);
        return (SubnetCoreBlockEntity) be;
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        var be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof ChestBlockEntity, "no chest at " + pos);
        return (ChestBlockEntity) be;
    }

    private static int countItem(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            var stack = chest.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** The pipe subnet in one block: import from one face, storage-bus another, items flow. */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void subnetPipesBetweenFaces(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(1, 1, 1), AE2Logistics.SUBNET_CORE.get());
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        chest(helper, new BlockPos(0, 1, 1)).setItem(0, new ItemStack(Items.IRON_INGOT, 16));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var core = core(helper, new BlockPos(1, 1, 1));
                    core.configureEntry(0, SubnetCoreEntry.Type.IMPORT_BUS.ordinal(),
                            Direction.WEST.ordinal(), 0);
                    core.configureEntry(1, SubnetCoreEntry.Type.STORAGE_BUS.ordinal(),
                            Direction.EAST.ordinal(), 0);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(countItem(chest(helper, new BlockPos(2, 1, 1)), Items.IRON_INGOT) == 16,
                            "east chest must receive all 16 iron");
                    helper.assertTrue(countItem(chest(helper, new BlockPos(0, 1, 1)), Items.IRON_INGOT) == 0,
                            "west chest must drain");
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(1, 1, 0), Blocks.AIR))
                .thenWaitUntil(() -> {
                    var core = core(helper, new BlockPos(1, 1, 1));
                    helper.assertTrue(core.activeEntries() == 0,
                            "entries must go dark without main power, got " + core.activeEntries());
                })
                .thenSucceed();
    }

    /** Uplink + export: machines are fed straight from MAIN network storage, no cables. */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void subnetFeedsFromMainStorage(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var cablePos = helper.absolutePos(new BlockPos(2, 1, 1));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), cablePos, null, null, (IPartItem<?>) cable);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        chest(helper, new BlockPos(1, 1, 1)).setItem(0, new ItemStack(Items.OAK_PLANKS, 32));
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(helper.getLevel(), cablePos, Direction.WEST, null, (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 1, 2), AE2Logistics.SUBNET_CORE.get());
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.CHEST);

        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    var core = core(helper, new BlockPos(2, 1, 2));
                    core.configureEntry(0, SubnetCoreEntry.Type.UPLINK.ordinal(),
                            Direction.NORTH.ordinal(), 0);
                    core.configureEntry(1, SubnetCoreEntry.Type.EXPORT_BUS.ordinal(),
                            Direction.SOUTH.ordinal(), 0);
                    core.setEntryFilter(1, new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1));
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        countItem(chest(helper, new BlockPos(2, 1, 3)), Items.OAK_PLANKS) == 32,
                        "target chest must receive all 32 planks from main storage"))
                .thenSucceed();
    }

    /** Face inventories aggregate to the main network; inserts route by priority + filter. */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void subnetExposesAggregateAndRoutes(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(2, 1, 1), AE2Logistics.SUBNET_CORE.get());
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.CHEST);
        chest(helper, new BlockPos(1, 1, 1)).setItem(0, new ItemStack(Items.IRON_INGOT, 10));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var core = core(helper, new BlockPos(2, 1, 1));
                    core.configureEntry(0, SubnetCoreEntry.Type.STORAGE_BUS.ordinal(),
                            Direction.WEST.ordinal(), 0);
                    core.configureEntry(1, SubnetCoreEntry.Type.STORAGE_BUS.ordinal(),
                            Direction.EAST.ordinal(), 10);
                    core.setEntryFilter(1, new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1));
                    core.configureEntry(2, SubnetCoreEntry.Type.DOWNLINK.ordinal(),
                            Direction.NORTH.ordinal(), 5);
                })
                .thenWaitUntil(() -> {
                    var core = core(helper, new BlockPos(2, 1, 1));
                    var main = core.mainGrid();
                    helper.assertTrue(main != null, "core must be on a grid");
                    long iron = main.getStorageService().getInventory()
                            .extract(AEItemKey.of(Items.IRON_INGOT), 10, Actionable.SIMULATE,
                                    IActionSource.empty());
                    helper.assertTrue(iron == 10, "main must see 10 iron through the downlink, got " + iron);
                })
                .thenExecute(() -> {
                    var core = core(helper, new BlockPos(2, 1, 1));
                    long inserted = core.mainGrid().getStorageService().getInventory()
                            .insert(AEItemKey.of(Items.GOLD_INGOT), 5, Actionable.MODULATE,
                                    IActionSource.empty());
                    helper.assertTrue(inserted == 5, "main insert must route into the subnet, got " + inserted);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        countItem(chest(helper, new BlockPos(3, 1, 1)), Items.GOLD_INGOT) == 5,
                        "gold must land in the filtered high-priority chest"))
                .thenSucceed();
    }

    /**
     * Uplink + downlink form a cycle; the reentrancy latch must keep counting and
     * routing conservative - no hangs, no double counts, no duplication.
     */
    @GameTest(template = "empty5", timeoutTicks = 600)
    public void subnetCycleStaysConservative(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(2, 1, 1), AE2Logistics.SUBNET_CORE.get());
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        chest(helper, new BlockPos(1, 1, 1)).setItem(0, new ItemStack(Items.IRON_INGOT, 10));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var core = core(helper, new BlockPos(2, 1, 1));
                    core.configureEntry(0, SubnetCoreEntry.Type.STORAGE_BUS.ordinal(),
                            Direction.WEST.ordinal(), 0);
                    core.configureEntry(1, SubnetCoreEntry.Type.UPLINK.ordinal(),
                            Direction.NORTH.ordinal(), 0);
                    core.configureEntry(2, SubnetCoreEntry.Type.DOWNLINK.ordinal(),
                            Direction.NORTH.ordinal(), 0);
                })
                .thenWaitUntil(() -> {
                    var core = core(helper, new BlockPos(2, 1, 1));
                    helper.assertTrue(core.activeEntries() == 3, "all three entries must be active");
                    var counter = new KeyCounter();
                    core.mainGrid().getStorageService().getInventory().getAvailableStacks(counter);
                    long iron = counter.get(AEItemKey.of(Items.IRON_INGOT));
                    helper.assertTrue(iron == 10,
                            "cycle must count the 10 iron exactly once, got " + iron);
                })
                .thenExecute(() -> {
                    var core = core(helper, new BlockPos(2, 1, 1));
                    var inventory = core.mainGrid().getStorageService().getInventory();
                    long inserted = inventory.insert(AEItemKey.of(Items.GOLD_INGOT), 10,
                            Actionable.MODULATE, IActionSource.empty());
                    helper.assertTrue(inserted == 10, "gold must insert through the cycle, got " + inserted);
                })
                .thenExecuteAfter(10, () -> {
                    var core = core(helper, new BlockPos(2, 1, 1));
                    var counter = new KeyCounter();
                    core.mainGrid().getStorageService().getInventory().getAvailableStacks(counter);
                    long gold = counter.get(AEItemKey.of(Items.GOLD_INGOT));
                    int inChest = countItem(chest(helper, new BlockPos(1, 1, 1)), Items.GOLD_INGOT);
                    helper.assertTrue(gold == 10, "network must count 10 gold exactly once, got " + gold);
                    helper.assertTrue(inChest == 10, "gold must be stored once in the face chest, got " + inChest);

                    long extracted = core.mainGrid().getStorageService().getInventory()
                            .extract(AEItemKey.of(Items.GOLD_INGOT), 20, Actionable.MODULATE,
                                    IActionSource.empty());
                    helper.assertTrue(extracted == 10,
                            "extraction must yield exactly the 10 stored gold, got " + extracted);
                    helper.assertTrue(countItem(chest(helper, new BlockPos(1, 1, 1)), Items.GOLD_INGOT) == 0,
                            "chest must drain on extraction");
                })
                .thenSucceed();
    }

    /** Entry configuration survives NBT and transfer, like every other core. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void subnetCoreRoundTripsNbt(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.setBlock(new BlockPos(1, 1, 1), AE2Logistics.SUBNET_CORE.get());

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    var core = core(helper, new BlockPos(1, 1, 1));
                    core.configureEntry(0, SubnetCoreEntry.Type.STORAGE_BUS.ordinal(),
                            Direction.EAST.ordinal(), 7);
                    core.setEntryFilter(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
                    core.configureEntry(1, SubnetCoreEntry.Type.DOWNLINK.ordinal(),
                            Direction.NORTH.ordinal(), 3);

                    var tag = core.saveWithFullMetadata(registries);
                    helper.setBlock(new BlockPos(3, 1, 1), AE2Logistics.SUBNET_CORE.get());
                    helper.getBlockEntity(new BlockPos(3, 1, 1)).loadWithComponents(tag, registries);
                })
                .thenExecuteAfter(20, () -> {
                    var twin = core(helper, new BlockPos(3, 1, 1));
                    helper.assertTrue(twin.entry(0).type() == SubnetCoreEntry.Type.STORAGE_BUS
                            && twin.entry(0).face() == Direction.EAST
                            && twin.entry(0).priority() == 7, "entry 0 must round-trip");
                    var filter = twin.entry(0).filter();
                    helper.assertTrue(filter != null
                            && filter.what().equals(AEItemKey.of(Items.IRON_INGOT)),
                            "filter must round-trip");
                    helper.assertTrue(twin.entry(1).type() == SubnetCoreEntry.Type.DOWNLINK
                            && twin.entry(1).priority() == 3, "entry 1 must round-trip");
                })
                .thenSucceed();
    }
}
