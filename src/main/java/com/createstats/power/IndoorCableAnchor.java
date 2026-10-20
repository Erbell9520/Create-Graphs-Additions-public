package com.createstats.power;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum IndoorCableAnchor implements StringRepresentable {
    FREE("free"),
    DOWN("down", Direction.DOWN),
    UP("up", Direction.UP),
    NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST),
    EAST("east", Direction.EAST),

    DOWN_NORTH("down_north", Direction.DOWN, Direction.NORTH),
    DOWN_SOUTH("down_south", Direction.DOWN, Direction.SOUTH),
    DOWN_WEST("down_west", Direction.DOWN, Direction.WEST),
    DOWN_EAST("down_east", Direction.DOWN, Direction.EAST),
    UP_NORTH("up_north", Direction.UP, Direction.NORTH),
    UP_SOUTH("up_south", Direction.UP, Direction.SOUTH),
    UP_WEST("up_west", Direction.UP, Direction.WEST),
    UP_EAST("up_east", Direction.UP, Direction.EAST),
    NORTH_WEST("north_west", Direction.NORTH, Direction.WEST),
    NORTH_EAST("north_east", Direction.NORTH, Direction.EAST),
    SOUTH_WEST("south_west", Direction.SOUTH, Direction.WEST),
    SOUTH_EAST("south_east", Direction.SOUTH, Direction.EAST);

    private final String name;
    private final List<Direction> faces;

    IndoorCableAnchor(String name, Direction... faces) {
        this.name = name;
        this.faces = List.of(faces);
    }

    @Nullable
    public Direction face() {
        return faces.size() == 1 ? faces.get(0) : null;
    }

    public List<Direction> faces() {
        return faces;
    }

    public boolean clipsTo(@Nullable Direction face) {
        return face != null && faces.contains(face);
    }

    public boolean usesAxis(Direction.Axis axis) {
        for (Direction face : faces) {
            if (face.getAxis() == axis) {
                return true;
            }
        }
        return false;
    }

    public static IndoorCableAnchor of(@Nullable Direction face) {
        if (face == null) {
            return FREE;
        }
        for (IndoorCableAnchor anchor : values()) {
            if (anchor.faces.size() == 1 && anchor.faces.get(0) == face) {
                return anchor;
            }
        }
        return FREE;
    }

    public static IndoorCableAnchor of(@Nullable Direction first, @Nullable Direction second) {
        if (first == null) {
            return of(second);
        }
        if (second == null || first == second || first.getAxis() == second.getAxis()) {
            return of(first);
        }
        for (IndoorCableAnchor anchor : values()) {
            if (anchor.faces.size() == 2
                    && anchor.faces.contains(first) && anchor.faces.contains(second)) {
                return anchor;
            }
        }
        return of(first);
    }

    public IndoorCableAnchor with(@Nullable Direction extra) {
        if (extra == null || clipsTo(extra) || faces.size() >= 2 || usesAxis(extra.getAxis())) {
            return this;
        }
        return faces.isEmpty() ? of(extra) : of(faces.get(0), extra);
    }

    public IndoorCableAnchor hug(@Nullable Direction.Axis travel, boolean clipRun,
                                 java.util.function.Predicate<Direction> supported) {
        if (faces.size() >= 2 || (this == FREE && !clipRun)) {
            return this;
        }
        IndoorCableAnchor kept = this;

        if (!kept.usesAxis(Direction.Axis.Y) && supported.test(Direction.DOWN)) {
            kept = kept.with(Direction.DOWN);
        }
        if (kept.faces.size() >= 2) {
            return kept;
        }
        Direction only = null;
        int candidates = 0;
        for (Direction face : Direction.values()) {
            if (kept.usesAxis(face.getAxis()) || !supported.test(face)) {
                continue;
            }

            if (travel != null && face.getAxis() == travel) {
                continue;
            }
            candidates++;
            only = face;
        }

        return candidates == 1 ? kept.with(only) : kept;
    }

    public Direction.Axis freeAxis() {
        if (!usesAxis(Direction.Axis.Y)) {
            return Direction.Axis.Y;
        }
        return usesAxis(Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;
    }

    public float centreOn(net.minecraft.core.Direction.Axis axis) {
        for (Direction face : faces) {
            if (face.getAxis() == axis) {
                return face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? 1.0f - IndoorCable.STANDOFF
                        : IndoorCable.STANDOFF;
            }
        }
        return 0.5f;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
