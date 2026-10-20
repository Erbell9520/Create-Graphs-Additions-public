package com.createstats.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public enum ConnectorAnchor implements StringRepresentable {
    CENTER("center", null),
    DOWN("down", Direction.DOWN),
    UP("up", Direction.UP),
    NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST),
    EAST("east", Direction.EAST);

    public static final double SHIFT_PX = 5.0;

    private final String name;
    @Nullable
    private final Direction direction;

    ConnectorAnchor(String name, @Nullable Direction direction) {
        this.name = name;
        this.direction = direction;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Nullable
    public Direction direction() {
        return direction;
    }

    public double offset(Direction.Axis axis) {
        if (direction == null || direction.getAxis() != axis) {
            return 0.0;
        }
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? SHIFT_PX / 16.0 : -SHIFT_PX / 16.0;
    }

    public static ConnectorAnchor inPlaneOf(Direction face, ConnectorAnchor anchor) {
        if (anchor.direction == null || anchor.direction.getAxis() == face.getAxis()) {
            return CENTER;
        }
        return anchor;
    }

    public static ConnectorAnchor forSupport(Direction face, VoxelShape support) {
        if (support.isEmpty()) {
            return CENTER;
        }
        final AABB bounds = support.bounds();
        ConnectorAnchor best = CENTER;
        double bestError = 0.0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == face.getAxis()) {
                continue;
            }
            final double centre = (bounds.min(axis) + bounds.max(axis)) / 2.0;
            final double error = centre - 0.5;
            // 0.1 of slop, only a real wall panel clears it

            if (Math.abs(error) <= 0.1 || Math.abs(error) <= Math.abs(bestError)) {
                continue;
            }
            bestError = error;
            best = of(Direction.fromAxisAndDirection(axis, error < 0
                    ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE));
        }
        return best;
    }

    private static ConnectorAnchor of(Direction direction) {
        for (ConnectorAnchor a : values()) {
            if (a.direction == direction) {
                return a;
            }
        }
        return CENTER;
    }
}
