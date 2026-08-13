package io.github.johnhamilto.ae2logistics.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.item.SignalCardItem;

/**
 * A trace panel: same-facing panels placed in a rectangle merge into one in-world
 * dashboard. Click with a bound Signal Card to add that channel's trace (sneak to
 * remove it); sneak with an empty hand clears the panel.
 */
public class TracePanelBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public TracePanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TracePanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != AE2Logistics.TRACE_PANEL_BE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> ((TracePanelBlockEntity) be).serverTick();
    }

    /** Placement and removal re-form every panel in the plane neighborhood. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
            boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        reformNeighborhood(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        boolean gone = !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (gone) {
            reformNeighborhood(level, pos);
        }
    }

    private static void reformNeighborhood(Level level, BlockPos center) {
        if (level.isClientSide()) {
            return;
        }
        int reach = TracePanelBlockEntity.MAX_EDGE;
        for (var pos : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach),
                center.offset(reach, reach, reach))) {
            if (level.getBlockEntity(pos) instanceof TracePanelBlockEntity panel) {
                panel.reformGroup();
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        var channel = SignalCardItem.getChannel(stack);
        if (channel == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TracePanelBlockEntity panel) {
            boolean remove = player.isShiftKeyDown();
            boolean applied = panel.bind(channel, remove);
            player.sendOverlayMessage(Component.literal(applied
                    ? (remove ? "Trace removed: " : "Trace added: ") + channel
                    : (remove ? "Not bound: " : "Panel full or already bound: ") + channel));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TracePanelBlockEntity panel) {
            if (player.isShiftKeyDown()) {
                panel.clearBindings();
                player.sendOverlayMessage(Component.literal("Panel cleared"));
            } else {
                var bound = panel.boundChannels();
                player.sendOverlayMessage(Component.literal(bound.isEmpty()
                        ? "No traces bound - click with a bound Signal Card"
                        : "Traces: " + bound));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
