package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GarageDoorOriginTest {
    private static GarageDoorSite.Detected run(BlockPos anyHood, Direction facing, int left,
                                               int width) {
        return new GarageDoorSite.Detected(
                new GarageDoorFrame.Result(GarageDoorFrame.Fault.OK, left, width, 4,
                        anyHood.getY()),
                anyHood, GarageDoorSite.widthAxis(facing), facing);
    }

    @Test
    @DisplayName("a controller at the left end is column zero")
    void controllerAtLeftEndIsColumnZero() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final BlockPos hood = new BlockPos(-249, 73, -17);
            final Direction axis = GarageDoorSite.widthAxis(facing);
            final int left = GarageDoorSite.axisCoord(hood, axis);
            final GarageDoorSite.Detected site = run(hood, facing, left, 8);
            assertEquals(0, site.columnOf(hood), "facing " + facing);
            assertEquals(hood, site.originPos(), "facing " + facing);
        }
    }

    @Test
    @DisplayName("a controller two hoods in from the left end is column two, whichever way it faces")
    void controllerInsideTheRunKnowsItsColumn() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final Direction axis = GarageDoorSite.widthAxis(facing);
            final BlockPos leftEnd = new BlockPos(-249, 73, -17);
            final BlockPos controller = leftEnd.relative(axis, 2);
            final GarageDoorSite.Detected site =
                    run(controller, facing, GarageDoorSite.axisCoord(leftEnd, axis), 8);
            assertEquals(2, site.columnOf(controller), "facing " + facing);
            assertEquals(leftEnd, site.originPos(), "facing " + facing);
        }
    }

    @Test
    @DisplayName("the bay from the region file: eight hoods facing west, controller two in")
    void theRealBay() {
        final Direction facing = Direction.WEST;
        final Direction axis = GarageDoorSite.widthAxis(facing);
        final BlockPos controller = new BlockPos(-249, 73, -19);
        final int left = GarageDoorSite.axisCoord(new BlockPos(-249, 73, -17), axis);
        final GarageDoorSite.Detected site = run(controller, facing, left, 8);

        assertEquals(2, site.columnOf(controller));
        assertEquals(new BlockPos(-249, 73, -17), site.originPos());

        for (int col = 0; col < 8; col++) {
            final BlockPos hood = site.originPos().relative(axis, col);
            assertEquals(col, site.columnOf(hood), "column " + col);
            assertEquals(-17 - col, hood.getZ(), "column " + col);
        }
    }

    private static BlockPos[] drawnColumns(BlockPos controller, Direction facing, int origin,
                                           int width) {
        final Direction axis = GarageDoorSite.widthAxis(facing);
        final BlockPos start = controller.relative(axis, -origin);
        final BlockPos[] out = new BlockPos[width];
        for (int col = 0; col < width; col++) {
            out[col] = start.relative(axis, col);
        }
        return out;
    }

    @Test
    @DisplayName("the drawn columns are the hood run, not the run shifted by the offset")
    void drawnColumnsCoverTheRun() {
        final Direction facing = Direction.WEST;
        final Direction axis = GarageDoorSite.widthAxis(facing);
        final BlockPos controller = new BlockPos(-249, 73, -19);
        final int left = GarageDoorSite.axisCoord(new BlockPos(-249, 73, -17), axis);
        final GarageDoorSite.Detected site = run(controller, facing, left, 8);

        final BlockPos[] drawn = drawnColumns(controller, facing, site.columnOf(controller), 8);
        assertEquals(8, drawn.length);
        assertEquals(new BlockPos(-249, 73, -17), drawn[0], "first column is the left end");
        assertEquals(new BlockPos(-249, 73, -24), drawn[7], "last column is the right end");
        for (BlockPos at : drawn) {
            assertEquals(-249, at.getX());
            assertEquals(73, at.getY());
        }
    }

    @Test
    @DisplayName("a controller already at column zero draws exactly where it always did")
    void offsetZeroIsUnchanged() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final BlockPos controller = new BlockPos(4, 70, 9);
            final BlockPos[] drawn = drawnColumns(controller, facing, 0, 5);
            assertEquals(controller, drawn[0], "facing " + facing);
            assertEquals(controller.relative(GarageDoorSite.widthAxis(facing), 4), drawn[4],
                    "facing " + facing);
        }
    }

    @Test
    @DisplayName("a one hood bay is its own column zero")
    void oneHoodBay() {
        final BlockPos hood = new BlockPos(12, 70, 34);
        final Direction axis = GarageDoorSite.widthAxis(Direction.SOUTH);
        final GarageDoorSite.Detected site =
                run(hood, Direction.SOUTH, GarageDoorSite.axisCoord(hood, axis), 1);
        assertEquals(0, site.columnOf(hood));
        assertEquals(hood, site.originPos());
    }
}
