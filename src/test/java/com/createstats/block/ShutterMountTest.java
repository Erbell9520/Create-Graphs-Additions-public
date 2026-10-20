package com.createstats.block;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutterMountTest {
    private static final float EPS = 1.0e-4f;

    @Test
    @DisplayName("centred is exactly where every shutter built before this existed is")
    void centreIsUnmoved() {
        assertEquals(0.0f, ShutterMount.CENTER.depth(), EPS,
                "the default mount moved, which moves every shutter in every old save");
        assertSame(ShutterMount.CENTER, ShutterMount.values()[0],
                "the default is the first constant, which is what an unset property loads as");
        assertTrue(!ShutterMount.CENTER.flush(), "the centred mount is not against anything");
    }

    @Test
    @DisplayName("both flush mounts land exactly on the face of the block")
    void flushMountsTouchTheFace() {
        assertEquals(0.0f, BlastShutterBlock.CASE_Z0 + ShutterMount.FRONT.depth(), EPS,
                "the front mount does not sit on the near face");
        assertEquals(16.0f, BlastShutterBlock.CASE_Z1 + ShutterMount.BACK.depth(), EPS,
                "the back mount does not sit on the far face");
        assertTrue(ShutterMount.FRONT.flush() && ShutterMount.BACK.flush(),
                "a mount that lands on a face is a flush one");
    }

    @Test
    @DisplayName("no mount takes any part of the shutter out of its own block")
    void nothingLeavesTheBlock() {
        for (ShutterMount mount : ShutterMount.values()) {
            final float caseZ0 = BlastShutterBlock.CASE_Z0 + mount.depth();
            final float caseZ1 = BlastShutterBlock.CASE_Z1 + mount.depth();
            assertTrue(caseZ0 >= -EPS && caseZ1 <= 16.0f + EPS,
                    mount + ": the housing sits at " + caseZ0 + ".." + caseZ1);
        }
    }

    @Test
    @DisplayName("the leaf is still enclosed by the housing at every mount")
    void theLeafStaysHidden() {
        for (ShutterMount mount : ShutterMount.values()) {
            final float caseZ0 = BlastShutterBlock.CASE_Z0 + mount.depth();
            final float caseZ1 = BlastShutterBlock.CASE_Z1 + mount.depth();
            final float leafZ0 = BlastShutterBlock.LEAF_Z0 + mount.depth();
            final float leafZ1 = BlastShutterBlock.LEAF_Z1 + mount.depth();
            assertTrue(caseZ0 <= leafZ0 + EPS && leafZ1 <= caseZ1 + EPS,
                    mount + ": the leaf is not inside the housing");
        }
    }

    @Test
    @DisplayName("the wrench cycle reaches every mount and comes back to where it started")
    void theCycleIsACycle() {
        for (ShutterMount start : ShutterMount.values()) {
            final Set<ShutterMount> seen = EnumSet.noneOf(ShutterMount.class);
            ShutterMount at = start;
            for (int click = 0; click < ShutterMount.values().length; click++) {
                assertNotEquals(at, at.next(), "a click that changes nothing looks like a broken tool");
                seen.add(at);
                at = at.next();
            }
            assertSame(start, at, start + ": three clicks did not come back round");
            assertEquals(ShutterMount.values().length, seen.size(),
                    start + ": the cycle skips a mount, which makes it unreachable");
        }
    }

    @Test
    @DisplayName("clicking a wall mounts on it, clicking a floor or a ceiling does not")
    void theClickedFaceDecides() {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            assertSame(ShutterMount.BACK, ShutterMount.forClickedFace(face),
                    face + ": a shutter placed on a wall has to end up against it");
        }
        assertSame(ShutterMount.CENTER, ShutterMount.forClickedFace(Direction.UP),
                "placing onto a floor is how an opening is filled, and that is centred");
        assertSame(ShutterMount.CENTER, ShutterMount.forClickedFace(Direction.DOWN),
                "placing onto a ceiling is how an opening is filled, and that is centred");
        assertSame(ShutterMount.CENTER, ShutterMount.forClickedFace(null),
                "no face at all has to answer the harmless one");
    }

    @Test
    @DisplayName("the serialized names are the ones the models and the blockstates are written under")
    void theNamesAreTheOnesOnDisk() {
        assertEquals("center", ShutterMount.CENTER.getSerializedName());
        assertEquals("front", ShutterMount.FRONT.getSerializedName());
        assertEquals("back", ShutterMount.BACK.getSerializedName());
    }
}
