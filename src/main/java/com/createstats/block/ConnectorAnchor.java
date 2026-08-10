package com.createstats.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** [1] */
public enum ConnectorAnchor implements StringRepresentable {

    CENTER("center", null),
    DOWN("down", Direction.DOWN),
    UP("up", Direction.UP),
    NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST),
    EAST("east", Direction.EAST);

    /** Pixels the connector moves toward {@link #direction()}. Matches {@code gen_power_connector.py}. */
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

    /** The world direction this anchor shifts toward, or null for {@link #CENTER}. */
    @Nullable
    public Direction direction() {
        return direction;
    }

    /** The shift in block units along one axis, zero when this anchor does not touch that axis. */
    public double offset(Direction.Axis axis) {
        if (direction == null || direction.getAxis() != axis) {
            return 0.0;
        }
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? SHIFT_PX / 16.0 : -SHIFT_PX / 16.0;
    }

    /** The anchor for a given mounting face, or {@link #CENTER} if it does not lie in that plane. */
    public static ConnectorAnchor inPlaneOf(Direction face, ConnectorAnchor anchor) {
        if (anchor.direction == null || anchor.direction.getAxis() == face.getAxis()) {
            return CENTER;
        }
        return anchor;
    }

    /**
     * Picks the anchor that puts a connector over the support block's actual geometry.
     *
     * <p>Reads the support's collision shape, and for each of the two axes in the mounting plane
     * asks where the support's mass actually sits. A full cube is centred on both and gets
     * {@link #CENTER}. A wall panel is heavily biased on one, and the connector shifts that way. When
     * both axes are biased, the stronger one wins: a connector can only move one way, and moving it
     * toward the larger error is the better of the two available answers.</p>
     *
     * @param face    the face of the support the connector is mounted on, pointing away from it
     * @param support the support block's shape, in its own block space
     */
    public static ConnectorAnchor forSupport(Direction face, VoxelShape support) {
        if (support.isEmpty()) {
            return CENTER;
        }
        final AABB bounds = support.bounds();
        ConnectorAnchor best = CENTER;
        double bestError = 0.0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == face.getAxis()) {
                continue; // this axis runs through the face, not across it
            }
            final double centre = (bounds.min(axis) + bounds.max(axis)) / 2.0;
            final double error = centre - 0.5;
            // A tenth of a block of slop, so a shape that is merely a little lopsided is still
            // treated as centred. Only a genuine wall panel clears this.
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
