package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.parts.storagebus.StorageBusPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.StorageJanitorBlockEntity;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class JanitorGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static StorageBusPart placeBus(GameTestHelper helper, BlockPos pos, Direction side) {
        var bus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        return (StorageBusPart) PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side,
                null, (IPartItem<?>) bus);
    }

    private static int count(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
        int total = 0;
        if (helper.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(item)) {
                    total += chest.getItem(i).getCount();
                }
            }
        }
        return total;
    }

    /**
     * The new-drawer-wall flow: stock sits in general storage, a partitioned
     * higher-priority home appears, one janitor run re-settles it - and the run
     * ends on its own with nothing stranded in the held buffer.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void janitorRehomesMisplacedStock(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 1), AE2Logistics.STORAGE_JANITOR.get());

        // General storage: unfiltered bus on chest A, holding misplaced tables.
        placeBus(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        helper.setBlock(new BlockPos(1, 1, 0), net.minecraft.world.level.block.Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 0)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.CRAFTING_TABLE, 40));
        }
        // The new home: partitioned, higher priority, on chest B.
        var homeBus = placeBus(helper, new BlockPos(2, 1, 1), Direction.NORTH);
        helper.setBlock(new BlockPos(2, 1, 0), net.minecraft.world.level.block.Blocks.CHEST);
        homeBus.setPriority(10);
        homeBus.getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1));

        // Wait for the REAL precondition instead of a fixed delay: the janitor's grid
        // must be up and see the misplaced stock (batch load order varies the timing).
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var janitor = (StorageJanitorBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 1));
                    var node = janitor.getGridNode(null);
                    if (node == null || node.getGrid() == null) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("no grid yet");
                    }
                    var counter = new appeng.api.stacks.KeyCounter();
                    node.getGrid().getStorageService().getInventory().getAvailableStacks(counter);
                    if (counter.get(AEItemKey.of(Items.CRAFTING_TABLE)) < 40) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("stock not visible yet");
                    }
                })
                .thenExecute(() -> {
                    var janitor = (StorageJanitorBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 1));
                    janitor.toggle();
                    helper.assertTrue(janitor.running(), "janitor must start with stock present");
                })
                .thenExecuteAfter(140, () -> {
                    var janitor = (StorageJanitorBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 1));
                    helper.assertTrue(!janitor.running(), "two-pass run must finish on its own");
                    helper.assertTrue(janitor.heldCount() == 0, "held buffer must be empty");
                    helper.assertTrue(janitor.processedTotal() > 0, "run must have processed stock");
                    int home = count(helper, new BlockPos(2, 1, 0), Items.CRAFTING_TABLE);
                    int old = count(helper, new BlockPos(1, 1, 0), Items.CRAFTING_TABLE);
                    helper.assertTrue(home == 40 && old == 0,
                            "tables must re-settle into the partitioned home, home " + home + " old " + old);
                })
                .thenSucceed();
    }

    /** Correctly-placed stock survives a run unmoved - the janitor is idempotent. */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void janitorLeavesSettledStockAlone(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(2, 1, 1), AE2Logistics.STORAGE_JANITOR.get());

        var bus = placeBus(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        helper.setBlock(new BlockPos(1, 1, 0), net.minecraft.world.level.block.Blocks.CHEST);
        bus.setPriority(10);
        bus.getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.OAK_LOG), 1));
        if (helper.getBlockEntity(new BlockPos(1, 1, 0)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_LOG, 25));
        }

        helper.runAfterDelay(40, () -> {
            if (helper.getBlockEntity(new BlockPos(2, 1, 1))
                    instanceof StorageJanitorBlockEntity janitor) {
                janitor.toggle();
            }
        });
        helper.runAfterDelay(140, () -> {
            var janitor = (StorageJanitorBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 1));
            helper.assertTrue(!janitor.running() && janitor.heldCount() == 0,
                    "run must finish clean");
            helper.assertTrue(count(helper, new BlockPos(1, 1, 0), Items.OAK_LOG) == 25,
                    "settled stock must stay in its home");
            helper.succeed();
        });
    }
}
