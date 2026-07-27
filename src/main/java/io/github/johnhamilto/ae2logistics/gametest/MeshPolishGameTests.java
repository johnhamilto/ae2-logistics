package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.P2PActionPayload;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;
import io.github.johnhamilto.ae2logistics.parts.P2PFrequencyTerminalPart;
import io.github.johnhamilto.ae2logistics.parts.P2PNames;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class MeshPolishGameTests {

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, (IPartItem<?>) cable);
    }

    private static MeshEndpointPart placeEndpoint(GameTestHelper helper, BlockPos pos, Direction side,
            String frequency, byte role, int mask) {
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null,
                AE2Logistics.MESH_ENDPOINT_PART.get());
        helper.assertTrue(part != null, "mesh endpoint placement failed at " + pos + " " + side);
        part.applyMeshConfig(frequency, role, 0, mask);
        return part;
    }

    private static P2PTunnelPart<?> placeTunnel(GameTestHelper helper, BlockPos pos, Direction side) {
        var tunnelItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:me_p2p_tunnel"));
        return (P2PTunnelPart<?>) PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), side, null,
                (IPartItem<?>) tunnelItem);
    }

    private static void makeOutput(P2PTunnelPart<?> tunnel) {
        try {
            var method = P2PTunnelPart.class.getDeclaredMethod("setOutput", boolean.class);
            method.setAccessible(true);
            method.invoke(tunnel, true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static int countItems(GameTestHelper helper, BlockPos pos) {
        int count = 0;
        if (helper.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                count += chest.getItem(i).getCount();
            }
        }
        return count;
    }

    /**
     * Names live on the tunnels, not on any terminal: renaming writes every tunnel of the
     * frequency, a tunnel retuned in adopts the name, a tunnel retuned away drops it, and
     * the frequency keeps its name as long as one named tunnel remains. No terminal part
     * is ever placed here.
     */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void p2pNamesLiveOnTunnels(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));

        var tunnelA = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.UP);
        var tunnelB = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.NORTH);
        var tunnelC = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.EAST);
        makeOutput(tunnelB);
        makeOutput(tunnelC);

        helper.runAfterDelay(10, () -> {
            var node = tunnelA.getMainNode().getNode();
            helper.assertTrue(node != null && node.getGrid() != null, "tunnel A has no grid");
            var grid = node.getGrid();
            var service = P2PService.get(grid);

            short first = service.newFrequency();
            service.updateFreq(tunnelA, first);
            service.updateFreq(tunnelB, first);

            P2PNames.rename(grid, first, "cargo");
            helper.assertTrue(P2PNames.nameOn(tunnelA).equals("cargo"),
                    "rename must write the input tunnel");
            helper.assertTrue(P2PNames.nameOn(tunnelB).equals("cargo"),
                    "rename must write the output tunnel");

            helper.assertTrue(P2PActionPayload.retune(tunnelC, first), "output retune must succeed");
            helper.assertTrue(P2PNames.nameOn(tunnelC).equals("cargo"),
                    "a tunnel retuned onto a named frequency must adopt its name");

            short second = service.newFrequency();
            helper.assertTrue(P2PActionPayload.retune(tunnelA, second), "input retune must succeed");
            helper.assertTrue(P2PNames.nameOn(tunnelA).isEmpty(),
                    "a tunnel retuned onto an unnamed frequency must drop the old name");
            helper.assertTrue(P2PNames.resolve(grid, first).equals("cargo"),
                    "the frequency keeps its name while any named tunnel remains");
            helper.succeed();
        });
    }

    /** Pre-0.6 per-terminal names must migrate onto the tunnels once the grid is up. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void p2pLegacyNamesMigrateToTunnels(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));

        var tunnel = placeTunnel(helper, new BlockPos(2, 1, 1), Direction.UP);
        var terminal = (P2PFrequencyTerminalPart) PartHelper.setPart(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, 1, 1)), Direction.NORTH, null,
                AE2Logistics.P2P_TERMINAL_PART.get());
        helper.assertTrue(terminal != null, "terminal placement failed");

        helper.runAfterDelay(20, () -> {
            var node = tunnel.getMainNode().getNode();
            helper.assertTrue(node != null && node.getGrid() != null, "tunnel has no grid");
            var service = P2PService.get(node.getGrid());
            short frequency = service.newFrequency();
            service.updateFreq(tunnel, frequency);

            var registries = helper.getLevel().registryAccess();
            var tag = new CompoundTag();
            terminal.writeToNBT(tag, registries);
            var list = new ListTag();
            var entry = new CompoundTag();
            entry.putShort("freq", frequency);
            entry.putString("name", "from-the-old-world");
            list.add(entry);
            tag.put("frequencyNames", list);
            terminal.readFromNBT(tag, registries);

            terminal.migrateLegacyNames();
            helper.assertTrue(P2PNames.nameOn(tunnel).equals("from-the-old-world"),
                    "legacy terminal names must migrate onto the tunnels, got '"
                            + P2PNames.nameOn(tunnel) + "'");
            helper.succeed();
        });
    }

    /** An IN endpoint's filter refuses non-matching inserts at the exposed handler. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void inFilterRefusesNonMatchingInserts(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var input = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.NORTH, "filter-in",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        input.setFilterSlot(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
        placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "filter-in",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);

        helper.runAfterDelay(30, () -> {
            var handler = input.exposedItemHandler();
            helper.assertTrue(handler != null, "input must expose a handler");
            var restGold = handler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 4), false);
            helper.assertTrue(restGold.getCount() == 4,
                    "gold must be refused by the iron filter, got back " + restGold.getCount());
            var restIron = handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
            helper.assertTrue(restIron.isEmpty(), "iron must pass the filter");
        });
        helper.runAfterDelay(40, () -> {
            int chest = countItems(helper, new BlockPos(2, 2, 1));
            helper.assertTrue(chest == 4, "only the iron may arrive, chest has " + chest);
            helper.succeed();
        });
    }

    /** OUT filters steer each stack to the machine whose whitelist matches it. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void outFiltersRouteByKey(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(3, 1, 1));

        var input = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.NORTH, "filter-out",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        var ironTarget = placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "filter-out",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        ironTarget.setFilterSlot(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
        var goldTarget = placeEndpoint(helper, new BlockPos(3, 1, 1), Direction.UP, "filter-out",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        goldTarget.setFilterSlot(0, new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1));
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.CHEST);

        helper.runAfterDelay(30, () -> {
            var handler = input.exposedItemHandler();
            helper.assertTrue(handler != null, "input must expose a handler");
            var restIron = handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
            var restGold = handler.insertItem(0, new ItemStack(Items.GOLD_INGOT, 4), false);
            helper.assertTrue(restIron.isEmpty() && restGold.isEmpty(),
                    "both stacks must be accepted somewhere");
        });
        helper.runAfterDelay(40, () -> {
            var ironChest = helper.getBlockEntity(new BlockPos(2, 2, 1));
            var goldChest = helper.getBlockEntity(new BlockPos(3, 2, 1));
            helper.assertTrue(ironChest instanceof ChestBlockEntity && goldChest instanceof ChestBlockEntity,
                    "chests missing");
            var iron = ((ChestBlockEntity) ironChest).getItem(0);
            var gold = ((ChestBlockEntity) goldChest).getItem(0);
            helper.assertTrue(iron.is(Items.IRON_INGOT) && iron.getCount() == 4,
                    "iron chest must hold 4 iron, has " + iron);
            helper.assertTrue(gold.is(Items.GOLD_INGOT) && gold.getCount() == 4,
                    "gold chest must hold 4 gold, has " + gold);
            helper.succeed();
        });
    }

    /**
     * A provider batch whose later ingredient is rejected by the first machine's filter
     * must move WHOLE to a machine that accepts everything - never split across machines.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void providerBatchMovesWholeToAcceptingMachine(GameTestHelper helper) {
        var level = helper.getLevel();

        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof ChestBlockEntity source) {
            source.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            source.setItem(1, new ItemStack(Items.IRON_INGOT, 4));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(level, helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST, null,
                (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider")));
        placeCable(helper, new BlockPos(2, 2, 2));
        placeCable(helper, new BlockPos(2, 2, 3));

        var input = placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.SOUTH, "batch-filter",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        var planksOnly = placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.WEST, "batch-filter",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        planksOnly.applyMeshConfig("batch-filter", MeshEndpointPart.ROLE_OUT, 10, MeshRegistry.TYPE_ITEM);
        planksOnly.setFilterSlot(0, new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1));
        placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.EAST, "batch-filter",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);

        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern.encode(pattern,
                java.util.List.of(
                        new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4),
                        new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2)),
                java.util.List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                java.util.List.of(
                        io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec.EXACT,
                        io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec.EXACT));
        if (helper.getBlockEntity(new BlockPos(2, 1, 3)) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe) {
            providerBe.getLogic().getPatternInv().setItemDirect(0, pattern);
        } else {
            helper.fail("no pattern provider");
        }

        var job = new Object() {
            java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> future;
            boolean submitted;
        };
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    var grid = input.getMainNode().getGrid();
                    var source = new appeng.me.helpers.MachineSource(input);
                    job.future = grid.getCraftingService().beginCraftingCalculation(level,
                            () -> source, AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    try {
                        var plan = job.future.get(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (plan.simulation()) {
                            helper.fail("plan incomplete");
                        }
                        if (!job.submitted) {
                            var grid = input.getMainNode().getGrid();
                            var result = grid.getCraftingService().submitJob(plan, null, null, true,
                                    new appeng.me.helpers.MachineSource(input));
                            if (!result.successful()) {
                                throw new net.minecraft.gametest.framework.GameTestAssertException(
                                        "submit failed: " + result.errorCode());
                            }
                            job.submitted = true;
                        }
                    } catch (java.util.concurrent.TimeoutException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("planning");
                    } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenExecuteAfter(80, () -> {
                    int filteredChest = countItems(helper, new BlockPos(1, 1, 2));
                    int openChest = countItems(helper, new BlockPos(3, 1, 2));
                    helper.assertTrue(filteredChest == 0,
                            "the planks-only machine must not receive a batch containing iron, has "
                                    + filteredChest);
                    helper.assertTrue(openChest == 6,
                            "the whole 6-item batch must land on the accepting machine, has " + openChest);
                    helper.succeed();
                })
                .thenSucceed();
    }

    /** Renaming a mesh frequency retags every endpoint and rebuilds registry membership. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void meshRenameRetagsEveryEndpoint(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));

        var first = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "ren-a",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        var second = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.NORTH, "ren-a",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);

        helper.runAfterDelay(10, () -> {
            MeshRegistry.renameFrequency("ren-a", "ren-b");
            helper.assertTrue(first.frequency().equals("ren-b") && second.frequency().equals("ren-b"),
                    "both endpoints must carry the new frequency");
            helper.assertTrue(MeshRegistry.endpoints("ren-a").isEmpty(),
                    "old frequency must be empty after rename");
            helper.assertTrue(MeshRegistry.endpoints("ren-b").size() == 2,
                    "new frequency must hold both endpoints");
            helper.succeed();
        });
    }

    /** Two ME endpoints already joined by cable must be flagged as a cabled loop. */
    @GameTest(template = "empty5", timeoutTicks = 200)
    public void cabledLoopIsFlagged(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));

        var first = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "loop-flag",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);
        var second = placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "loop-flag",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);

        helper.runAfterDelay(40, () -> {
            int loops = 0;
            int hubs = 0;
            for (var endpoint : new MeshEndpointPart[] {first, second}) {
                if (MeshRegistry.statusOf(endpoint) == MeshRegistry.STATUS_CABLED_LOOP) {
                    loops++;
                }
                if (endpoint.meLinkState() == MeshRegistry.ME_STATE_HUB) {
                    hubs++;
                }
            }
            helper.assertTrue(hubs == 1, "exactly one endpoint must be the ME hub, got " + hubs);
            helper.assertTrue(loops == 1,
                    "the spoke must be flagged as a cabled loop, flagged " + loops);
            helper.succeed();
        });
    }
}
