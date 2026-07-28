package io.github.johnhamilto.ae2logistics.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.SubnetCoreMenu;

public class SubnetCoreBlock extends Block implements EntityBlock {

    public SubnetCoreBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SubnetCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide || type != AE2Logistics.SUBNET_CORE_BE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> ((SubnetCoreBlockEntity) be).serverTick();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SubnetCoreBlockEntity core) {
            core.onNeighborChanged();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SubnetCoreBlockEntity core) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new SubnetCoreMenu(id, inventory, core),
                            Component.translatable("block.ae2logistics.subnet_core")),
                    buffer -> SubnetCoreMenu.writeOpenData(buffer, core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
