package com.createstats.blockentity.inventory;

import com.createstats.block.ProductionDetectorBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class InventoryDialBox extends ValueBoxTransform {
    private static final float SCALE = 0.4f;

    private final boolean high;

    public InventoryDialBox(boolean high) {
        this.high = high;
        this.scale = SCALE;
    }

    public boolean isHigh() {
        return this.high;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(ProductionDetectorBlock.FACING)
                ? state.getValue(ProductionDetectorBlock.FACING) : Direction.NORTH;
    }

    public Direction side(BlockState state) {
        final Direction f = facing(state);
        return this.high ? f.getClockWise() : f.getCounterClockWise();
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        final double x = this.high ? PanelGeometry.DIAL_HIGH_X + 0.05 : PanelGeometry.DIAL_LOW_X - 0.05;
        return PanelGeometry.toWorld(facing(state), x, PanelGeometry.DIAL_Y, PanelGeometry.DIAL_Z);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        final Direction side = side(state);
        TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(side) + 180.0f);
    }

    @Override
    public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof ProductionDetectorBlock && super.shouldRender(level, pos, state);
    }

    @Override
    public float getScale() {
        return SCALE;
    }
}
