package io.github.johnhamilto.ae2logistics;

import java.util.function.Supplier;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.stacks.AEKeyTypes;

import io.github.johnhamilto.ae2logistics.block.RegisterBankBlock;
import io.github.johnhamilto.ae2logistics.block.RegisterBankBlockEntity;
import io.github.johnhamilto.ae2logistics.client.SignalRenderer;
import io.github.johnhamilto.ae2logistics.command.SignalCommands;
import io.github.johnhamilto.ae2logistics.item.SignalCardItem;
import io.github.johnhamilto.ae2logistics.signal.SignalCardContainerStrategy;
import io.github.johnhamilto.ae2logistics.signal.SignalKey;
import io.github.johnhamilto.ae2logistics.signal.SignalKeyType;

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

    public static final Supplier<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2logistics"))
                    .icon(() -> REGISTER_BANK_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(REGISTER_BANK_ITEM.get());
                        output.accept(SIGNAL_CARD.get());
                    })
                    .build());

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public AE2Logistics(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DATA_COMPONENTS.register(modBus);
        CREATIVE_TABS.register(modBus);

        modBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                AEKeyTypes.register(SignalKeyType.TYPE);
            }
        });

        modBus.addListener((RegisterCapabilitiesEvent event) -> event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST, REGISTER_BANK_BE.get(), (be, context) -> be));

        ContainerItemStrategy.register(SignalKeyType.TYPE, SignalKey.class, new SignalCardContainerStrategy());

        NeoForge.EVENT_BUS.addListener(SignalCommands::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            SignalRenderer.initialize(modBus);
        }

        LOG.info("AE2 Logistics initialized");
    }
}
