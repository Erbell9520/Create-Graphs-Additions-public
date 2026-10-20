package com.createstats.block;

import com.createstats.blockentity.ContactorBE;
import com.createstats.contactor.ContactorFrame;
import com.createstats.contactor.ContactorLogic;
import com.createstats.contactor.ContactorState;
import com.createstats.contactor.ContactorStuds;
import com.createstats.registry.ModSounds;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ContactorBlock extends Block implements EntityBlock, IWrenchable, GirderMounting.Fixture {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, 3);

    public static final BooleanProperty LEFT = BooleanProperty.create("left");

    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public static final BooleanProperty NORMALLY_CLOSED = BooleanProperty.create("normally_closed");

    private static final float PULL_IN_VOLUME = 0.55f;
    private static final float DROP_OUT_VOLUME = 0.48f;

    public ContactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SPIN, 0)
                .setValue(LEFT, false)
                .setValue(RIGHT, false)
                .setValue(NORMALLY_CLOSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPIN, LEFT, RIGHT, NORMALLY_CLOSED);
    }

    public static int spin(BlockState state) {
        return ContactorFrame.effectiveSpin(state.getValue(FACING), state.getValue(SPIN));
    }

    public static boolean poleClosed(BlockState state, int pole) {
        return state.getBlock() instanceof ContactorBlock
                && ContactorLogic.poleClosed(pole, state.getValue(LEFT), state.getValue(RIGHT),
                state.getValue(NORMALLY_CLOSED));
    }

    public static boolean[] poles(BlockState state) {
        final boolean[] out = new boolean[ContactorLogic.POLES];
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            out[pole] = poleClosed(state, pole);
        }
        return out;
    }

    public static Direction side(BlockState state, int side) {
        final Direction facing = state.getValue(FACING);
        return side == ContactorState.LEFT_SIDE
                ? ContactorFrame.leftOf(facing, spin(state)) : ContactorFrame.rightOf(facing, spin(state));
    }

    public static boolean coil(SignalGetter level, BlockPos pos, BlockState state, int side) {
        if (!(state.getBlock() instanceof ContactorBlock)) {
            return false;
        }
        final Direction toward = side(state, side);
        return level.hasSignal(pos.relative(toward), toward);
    }

    public static Component formName(boolean normallyClosed) {
        return Component.translatable(normallyClosed
                ? "create_stats.contactor.form.normally_closed" : "create_stats.contactor.form.normally_open");
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        if (direction == null || !(state.getBlock() instanceof ContactorBlock)) {
            return false;
        }
        final Direction left = side(state, ContactorState.LEFT_SIDE);
        return direction == left || direction == left.getOpposite();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide()) {
            followCoils(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        followCoils(level, pos, state);
    }

    public void followCoils(Level level, BlockPos pos, BlockState state) {
        if (!state.is(this)) {
            return;
        }
        final ContactorBE be = level.getBlockEntity(pos) instanceof ContactorBE c ? c : null;
        final long now = level.getGameTime();
        final boolean[] in = {state.getValue(LEFT), state.getValue(RIGHT)};
        final boolean[] next = in.clone();
        int wait = Integer.MAX_VALUE;
        for (int side = 0; side < 2; side++) {
            final boolean energised = coil(level, pos, state, side);
            final long last = be != null ? be.contactor().lastMoveTick(side) : ContactorLogic.NEVER;
            switch (ContactorLogic.step(energised, in[side], last, now)) {
                case STAY -> {
                }
                case WAIT -> wait = Math.min(wait, ContactorLogic.ticksToDwellEnd(last, now));
                case MOVE -> next[side] = energised;
            }
        }
        if (wait != Integer.MAX_VALUE) {
            level.scheduleTick(pos, this, wait);
        }
        if (next[0] == in[0] && next[1] == in[1]) {
            return;
        }
        final BlockState after = state.setValue(LEFT, next[0]).setValue(RIGHT, next[1]);
        level.setBlock(pos, after, Block.UPDATE_ALL);
        if (be != null) {
            be.invalidatePoles(changedPoles(state, after));
            for (int side = 0; side < 2; side++) {
                if (next[side] != in[side]) {
                    be.contactor().recordMove(side, now);
                }
            }
        }
        for (int side = 0; side < 2; side++) {
            if (next[side] != in[side]) {
                playArmature(level, pos, next[side]);
            }
        }
    }

    static int changedPoles(BlockState before, BlockState after) {
        int mask = 0;
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            if (poleClosed(before, pole) != poleClosed(after, pole)) {
                mask |= 1 << pole;
            }
        }
        return mask;
    }

    private static void playArmature(Level level, BlockPos pos, boolean pulledIn) {
        final float pitch = 1.0f + (level.random.nextFloat() - 0.5f) * 0.06f;
        level.playSound(null, pos, pulledIn ? ModSounds.CONTACTOR_PULL_IN.get() : ModSounds.CONTACTOR_DROP_OUT.get(),
                SoundSource.BLOCKS, pulledIn ? PULL_IN_VOLUME : DROP_OUT_VOLUME, pitch);
    }

    public boolean setForm(Level level, BlockPos pos, BlockState state, boolean normallyClosed) {
        if (!state.is(this) || state.getValue(NORMALLY_CLOSED) == normallyClosed) {
            return false;
        }
        final BlockState after = state.setValue(NORMALLY_CLOSED, normallyClosed);
        level.setBlock(pos, after, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ContactorBE be) {
            be.invalidatePoles(changedPoles(state, after));
            be.contactor().recordMove(ContactorState.LEFT_SIDE, level.getGameTime());
            be.contactor().recordMove(ContactorState.RIGHT_SIDE, level.getGameTime());
        }
        return true;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos pos = context.getClickedPos();
        final boolean normallyClosed = !state.getValue(NORMALLY_CLOSED);
        if (setForm(level, pos, state, normallyClosed)) {
            IWrenchable.playRotateSound(level, pos);
            final Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.translatable("create_stats.contactor.form_set",
                        formName(normallyClosed)).withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ContactorBE be) {
            player.displayClientMessage(be.status(), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction facing = context.getClickedFace();
        return defaultBlockState().setValue(FACING, facing)
                .setValue(SPIN, ContactorFrame.spinFor(facing, context.getHorizontalDirection()));
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
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ContactorStuds.shape(state.getValue(FACING), spin(state));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        final Direction facing = state.getValue(FACING);
        if (facing.getAxis() != Direction.Axis.Y) {
            return state.setValue(FACING, rotation.rotate(facing));
        }
        return state.setValue(SPIN, spinWithTop(facing, rotation.rotate(ContactorFrame.topOf(facing, spin(state)))));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        final Direction facing = state.getValue(FACING);
        if (facing.getAxis() != Direction.Axis.Y) {
            return state.rotate(mirror.getRotation(facing));
        }
        return state.setValue(SPIN, spinWithTop(facing, mirror.mirror(ContactorFrame.topOf(facing, spin(state)))));
    }

    private static int spinWithTop(Direction facing, Direction top) {
        for (int spin = 0; spin < 4; spin++) {
            if (ContactorFrame.topOf(facing, spin) == top) {
                return spin;
            }
        }
        return 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof ContactorBE be) {
            be.dropAllStudWires();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContactorBE(com.createstats.registry.ModBlockEntities.CONTACTOR.get(), pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != com.createstats.registry.ModBlockEntities.CONTACTOR.get()) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, pos, blockState, be) -> ContactorBE.clientTick(lvl, pos, blockState, (ContactorBE) be)
                : (lvl, pos, blockState, be) -> ContactorBE.serverTick(lvl, pos, blockState, (ContactorBE) be);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ContactorBE be ? be.contactor().comparatorLevel() : 0;
    }
}
