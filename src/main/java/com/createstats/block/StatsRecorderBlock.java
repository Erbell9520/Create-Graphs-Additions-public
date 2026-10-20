package com.createstats.block;

import com.createstats.antenna.AntennaAttachmentHelper;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.item.BlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.StatsRecorderBE;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StatsRecorderBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    public static final DirectionProperty FACING;

    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 12.0, 15.0);

    public StatsRecorderBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((this.stateDefinition.any()).setValue(StatsRecorderBlock.FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(StatsRecorderBlock::new);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { StatsRecorderBlock.FACING });
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(StatsRecorderBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsRecorderBE recorder) {
            ItemInteractionResult antennaResult = AntennaAttachmentHelper.handleUse(stack, level, player, hand, recorder);
            if (antennaResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return antennaResult;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                recorder.onPlayerInteract(serverPlayer, hand);
                return ItemInteractionResult.CONSUME;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof StatsRecorderBE recorder && recorder.getStore().getSampleCount() > 0) {
            for (ItemStack drop : drops) {
                if (!drop.isEmpty() && drop.getItem() instanceof BlockItem) {
                    recorder.saveToItem(drop, builder.getLevel().registryAccess());
                    break;
                }
            }
        }
        return drops;
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.stats_recorder");
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatsRecorderBE((BlockEntityType<?>)ModBlockEntities.STATS_RECORDER.get(), pos, state);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type != ModBlockEntities.STATS_RECORDER.get()) return null;
        return (lvl, pos, st, be) -> ((StatsRecorderBE) be).tick();
    }

    public boolean isSignalSource(BlockState state) {
        return true;
    }

    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsRecorderBE recorder) {
            return recorder.getRedstonePower();
        }
        return 0;
    }

    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsRecorderBE recorder) {
            final Direction facing = (Direction)state.getValue(StatsRecorderBlock.FACING);
            return (dir == facing) ? recorder.getRedstonePower() : 0;
        }
        return 0;
    }

    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsRecorderBE recorder) {
            return recorder.getAnalogOutputSignal();
        }
        return 0;
    }

    static {
        FACING = HorizontalDirectionalBlock.FACING;
    }
}
