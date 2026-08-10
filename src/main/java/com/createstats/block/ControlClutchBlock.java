package com.createstats.block;

import com.createstats.blockentity.ControlClutchBE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

import java.util.List;

public class ControlClutchBlock extends AbstractEncasedShaftBlock implements IBE<ControlClutchBE> {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ControlClutchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ControlClutchBE clutch)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        player.displayClientMessage(buildStatusMessage(clutch), false);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getRunSignal(level, pos);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getRunSignal(level, pos);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    private int getRunSignal(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ControlClutchBE clutch && clutch.isControllerLinked() && clutch.isOutputRunning()) {
            return 15;
        }
        return 0;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ControlClutchBE clutch) {
            return clutch.getAnalogOutputSignal();
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ControlClutchBE clutch) {
            RotationPropagator.handleAdded(level, pos, clutch);
            clutch.syncKineticState();
        } else if (be instanceof KineticBlockEntity kbe) {
            RotationPropagator.handleAdded(level, pos, kbe);
            if (kbe.hasNetwork()) {
                kbe.getOrCreateNetwork().updateNetwork();
            }
        }
    }

    @Override
    public Class<ControlClutchBE> getBlockEntityClass() {
        return ControlClutchBE.class;
    }

    @Override
    public BlockEntityType<? extends ControlClutchBE> getBlockEntityType() {
        return ModBlockEntities.CONTROL_CLUTCH.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.control_clutch");
    }

    private static Component buildStatusMessage(ControlClutchBE clutch) {
        if (!clutch.isControllerLinked() || clutch.getControllerPos() == null) {
            return Component.translatable("message.create_stats.control_clutch.status.waiting");
        }
        Component rowLabel = clutch.getControllerRow() >= 0
                ? Component.translatable("message.create_stats.control_clutch.status.row", clutch.getControllerRow() + 1)
                : Component.empty();
        Component state = Component.translatable(clutch.isControllerRequestingRun()
                ? "message.create_stats.control_clutch.status.run"
                : "message.create_stats.control_clutch.status.stop");
        return Component.translatable("message.create_stats.control_clutch.status.linked",
                formatPos(clutch.getControllerPos()), rowLabel, clutch.getLastControllerSignal(), state);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
