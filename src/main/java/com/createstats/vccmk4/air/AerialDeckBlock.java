package com.createstats.vccmk4.air;

import com.createstats.registry.ModBlockEntities;
import com.createstats.util.CreateStatsTooltipHelper;
import com.createstats.vccmk4.air.core.CockpitShape;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class AerialDeckBlock extends AerialBlock implements IProxyHoveringInformation {
    public static final MapCodec<AerialDeckBlock> CODEC = simpleCodec(AerialDeckBlock::new);
    public static final EnumProperty<DeckHalf> HALF = EnumProperty.create("half", DeckHalf.class);

    private static final Map<Direction, VoxelShape> LEFT_SHAPES = shapes(CockpitShape.LEFT);
    private static final Map<Direction, VoxelShape> RIGHT_SHAPES = shapes(CockpitShape.RIGHT);

    public AerialDeckBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DeckHalf.LEFT));
    }

    private static Map<Direction, VoxelShape> shapes(double[][] north) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        Direction[] byTurns = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int turns = 0; turns < byTurns.length; turns++) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : CockpitShape.boxes(north, turns)) {
                shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
            map.put(byTurns[turns], shape.optimize());
        }
        return map;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    public static Direction towardPartner(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(HALF) == DeckHalf.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    public static BlockPos master(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DeckHalf.LEFT ? pos : pos.relative(towardPartner(state));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos right = context.getClickedPos().relative(facing.getClockWise());
        Level level = context.getLevel();
        if (!level.getWorldBorder().isWithinBounds(right) || !level.getBlockState(right).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(HALF, DeckHalf.LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.relative(towardPartner(state)), state.setValue(HALF, DeckHalf.RIGHT), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == towardPartner(state)) {
            boolean partner = neighbor.is(this) && neighbor.getValue(HALF) != state.getValue(HALF)
                    && neighbor.getValue(FACING) == state.getValue(FACING);
            return partner ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DeckHalf.RIGHT) {
            BlockPos left = master(state, pos);
            BlockState other = level.getBlockState(left);
            if (other.is(this) && other.getValue(HALF) == DeckHalf.LEFT) {
                level.setBlock(left, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
                level.levelEvent(player, 2001, left, Block.getId(other));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DeckHalf.LEFT ? LEFT_SHAPES : RIGHT_SHAPES).get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return super.useWithoutItem(state, level, master(state, pos), player, hit);
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        return master(state, pos);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !context.getLevel().isClientSide()) {
            player.displayClientMessage(Component.literal("The Flight Deck turns as a whole: break it and place it again facing the nose.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (state.getValue(HALF) == DeckHalf.LEFT || context.getPlayer() == null) {
            return super.onSneakWrenched(state, context);
        }
        BlockPos left = master(state, context.getClickedPos());
        BlockState other = context.getLevel().getBlockState(left);
        if (!other.is(this)) return super.onSneakWrenched(state, context);
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), left, context.isInside());
        return super.onSneakWrenched(other, new UseOnContext(context.getPlayer(), context.getHand(), hit));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.vcc_mk4_aerial_flight_deck");
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DeckHalf.LEFT) return super.newBlockEntity(pos, state);
        return new AerialDeckPartBE(ModBlockEntities.VCC_MK4_AERIAL_DECK_PART.get(), pos, state);
    }
}
