package com.createstats.block;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GirderMountingTest {
    @Test
    @DisplayName("a block that is not a girder never holds by the girder rule")
    void notAGirder() {
        for (Direction face : Direction.values()) {
            assertEquals(false, GirderMounting.faceHolds(GirderMounting.Kind.NONE, null, face), face.getName());
        }
    }

    @Test
    @DisplayName("a plain girder holds on all six faces, because it can grow toward any of them")
    void plainGirder() {
        for (Direction face : Direction.values()) {
            assertEquals(true, GirderMounting.faceHolds(GirderMounting.Kind.GIRDER, null, face), face.getName());
        }
    }

    @Test
    @DisplayName("an encased shaft girder refuses the two faces its shaft comes out of, and only those")
    void encasedShaftGirder() {
        for (Direction.Axis shaft : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            for (Direction face : Direction.values()) {
                final boolean expected = face.getAxis() != shaft;
                assertEquals(expected,
                        GirderMounting.faceHolds(GirderMounting.Kind.ENCASED_SHAFT, shaft, face),
                        "shaft along " + shaft + ", face " + face.getName());
            }
        }
    }
}
