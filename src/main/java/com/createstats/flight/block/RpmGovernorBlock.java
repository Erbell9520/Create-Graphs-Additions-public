package com.createstats.flight.block;

import com.createstats.DebugLogging;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class RpmGovernorBlock extends DirectionalKineticBlock implements IBE<RpmGovernorBE> {
    public static final EnumProperty<VisualState> VISUAL_STATE = EnumProperty.create("visual_state", VisualState.class);

    public RpmGovernorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VISUAL_STATE, VisualState.IDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VISUAL_STATE);
        super.createBlockStateDefinition(builder);
    }

    // opposite face connects only. create calls a conflicting source overstress anyway
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        return face == facing || face == facing.getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hideStressImpact() {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RpmGovernorBE rg) {
                DebugLogging.rpmGovernorInfo("[RPMGov] block removed pos={} command={} applied={} speed={} source={} hasNetwork={} newBlock={} moving={}",
                        pos, rg.getCommandedRpm(), rg.getAppliedRpm(), rg.getSpeed(),
                        rg.hasSource(), rg.hasNetwork(), newState.getBlock(), isMoving);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public Class<RpmGovernorBE> getBlockEntityClass() {
        return RpmGovernorBE.class;
    }

    @Override
    public BlockEntityType<? extends RpmGovernorBE> getBlockEntityType() {
        return ModBlockEntities.RPM_GOVERNOR.get();
    }

    public enum VisualState implements StringRepresentable {
        IDLE("idle"),
        ACTIVE("active");

        private final String name;

        VisualState(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }
}
