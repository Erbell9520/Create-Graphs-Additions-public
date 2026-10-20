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

// on the pedestal, the snout is pressed against the container
public class SampleCupBox extends ValueBoxTransform {
    private static final float SCALE = 0.4f;

    public SampleCupBox() {
        this.scale = SCALE;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(ProductionDetectorBlock.FACING)
                ? state.getValue(ProductionDetectorBlock.FACING) : Direction.NORTH;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return PanelGeometry.toWorld(facing(state), PanelGeometry.CUP_X, PanelGeometry.CUP_Y, PanelGeometry.CUP_Z);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        final Direction out = facing(state).getOpposite();
        TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(out) + 180.0f);
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
