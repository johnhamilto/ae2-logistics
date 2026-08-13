package io.github.johnhamilto.ae2logistics.testplots;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredItem;

import appeng.api.parts.IPart;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.ItemDefinition;
import appeng.items.parts.PartItem;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.compat.CompatMods;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;
import io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart;

/**
 * Interactive scenes for AE2's {@code /ae2 setuptestworld}. AE2 scans every mod for
 * {@link TestPlotClass} and spawns each {@link TestPlot} method's build in the test
 * world grid. Ids are forced into the {@code ae2} namespace by {@code AppEng.makeId},
 * so values must be plain paths (a colon crashes the whole scan) - hence the
 * {@code logistics_} prefix.
 */
@TestPlotClass
public final class LogisticsTestPlots {

    private static final Identifier LEVEL = Identifier.parse("demo:level");
    private static final Identifier ALARM = Identifier.parse("demo:alarm");

    private LogisticsTestPlots() {
    }

    /** Our part items as AE2 {@link ItemDefinition}s, the currency {@link PlotBuilder} trades in. */
    private static <T extends IPart> ItemDefinition<PartItem<T>> def(DeferredItem<PartItem<T>> item) {
        return new ItemDefinition<>(item.getId().getPath(), item);
    }

    /**
     * Constant writes demo:level=500, threshold raises demo:alarm at >=100, the redstone
     * port emits the alarm onto a lamp. Edit the constant below 100 in its GUI and the
     * lamp goes dark. A Register Bank (right-click lists the signals) and a Logic Core
     * (the eight-entry list GUI) sit on the same run, so the whole signal family is
     * pokeable in one scene.
     */
    @TestPlot("logistics_signal_chain")
    public static void signalChain(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        plot.cable("1 0 0")
                .part(Direction.UP, def(AE2Logistics.CONSTANT_PART),
                        part -> part.applyConfig(LEVEL, null, null, 0, 500, 0, false))
                .part(Direction.NORTH, def(AE2Logistics.THRESHOLD_PART),
                        part -> part.applyConfig(ALARM, LEVEL, null, 4, 100, 0, false));
        plot.cable("2 0 0")
                .part(Direction.NORTH, def(AE2Logistics.REDSTONE_IO_PART),
                        part -> part.applyConfig(null, ALARM, null, 0, 0, 0, true));
        plot.block("2 0 -1", Blocks.REDSTONE_LAMP);
        plot.block("3 0 0", AE2Logistics.REGISTER_BANK.get());
        plot.block("4 0 0", AE2Logistics.LOGIC_CORE.get());
    }

    /**
     * A 2x2 trace panel wall pre-bound to demo:level, fed by the signal-chain style
     * constant: walk up and watch the sparkline crawl.
     */
    @TestPlot("logistics_trace_panels")
    public static void tracePanels(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        plot.cable("1 0 0").part(Direction.UP, def(AE2Logistics.CONSTANT_PART),
                part -> part.applyConfig(LEVEL, null, null, 0, 500, 0, false));
        for (var offset : new String[] {"2 0 0", "3 0 0", "2 1 0", "3 1 0"}) {
            plot.blockState(offset, AE2Logistics.TRACE_PANEL.get().defaultBlockState()
                    .setValue(io.github.johnhamilto.ae2logistics.block.TracePanelBlock.FACING,
                            Direction.NORTH));
        }
        plot.addPostInitAction((level, player, origin) -> {
            var pos = origin.offset(2, 0, 0);
            if (level.getBlockEntity(pos)
                    instanceof io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity panel) {
                panel.bind(LEVEL, false);
            }
        });
    }

