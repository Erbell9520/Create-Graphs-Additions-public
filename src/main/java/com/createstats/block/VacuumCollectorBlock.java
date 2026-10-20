package com.createstats.block;

import com.createstats.blockentity.VacuumCollectorBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.vacuum.CollectorButton;
import com.createstats.vacuum.VacuumDraw;
import com.createstats.vacuum.VacuumFrame;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

public class VacuumCollectorBlock extends HorizontalDirectionalBlock implements IBE<VacuumCollectorBE>, IWrenchable {
    public static final MapCodec<VacuumCollectorBlock> CODEC = simpleCodec(VacuumCollectorBlock::new);

    private static final Map<Direction, VoxelShape> SHAPES = VacuumShapes.horizontal(
            new double[] {1, 0, 1, 15, 3.5, 15},
            new double[] {2.75, 3.5, 4, 13.25, 13.3, 15},
            new double[] {4.75, 12.75, 5.75, 11.25, 16, 12.25},
            new double[] {2, 6.75, VacuumCollectorBE.DUCT_FRONT, 8, VacuumCollectorBE.DUCT_TOP, 5.5},
            new double[] {12.35, 3.5, 9.75, 14.4, 12.6, 13.25},
            new double[] {CollectorButton.X0, CollectorButton.Y0, CollectorButton.Z0, CollectorButton.X1,
                    CollectorButton.Y1, CollectorButton.Z1});

    public VacuumCollectorBlock(Properties properties) {
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return pressedButton(state, level, pos, hit) ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return pressedButton(state, level, pos, hit) ? InteractionResult.sidedSuccess(level.isClientSide())
                : InteractionResult.PASS;
    }

    private static boolean pressedButton(BlockState state, Level level, BlockPos pos, BlockHitResult hit) {
        final Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        final Vec3 m = VacuumFrame.toHorizontal(state.getValue(FACING), local).scale(16.0);
        if (!CollectorButton.hit(m.x, m.y, m.z)) {
            return false;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VacuumCollectorBE collector) {
            collector.pressButton();
        }
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VacuumCollectorBE collector) {
            for (ItemStack stack : collector.dropContents()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.create_stats.vacuum_collector.tooltip",
                VacuumDraw.collectorFan(VacuumDraw.COLLECTOR_MIN_RADIUS),
                VacuumDraw.collectorFan(VacuumDraw.COLLECTOR_MAX_RADIUS),
                VacuumDraw.COLLECTOR_PER_ITEM).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public Class<VacuumCollectorBE> getBlockEntityClass() {
        return VacuumCollectorBE.class;
    }

    @Override
    public BlockEntityType<? extends VacuumCollectorBE> getBlockEntityType() {
        return ModBlockEntities.VACUUM_COLLECTOR.get();
    }
}
