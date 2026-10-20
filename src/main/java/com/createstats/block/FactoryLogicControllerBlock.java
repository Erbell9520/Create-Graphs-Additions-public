package com.createstats.block;

import com.createstats.antenna.AntennaAttachmentHelper;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;

import com.createstats.blockentity.FactoryLogicControllerBE;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FactoryLogicControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0),
            Block.box(0.0, 3.0, 0.0, 16.0, 5.0, 6.0),
            Block.box(0.0, 5.0, 2.0, 16.0, 15.0, 12.0),
            Block.box(2.0, 5.0, 12.0, 14.0, 14.0, 16.0),
            Block.box(0.0, 14.0, 5.0, 16.0, 16.0, 12.0));
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0),
            Block.box(0.0, 3.0, 10.0, 16.0, 5.0, 16.0),
            Block.box(0.0, 5.0, 4.0, 16.0, 15.0, 14.0),
            Block.box(2.0, 5.0, 0.0, 14.0, 14.0, 4.0),
            Block.box(0.0, 14.0, 4.0, 16.0, 16.0, 11.0));
    private static final VoxelShape SHAPE_EAST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0),
            Block.box(10.0, 3.0, 0.0, 16.0, 5.0, 16.0),
            Block.box(4.0, 5.0, 0.0, 14.0, 15.0, 16.0),
            Block.box(0.0, 5.0, 2.0, 4.0, 14.0, 14.0),
            Block.box(4.0, 14.0, 0.0, 11.0, 16.0, 16.0));
    private static final VoxelShape SHAPE_WEST = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0),
            Block.box(0.0, 3.0, 0.0, 6.0, 5.0, 16.0),
            Block.box(2.0, 5.0, 0.0, 12.0, 15.0, 16.0),
            Block.box(12.0, 5.0, 2.0, 16.0, 14.0, 14.0),
            Block.box(5.0, 14.0, 0.0, 12.0, 16.0, 16.0));

    public FactoryLogicControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(FactoryLogicControllerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getFacingShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getFacingShape(state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FactoryLogicControllerBE controller)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemInteractionResult antennaResult = AntennaAttachmentHelper.handleUse(stack, level, player, hand, controller);
        if (antennaResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return antennaResult;
        }
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(controller, pos);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FactoryLogicControllerBE controller) {
                controller.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactoryLogicControllerBE(ModBlockEntities.FACTORY_LOGIC_CONTROLLER.get(), pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FACTORY_LOGIC_CONTROLLER.get()) {
            return null;
        }
        return (lvl, pos, blockState, blockEntity) -> ((FactoryLogicControllerBE) blockEntity).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.factory_logic_controller");
    }

    private static VoxelShape getFacingShape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }
}
