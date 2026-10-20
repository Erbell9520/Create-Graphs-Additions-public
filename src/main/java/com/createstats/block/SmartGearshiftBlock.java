package com.createstats.block;

import com.createstats.blockentity.SmartGearshiftBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.GaugeParticleHelper;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

import java.util.List;

public class SmartGearshiftBlock extends AbstractEncasedShaftBlock implements IBE<SmartGearshiftBE> {
    public static final EnumProperty<VisualState> VISUAL_STATE = EnumProperty.create("visual_state", VisualState.class);

    public SmartGearshiftBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VISUAL_STATE, VisualState.NEUTRAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VISUAL_STATE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getStageSignal(level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SmartGearshiftBE gearshift) {
            GaugeParticleHelper.spawnRpmParticles(level, pos, random, Direction.UP, gearshift.dialColor, gearshift.getDisplayedOutputSpeed());
        }
    }

    private int getStageSignal(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SmartGearshiftBE gearshift) {
            return gearshift.getCurrentStage();
        }
        return 0;
    }

    @Override
    public Class<SmartGearshiftBE> getBlockEntityClass() {
        return SmartGearshiftBE.class;
    }

    @Override
    public BlockEntityType<? extends SmartGearshiftBE> getBlockEntityType() {
        return ModBlockEntities.SMART_GEARSHIFT.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.smart_gearshift");
    }

    public enum VisualState implements StringRepresentable {
        NEUTRAL("neutral"),
        BOOST("boost"),
        THROTTLE("throttle");

        private final String serializedName;

        VisualState(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
