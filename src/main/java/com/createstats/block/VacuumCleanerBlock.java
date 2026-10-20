package com.createstats.block;

import com.createstats.blockentity.VacuumCleanerBE;
import com.createstats.item.SuctionWandItem;
import com.createstats.registry.ModBlockEntities;
import com.createstats.vacuum.HoseTether;
import com.createstats.vacuum.VacuumDraw;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

public class VacuumCleanerBlock extends HorizontalDirectionalBlock implements IBE<VacuumCleanerBE>, IWrenchable {
    public static final MapCodec<VacuumCleanerBlock> CODEC = simpleCodec(VacuumCleanerBlock::new);

    private static final Map<Direction, VoxelShape> SHAPES = VacuumShapes.horizontal(
            new double[] {2, 0, 2.5, 14, 1, 14.5},
            new double[] {5, 1, 5.75, 11, 5.5, 11},
            new double[] {3, 5.5, 3.5, 13, 13.8, 13.5},
            new double[] {3.4, 13.8, 3.9, 12.6, 16, 13.1},
            new double[] {6, 8, 0, 10, 12, 3.5});

    public VacuumCleanerBlock(Properties properties) {
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
        if (stack.getItem() instanceof SuctionWandItem && SuctionWandItem.belongsTo(stack, level, pos)) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VacuumCleanerBE cleaner) {
                cleaner.reclaimHose();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VacuumCleanerBE cleaner) {
            final Component refusal = cleaner.takeHose(player);
            if (refusal != null) {
                player.displayClientMessage(refusal, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VacuumCleanerBE cleaner) {
            for (ItemStack stack : cleaner.dropContents()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.create_stats.vacuum_cleaner.tooltip",
                (int) HoseTether.LENGTH, VacuumDraw.CLEANER_SUCTION).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public Class<VacuumCleanerBE> getBlockEntityClass() {
        return VacuumCleanerBE.class;
    }

    @Override
    public BlockEntityType<? extends VacuumCleanerBE> getBlockEntityType() {
        return ModBlockEntities.VACUUM_CLEANER.get();
    }
}
