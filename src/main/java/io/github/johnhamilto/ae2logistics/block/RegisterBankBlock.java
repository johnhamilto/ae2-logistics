package io.github.johnhamilto.ae2logistics.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import io.github.johnhamilto.ae2logistics.item.SignalCardItem;

public class RegisterBankBlock extends Block implements EntityBlock {

    public RegisterBankBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RegisterBankBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        var channel = SignalCardItem.getChannel(stack);
        if (channel == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RegisterBankBlockEntity bank) {
            var value = bank.getSignal(channel);
            player.sendOverlayMessage(Component.literal(channel + " = " + value));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RegisterBankBlockEntity bank) {
            var signals = bank.signals();
            if (signals.isEmpty()) {
                player.sendSystemMessage(Component.literal("No signals on this network"));
            } else {
                for (var entry : signals.entrySet()) {
                    player.sendSystemMessage(
                            Component.literal(entry.getKey() + " = " + entry.getValue()));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
