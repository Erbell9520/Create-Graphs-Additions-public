package com.createstats.block;

import com.createstats.antenna.AntennaAttachmentHelper;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;
import com.createstats.blockentity.StatsAlarmBE;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class StatsAlarmBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    public static final DirectionProperty FACING;
    public static final BooleanProperty ACTIVE;

    public StatsAlarmBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(((this.stateDefinition.any()).setValue(StatsAlarmBlock.FACING, Direction.NORTH)).setValue(StatsAlarmBlock.ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(StatsAlarmBlock::new);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { StatsAlarmBlock.FACING, StatsAlarmBlock.ACTIVE });
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(StatsAlarmBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    public boolean isSignalSource(BlockState state) {
        return true;
    }

    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(StatsAlarmBlock.ACTIVE)) {
            return 0;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsAlarmBE alarm) {
            return alarm.getAlarmSettings().isRedstoneEnabled() ? 15 : 0;
        }
        return 0;
    }

    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.getSignal(state, level, pos, direction);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsAlarmBE alarm) {
            return AntennaAttachmentHelper.handleUse(stack, level, player, hand, alarm);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatsAlarmBE((BlockEntityType<?>)ModBlockEntities.STATS_ALARM.get(), pos, state);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.STATS_ALARM.get()) {
            return null;
        }
        return (BlockEntityTicker<T>)((lvl, pos, st, be) -> ((StatsAlarmBE)be).tick());
    }

    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean wasActive = !level.isClientSide() && !state.is(newState.getBlock()) && state.getValue(ACTIVE);
        super.onRemove(state, level, pos, newState, isMoving);
        if (wasActive) {
            level.updateNeighborsAt(pos, this);
        }
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.stats_alarm");
    }

    static {
        FACING = HorizontalDirectionalBlock.FACING;
        ACTIVE = BooleanProperty.create("active");
    }
}
