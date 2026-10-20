package com.createstats.block;

import com.createstats.blockentity.StormCoilBE;
import com.createstats.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class StormCoilPartBlock extends HorizontalDirectionalBlock implements IProxyHoveringInformation, IWrenchable {
    public enum Kind implements StringRepresentable {
        COLUMN, ROD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final MapCodec<StormCoilPartBlock> CODEC = simpleCodec(StormCoilPartBlock::new);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    private static final VoxelShape COLUMN = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape ROD = Block.box(6.5, 0, 6.5, 9.5, 13, 9.5);

    public StormCoilPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.COLUMN).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(KIND) == Kind.ROD ? ROD : COLUMN;
    }

    public static BlockState rod(int dx, int dz) {
        final Direction facing = dx > 0 ? (dz > 0 ? Direction.WEST : Direction.SOUTH)
                : (dz > 0 ? Direction.NORTH : Direction.EAST);
        return ModBlocks.STORM_COIL_PART.get().defaultBlockState().setValue(KIND, Kind.ROD).setValue(FACING, facing);
    }

    public static BlockState column() {
        return ModBlocks.STORM_COIL_PART.get().defaultBlockState().setValue(KIND, Kind.COLUMN);
    }

    @Nullable
    public static BlockPos master(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof StormCoilPartBlock)) {
            return state.getBlock() instanceof StormCoilBlock ? pos : null;
        }
        if (state.getValue(KIND) == Kind.ROD) {
            final Direction f = state.getValue(FACING);
            final BlockPos base = pos.relative(f).relative(f.getClockWise());
            return level.getBlockState(base).getBlock() instanceof StormCoilBlock ? base : null;
        }
        BlockPos at = pos;
        for (int i = 0; i < 3; i++) {
            at = at.below();
            final BlockState below = level.getBlockState(at);
            if (below.getBlock() instanceof StormCoilBlock) {
                return at;
            }
            if (!(below.getBlock() instanceof StormCoilPartBlock) || below.getValue(KIND) != Kind.COLUMN) {
                return null;
            }
        }
        return null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            final BlockPos coil = master(level, pos, state);
            if (coil != null && level.getBlockEntity(coil) instanceof StormCoilBE be && !be.isReleasing()) {
                level.destroyBlock(coil, true);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        final BlockPos coil = master(level, pos, state);
        if (coil != null) {
            level.destroyBlockProgress(coil.hashCode(), coil, -1);
            if (!level.isClientSide() && player.isCreative()) {
                level.destroyBlock(coil, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (master(level, pos, state) == null && level instanceof Level real && !real.isClientSide()
                && !real.getBlockTicks().hasScheduledTick(pos, this)) {
            real.scheduleTick(pos, this, 1);
        }
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (master(level, pos, state) == null) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        final BlockPos coil = master(level, pos, state);
        return coil == null ? pos : coil;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return new ItemStack(ModBlocks.STORM_COIL.get());
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
