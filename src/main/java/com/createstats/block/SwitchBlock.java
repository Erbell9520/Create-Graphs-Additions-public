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

public class SwitchBlock extends Block implements net.minecraft.world.level.block.EntityBlock,
        GirderMounting.Fixture {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final BooleanProperty POWERED_B = BooleanProperty.create("powered_b");

    public enum Click {
        ROCKER(SoundEvents.LEVER_CLICK, 0.34f, 0.62f, 0.52f),

        DOLLY(SoundEvents.LEVER_CLICK, 0.30f, 1.45f, 1.20f),

        ISOLATOR(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.30f, 0.85f, 0.70f),

        STOP(SoundEvents.STONE_BUTTON_CLICK_ON, 0.42f, 0.62f, 0.90f);

        private final net.minecraft.sounds.SoundEvent sound;
        private final float volume;
        private final float pitchOn;
        private final float pitchOff;

        Click(net.minecraft.sounds.SoundEvent sound, float volume, float pitchOn, float pitchOff) {
            this.sound = sound;
            this.volume = volume;
            this.pitchOn = pitchOn;
            this.pitchOff = pitchOff;
        }

        void play(Level level, BlockPos pos, boolean on) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, on ? pitchOn : pitchOff);
        }
    }

    private final VoxelShape upShape;
    private final boolean twoGang;
    private final Click click;

    public SwitchBlock(Properties properties, VoxelShape upShape, boolean twoGang, Click click) {
        super(properties);
        this.upShape = upShape;
        this.twoGang = twoGang;
        this.click = click;
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
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LampShapes.rotate(upShape, state.getValue(FACING));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final Direction facing = state.getValue(FACING);
        return GirderMounting.holds(level, pos.relative(facing.getOpposite()), facing);
    }

    @Override
    public boolean isBackedBy(BlockState state, Direction towardSupport) {
        return state.getValue(FACING) == towardSupport.getOpposite();
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
        setContacts(level, pos, state, gang, !state.getValue(gang));
        return InteractionResult.CONSUME;
    }

    protected final void setContacts(Level level, BlockPos pos, BlockState state, BooleanProperty gang,
                                     boolean closed) {
        final BlockState next = state.setValue(gang, closed);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        playContacts(level, pos, closed);
        updateNeighbours(next, level, pos);
        onContactsMoved(level, pos);
    }

    protected void playContacts(Level level, BlockPos pos, boolean closed) {
        click.play(level, pos, closed);
    }

    public void tickSwitch(Level level, BlockPos pos, BlockState state,
                           com.createstats.blockentity.SwitchBE be) {
    }

    public boolean addGoggleLines(com.createstats.blockentity.SwitchBE be,
                                  java.util.List<net.minecraft.network.chat.Component> tooltip,
                                  boolean isPlayerSneaking) {
        return false;
    }

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

    protected void onContactsMoved(Level level, BlockPos pos) {
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

    protected void updateNeighbours(BlockState state, Level level, BlockPos pos) {
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

        final Direction facing = state.getValue(FACING);
        final Direction left = facing.getAxis() == Direction.Axis.Y ? Direction.WEST : facing.getCounterClockWise();
        final Direction right = left.getOpposite();

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
        final boolean on = state.getValue(POWERED) || state.getValue(POWERED_B);
        return on && state.getValue(FACING) == direction ? 15 : 0;
    }

    public static boolean isOn(BlockState state) {
        return state.getValue(POWERED) || state.getValue(POWERED_B);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                                net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, stack.getItem().getDescriptionId());
    }
}
