package com.createstats.block;

import com.createstats.blockentity.FeMotorBE;
import com.createstats.power.MotorTier;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class FeMotorBlock extends DirectionalKineticBlock implements IBE<FeMotorBE> {
    // one literal shape per facing, generated off the model. do NOT hand edit these

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(1, 0, 2, 15, 1.2, 14),
            Block.box(3.5, 1.2, 3.2, 12.5, 3.9, 12.8),
            Block.box(3.3, 3.7, 2.6, 12.7, 12.6, 14),
            Block.box(3, 2.9, 1.1, 13, 12.9, 2.8),
            Block.box(9.4, 11.3, 3.6, 14.5, 13.9, 7));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(1, 0, 2, 15, 1.2, 14),
            Block.box(3.5, 1.2, 3.2, 12.5, 3.9, 12.8),
            Block.box(3.3, 3.7, 2, 12.7, 12.6, 13.4),
            Block.box(3, 2.9, 13.2, 13, 12.9, 14.9),
            Block.box(1.5, 11.3, 9, 6.6, 13.9, 12.4));
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(2, 0, 1, 14, 1.2, 15),
            Block.box(3.2, 1.2, 3.5, 12.8, 3.9, 12.5),
            Block.box(2, 3.7, 3.3, 13.4, 12.6, 12.7),
            Block.box(13.2, 2.9, 3, 14.9, 12.9, 13),
            Block.box(9, 11.3, 9.4, 12.4, 13.9, 14.5));
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(2, 0, 1, 14, 1.2, 15),
            Block.box(3.2, 1.2, 3.5, 12.8, 3.9, 12.5),
            Block.box(2.6, 3.7, 3.3, 14, 12.6, 12.7),
            Block.box(1.1, 2.9, 3, 2.8, 12.9, 13),
            Block.box(3.6, 11.3, 1.5, 7, 13.9, 6.6));
    private static final VoxelShape UP_SHAPE = Shapes.or(
            Block.box(1, 2, 0, 15, 14, 1.2),
            Block.box(3.5, 3.2, 1.2, 12.5, 12.8, 3.9),
            Block.box(3.3, 2, 3.7, 12.7, 13.4, 12.6),
            Block.box(3, 13.2, 2.9, 13, 14.9, 12.9),
            Block.box(9.4, 9, 11.3, 14.5, 12.4, 13.9));
    private static final VoxelShape DOWN_SHAPE = Shapes.or(
            Block.box(1, 2, 14.8, 15, 14, 16),
            Block.box(3.5, 3.2, 12.1, 12.5, 12.8, 14.8),
            Block.box(3.3, 2.6, 3.4, 12.7, 14, 12.3),
            Block.box(3, 1.1, 3.1, 13, 2.8, 13.1),
            Block.box(9.4, 3.6, 2.1, 14.5, 7, 4.7));

    private final MotorTier tier;

    public FeMotorBlock(Properties properties, MotorTier tier) {
        super(properties);
        this.tier = tier;
    }

    public MotorTier tier() {
        return tier;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FeMotorBE motor) {
            motor.dropWiresAndUnlinkAll();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("block.create_stats.fe_motor.tooltip",
                        tier.ratedRpm(), tier.stressCapacitySu())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public MapCodec<? extends FeMotorBlock> codec() {
        return simpleCodec(p -> new FeMotorBlock(p, tier));
    }

    @Override
    public Class<FeMotorBE> getBlockEntityClass() {
        return FeMotorBE.class;
    }

    @Override
    public BlockEntityType<? extends FeMotorBE> getBlockEntityType() {
        return ModBlockEntities.FE_MOTOR.get();
    }
}