    /**
     * Janitor demo: misplaced crafting tables sit in the unfiltered chest; the other
     * chest's bus is partitioned to tables at higher priority. Open the janitor,
     * press Rejigger, watch the stock re-settle.
     */
    @TestPlot("logistics_janitor")
    public static void janitor(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        plot.cable("1 0 0").part(Direction.NORTH, AEParts.STORAGE_BUS);
        plot.cable("2 0 0").part(Direction.NORTH, AEParts.STORAGE_BUS, bus -> {
            bus.setPriority(10);
            bus.getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1));
        });
        plot.block("3 0 0", AE2Logistics.STORAGE_JANITOR.get());
        plot.chest("1 0 -1", new ItemStack(Items.CRAFTING_TABLE, 40));
        plot.chest("2 0 -1");
    }

    /**
     * One backbone, two lanes. Item lane: a hopper feeds the typed input endpoint and
     * deliveries land in the output chests. ME lane: two typed ME endpoints fuse the
     * creative cells on their faces into one carried grid (inspect with a network tool).
     * The P2P Frequency Terminal on the riser lists both mesh frequencies; any endpoint
     * GUI shows its frequency's live roster - hover a row, click to locate in world.
     */
    @TestPlot("logistics_mesh_hub")
    public static void meshHub(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        plot.cable("0 1 0").part(Direction.NORTH, def(AE2Logistics.P2P_TERMINAL_PART));
        plot.cable("1 0 0").part(Direction.NORTH, def(AE2Logistics.MESH_ENDPOINT_ITEM_PART),
                part -> part.applyMeshConfig("item-demo", MeshEndpointPart.ROLE_IN, 0, 0));
        plot.cable("2 0 0").part(Direction.UP, def(AE2Logistics.MESH_ENDPOINT_ITEM_PART),
                part -> part.applyMeshConfig("item-demo", MeshEndpointPart.ROLE_OUT, 0, 0));
        plot.cable("3 0 0").part(Direction.UP, def(AE2Logistics.MESH_ENDPOINT_ITEM_PART),
                part -> part.applyMeshConfig("item-demo", MeshEndpointPart.ROLE_OUT, 0, 0));
        plot.filledHopper("1 0 -1", Direction.SOUTH, new ItemStack(Items.IRON_INGOT, 64));
        plot.chest("2 1 0");
        plot.chest("3 1 0");

        plot.cable("4 0 0").part(Direction.UP, def(AE2Logistics.MESH_ENDPOINT_ME_PART),
                part -> part.applyMeshConfig("me-demo", MeshEndpointPart.ROLE_BOTH, 0, 0));
        plot.cable("5 0 0").part(Direction.UP, def(AE2Logistics.MESH_ENDPOINT_ME_PART),
                part -> part.applyMeshConfig("me-demo", MeshEndpointPart.ROLE_BOTH, 0, 0));
        plot.creativeEnergyCell("4 1 0");
        plot.creativeEnergyCell("5 1 0");
    }

    /**
     * Provider-through-mesh hall with a full crafting loop: request charcoal at the
     * terminal, the provider pushes log batches through the mesh, each batch lands on
     * a DIFFERENT furnace because the provider runs BLOCKING MODE - provider settings
     * map through the mesh; flip it off in the provider GUI and pushes round-robin
     * without waiting. Import buses return the charcoal and the job completes. The chest holds
     * oak AND spruce logs - the adaptive pattern's tag input accepts either. Controller
     * hub because mesh endpoints require channels and the hall runs ten devices.
     */
    @TestPlot("logistics_provider_hall")
    public static void providerHall(PlotBuilder plot) {
        plot.block("2 0 1", AEBlocks.CONTROLLER);
        plot.creativeEnergyCell("2 1 1");
        plot.cable("1 0 1")
                .part(Direction.NORTH, AEParts.CRAFTING_TERMINAL)
                .part(Direction.WEST, AEParts.STORAGE_BUS);
        plot.chest("0 0 1", new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.SPRUCE_LOG, 32));
        plot.block("3 0 1", AEBlocks.CRAFTING_STORAGE_1K);
        // The provider's push face points at the mesh input, so its grid connection
        // comes from the cable path above.
        plot.block("2 0 3", AEBlocks.PATTERN_PROVIDER);
        plot.cable("2 1 2");
        plot.cable("2 1 3");
        plot.cable("2 0 2").part(Direction.SOUTH, def(AE2Logistics.MESH_ENDPOINT_PART),
                part -> part.applyMeshConfig("provider-demo", MeshEndpointPart.ROLE_IN, 0,
                        MeshRegistry.TYPE_PROVIDER));
        // Machines: outputs push DOWN into the furnaces' input slots, import buses
        // below pull the charcoal back into the network.
        plot.cable("1 1 2").part(Direction.DOWN, def(AE2Logistics.MESH_ENDPOINT_PART),
                part -> part.applyMeshConfig("provider-demo", MeshEndpointPart.ROLE_OUT, 0,
                        MeshRegistry.TYPE_PROVIDER));
        plot.cable("3 1 2").part(Direction.DOWN, def(AE2Logistics.MESH_ENDPOINT_PART),
                part -> part.applyMeshConfig("provider-demo", MeshEndpointPart.ROLE_OUT, 0,
                        MeshRegistry.TYPE_PROVIDER));
        plot.block("1 0 2", Blocks.FURNACE);
        plot.block("3 0 2", Blocks.FURNACE);
        plot.cable("2 -1 2");
        plot.cable("1 -1 2").part(Direction.UP, AEParts.IMPORT_BUS);
        plot.cable("3 -1 2").part(Direction.UP, AEParts.IMPORT_BUS);
        plot.customizeBlockEntity("2 0 3", AEBlockEntities.PATTERN_PROVIDER.get(), provider -> {
            provider.getLogic().getPatternInv().setItemDirect(0, logCharcoalPattern());
            provider.getLogic().getConfigManager().putSetting(
                    appeng.api.config.Settings.BLOCKING_MODE, appeng.api.config.YesNo.YES);
        });
        plot.customizeBlockEntity("1 0 2", BlockEntityType.FURNACE,
                furnace -> furnace.setItem(1, new ItemStack(Items.COAL, 64)));
        plot.customizeBlockEntity("3 0 2", BlockEntityType.FURNACE,
                furnace -> furnace.setItem(1, new ItemStack(Items.COAL, 64)));
    }

    /**
     * Subnet links: the left face powers a real subnet (interface comes online through
     * the link), the right face mounts a subnet storage bus so the main-side terminal
     * sees the chest's gold.
     */
    @TestPlot("logistics_subnet_links")
    public static void subnetLinks(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        plot.cable("1 0 0").part(Direction.UP, def(AE2Logistics.SUBNET_LINK_PART));
        plot.cable("1 1 0");
        plot.block("1 2 0", AEBlocks.INTERFACE);

        plot.cable("2 0 0");
        plot.cable("3 0 0")
                .part(Direction.UP, def(AE2Logistics.SUBNET_LINK_PART))
                .part(Direction.NORTH, AEParts.TERMINAL);
        plot.cable("3 1 0").part(Direction.NORTH, AEParts.STORAGE_BUS);
        plot.chest("3 1 -1", new ItemStack(Items.GOLD_INGOT, 7));
    }

    /**
     * Compat bed: an ExtendedAE provider virtualized through pre-linked provider
     * tunnels - drop a pattern into the Ex Pattern Provider and request. Builds a bare
     * sign pedestal instead when ExtendedAE is not in the dev runtime.
     */
    @TestPlot("logistics_compat_extendedae")
    public static void compatExtendedAe(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        if (!CompatMods.loaded(CompatMods.EXTENDED_AE)) {
            plot.block("1 0 0", Blocks.OAK_SIGN);
            return;
        }
        plot.cable("1 0 0").part(Direction.NORTH, def(AE2Logistics.PROVIDER_P2P_TUNNEL_PART));
        plot.cable("2 0 0").part(Direction.UP, def(AE2Logistics.PROVIDER_P2P_TUNNEL_PART));
        plot.chest("2 1 0");
        // The provider's grid connection loops past the cell; its push face is the tunnel.
        plot.cable("0 0 -1");
        plot.block("1 0 -1",
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("extendedae:ex_pattern_provider")));
        plot.addPostInitAction((level, player, origin) -> linkTunnels(level,
                origin.offset(1, 0, 0), Direction.NORTH, origin.offset(2, 0, 0), Direction.UP));
    }

    /** Links a placed tunnel pair the way a memory card would. */
    private static void linkTunnels(net.minecraft.server.level.ServerLevel level,
            BlockPos inputPos, Direction inputSide, BlockPos outputPos, Direction outputSide) {
        var input = tunnelAt(level, inputPos, inputSide);
        var output = tunnelAt(level, outputPos, outputSide);
        if (input == null || output == null) {
            return;
        }
        var grid = input.getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        var p2p = appeng.me.service.P2PService.get(grid);
        var freq = p2p.newFrequency();
        p2p.updateFreq(input, freq);
        var settings = net.minecraft.core.component.DataComponentMap.builder()
                .set(appeng.api.ids.AEComponents.EXPORTED_P2P_FREQUENCY, freq).build();
        output.importSettings(appeng.util.SettingsFrom.MEMORY_CARD, settings, null);
    }

    @Nullable
    private static ProviderP2PTunnelPart tunnelAt(net.minecraft.server.level.ServerLevel level,
            BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof appeng.api.parts.IPartHost host
                && host.getPart(side) instanceof ProviderP2PTunnelPart tunnel ? tunnel : null;
    }

    private static ItemStack logCharcoalPattern() {
        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(pattern,
                List.of(new GenericStack(AEItemKey.of(Items.OAK_LOG), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.CHARCOAL), 1)),
                List.of(AdaptiveInputSpec.ofTag(Identifier.parse("minecraft:logs"))));
        return pattern;
    }
}
