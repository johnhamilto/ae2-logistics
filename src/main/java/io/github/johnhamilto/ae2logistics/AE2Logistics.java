package io.github.johnhamilto.ae2logistics;

import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridServices;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEKeyTypes;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;

import io.github.johnhamilto.ae2logistics.block.PatternWorkbenchBlock;
import io.github.johnhamilto.ae2logistics.block.PatternWorkbenchBlockEntity;
import io.github.johnhamilto.ae2logistics.block.RegisterBankBlock;
import io.github.johnhamilto.ae2logistics.block.RegisterBankBlockEntity;
import io.github.johnhamilto.ae2logistics.client.AE2LogisticsClient;
import io.github.johnhamilto.ae2logistics.command.SignalCommands;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;
import io.github.johnhamilto.ae2logistics.crafting.EncodedAdaptivePattern;
import io.github.johnhamilto.ae2logistics.item.SignalCardItem;
import io.github.johnhamilto.ae2logistics.menu.ConfigureMeshPayload;
import io.github.johnhamilto.ae2logistics.menu.ConfigurePartPayload;
import io.github.johnhamilto.ae2logistics.menu.MeshEndpointMenu;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.menu.CyclePatternSpecPayload;
import io.github.johnhamilto.ae2logistics.menu.LogicPartMenu;
import io.github.johnhamilto.ae2logistics.menu.PatternWorkbenchMenu;
import io.github.johnhamilto.ae2logistics.menu.P2PActionPayload;
import io.github.johnhamilto.ae2logistics.menu.P2PDataPayload;
import io.github.johnhamilto.ae2logistics.menu.P2PFrequencyTerminalMenu;
import io.github.johnhamilto.ae2logistics.menu.SelectTracerChannelPayload;
import io.github.johnhamilto.ae2logistics.menu.TracerDataPayload;
import io.github.johnhamilto.ae2logistics.menu.TracerTerminalMenu;
import io.github.johnhamilto.ae2logistics.parts.ArithmeticPart;
import io.github.johnhamilto.ae2logistics.parts.BooleanPart;
import io.github.johnhamilto.ae2logistics.parts.ConstantPart;
import io.github.johnhamilto.ae2logistics.parts.CounterPart;
import io.github.johnhamilto.ae2logistics.parts.HysteresisPart;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;
import io.github.johnhamilto.ae2logistics.parts.RatePart;
import io.github.johnhamilto.ae2logistics.parts.RedstoneIOPart;
import io.github.johnhamilto.ae2logistics.parts.P2PFrequencyTerminalPart;
import io.github.johnhamilto.ae2logistics.parts.StockSensorPart;
import io.github.johnhamilto.ae2logistics.parts.ThresholdPart;
import io.github.johnhamilto.ae2logistics.parts.TimerPart;
import io.github.johnhamilto.ae2logistics.parts.TracerTerminalPart;
import io.github.johnhamilto.ae2logistics.signal.SignalCardContainerStrategy;
import io.github.johnhamilto.ae2logistics.signal.SignalGridService;
import io.github.johnhamilto.ae2logistics.signal.SignalKey;
import io.github.johnhamilto.ae2logistics.signal.SignalKeyType;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

@Mod(AE2Logistics.MOD_ID)
public class AE2Logistics {

