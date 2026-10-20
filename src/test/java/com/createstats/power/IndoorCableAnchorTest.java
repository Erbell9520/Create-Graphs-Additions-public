package com.createstats.power;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IndoorCableAnchorTest {
    private static java.util.function.Predicate<Direction> walls(Direction... faces) {
        final Set<Direction> held = faces.length == 0
                ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(java.util.List.of(faces));
        return held::contains;
    }

    @Test
    @DisplayName("a run coming down a wall picks up the floor at the corner and keeps the wall")
    void theCornerOfADoorFrameKeepsBothSurfaces() {
        assertEquals(IndoorCableAnchor.DOWN_WEST,
                IndoorCableAnchor.WEST.hug(Direction.Axis.Y, true,
                        walls(Direction.WEST, Direction.DOWN)));
    }

    @Test
    @DisplayName("turning up a wall off a floor run still hugs that wall, rather than going free")
    void aDroppedAnchorStillHugsWhenTheRunWasClipped() {
        assertEquals(IndoorCableAnchor.NORTH,
                IndoorCableAnchor.FREE.hug(Direction.Axis.Y, true, walls(Direction.NORTH)));
    }

    @Test
    @DisplayName("a length placed free on purpose stays free, so a bore still has somewhere to start")
    void aFreeRunIsNotDraggedOntoAWall() {
        assertSame(IndoorCableAnchor.FREE,
                IndoorCableAnchor.FREE.hug(Direction.Axis.Y, false, walls(Direction.NORTH)));
        assertSame(IndoorCableAnchor.FREE,
                IndoorCableAnchor.FREE.hug(null, false, walls(Direction.DOWN, Direction.NORTH)));
    }

    @Test
    @DisplayName("the wall a run is heading into is not a surface it clips to")
    void aWallOnTheTravelAxisIsNotACandidate() {
        assertSame(IndoorCableAnchor.DOWN,
                IndoorCableAnchor.DOWN.hug(Direction.Axis.X, true,
                        walls(Direction.DOWN, Direction.EAST)));
    }

    @Test
    @DisplayName("running along a skirting resolves, because only the perpendicular wall counts")
    void aRunBesideAWallReachesTheCorner() {
        assertEquals(IndoorCableAnchor.DOWN_NORTH,
                IndoorCableAnchor.DOWN.hug(Direction.Axis.X, true,
                        walls(Direction.DOWN, Direction.NORTH, Direction.EAST)));
    }

    @Test
    @DisplayName("a run travelling straight down onto a floor still lies on that floor")
    void theFloorIsExemptFromTheTravelAxisRule() {
        assertSame(IndoorCableAnchor.DOWN,
                IndoorCableAnchor.FREE.hug(Direction.Axis.Y, true, walls(Direction.DOWN)));
    }

    @Test
    @DisplayName("two perpendicular walls and no floor is a niche, and the anchor is left alone")
    void twoCandidatesAreLeftToThePlayer() {
        assertSame(IndoorCableAnchor.FREE,
                IndoorCableAnchor.FREE.hug(Direction.Axis.Y, true,
                        walls(Direction.NORTH, Direction.WEST)));
    }

    @Test
    @DisplayName("an anchor already in a corner is never given a third face")
    void aCornerIsNeverPinnedOnThreeSides() {
        assertSame(IndoorCableAnchor.DOWN_WEST,
                IndoorCableAnchor.DOWN_WEST.hug(null, true,
                        walls(Direction.DOWN, Direction.WEST, Direction.NORTH)));
    }

    @Test
    @DisplayName("the floor wins, and a single remaining wall is taken as well")
    void theFloorAndOneWallCompose() {
        assertEquals(IndoorCableAnchor.DOWN_SOUTH,
                IndoorCableAnchor.FREE.hug(Direction.Axis.X, true,
                        walls(Direction.DOWN, Direction.SOUTH)));
    }

    @Test
    @DisplayName("centreOn puts a corner at the standoff on both of its axes and mid block on the third")
    void aCornerOffsetsTwoAxes() {
        assertEquals(IndoorCable.STANDOFF, IndoorCableAnchor.DOWN_WEST.centreOn(Direction.Axis.Y), 1.0e-6f);
        assertEquals(IndoorCable.STANDOFF, IndoorCableAnchor.DOWN_WEST.centreOn(Direction.Axis.X), 1.0e-6f);
        assertEquals(0.5f, IndoorCableAnchor.DOWN_WEST.centreOn(Direction.Axis.Z), 1.0e-6f);
    }

    @Test
    @DisplayName("a wall length and the corner below it sit in the same lane, so no step is drawn")
    void aWallAndItsCornerShareALane() {
        for (Direction.Axis across : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            assertEquals(IndoorCableAnchor.WEST.centreOn(across),
                    IndoorCableAnchor.DOWN_WEST.centreOn(across), 1.0e-6f,
                    "wall and corner disagree on " + across);
        }
    }
}
