package com.createstats.block;

import java.util.List;

import com.createstats.blockentity.PowerMeterBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

public class PowerMeterBlock extends Block implements EntityBlock, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public PowerMeterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();

        final Direction againstClicked = context.getClickedFace().getOpposite();
        if (againstClicked.getAxis().isHorizontal() && takesPower(level, pos, againstClicked)) {
            return this.defaultBlockState().setValue(FACING, againstClicked.getOpposite());
        }
        for (final Direction dir : Direction.Plane.HORIZONTAL) {
            if (takesPower(level, pos, dir)) {
                return this.defaultBlockState().setValue(FACING, dir.getOpposite());
            }
        }

        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    private static boolean takesPower(Level level, BlockPos meterPos, Direction dir) {
        final BlockPos neighbour = meterPos.relative(dir);
        if (PowerMeterBE.isWiring(level.getBlockEntity(neighbour))) {
            return false;
        }
        final var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighbour,
                dir.getOpposite());
        return storage != null && storage.canReceive();
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PowerMeterBE meter)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            meter.resetTotals();
            player.displayClientMessage(
                    Component.translatable("message.create_stats.power_meter.reset")
                            .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide()) {
            com.createstats.util.ClientScreenAccess.openPowerMeter(meter);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PowerMeterBE meter ? meter.getAnalogOutputSignal() : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
            Player player) {
        return ModBlocks.POWER_METER.asStack();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.power_meter");
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(PowerMeterBlock::new);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerMeterBE(ModBlockEntities.POWER_METER.get(), pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.POWER_METER.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((PowerMeterBE) be).tick();
    }
}
