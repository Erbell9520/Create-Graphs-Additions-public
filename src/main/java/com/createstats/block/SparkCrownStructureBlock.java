package com.createstats.block;

import com.createstats.blockentity.SparkCrownBE;
import com.createstats.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public class SparkCrownStructureBlock extends DirectionalBlock implements IProxyHoveringInformation, IWrenchable {
    public static final MapCodec<SparkCrownStructureBlock> CODEC = simpleCodec(SparkCrownStructureBlock::new);

    private static final int MAX_CHAIN = 4;

    public SparkCrownStructureBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    public static BlockPos master(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos at = pos;
        BlockState s = state;
        for (int i = 0; i < MAX_CHAIN && s.getBlock() instanceof SparkCrownStructureBlock; i++) {
            at = at.relative(s.getValue(FACING));
            s = level.getBlockState(at);
        }
        return s.getBlock() instanceof SparkCrownBlock ? at : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        final BlockPos station = master(level, pos, state);
        return station == null ? InteractionResult.PASS : SparkCrownBlock.use(level, station, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            final BlockPos station = master(level, pos, state);
            if (station != null && level.getBlockEntity(station) instanceof SparkCrownBE be && !be.isReleasing()) {
                level.destroyBlock(station, true);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        final BlockPos station = master(level, pos, state);
        if (station != null) {
            level.destroyBlockProgress(station.hashCode(), station, -1);
            if (!level.isClientSide() && player.isCreative()) {
                level.destroyBlock(station, false);
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
        final BlockPos station = master(level, pos, state);
        return station == null ? pos : station;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return new ItemStack(ModBlocks.SPARK_CROWN.get());
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
