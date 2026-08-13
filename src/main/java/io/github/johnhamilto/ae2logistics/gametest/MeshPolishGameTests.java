package io.github.johnhamilto.ae2logistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.MeshEndpointMenu;
import io.github.johnhamilto.ae2logistics.menu.P2PActionPayload;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;
import io.github.johnhamilto.ae2logistics.parts.P2PFrequencyTerminalPart;
import io.github.johnhamilto.ae2logistics.parts.P2PNames;

public class MeshPolishGameTests {

    static void register() {
        LogisticsTestInstance.add("p2pNamesLiveOnTunnels", "empty5", 200, MeshPolishGameTests::p2pNamesLiveOnTunnels);
        LogisticsTestInstance.add("p2pLegacyNamesMigrateToTunnels", "empty5", 200, MeshPolishGameTests::p2pLegacyNamesMigrateToTunnels);
        LogisticsTestInstance.add("providerBatchMovesWholeToAcceptingMachine", "empty5", 400, MeshPolishGameTests::providerBatchMovesWholeToAcceptingMachine);
        LogisticsTestInstance.add("meshRenameRetagsEveryEndpoint", "empty5", 200, MeshPolishGameTests::meshRenameRetagsEveryEndpoint);
        LogisticsTestInstance.add("cabledLoopIsFlagged", "empty5", 200, MeshPolishGameTests::cabledLoopIsFlagged);
        LogisticsTestInstance.add("rosterListsCarrierFrequencyWithSelf", "empty5", 200, MeshPolishGameTests::rosterListsCarrierFrequencyWithSelf);
        LogisticsTestInstance.add("rosterScopesToCarrierNetwork", "empty5", 200, MeshPolishGameTests::rosterScopesToCarrierNetwork);
        LogisticsTestInstance.add("universalPlacesOpenAndUnlocked", "empty5", 100, MeshPolishGameTests::universalPlacesOpenAndUnlocked);
        LogisticsTestInstance.add("endpointRecipesRideTheTunnelTag", "empty5", 100, MeshPolishGameTests::endpointRecipesRideTheTunnelTag);
        LogisticsTestInstance.add("meshEndpointRetunesAcrossFrequencies", "empty5", 200, MeshPolishGameTests::meshEndpointRetunesAcrossFrequencies);
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:fluix_glass_cable"));
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
        var tunnelItem = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:me_p2p_tunnel"));
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
        if (helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
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
    public static void p2pNamesLiveOnTunnels(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
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
    public static void p2pLegacyNamesMigrateToTunnels(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
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
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, registries);
            terminal.writeToNBT(out);
            var tag = out.buildResult();
            var list = new ListTag();
            var entry = new CompoundTag();
            entry.putShort("freq", frequency);
            entry.putString("name", "from-the-old-world");
            list.add(entry);
            tag.put("frequencyNames", list);
            terminal.readFromNBT(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

            terminal.migrateLegacyNames();
            helper.assertTrue(P2PNames.nameOn(tunnel).equals("from-the-old-world"),
                    "legacy terminal names must migrate onto the tunnels, got '"
                            + P2PNames.nameOn(tunnel) + "'");
            helper.succeed();
        });
    }

    /**
     * A provider batch whose later ingredient cannot fit in the first machine must move
     * WHOLE to a machine that accepts everything - never split across machines.
     */
    public static void providerBatchMovesWholeToAcceptingMachine(GameTestHelper helper) {
        var level = helper.getLevel();

        helper.setBlock(new BlockPos(2, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(1, 1, 1), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity source) {
            source.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            source.setItem(1, new ItemStack(Items.IRON_INGOT, 4));
        }
        var storageBus = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:storage_bus"));
        PartHelper.setPart(level, helper.absolutePos(new BlockPos(2, 1, 1)), Direction.WEST, null,
                (IPartItem<?>) storageBus);
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:1k_crafting_storage")));
        helper.setBlock(new BlockPos(2, 1, 3),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:pattern_provider")));
        placeCable(helper, new BlockPos(2, 2, 2));
        placeCable(helper, new BlockPos(2, 2, 3));

        var input = placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.SOUTH, "batch-filter",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_PROVIDER);
        var cramped = placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.WEST, "batch-filter",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_PROVIDER);
        cramped.applyMeshConfig("batch-filter", MeshEndpointPart.ROLE_OUT, 10, MeshRegistry.TYPE_PROVIDER);
        placeEndpoint(helper, new BlockPos(2, 1, 2), Direction.EAST, "batch-filter",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_PROVIDER);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);
        // The preferred (priority 10) machine has room for the planks but no slot for
        // the iron, so the whole batch must move on rather than split.
        if (helper.getBlockEntity(new BlockPos(1, 1, 2), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity crampedChest) {
            for (int i = 0; i < crampedChest.getContainerSize() - 1; i++) {
                crampedChest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            crampedChest.setItem(crampedChest.getContainerSize() - 1,
                    new ItemStack(Items.OAK_PLANKS, 60));
        }

        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern.encode(pattern,
                java.util.List.of(
                        new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4),
                        new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2)),
                java.util.List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                java.util.List.of(
                        io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec.EXACT,
                        io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec.EXACT));
        if (helper.getBlockEntity(new BlockPos(2, 1, 3), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof appeng.blockentity.crafting.PatternProviderBlockEntity providerBe) {
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
                                throw helper.assertionException(
                                        "submit failed: " + result.errorCode());
                            }
                            job.submitted = true;
                        }
                    } catch (java.util.concurrent.TimeoutException e) {
                        throw helper.assertionException("planning");
                    } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenExecuteAfter(80, () -> {
                    int openChest = countItems(helper, new BlockPos(3, 1, 2));
                    int crampedPlanks = 0;
                    int crampedIron = 0;
                    if (helper.getBlockEntity(new BlockPos(1, 1, 2), net.minecraft.world.level.block.entity.BlockEntity.class) instanceof ChestBlockEntity chest) {
                        for (int i = 0; i < chest.getContainerSize(); i++) {
                            var stack = chest.getItem(i);
                            if (stack.is(Items.OAK_PLANKS)) {
                                crampedPlanks += stack.getCount();
                            } else if (stack.is(Items.IRON_INGOT)) {
                                crampedIron += stack.getCount();
                            }
                        }
                    }
                    helper.assertTrue(crampedPlanks == 60 && crampedIron == 0,
                            "the cramped machine must receive nothing from a batch it cannot hold whole, has "
                                    + crampedPlanks + " planks / " + crampedIron + " iron");
                    helper.assertTrue(openChest == 6,
                            "the whole 6-item batch must land on the accepting machine, has " + openChest);
                    helper.succeed();
                })
                .thenSucceed();
    }

    /** Renaming a mesh frequency retags every endpoint and rebuilds registry membership. */
    public static void meshRenameRetagsEveryEndpoint(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));

        var first = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "ren-a",
                MeshEndpointPart.ROLE_IN, MeshRegistry.TYPE_ITEM);
        var second = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.NORTH, "ren-a",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ITEM);

        helper.runAfterDelay(10, () -> {
            MeshRegistry.renameFrequency("ren-a", "ren-b",
                    first.getMainNode().getNode().getGrid());
            helper.assertTrue(first.frequency().equals("ren-b") && second.frequency().equals("ren-b"),
                    "both endpoints must carry the new frequency");
            helper.assertTrue(MeshRegistry.endpoints("ren-a").isEmpty(),
                    "old frequency must be empty after rename");
            helper.assertTrue(MeshRegistry.endpoints("ren-b").size() == 2,
                    "new frequency must hold both endpoints");
            helper.succeed();
        });
    }

    /** Two ME endpoints whose FED networks already touch must be flagged as a cabled loop. */
    public static void cabledLoopIsFlagged(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        // Fed cells above the endpoint faces, adjacent to EACH OTHER: the carried
        // network already spans both endpoints before the mesh links it - a loop.
        helper.setBlock(new BlockPos(1, 2, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        helper.setBlock(new BlockPos(2, 2, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));

        var first = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "loop-flag",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);
        var second = placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "loop-flag",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ME);

        // Loop detection runs when lanes (re)build; the initial build can race the
        // carried nodes' in-world connections, so force the documented re-check.
        helper.runAfterDelay(20, () -> MeshRegistry.forceRelink("loop-flag"));

        helper.runAfterDelay(60, () -> {
            // One pre-linked carried grid spans every endpoint, so the mesh has nothing
            // to bridge: every endpoint is flagged, and no lanes exist.
            int loops = 0;
            for (var endpoint : new MeshEndpointPart[] {first, second}) {
                if (endpoint.meLinkState() == MeshRegistry.ME_STATE_LOOP) {
                    loops++;
                }
            }
            helper.assertTrue(loops == 2,
                    "both endpoints of an already-cabled frequency must be flagged, flagged " + loops);
            helper.succeed();
        });
    }

    /**
     * The GUI roster is the carrier-scoped frequency membership: every same-network
     * endpoint of the frequency appears exactly once (typed and universal alike),
     * exactly one row marks itself, rows carry live role and priority, and other
     * frequencies never leak in.
     */
    public static void rosterListsCarrierFrequencyWithSelf(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(3, 1, 1));
        placeCable(helper, new BlockPos(1, 1, 2));

        var self = placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "roster-a",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ALL);
        var out = placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "roster-a",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ALL);
        out.applyMeshConfig("roster-a", MeshEndpointPart.ROLE_OUT, 7, MeshRegistry.TYPE_ALL);
        // A typed endpoint joins the same frequency - the roster does not care which
        // part item a row came from.
        var typed = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1)),
                Direction.UP, null, AE2Logistics.MESH_ENDPOINT_ITEM_PART.get());
        typed.applyMeshConfig("roster-a", MeshEndpointPart.ROLE_BOTH, 0, 0);
        // Same network, different frequency: never in this roster.
        placeEndpoint(helper, new BlockPos(1, 1, 2), Direction.UP, "roster-b",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ALL);

        helper.runAfterDelay(60, () -> {
            var roster = MeshEndpointMenu.buildRoster(self);
            helper.assertTrue(roster.total() == 3, "expected 3 roster rows, got " + roster.total());
            int selfRows = 0;
            boolean sawOut = false;
            var positions = new java.util.HashSet<BlockPos>();
            for (var row : roster.rows()) {
                positions.add(row.pos());
                if (row.self()) {
                    selfRows++;
                    helper.assertTrue(row.pos().equals(helper.absolutePos(new BlockPos(1, 1, 1))),
                            "self row must sit at the opening endpoint");
                }
                if (row.pos().equals(helper.absolutePos(new BlockPos(2, 1, 1)))) {
                    sawOut = true;
                    helper.assertTrue(row.role() == MeshEndpointPart.ROLE_OUT && row.priority() == 7,
                            "roster row must carry the endpoint's live role and priority");
                }
            }
            helper.assertTrue(selfRows == 1, "exactly one row marks itself, got " + selfRows);
            helper.assertTrue(sawOut, "configured output endpoint missing from the roster");
            helper.assertTrue(positions.contains(helper.absolutePos(new BlockPos(3, 1, 1))),
                    "typed endpoint missing from the roster");
            helper.assertTrue(!positions.contains(helper.absolutePos(new BlockPos(1, 1, 2))),
                    "other frequency leaked into the roster");
            helper.succeed();
        });
    }

    /** Same frequency on two ISOLATED networks: each roster sees only its own carrier. */
    public static void rosterScopesToCarrierNetwork(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 0));
        placeCable(helper, new BlockPos(2, 1, 0));
        helper.setBlock(new BlockPos(0, 1, 3),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 3));

        var nearA = placeEndpoint(helper, new BlockPos(1, 1, 0), Direction.UP, "roster-x",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ALL);
        placeEndpoint(helper, new BlockPos(2, 1, 0), Direction.UP, "roster-x",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ALL);
        var far = placeEndpoint(helper, new BlockPos(1, 1, 3), Direction.UP, "roster-x",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ALL);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(MeshRegistry.endpoints("roster-x").size() == 3,
                    "registry must hold all three endpoints of the frequency");
            int nearTotal = MeshEndpointMenu.buildRoster(nearA).total();
            int farTotal = MeshEndpointMenu.buildRoster(far).total();
            helper.assertTrue(nearTotal == 2, "two-endpoint carrier must roster 2, got " + nearTotal);
            helper.assertTrue(farTotal == 1, "lone carrier must roster only itself, got " + farTotal);
            helper.succeed();
        });
    }

    /** A raw-placed universal endpoint starts open: every transport, unlocked, no frequency. */
    public static void universalPlacesOpenAndUnlocked(GameTestHelper helper) {
        placeCable(helper, new BlockPos(1, 1, 1));
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Direction.UP, null, AE2Logistics.MESH_ENDPOINT_PART.get());
        helper.assertTrue(part != null, "universal endpoint placement failed");
        helper.assertTrue(part.capabilityMask() == MeshRegistry.TYPE_ALL,
                "universal must place attuned to every transport");
        helper.assertTrue(!part.capabilityLocked(), "universal mask must stay editable");
        helper.assertTrue(MeshEndpointMenu.buildRoster(part).total() == 0,
                "a blank frequency must roster empty");
        helper.succeed();
    }

    /**
     * Recipe sanity for the tag-driven endpoint recipes: the p2p_tunnels tag resolves
     * against live AE2 ids (upstream id drift would silently break every recipe), and
     * the endpoint recipes actually accept a tag member as their tunnel ingredient.
     */
    public static void endpointRecipesRideTheTunnelTag(GameTestHelper helper) {
        var tag = TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(AE2Logistics.MOD_ID, "p2p_tunnels"));
        var ae2Tunnel = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:redstone_p2p_tunnel"));
        helper.assertTrue(new ItemStack(ae2Tunnel).is(tag),
                "AE2's tunnels must sit in the recipe tag");
        helper.assertTrue(new ItemStack(AE2Logistics.PROVIDER_P2P_TUNNEL_PART.get()).is(tag),
                "the provider tunnel must sit in the recipe tag");

        var manager = helper.getLevel().getServer().getRecipeManager();
        for (var id : new String[] {"mesh_endpoint", "mesh_endpoint_item", "mesh_endpoint_provider"}) {
            var recipe = manager.byKey(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.RECIPE,
                    Identifier.fromNamespaceAndPath(AE2Logistics.MOD_ID, id)));
            helper.assertTrue(recipe.isPresent(), id + " recipe must exist");
            boolean tunnelSlot = false;
            for (var ingredient : recipe.get().value().getIngredients()) {
                tunnelSlot |= ingredient.test(new ItemStack(ae2Tunnel));
            }
            helper.assertTrue(tunnelSlot, id + " must accept any tagged tunnel");
        }
        helper.succeed();
    }

    /**
     * Terminal-driven mesh retune: exactly the identified endpoint moves onto the
     * target frequency with role, priority, and transports intact; a stale identity
     * (already moved) and a blank or same-frequency target are refused.
     */
    public static void meshEndpointRetunesAcrossFrequencies(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1),
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("ae2:creative_energy_cell")));
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        placeCable(helper, new BlockPos(3, 1, 1));
        placeEndpoint(helper, new BlockPos(1, 1, 1), Direction.UP, "ret-a",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ALL);
        var moved = placeEndpoint(helper, new BlockPos(2, 1, 1), Direction.UP, "ret-a",
                MeshEndpointPart.ROLE_OUT, MeshRegistry.TYPE_ALL);
        moved.applyMeshConfig("ret-a", MeshEndpointPart.ROLE_OUT, 7, MeshRegistry.TYPE_ALL);
        placeEndpoint(helper, new BlockPos(3, 1, 1), Direction.UP, "ret-b",
                MeshEndpointPart.ROLE_BOTH, MeshRegistry.TYPE_ALL);

        helper.runAfterDelay(40, () -> {
            var grid = moved.getMainNode().getGrid();
            helper.assertTrue(grid != null, "endpoint must be on a grid");
            var dimension = helper.getLevel().dimension().identifier().toString();
            var pos = helper.absolutePos(new BlockPos(2, 1, 1));
            byte side = (byte) Direction.UP.ordinal();

            helper.assertTrue(!MeshRegistry.retuneEndpoint(grid, "ret-a", pos, side, dimension, " "),
                    "blank target must be refused");
            helper.assertTrue(!MeshRegistry.retuneEndpoint(grid, "ret-a", pos, side, dimension, "ret-a"),
                    "same-frequency target must be refused");
            helper.assertTrue(MeshRegistry.retuneEndpoint(grid, "ret-a", pos, side, dimension, "ret-b"),
                    "retune must find and move the endpoint");
            helper.assertTrue(!MeshRegistry.retuneEndpoint(grid, "ret-a", pos, side, dimension, "ret-b"),
                    "a stale identity must be refused after the move");

            helper.assertTrue(MeshRegistry.endpoints("ret-a").size() == 1,
                    "old frequency must keep only the untouched endpoint");
            helper.assertTrue(MeshRegistry.endpoints("ret-b").size() == 2,
                    "target frequency must gain the moved endpoint");
            helper.assertTrue(moved.frequency().equals("ret-b")
                    && moved.role() == MeshEndpointPart.ROLE_OUT && moved.priority() == 7
                    && moved.capabilityMask() == MeshRegistry.TYPE_ALL,
                    "retune must keep role, priority, and transports");
            helper.succeed();
        });
    }
}
