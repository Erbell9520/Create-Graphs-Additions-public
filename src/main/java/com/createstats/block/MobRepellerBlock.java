package com.createstats.block;

import com.createstats.blockentity.MobRepellerBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.repeller.RepellerDraw;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MobRepellerBlock extends HorizontalDirectionalBlock implements IBE<MobRepellerBE>, IWrenchable {
    public static final MapCodec<MobRepellerBlock> CODEC = simpleCodec(MobRepellerBlock::new);

    private static final double[][] BOXES = {
            {2, 0, 3, 14, 10.25, 13},
            {3.5, 10.25, 3.5, 12.5, 15, 12.5},
    };

    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    public MobRepellerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.create_stats.mob_repeller.tooltip",
                RepellerDraw.MIN_RADIUS, RepellerDraw.MAX_RADIUS,
                RepellerDraw.drawFor(RepellerDraw.MIN_RADIUS), RepellerDraw.drawFor(RepellerDraw.MAX_RADIUS))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.create_stats.mob_repeller.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("block.create_stats.mob_repeller.tooltip.spawners")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public Class<MobRepellerBE> getBlockEntityClass() {
        return MobRepellerBE.class;
    }

    @Override
    public BlockEntityType<? extends MobRepellerBE> getBlockEntityType() {
        return ModBlockEntities.MOB_REPELLER.get();
    }

    public static double[] turn(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {16 - z, x};
            case SOUTH -> new double[] {16 - x, 16 - z};
            case WEST -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }

    private static Map<Direction, VoxelShape> shapes() {
        final Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : BOXES) {
                final double[] a = turn(facing, b[0], b[2]);
                final double[] c = turn(facing, b[3], b[5]);
                shape = Shapes.or(shape, Block.box(Math.min(a[0], c[0]), b[1], Math.min(a[1], c[1]),
                        Math.max(a[0], c[0]), b[4], Math.max(a[1], c[1])));
            }
            out.put(facing, shape.optimize());
        }
        return out;
    }
}