    public static final String MOD_ID = "ae2logistics";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister
            .create(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
            .create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    /** Per cable-bus block entity: part side name -> P2P frequency name. See {@code P2PNames}. */
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.Map<String, String>>> P2P_NAMES =
            ATTACHMENTS.register("p2p_names",
                    () -> net.neoforged.neoforge.attachment.AttachmentType
                            .<java.util.Map<String, String>>builder(() -> new java.util.HashMap<>())
                            .serialize(
                                    com.mojang.serialization.Codec
                                            .unboundedMap(com.mojang.serialization.Codec.STRING,
                                                    com.mojang.serialization.Codec.STRING)
                                            .xmap(java.util.HashMap::new, java.util.HashMap::new),
                                    map -> !map.isEmpty())
                            .build());

    public static final DeferredBlock<RegisterBankBlock> REGISTER_BANK = BLOCKS.register("register_bank",
            () -> new RegisterBankBlock(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> REGISTER_BANK_ITEM = ITEMS.registerSimpleBlockItem(REGISTER_BANK);
    public static final Supplier<BlockEntityType<RegisterBankBlockEntity>> REGISTER_BANK_BE = BLOCK_ENTITIES
            .register("register_bank", () -> BlockEntityType.Builder
                    .of(RegisterBankBlockEntity::new, REGISTER_BANK.get()).build(null));

    public static final Supplier<DataComponentType<ResourceLocation>> SIGNAL_CHANNEL = DATA_COMPONENTS
            .register("signal_channel", () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .build());

    public static final DeferredItem<SignalCardItem> SIGNAL_CARD = ITEMS.register("signal_card",
            () -> new SignalCardItem(new Item.Properties().stacksTo(1)));

    public static final Supplier<DataComponentType<EncodedAdaptivePattern>> ENCODED_ADAPTIVE_PATTERN = DATA_COMPONENTS
            .register("encoded_adaptive_pattern", () -> DataComponentType.<EncodedAdaptivePattern>builder()
                    .persistent(EncodedAdaptivePattern.CODEC)
                    .networkSynchronized(EncodedAdaptivePattern.STREAM_CODEC)
                    .build());

    public static final DeferredItem<Item> ADAPTIVE_PATTERN = ITEMS.register("adaptive_processing_pattern",
            () -> PatternDetailsHelper.encodedPatternItemBuilder(AdaptivePattern::new)
                    .invalidPatternTooltip(AdaptivePattern::getInvalidPatternTooltip)
                    .build());

    public static final DeferredItem<Item> GUIDE_TABLET = ITEMS.register("guide_tablet",
            () -> new io.github.johnhamilto.ae2logistics.item.GuideTabletItem(new Item.Properties().stacksTo(1)));

    /** The mod's themed resource: forms when charged certus, redstone, and glowstone meet water. */
    public static final DeferredItem<Item> REGULUS_CRYSTAL = ITEMS.register("regulus_crystal",
            () -> new Item(new Item.Properties()));

    public static final Supplier<DataComponentType<io.github.johnhamilto.ae2logistics.crafting.GuardedPatternData>> GUARDED_PATTERN_DATA =
            DATA_COMPONENTS.register("guarded_pattern",
                    () -> DataComponentType.<io.github.johnhamilto.ae2logistics.crafting.GuardedPatternData>builder()
                            .persistent(io.github.johnhamilto.ae2logistics.crafting.GuardedPatternData.CODEC)
                            .networkSynchronized(io.github.johnhamilto.ae2logistics.crafting.GuardedPatternData.STREAM_CODEC)
                            .build());

    public static final DeferredItem<Item> GUARDED_PATTERN = ITEMS.register("guarded_pattern",
            () -> PatternDetailsHelper
                    .encodedPatternItemBuilder(io.github.johnhamilto.ae2logistics.crafting.GuardedPattern::new)
                    .invalidPatternTooltip(
                            io.github.johnhamilto.ae2logistics.crafting.GuardedPattern::getInvalidPatternTooltip)
                    .build());

    // Memory-card payloads: settings our parts export beyond AE2's generic ones.
    public static final Supplier<DataComponentType<net.minecraft.nbt.CompoundTag>> EXPORTED_LOGIC_SETTINGS =
            DATA_COMPONENTS.register("exported_logic_settings",
                    () -> DataComponentType.<net.minecraft.nbt.CompoundTag>builder()
                            .persistent(net.minecraft.nbt.CompoundTag.CODEC).build());
    public static final Supplier<DataComponentType<appeng.api.stacks.GenericStack>> EXPORTED_WATCHED_KEY =
            DATA_COMPONENTS.register("exported_watched_key",
                    () -> DataComponentType.<appeng.api.stacks.GenericStack>builder()
                            .persistent(appeng.api.stacks.GenericStack.CODEC).build());
    public static final Supplier<DataComponentType<net.minecraft.nbt.CompoundTag>> EXPORTED_MESH_SETTINGS =
            DATA_COMPONENTS.register("exported_mesh_settings",
                    () -> DataComponentType.<net.minecraft.nbt.CompoundTag>builder()
                            .persistent(net.minecraft.nbt.CompoundTag.CODEC).build());
    public static final Supplier<DataComponentType<java.util.List<appeng.api.stacks.GenericStack>>> EXPORTED_MESH_FILTER =
            DATA_COMPONENTS.register("exported_mesh_filter",
                    () -> DataComponentType.<java.util.List<appeng.api.stacks.GenericStack>>builder()
                            .persistent(appeng.api.stacks.GenericStack.FAULT_TOLERANT_NULLABLE_LIST_CODEC)
                            .build());

    public static final DeferredBlock<PatternWorkbenchBlock> PATTERN_WORKBENCH = BLOCKS.register(
            "pattern_workbench",
            () -> new PatternWorkbenchBlock(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> PATTERN_WORKBENCH_ITEM = ITEMS
            .registerSimpleBlockItem(PATTERN_WORKBENCH);
    public static final Supplier<BlockEntityType<PatternWorkbenchBlockEntity>> PATTERN_WORKBENCH_BE = BLOCK_ENTITIES
            .register("pattern_workbench", () -> BlockEntityType.Builder
                    .of(PatternWorkbenchBlockEntity::new, PATTERN_WORKBENCH.get()).build(null));

    public static final Supplier<MenuType<PatternWorkbenchMenu>> PATTERN_WORKBENCH_MENU = MENUS.register(
            "pattern_workbench", () -> IMenuTypeExtension.create(PatternWorkbenchMenu::new));

    public static final DeferredBlock<io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlock> GUARDED_PROVIDER =
            BLOCKS.register("guarded_pattern_provider",
                    () -> new io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlock(
                            BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> GUARDED_PROVIDER_ITEM = ITEMS
            .registerSimpleBlockItem(GUARDED_PROVIDER);
    public static final Supplier<BlockEntityType<io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity>> GUARDED_PROVIDER_BE =
            BLOCK_ENTITIES.register("guarded_pattern_provider", () -> BlockEntityType.Builder
                    .of(io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity::new,
                            GUARDED_PROVIDER.get())
                    .build(null));
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.GuardedProviderMenu>> GUARDED_PROVIDER_MENU =
            MENUS.register("guarded_pattern_provider", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.GuardedProviderMenu::new));

    public static final DeferredItem<PartItem<ConstantPart>> CONSTANT_PART = part(
            "constant", ConstantPart.class, ConstantPart::new);
    public static final DeferredItem<PartItem<ThresholdPart>> THRESHOLD_PART = part(
            "threshold", ThresholdPart.class, ThresholdPart::new);
    public static final DeferredItem<PartItem<HysteresisPart>> HYSTERESIS_PART = part(
            "hysteresis", HysteresisPart.class, HysteresisPart::new);
    public static final DeferredItem<PartItem<ArithmeticPart>> ARITHMETIC_PART = part(
            "arithmetic", ArithmeticPart.class, ArithmeticPart::new);
    public static final DeferredItem<PartItem<BooleanPart>> LOGIC_GATE_PART = part(
            "logic_gate", BooleanPart.class, BooleanPart::new);
    public static final DeferredItem<PartItem<RedstoneIOPart>> REDSTONE_IO_PART = part(
            "redstone_port", RedstoneIOPart.class, RedstoneIOPart::new);
    public static final DeferredItem<PartItem<StockSensorPart>> STOCK_SENSOR_PART = part(
            "stock_sensor", StockSensorPart.class, StockSensorPart::new);
    public static final DeferredItem<PartItem<RatePart>> RATE_PART = part(
            "rate", RatePart.class, RatePart::new);
    public static final DeferredItem<PartItem<CounterPart>> COUNTER_PART = part(
            "counter", CounterPart.class, CounterPart::new);
    public static final DeferredItem<PartItem<TimerPart>> TIMER_PART = part(
            "timer", TimerPart.class, TimerPart::new);
    public static final DeferredItem<PartItem<TracerTerminalPart>> TRACER_TERMINAL_PART = part(
            "tracer_terminal", TracerTerminalPart.class, TracerTerminalPart::new);

    public static final Supplier<MenuType<TracerTerminalMenu>> TRACER_TERMINAL_MENU = MENUS.register(
            "tracer_terminal", () -> IMenuTypeExtension.create(TracerTerminalMenu::new));

    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.JobMonitorPart>> JOB_MONITOR_PART =
            part("job_monitor", io.github.johnhamilto.ae2logistics.parts.JobMonitorPart.class,
                    io.github.johnhamilto.ae2logistics.parts.JobMonitorPart::new);
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.JobMonitorMenu>> JOB_MONITOR_MENU =
            MENUS.register("job_monitor", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.JobMonitorMenu::new));

    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.QueryTerminalPart>> QUERY_TERMINAL_PART =
            part("query_terminal", io.github.johnhamilto.ae2logistics.parts.QueryTerminalPart.class,
                    io.github.johnhamilto.ae2logistics.parts.QueryTerminalPart::new);
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.QueryTerminalMenu>> QUERY_TERMINAL_MENU =
            MENUS.register("query_terminal", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.QueryTerminalMenu::new));
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.QuerySensorPart>> QUERY_SENSOR_PART =
            part("query_sensor", io.github.johnhamilto.ae2logistics.parts.QuerySensorPart.class,
                    io.github.johnhamilto.ae2logistics.parts.QuerySensorPart::new);
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.QuerySensorMenu>> QUERY_SENSOR_MENU =
            MENUS.register("query_sensor", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.QuerySensorMenu::new));
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.QueryExportBusPart>> QUERY_EXPORT_BUS_PART =
            part("query_export_bus", io.github.johnhamilto.ae2logistics.parts.QueryExportBusPart.class,
                    io.github.johnhamilto.ae2logistics.parts.QueryExportBusPart::new);
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.QueryExportBusMenu>> QUERY_EXPORT_BUS_MENU =
            MENUS.register("query_export_bus", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.QueryExportBusMenu::new));

    public static final DeferredItem<PartItem<P2PFrequencyTerminalPart>> P2P_TERMINAL_PART = part(
            "p2p_frequency_terminal", P2PFrequencyTerminalPart.class, P2PFrequencyTerminalPart::new);
    public static final DeferredItem<PartItem<MeshEndpointPart>> MESH_ENDPOINT_PART = part(
            "mesh_endpoint", MeshEndpointPart.class, MeshEndpointPart::new);
    public static final Supplier<MenuType<MeshEndpointMenu>> MESH_ENDPOINT_MENU = MENUS.register(
            "mesh_endpoint", () -> IMenuTypeExtension.create(MeshEndpointMenu::new));
    public static final Supplier<MenuType<P2PFrequencyTerminalMenu>> P2P_TERMINAL_MENU = MENUS.register(
            "p2p_frequency_terminal", () -> IMenuTypeExtension.create(P2PFrequencyTerminalMenu::new));

    public static final Supplier<MenuType<LogicPartMenu>> LOGIC_PART_MENU = MENUS.register("logic_part",
            () -> IMenuTypeExtension.create(LogicPartMenu::new));

    public static final Supplier<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2logistics"))
                    .icon(() -> REGISTER_BANK_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(REGISTER_BANK_ITEM.get());
                        output.accept(PATTERN_WORKBENCH_ITEM.get());
                        output.accept(GUARDED_PROVIDER_ITEM.get());
                        output.accept(SIGNAL_CARD.get());
                        output.accept(CONSTANT_PART.get());
                        output.accept(THRESHOLD_PART.get());
                        output.accept(HYSTERESIS_PART.get());
                        output.accept(ARITHMETIC_PART.get());
                        output.accept(LOGIC_GATE_PART.get());
                        output.accept(REDSTONE_IO_PART.get());
                        output.accept(STOCK_SENSOR_PART.get());
                        output.accept(RATE_PART.get());
                        output.accept(COUNTER_PART.get());
                        output.accept(TIMER_PART.get());
                        output.accept(TRACER_TERMINAL_PART.get());
                        output.accept(JOB_MONITOR_PART.get());
                        output.accept(QUERY_TERMINAL_PART.get());
                        output.accept(QUERY_SENSOR_PART.get());
                        output.accept(QUERY_EXPORT_BUS_PART.get());
                        output.accept(P2P_TERMINAL_PART.get());
                        output.accept(MESH_ENDPOINT_PART.get());
                        output.accept(REGULUS_CRYSTAL.get());
                        output.accept(GUIDE_TABLET.get());
                    })
                    .build());

