package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** [1] */
public class SwitchBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** The second gang. Always false on a single gang switch. */
    public static final BooleanProperty POWERED_B = BooleanProperty.create("powered_b");

    private final VoxelShape upShape;
    private final boolean twoGang;

    public SwitchBlock(Properties properties, VoxelShape upShape, boolean twoGang) {
        super(properties);
        this.upShape = upShape;
        this.twoGang = twoGang;
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(POWERED_B, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, POWERED_B);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Points out of the face that was clicked, so it sits flush on a wall, floor or ceiling.
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LampShapes.rotate(upShape, state.getValue(FACING));
    }

    /** A switch needs something behind it, and falls off if that goes. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final Direction facing = state.getValue(FACING);
        final BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return canSurvive(state, level, pos)
                ? state
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final boolean secondGang = twoGang && isSecondGang(state, pos, hit.getLocation());
        final BooleanProperty gang = secondGang ? POWERED_B : POWERED;
        final boolean now = !state.getValue(gang);
        final BlockState next = state.setValue(gang, now);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f,
                now ? 0.6f : 0.5f);
        updateNeighbours(next, level, pos);
        onContactsMoved(level, pos);
        return InteractionResult.CONSUME;
    }

    /** [2] */
    private boolean isSecondGang(BlockState state, BlockPos pos, Vec3 hitPos) {
        final Direction facing = state.getValue(FACING);
        final Vec3 local = hitPos.subtract(pos.getX(), pos.getY(), pos.getZ());
        final Direction across = facing.getAxis() == Direction.Axis.Y
                ? Direction.EAST : facing.getClockWise();
        return switch (across.getAxis()) {
            case X -> local.x > 0.5;
            case Y -> local.y > 0.5;
            case Z -> local.z > 0.5;
        };
    }

    /** [3] */
    private void onContactsMoved(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof com.createstats.blockentity.SwitchBE be) {
            be.invalidateBranch();
            be.pushGateToLinked();
        }
    }

    @Override
    @Nullable
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.createstats.registry.ModBlockEntities.SWITCH.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
                    Level level, BlockState state,
                    net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || type != com.createstats.registry.ModBlockEntities.SWITCH.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((com.createstats.blockentity.SwitchBE) be).tick();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof com.createstats.blockentity.SwitchBE be) {
            be.invalidateBranch();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!twoGang) {
            return state.getValue(POWERED) ? 15 : 0;
        }
        // Each gang owns one side, which is what makes a double gang two circuits rather than a wide
        // rocker. Both also drive the block behind, via getDirectSignal below.
        final Direction facing = state.getValue(FACING);
        final Direction left = facing.getAxis() == Direction.Axis.Y ? Direction.WEST : facing.getCounterClockWise();
        final Direction right = left.getOpposite();
        // getSignal is asked from the neighbour's point of view, so the direction arrives reversed.
        final Direction asked = direction.getOpposite();
        if (asked == left) {
            return state.getValue(POWERED) ? 15 : 0;
        }
        if (asked == right) {
            return state.getValue(POWERED_B) ? 15 : 0;
        }
        return state.getValue(POWERED) || state.getValue(POWERED_B) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // Strong only into the block it is mounted on, exactly like a lever, so a switch on a wall
        // powers whatever is on the far side of that wall.
        final boolean on = state.getValue(POWERED) || state.getValue(POWERED_B);
        return on && state.getValue(FACING) == direction ? 15 : 0;
    }

    /** True when either gang is on, for the model to pick its pressed variant. */
    public static boolean isOn(BlockState state) {
        return state.getValue(POWERED) || state.getValue(POWERED_B);
    }
}
