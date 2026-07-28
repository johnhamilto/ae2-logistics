package io.github.johnhamilto.ae2logistics.item;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.parts.IPartHost;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex;

/**
 * A multi-block memory card. Click two corners to capture every AE2-based device in the
 * box (relative position, part side, type, full memory-card settings); sneak-click the
 * rebuilt region's minimum corner to reapply everything to matching devices. Sneak-use
 * in the air to clear.
 */
public class ConfigBlueprintItem extends Item {

    private static final int MAX_VOLUME = 4096;

    public record Entry(BlockPos rel, byte side, String typeId, DataComponentMap settings) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                BlockPos.CODEC.fieldOf("rel").forGetter(Entry::rel),
                Codec.BYTE.fieldOf("side").forGetter(Entry::side),
                Codec.STRING.fieldOf("type").forGetter(Entry::typeId),
                DataComponentMap.CODEC.fieldOf("settings").forGetter(Entry::settings))
                .apply(builder, Entry::new));

        public static final Codec<List<Entry>> LIST_CODEC = CODEC.listOf();
    }

    public ConfigBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        var stack = context.getItemInHand();
        var clicked = context.getClickedPos();

        if (player.isShiftKeyDown()) {
            var entries = stack.get(AE2Logistics.BLUEPRINT_DATA.get());
            if (entries == null || entries.isEmpty()) {
                player.displayClientMessage(Component.literal("Blueprint is empty"), true);
                return InteractionResult.SUCCESS;
            }
            if (!ConfigDeviceIndex.mayEdit(player, clicked)) {
                player.displayClientMessage(Component.literal("No permission here"), true);
                return InteractionResult.SUCCESS;
            }
            var result = apply(serverLevel, clicked, entries, player);
            player.displayClientMessage(Component.literal(
                    "Applied " + result[0] + "/" + result[1] + " device configs"), true);
            return InteractionResult.SUCCESS;
        }

        var corner = stack.get(AE2Logistics.BLUEPRINT_CORNER.get());
        if (corner == null) {
            stack.set(AE2Logistics.BLUEPRINT_CORNER.get(), clicked);
            player.displayClientMessage(Component.literal(
                    "Corner set - click the opposite corner"), true);
            return InteractionResult.SUCCESS;
        }
        var entries = capture(serverLevel, corner, clicked);
        stack.remove(AE2Logistics.BLUEPRINT_CORNER.get());
        stack.set(AE2Logistics.BLUEPRINT_DATA.get(), entries);
        player.displayClientMessage(Component.literal(
                "Captured " + entries.size() + " device configs; sneak-click the min corner to apply"),
                true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !level.isClientSide) {
            stack.remove(AE2Logistics.BLUEPRINT_CORNER.get());
            stack.remove(AE2Logistics.BLUEPRINT_DATA.get());
            player.displayClientMessage(Component.literal("Blueprint cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Captures all AE2-based devices in the box; positions relative to the min corner. */
    public static List<Entry> capture(ServerLevel level, BlockPos a, BlockPos b) {
        var min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        var max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        var entries = new ArrayList<Entry>();
        int volume = 0;
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (++volume > MAX_VOLUME) {
                break;
            }
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                continue;
            }
            var rel = pos.immutable().subtract(min);
            if (blockEntity instanceof io.github.johnhamilto.ae2logistics.config.TransferableSettings transferable) {
                var typeId = blockTypeId(level, pos);
                if (typeId != null) {
                    entries.add(new Entry(rel, (byte) 6, typeId,
                            transferable.exportTransferSettings(null)));
                }
            } else if (blockEntity instanceof AEBaseBlockEntity aeBlockEntity) {
                var typeId = blockTypeId(level, pos);
                if (typeId != null) {
                    entries.add(new Entry(rel, (byte) 6, typeId,
                            aeBlockEntity.exportSettings(SettingsFrom.MEMORY_CARD, null)));
                }
            }
            if (blockEntity instanceof IPartHost host) {
                for (var direction : Direction.values()) {
                    if (host.getPart(direction) instanceof AEBasePart part) {
                        entries.add(new Entry(rel, (byte) direction.ordinal(),
                                BuiltInRegistries.ITEM.getKey(part.getPartItem().asItem()).toString(),
                                part.exportSettings(SettingsFrom.MEMORY_CARD)));
                    }
                }
            }
        }
        return entries;
    }

    /** Applies entries at anchor (the region's min corner); returns {applied, total}. */
    public static int[] apply(ServerLevel level, BlockPos anchor, List<Entry> entries,
            @Nullable Player player) {
        int applied = 0;
        for (var entry : entries) {
            var target = anchor.offset(entry.rel());
            var blockEntity = level.getBlockEntity(target);
            if (entry.side() == 6) {
                if (blockEntity instanceof io.github.johnhamilto.ae2logistics.config.TransferableSettings transferable
                        && entry.typeId().equals(blockTypeId(level, target))) {
                    transferable.importTransferSettings(entry.settings(), player);
                    applied++;
                } else if (blockEntity instanceof AEBaseBlockEntity aeBlockEntity
                        && entry.typeId().equals(blockTypeId(level, target))) {
                    aeBlockEntity.importSettings(SettingsFrom.MEMORY_CARD, entry.settings(), player);
                    applied++;
                }
            } else if (blockEntity instanceof IPartHost host && entry.side() < 6) {
                var part = host.getPart(Direction.values()[entry.side()]);
                if (part instanceof AEBasePart aePart
                        && BuiltInRegistries.ITEM.getKey(aePart.getPartItem().asItem()).toString()
                                .equals(entry.typeId())) {
                    aePart.importSettings(SettingsFrom.MEMORY_CARD, entry.settings(), player);
                    applied++;
                }
            }
        }
        return new int[] {applied, entries.size()};
    }

    @Nullable
    private static String blockTypeId(ServerLevel level, BlockPos pos) {
        var item = level.getBlockState(pos).getBlock().asItem();
        if (item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        var entries = stack.get(AE2Logistics.BLUEPRINT_DATA.get());
        if (entries != null) {
            tooltip.add(Component.literal(entries.size() + " device configs")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        } else if (stack.has(AE2Logistics.BLUEPRINT_CORNER.get())) {
            tooltip.add(Component.literal("Corner set - click the opposite corner")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Click two corners to capture device configs")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
