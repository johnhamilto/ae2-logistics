package io.github.johnhamilto.ae2logistics.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public class WirelessBridgeBlock extends Block implements EntityBlock {

    public WirelessBridgeBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessBridgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide || type != AE2Logistics.WIRELESS_BRIDGE_BE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> ((WirelessBridgeBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        var anchor = stack.get(AE2Logistics.BRIDGE_ANCHOR.get());
        if (anchor != null && !level.isClientSide
                && level.getBlockEntity(pos) instanceof WirelessBridgeBlockEntity bridge) {
            bridge.setAnchor(anchor);
        }
    }
}
