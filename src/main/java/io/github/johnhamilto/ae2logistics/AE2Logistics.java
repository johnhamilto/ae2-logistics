package io.github.johnhamilto.ae2logistics;

import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import appeng.api.stacks.AEKeyTypes;
import appeng.items.parts.PartItem;

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
import io.github.johnhamilto.ae2logistics.menu.GhostSlotPayload;
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
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> CONDITION_CODECS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS, MOD_ID);

    public static final Supplier<com.mojang.serialization.MapCodec<DevOnlyCondition>> DEV_ONLY_CONDITION =
            CONDITION_CODECS.register("dev_only", () -> DevOnlyCondition.CODEC);

    /** Per cable-bus block entity: part side name -> P2P frequency name. See {@code P2PNames}. */
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.HashMap<String, String>>> P2P_NAMES =
            ATTACHMENTS.register("p2p_names",
                    () -> net.neoforged.neoforge.attachment.AttachmentType
                            .<java.util.HashMap<String, String>>builder(() -> new java.util.HashMap<>())
                            .serialize(
                                    com.mojang.serialization.Codec
                                            .unboundedMap(com.mojang.serialization.Codec.STRING,
                                                    com.mojang.serialization.Codec.STRING)
                                            .xmap(java.util.HashMap::new, java.util.HashMap::new)
                                            .fieldOf("names"),
                                    map -> !map.isEmpty())
                            .build());

    public static final DeferredBlock<RegisterBankBlock> REGISTER_BANK = BLOCKS.registerBlock("register_bank", RegisterBankBlock::new,
            () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> REGISTER_BANK_ITEM = ITEMS.registerSimpleBlockItem(REGISTER_BANK);
    public static final Supplier<BlockEntityType<RegisterBankBlockEntity>> REGISTER_BANK_BE = BLOCK_ENTITIES
            .register("register_bank", () -> new BlockEntityType<>(RegisterBankBlockEntity::new, REGISTER_BANK.get()));

    public static final DeferredBlock<io.github.johnhamilto.ae2logistics.block.StorageJanitorBlock> STORAGE_JANITOR =
            BLOCKS.registerBlock("storage_janitor", io.github.johnhamilto.ae2logistics.block.StorageJanitorBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> STORAGE_JANITOR_ITEM = ITEMS.registerSimpleBlockItem(STORAGE_JANITOR);
    public static final Supplier<BlockEntityType<io.github.johnhamilto.ae2logistics.block.StorageJanitorBlockEntity>> STORAGE_JANITOR_BE =
            BLOCK_ENTITIES.register("storage_janitor", () -> new BlockEntityType<>(io.github.johnhamilto.ae2logistics.block.StorageJanitorBlockEntity::new,
                            STORAGE_JANITOR.get()));
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.StorageJanitorMenu>> STORAGE_JANITOR_MENU =
            MENUS.register("storage_janitor", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.StorageJanitorMenu::new));

    public static final DeferredBlock<io.github.johnhamilto.ae2logistics.block.TracePanelBlock> TRACE_PANEL =
            BLOCKS.registerBlock("trace_panel", io.github.johnhamilto.ae2logistics.block.TracePanelBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> TRACE_PANEL_ITEM = ITEMS.registerSimpleBlockItem(TRACE_PANEL);
    public static final Supplier<BlockEntityType<io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity>> TRACE_PANEL_BE =
            BLOCK_ENTITIES.register("trace_panel", () -> new BlockEntityType<>(io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity::new,
                            TRACE_PANEL.get()));
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.TracePanelMenu>> TRACE_PANEL_MENU =
            MENUS.register("trace_panel", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.TracePanelMenu::new));

    public static final Supplier<DataComponentType<Identifier>> SIGNAL_CHANNEL = DATA_COMPONENTS
            .register("signal_channel", () -> DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC)
                    .build());

    public static final DeferredItem<SignalCardItem> SIGNAL_CARD = ITEMS.registerItem("signal_card", SignalCardItem::new,
            () -> new Item.Properties().stacksTo(1));

    // Storage bus input cards (DESIGN F12); associations registered in common setup.
    public static final DeferredItem<Item> CONFORM_CARD = ITEMS.registerItem("conform_card",
            appeng.api.upgrades.Upgrades::createUpgradeCardItem);
    public static final DeferredItem<Item> STACK_LIMITER_CARD = ITEMS.registerItem("stack_limiter_card",
            appeng.api.upgrades.Upgrades::createUpgradeCardItem);

    /** Installed in terminal upgrade slots: see {@link io.github.johnhamilto.ae2logistics.item.PatternImportCard}. */
    public static final DeferredItem<Item> PATTERN_IMPORT_CARD = ITEMS.registerItem("pattern_import_card",
            appeng.api.upgrades.Upgrades::createUpgradeCardItem);

    /** Config slots become variant templates: see {@link io.github.johnhamilto.ae2logistics.parts.VariantMatching}. */
    public static final DeferredItem<Item> VARIANT_CARD = ITEMS.registerItem("variant_card",
            appeng.api.upgrades.Upgrades::createUpgradeCardItem);

    /** Bound query name on a Query Card. */
    public static final Supplier<DataComponentType<String>> QUERY_NAME = DATA_COMPONENTS
            .register("query_name", () -> DataComponentType.<String>builder()
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
                    .build());

    /** The bus partition as live query membership: see {@link io.github.johnhamilto.ae2logistics.item.QueryCardItem}. */
    public static final DeferredItem<io.github.johnhamilto.ae2logistics.item.QueryCardItem> QUERY_CARD =
            ITEMS.registerItem("query_card", properties -> new io.github.johnhamilto.ae2logistics.item.QueryCardItem(
                    properties.stacksTo(1)));


    public static final Supplier<DataComponentType<EncodedAdaptivePattern>> ENCODED_ADAPTIVE_PATTERN = DATA_COMPONENTS
            .register("encoded_adaptive_pattern", () -> DataComponentType.<EncodedAdaptivePattern>builder()
                    .persistent(EncodedAdaptivePattern.CODEC)
                    .networkSynchronized(EncodedAdaptivePattern.STREAM_CODEC)
                    .build());

    public static final DeferredItem<Item> ADAPTIVE_PATTERN = ITEMS.registerItem("adaptive_processing_pattern",
            properties -> PatternDetailsHelper.encodedPatternItemBuilder(AdaptivePattern::new)
                    .invalidPatternTooltip(AdaptivePattern::getInvalidPatternTooltip)
                    .build(properties));

    /** The mod's themed resource: forms when charged certus, redstone, and glowstone meet water. */
    public static final DeferredItem<Item> REGULUS_CRYSTAL = ITEMS.registerItem("regulus_crystal", Item::new);

    public static final Supplier<DataComponentType<io.github.johnhamilto.ae2logistics.crafting.GuardedPatternData>> GUARDED_PATTERN_DATA =
            DATA_COMPONENTS.register("guarded_pattern",
                    () -> DataComponentType.<io.github.johnhamilto.ae2logistics.crafting.GuardedPatternData>builder()
                            .persistent(io.github.johnhamilto.ae2logistics.crafting.GuardedPatternData.CODEC)
                            .networkSynchronized(io.github.johnhamilto.ae2logistics.crafting.GuardedPatternData.STREAM_CODEC)
                            .build());

    public static final Supplier<DataComponentType<net.minecraft.core.BlockPos>> BLUEPRINT_CORNER =
            DATA_COMPONENTS.register("blueprint_corner",
                    () -> DataComponentType.<net.minecraft.core.BlockPos>builder()
                            .persistent(net.minecraft.core.BlockPos.CODEC).build());
    public static final Supplier<DataComponentType<java.util.List<io.github.johnhamilto.ae2logistics.item.ConfigBlueprintItem.Entry>>> BLUEPRINT_DATA =
            DATA_COMPONENTS.register("blueprint_data",
                    () -> DataComponentType.<java.util.List<io.github.johnhamilto.ae2logistics.item.ConfigBlueprintItem.Entry>>builder()
                            .persistent(io.github.johnhamilto.ae2logistics.item.ConfigBlueprintItem.Entry.LIST_CODEC)
                            .build());
    public static final DeferredItem<Item> CONFIG_BLUEPRINT = ITEMS.registerItem("config_blueprint", io.github.johnhamilto.ae2logistics.item.ConfigBlueprintItem::new,
            () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> GUARDED_PATTERN = ITEMS.registerItem("guarded_pattern",
            properties -> PatternDetailsHelper
                    .encodedPatternItemBuilder(io.github.johnhamilto.ae2logistics.crafting.GuardedPattern::new)
                    .invalidPatternTooltip(
                            io.github.johnhamilto.ae2logistics.crafting.GuardedPattern::getInvalidPatternTooltip)
                    .build(properties));

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

    public static final DeferredBlock<PatternWorkbenchBlock> PATTERN_WORKBENCH = BLOCKS.registerBlock(
            "pattern_workbench", PatternWorkbenchBlock::new,
            () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> PATTERN_WORKBENCH_ITEM = ITEMS
            .registerSimpleBlockItem(PATTERN_WORKBENCH);
    public static final Supplier<BlockEntityType<PatternWorkbenchBlockEntity>> PATTERN_WORKBENCH_BE = BLOCK_ENTITIES
            .register("pattern_workbench", () -> new BlockEntityType<>(PatternWorkbenchBlockEntity::new, PATTERN_WORKBENCH.get()));

    public static final Supplier<MenuType<PatternWorkbenchMenu>> PATTERN_WORKBENCH_MENU = MENUS.register(
            "pattern_workbench", () -> IMenuTypeExtension.create(PatternWorkbenchMenu::new));

    public static final DeferredBlock<io.github.johnhamilto.ae2logistics.block.JobSchedulerBlock> JOB_SCHEDULER =
            BLOCKS.registerBlock("job_scheduler", io.github.johnhamilto.ae2logistics.block.JobSchedulerBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> JOB_SCHEDULER_ITEM = ITEMS
            .registerSimpleBlockItem(JOB_SCHEDULER);
    public static final Supplier<BlockEntityType<io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity>> JOB_SCHEDULER_BE =
            BLOCK_ENTITIES.register("job_scheduler", () -> new BlockEntityType<>(io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity::new,
                            JOB_SCHEDULER.get()));
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.JobSchedulerMenu>> JOB_SCHEDULER_MENU =
            MENUS.register("job_scheduler", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.JobSchedulerMenu::new));

    public static final DeferredBlock<io.github.johnhamilto.ae2logistics.block.LogicCoreBlock> LOGIC_CORE =
            BLOCKS.registerBlock("logic_core", io.github.johnhamilto.ae2logistics.block.LogicCoreBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> LOGIC_CORE_ITEM = ITEMS
            .registerSimpleBlockItem(LOGIC_CORE);
    public static final Supplier<BlockEntityType<io.github.johnhamilto.ae2logistics.block.LogicCoreBlockEntity>> LOGIC_CORE_BE =
            BLOCK_ENTITIES.register("logic_core", () -> new BlockEntityType<>(io.github.johnhamilto.ae2logistics.block.LogicCoreBlockEntity::new,
                            LOGIC_CORE.get()));
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.LogicCoreMenu>> LOGIC_CORE_MENU =
            MENUS.register("logic_core", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.LogicCoreMenu::new));

    /** Where a placed Wireless Bridge should look for its network: an access point position. */
    public static final Supplier<DataComponentType<net.minecraft.core.GlobalPos>> BRIDGE_ANCHOR =
            DATA_COMPONENTS.register("bridge_anchor",
                    () -> DataComponentType.<net.minecraft.core.GlobalPos>builder()
                            .persistent(net.minecraft.core.GlobalPos.CODEC).build());

    public static final DeferredBlock<io.github.johnhamilto.ae2logistics.block.DenseWapBlock> DENSE_WAP =
            BLOCKS.registerBlock("dense_wireless_access_point", io.github.johnhamilto.ae2logistics.block.DenseWapBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> DENSE_WAP_ITEM = ITEMS
            .registerSimpleBlockItem(DENSE_WAP);
    public static final Supplier<BlockEntityType<io.github.johnhamilto.ae2logistics.block.DenseWapBlockEntity>> DENSE_WAP_BE =
            BLOCK_ENTITIES.register("dense_wireless_access_point", () -> new BlockEntityType<>(io.github.johnhamilto.ae2logistics.block.DenseWapBlockEntity::new,
                            DENSE_WAP.get()));

    public static final DeferredBlock<io.github.johnhamilto.ae2logistics.block.WirelessBridgeBlock> WIRELESS_BRIDGE =
            BLOCKS.registerBlock("wireless_bridge", io.github.johnhamilto.ae2logistics.block.WirelessBridgeBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<io.github.johnhamilto.ae2logistics.item.WirelessBridgeItem> WIRELESS_BRIDGE_ITEM =
            ITEMS.registerItem("wireless_bridge",
                    properties -> new io.github.johnhamilto.ae2logistics.item.WirelessBridgeItem(WIRELESS_BRIDGE.get(), properties));
    public static final Supplier<BlockEntityType<io.github.johnhamilto.ae2logistics.block.WirelessBridgeBlockEntity>> WIRELESS_BRIDGE_BE =
            BLOCK_ENTITIES.register("wireless_bridge", () -> new BlockEntityType<>(io.github.johnhamilto.ae2logistics.block.WirelessBridgeBlockEntity::new,
                            WIRELESS_BRIDGE.get()));

    public static final DeferredBlock<io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlock> GUARDED_PROVIDER =
            BLOCKS.registerBlock("guarded_pattern_provider", io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> GUARDED_PROVIDER_ITEM = ITEMS
            .registerSimpleBlockItem(GUARDED_PROVIDER);
    public static final Supplier<BlockEntityType<io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity>> GUARDED_PROVIDER_BE =
            BLOCK_ENTITIES.register("guarded_pattern_provider", () -> new BlockEntityType<>(io.github.johnhamilto.ae2logistics.block.GuardedPatternProviderBlockEntity::new,
                            GUARDED_PROVIDER.get()));
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
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.ConfigTerminalPart>> CONFIG_TERMINAL_PART =
            part("config_terminal", io.github.johnhamilto.ae2logistics.parts.ConfigTerminalPart.class,
                    io.github.johnhamilto.ae2logistics.parts.ConfigTerminalPart::new);
    public static final Supplier<MenuType<io.github.johnhamilto.ae2logistics.menu.ConfigTerminalMenu>> CONFIG_TERMINAL_MENU =
            MENUS.register("config_terminal", () -> IMenuTypeExtension
                    .create(io.github.johnhamilto.ae2logistics.menu.ConfigTerminalMenu::new));
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
    // Typed variants share the part class; MeshEndpointPart locks their mask by item id.
    public static final DeferredItem<PartItem<MeshEndpointPart>> MESH_ENDPOINT_REDSTONE_PART = part(
            "mesh_endpoint_redstone", MeshEndpointPart.class, MeshEndpointPart::new);
    public static final DeferredItem<PartItem<MeshEndpointPart>> MESH_ENDPOINT_ITEM_PART = part(
            "mesh_endpoint_item", MeshEndpointPart.class, MeshEndpointPart::new);
    public static final DeferredItem<PartItem<MeshEndpointPart>> MESH_ENDPOINT_FLUID_PART = part(
            "mesh_endpoint_fluid", MeshEndpointPart.class, MeshEndpointPart::new);
    public static final DeferredItem<PartItem<MeshEndpointPart>> MESH_ENDPOINT_ENERGY_PART = part(
            "mesh_endpoint_energy", MeshEndpointPart.class, MeshEndpointPart::new);
    public static final DeferredItem<PartItem<MeshEndpointPart>> MESH_ENDPOINT_SIGNAL_PART = part(
            "mesh_endpoint_signal", MeshEndpointPart.class, MeshEndpointPart::new);
    public static final DeferredItem<PartItem<MeshEndpointPart>> MESH_ENDPOINT_ME_PART = part(
            "mesh_endpoint_me", MeshEndpointPart.class, MeshEndpointPart::new);
    public static final DeferredItem<PartItem<MeshEndpointPart>> MESH_ENDPOINT_PROVIDER_PART = part(
            "mesh_endpoint_provider", MeshEndpointPart.class, MeshEndpointPart::new);
    // AE2's locator-based menu plumbing, so sub-menus (the priority picker) can
    // reopen this menu and vice versa via SwitchGuisPacket.
    public static final Supplier<MenuType<MeshEndpointMenu>> MESH_ENDPOINT_MENU = MENUS.register(
            "mesh_endpoint", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(MeshEndpointMenu::new, MeshEndpointPart.class)
                    .withInitialData(MeshEndpointMenu::writeOpenData,
                            (host, menu, buffer) -> menu.readInitialData(buffer))
                    .buildUnregistered(id("mesh_endpoint")));
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart>> PROVIDER_P2P_TUNNEL_PART = part(
            "provider_p2p_tunnel", io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart.class,
            io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart::new);
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart>> SUBNET_LINK_PART = part(
            "subnet_link", io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart.class,
            io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart::new);
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.WirelessConnectorPart>> WIRELESS_CONNECTOR_PART = part(
            "wireless_connector", io.github.johnhamilto.ae2logistics.parts.WirelessConnectorPart.class,
            io.github.johnhamilto.ae2logistics.parts.WirelessConnectorPart::new);
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.GatedStorageBusPart>> GATED_STORAGE_BUS_PART = part(
            "gated_storage_bus", io.github.johnhamilto.ae2logistics.parts.GatedStorageBusPart.class,
            io.github.johnhamilto.ae2logistics.parts.GatedStorageBusPart::new);
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.VariantImportBusPart>> VARIANT_IMPORT_BUS_PART = part(
            "variant_import_bus", io.github.johnhamilto.ae2logistics.parts.VariantImportBusPart.class,
            io.github.johnhamilto.ae2logistics.parts.VariantImportBusPart::new);
    public static final DeferredItem<PartItem<io.github.johnhamilto.ae2logistics.parts.VariantExportBusPart>> VARIANT_EXPORT_BUS_PART = part(
            "variant_export_bus", io.github.johnhamilto.ae2logistics.parts.VariantExportBusPart.class,
            io.github.johnhamilto.ae2logistics.parts.VariantExportBusPart::new);
    // AE2's storage bus menu under our own type, so the window titles as a Subnet Link.
    public static final Supplier<MenuType<appeng.menu.implementations.StorageBusMenu>> SUBNET_LINK_MENU =
            MENUS.register("subnet_link", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(appeng.menu.implementations.StorageBusMenu::new,
                            io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart.class)
                    .buildUnregistered(id("subnet_link")));
    public static final Supplier<MenuType<appeng.menu.implementations.StorageBusMenu>> GATED_STORAGE_BUS_MENU =
            MENUS.register("gated_storage_bus", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(appeng.menu.implementations.StorageBusMenu::new,
                            io.github.johnhamilto.ae2logistics.parts.GatedStorageBusPart.class)
                    .buildUnregistered(id("gated_storage_bus")));
    // AE2's IO bus menu under our own types, so the windows title as variant buses.
    public static final Supplier<MenuType<appeng.menu.implementations.IOBusMenu>> VARIANT_IMPORT_BUS_MENU =
            MENUS.register("variant_import_bus", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(appeng.menu.implementations.IOBusMenu::new,
                            io.github.johnhamilto.ae2logistics.parts.VariantImportBusPart.class)
                    .buildUnregistered(id("variant_import_bus")));
    public static final Supplier<MenuType<appeng.menu.implementations.IOBusMenu>> VARIANT_EXPORT_BUS_MENU =
            MENUS.register("variant_export_bus", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(appeng.menu.implementations.IOBusMenu::new,
                            io.github.johnhamilto.ae2logistics.parts.VariantExportBusPart.class)
                    .buildUnregistered(id("variant_export_bus")));
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
                        if (!FMLEnvironment.isProduction()) {
                            output.accept(STORAGE_JANITOR_ITEM.get());
                            output.accept(TRACE_PANEL_ITEM.get());
                        }
                        output.accept(PATTERN_WORKBENCH_ITEM.get());
                        output.accept(GUARDED_PROVIDER_ITEM.get());
                        output.accept(JOB_SCHEDULER_ITEM.get());
                        output.accept(LOGIC_CORE_ITEM.get());
                        output.accept(DENSE_WAP_ITEM.get());
                        output.accept(WIRELESS_BRIDGE_ITEM.get());
                        output.accept(WIRELESS_CONNECTOR_PART.get());
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
                        output.accept(CONFIG_TERMINAL_PART.get());
                        output.accept(CONFIG_BLUEPRINT.get());
                        output.accept(P2P_TERMINAL_PART.get());
                        output.accept(PROVIDER_P2P_TUNNEL_PART.get());
                        output.accept(MESH_ENDPOINT_REDSTONE_PART.get());
                        output.accept(MESH_ENDPOINT_ITEM_PART.get());
                        output.accept(MESH_ENDPOINT_FLUID_PART.get());
                        output.accept(MESH_ENDPOINT_ENERGY_PART.get());
                        output.accept(MESH_ENDPOINT_SIGNAL_PART.get());
                        output.accept(MESH_ENDPOINT_ME_PART.get());
                        output.accept(MESH_ENDPOINT_PROVIDER_PART.get());
                        output.accept(MESH_ENDPOINT_PART.get());
                        output.accept(SUBNET_LINK_PART.get());
                        output.accept(GATED_STORAGE_BUS_PART.get());
                        output.accept(VARIANT_IMPORT_BUS_PART.get());
                        output.accept(VARIANT_EXPORT_BUS_PART.get());
                        output.accept(CONFORM_CARD.get());
                        output.accept(STACK_LIMITER_CARD.get());
                        output.accept(VARIANT_CARD.get());
                        output.accept(QUERY_CARD.get());
                        output.accept(PATTERN_IMPORT_CARD.get());
                        output.accept(REGULUS_CRYSTAL.get());
                    })
                    .build());

    private static <T extends IPart> DeferredItem<PartItem<T>> part(String id, Class<T> partClass,
            Function<IPartItem<T>, T> factory) {
        // 26.1: part models are client-side data now; the @PartModels scan is gone.
        return ITEMS.registerItem(id, properties -> new PartItem<>(properties, partClass, factory));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public AE2Logistics(IEventBus modBus, net.neoforged.fml.ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                AE2LogisticsConfig.SPEC);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DATA_COMPONENTS.register(modBus);
        CREATIVE_TABS.register(modBus);
        MENUS.register(modBus);
        ATTACHMENTS.register(modBus);
        CONDITION_CODECS.register(modBus);

        modBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                AEKeyTypes.register(SignalKeyType.TYPE);
            } else if (event.getRegistryKey().equals(Registries.TEST_INSTANCE_TYPE)) {
                event.register(Registries.TEST_INSTANCE_TYPE, id("logistics_test"),
                        () -> io.github.johnhamilto.ae2logistics.gametest.LogisticsTestInstance.CODEC);
            }
        });
        modBus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event) ->
                io.github.johnhamilto.ae2logistics.gametest.LogisticsTestInstance.registerAll(event));

        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, REGISTER_BANK_BE.get(), (be, context) -> be);
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, GUARDED_PROVIDER_BE.get(), (be, context) -> be);
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, JOB_SCHEDULER_BE.get(), (be, context) -> be);
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, LOGIC_CORE_BE.get(), (be, context) -> be);
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, DENSE_WAP_BE.get(), (be, context) -> be);
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, WIRELESS_BRIDGE_BE.get(), (be, context) -> be);
        });

        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("1");
            registrar.playToServer(ConfigurePartPayload.TYPE, ConfigurePartPayload.STREAM_CODEC,
                    ConfigurePartPayload::handle);
            registrar.playToServer(GhostSlotPayload.TYPE, GhostSlotPayload.STREAM_CODEC,
                    GhostSlotPayload::handle);
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
            registrar.playToClient(io.github.johnhamilto.ae2logistics.menu.MeshRosterPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.MeshRosterPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.MeshRosterPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.MeshRetunePayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.MeshRetunePayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.MeshRetunePayload::handle);
            registrar.playToClient(io.github.johnhamilto.ae2logistics.menu.JobBoardPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.JobBoardPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.JobBoardPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.JanitorTogglePayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.JanitorTogglePayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.JanitorTogglePayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.TracePanelActionPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.TracePanelActionPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.TracePanelActionPayload::handle);
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
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.ConfigTerminalActionPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.ConfigTerminalActionPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.ConfigTerminalActionPayload::handle);
            registrar.playToClient(io.github.johnhamilto.ae2logistics.menu.ConfigTerminalDataPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.ConfigTerminalDataPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.ConfigTerminalDataPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.ConfigureSchedulerPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureSchedulerPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureSchedulerPayload::handle);
            registrar.playToServer(io.github.johnhamilto.ae2logistics.menu.ConfigureCoreEntryPayload.TYPE,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureCoreEntryPayload.STREAM_CODEC,
                    io.github.johnhamilto.ae2logistics.menu.ConfigureCoreEntryPayload::handle);
        });

        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> {
            event.enqueueWork(() -> {
                appeng.api.features.P2PTunnelAttunement
                        .registerAttunementTag(PROVIDER_P2P_TUNNEL_PART.get());
                // Upgrade-card associations: slot validation checks these, so without
                // them a card physically cannot be inserted. Both our bus-family parts
                // mirror the stock storage bus card set, plus the two input cards.
                for (var bus : java.util.List.of(SUBNET_LINK_PART, GATED_STORAGE_BUS_PART)) {
                    appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.FUZZY_CARD, bus, 1);
                    appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.INVERTER_CARD, bus, 1);
                    appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.CAPACITY_CARD, bus, 5);
                    appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.VOID_CARD, bus, 1);
                    appeng.api.upgrades.Upgrades.add(CONFORM_CARD, bus, 1);
                    appeng.api.upgrades.Upgrades.add(STACK_LIMITER_CARD, bus, 1);
                    appeng.api.upgrades.Upgrades.add(VARIANT_CARD, bus, 1);
                    appeng.api.upgrades.Upgrades.add(QUERY_CARD, bus, 1);
                }
                // The variant IO buses mirror the stock IO bus card sets, plus the
                // Variant Card that gives them their name.
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.FUZZY_CARD, VARIANT_IMPORT_BUS_PART, 1);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.REDSTONE_CARD, VARIANT_IMPORT_BUS_PART, 1);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.CAPACITY_CARD, VARIANT_IMPORT_BUS_PART, 5);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.SPEED_CARD, VARIANT_IMPORT_BUS_PART, 4);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.INVERTER_CARD, VARIANT_IMPORT_BUS_PART, 1);
                appeng.api.upgrades.Upgrades.add(VARIANT_CARD, VARIANT_IMPORT_BUS_PART, 1);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.FUZZY_CARD, VARIANT_EXPORT_BUS_PART, 1);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.REDSTONE_CARD, VARIANT_EXPORT_BUS_PART, 1);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.CAPACITY_CARD, VARIANT_EXPORT_BUS_PART, 5);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.SPEED_CARD, VARIANT_EXPORT_BUS_PART, 4);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.CRAFTING_CARD, VARIANT_EXPORT_BUS_PART, 1);
                appeng.api.upgrades.Upgrades.add(VARIANT_CARD, VARIANT_EXPORT_BUS_PART, 1);
                // AE2WTLib terminals have real upgrade slots; the import card installs
                // there as a normal upgrade (the cable part has none - see PatternImportCard).
                if (io.github.johnhamilto.ae2logistics.compat.CompatMods
                        .loaded(io.github.johnhamilto.ae2logistics.compat.CompatMods.AE2WTLIB)) {
                    for (var id : new String[] {"ae2wtlib:wireless_pattern_encoding_terminal",
                            "ae2wtlib:wireless_universal_terminal"}) {
                        appeng.api.upgrades.Upgrades.add(PATTERN_IMPORT_CARD,
                                net.minecraft.core.registries.BuiltInRegistries.ITEM
                                        .getValue(Identifier.parse(id)), 1);
                    }
                }
            });
        });

        modBus.addListener((appeng.api.parts.RegisterPartCapabilitiesEvent event) -> {
            event.register(AECapabilities.ME_STORAGE,
                    (part, context) -> part.exposedStorage(),
                    io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart.class);
            event.register(AECapabilities.GENERIC_INTERNAL_INV,
                    (part, context) -> part.exposedReturnGenericInv(),
                    io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart.class);
            event.register(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    (part, context) -> part.exposedReturnItemHandler(),
                    io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart.class);
            event.register(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                    (part, context) -> part.exposedReturnFluidHandler(),
                    io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart.class);
            event.register(AECapabilities.ME_STORAGE,
                    (part, context) -> part.exposedMeStorage(), MeshEndpointPart.class);
            event.register(AECapabilities.GENERIC_INTERNAL_INV,
                    (part, context) -> part.exposedReturnGenericInv(), MeshEndpointPart.class);
            event.register(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                    (part, context) -> part.exposedItemHandler(), MeshEndpointPart.class);
            event.register(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                    (part, context) -> part.exposedFluidHandler(), MeshEndpointPart.class);
            event.register(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                    (part, context) -> part.exposedEnergyHandler(), MeshEndpointPart.class);
            // AppMekReturns (guarded chemical bridge) lives on main only until the
            // compat suite has 26.1 ports.
        });

        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            MeshRegistry.tick(event.getServer().getTickCount());
            io.github.johnhamilto.ae2logistics.wireless.WirelessLinkRegistry.tick();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
            MeshRegistry.clear();
            io.github.johnhamilto.ae2logistics.wireless.WirelessLinkRegistry.clear();
        });

        ContainerItemStrategy.register(SignalKeyType.TYPE, SignalKey.class, new SignalCardContainerStrategy());

        GridServices.register(SignalService.class, SignalGridService.class);
        GridServices.register(io.github.johnhamilto.ae2logistics.query.QueryService.class,
                io.github.johnhamilto.ae2logistics.query.QueryGridService.class);

        NeoForge.EVENT_BUS.addListener(io.github.johnhamilto.ae2logistics.item.PatternImportCard::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SignalCommands::register);
        NeoForge.EVENT_BUS.addListener(io.github.johnhamilto.ae2logistics.command.MeshCommands::register);
        NeoForge.EVENT_BUS.addListener(io.github.johnhamilto.ae2logistics.command.QueryCommands::register);
        NeoForge.EVENT_BUS.addListener(io.github.johnhamilto.ae2logistics.command.WirelessCommands::register);
        NeoForge.EVENT_BUS.addListener(io.github.johnhamilto.ae2logistics.command.TestWorldCommands::register);
        NeoForge.EVENT_BUS.addListener(io.github.johnhamilto.ae2logistics.command.JanitorCommands::register);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            AE2LogisticsClient.initialize(modBus);
        }

        LOG.info("AE2 Logistics initialized");
    }
}