    private static <T extends IPart> DeferredItem<PartItem<T>> part(String id, Class<T> partClass,
            Function<IPartItem<T>, T> factory) {
        PartModels.registerModels(PartModelsHelper.createModels(partClass));
        return ITEMS.register(id, () -> new PartItem<>(new Item.Properties(), partClass, factory));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public AE2Logistics(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DATA_COMPONENTS.register(modBus);
        CREATIVE_TABS.register(modBus);
        MENUS.register(modBus);
        ATTACHMENTS.register(modBus);

        modBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                AEKeyTypes.register(SignalKeyType.TYPE);
            }
        });

        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, REGISTER_BANK_BE.get(), (be, context) -> be);
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, GUARDED_PROVIDER_BE.get(), (be, context) -> be);
        });

        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("1");
            registrar.playToServer(ConfigurePartPayload.TYPE, ConfigurePartPayload.STREAM_CODEC,
                    ConfigurePartPayload::handle);
            registrar.playToServer(CyclePatternSpecPayload.TYPE, CyclePatternSpecPayload.STREAM_CODEC,
                    CyclePatternSpecPayload::handle);
            registrar.playToServer(SelectTracerChannelPayload.TYPE, SelectTracerChannelPayload.STREAM_CODEC,
                    SelectTracerChannelPayload::handle);
            registrar.playToClient(TracerDataPayload.TYPE, TracerDataPayload.STREAM_CODEC,
                    TracerDataPayload::handle);
            registrar.playToServer(P2PActionPayload.TYPE, P2PActionPayload.STREAM_CODEC,
                    P2PActionPayload::handle);
            registrar.playToClient(P2PDataPayload.TYPE, P2PDataPayload.STREAM_CODEC,
                    P2PDataPayload::handle);
            registrar.playToServer(ConfigureMeshPayload.TYPE, ConfigureMeshPayload.STREAM_CODEC,
                    ConfigureMeshPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.ConfigureJobMonitorPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureJobMonitorPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureJobMonitorPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.WrapPatternPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.WrapPatternPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.WrapPatternPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.ConfigureGuardPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureGuardPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureGuardPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.QueryEditPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.QueryEditPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.QueryEditPayload::handle);
            registrar.playToClient(io.github.johnhamilto.ae2logistics.menu.QueryPreviewPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.QueryPreviewPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.QueryPreviewPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.ConfigureQueryPartPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureQueryPartPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureQueryPartPayload::handle);
        });

        modBus.addListener((appeng.api.parts.RegisterPartCapabilitiesEvent event) -> {
            event.register(AECapabilities.ME_STORAGE,
                    (part, context) -> part.exposedMeStorage(), MeshEndpointPart.class);
            event.register(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    (part, context) -> part.exposedItemHandler(), MeshEndpointPart.class);
            event.register(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    (part, context) -> part.exposedFluidHandler(), MeshEndpointPart.class);
            event.register(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    (part, context) -> part.exposedEnergyHandler(), MeshEndpointPart.class);
        });

        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                MeshRegistry.tick(event.getServer().getTickCount()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                MeshRegistry.clear());

        ContainerItemStrategy.register(SignalKeyType.TYPE, SignalKey.class, new SignalCardContainerStrategy());

        GridServices.register(SignalService.class, SignalGridService.class);
        GridServices.register(io.github.johnhamilto.ae2logistics.query.QueryService.class,
                io.github.johnhamilto.ae2logistics.query.QueryGridService.class);

        NeoForge.EVENT_BUS.addListener(SignalCommands::register);
        NeoForge.EVENT_BUS.addListener(io.github.johnhamilto.ae2logistics.command.MeshCommands::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            AE2LogisticsClient.initialize(modBus);
        }

        LOG.info("AE2 Logistics initialized");
    }
}
