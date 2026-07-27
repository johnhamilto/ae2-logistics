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
import io.github.johnhamilto.ae2logistics.menu.ConfigurePartPayload;
import io.github.johnhamilto.ae2logistics.menu.CyclePatternSpecPayload;
import io.github.johnhamilto.ae2logistics.menu.LogicPartMenu;
import io.github.johnhamilto.ae2logistics.menu.PatternWorkbenchMenu;
import io.github.johnhamilto.ae2logistics.parts.ArithmeticPart;
import io.github.johnhamilto.ae2logistics.parts.BooleanPart;
import io.github.johnhamilto.ae2logistics.parts.ConstantPart;
import io.github.johnhamilto.ae2logistics.parts.CounterPart;
import io.github.johnhamilto.ae2logistics.parts.HysteresisPart;
import io.github.johnhamilto.ae2logistics.parts.RatePart;
import io.github.johnhamilto.ae2logistics.parts.RedstoneIOPart;
import io.github.johnhamilto.ae2logistics.parts.StockSensorPart;
import io.github.johnhamilto.ae2logistics.parts.ThresholdPart;
import io.github.johnhamilto.ae2logistics.parts.TimerPart;
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
            () -> PatternDetailsHelper.encodedPatternItemBuilder(AdaptivePattern::new).build());

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

    public static final Supplier<MenuType<LogicPartMenu>> LOGIC_PART_MENU = MENUS.register("logic_part",
            () -> IMenuTypeExtension.create(LogicPartMenu::new));

    public static final Supplier<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2logistics"))
                    .icon(() -> REGISTER_BANK_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(REGISTER_BANK_ITEM.get());
                        output.accept(PATTERN_WORKBENCH_ITEM.get());
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

        modBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                AEKeyTypes.register(SignalKeyType.TYPE);
            }
        });

        modBus.addListener((RegisterCapabilitiesEvent event) -> event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST, REGISTER_BANK_BE.get(), (be, context) -> be));

        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("1");
            registrar.playToServer(ConfigurePartPayload.TYPE, ConfigurePartPayload.STREAM_CODEC,
                    ConfigurePartPayload::handle);
            registrar.playToServer(CyclePatternSpecPayload.TYPE, CyclePatternSpecPayload.STREAM_CODEC,
                    CyclePatternSpecPayload::handle);
        });

        ContainerItemStrategy.register(SignalKeyType.TYPE, SignalKey.class, new SignalCardContainerStrategy());

        GridServices.register(SignalService.class, SignalGridService.class);

        NeoForge.EVENT_BUS.addListener(SignalCommands::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            AE2LogisticsClient.initialize(modBus);
        }

        LOG.info("AE2 Logistics initialized");
    }
}
