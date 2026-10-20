package com.createstats.block;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class SplitShaftValueBoxTransform extends ValueBoxTransform.Sided {
    private static final double FACE_Z = 15.0;

    public enum Slot {
        SAFETY_TRIP(8.0, 10.0, 0.36f),
        SAFETY_GAP(8.0, 3.9, 0.28f),
        VARIATOR_LOAD(8.0, 10.2, 0.34f),
        VARIATOR_MIN(4.6, 4.0, 0.28f),
        VARIATOR_MAX(11.4, 4.0, 0.28f);

        private final double x;
        private final double y;
        private final float scale;

        Slot(double x, double y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }

    private final Slot slot;

    public SplitShaftValueBoxTransform(Slot slot) {
        this.slot = slot;
        this.scale = slot.scale;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(slot.x, slot.y, FACE_Z);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        if (direction.getAxis().isVertical()) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.AXIS)
                || direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
    }

    @Override
    public float getScale() {
        return slot == null ? 0.36f : slot.scale;
    }
}
