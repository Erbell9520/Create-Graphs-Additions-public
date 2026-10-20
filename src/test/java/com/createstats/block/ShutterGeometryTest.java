package com.createstats.block;

import com.createstats.renderer.BlastShutterRenderer;
import com.createstats.security.ShutterBank;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutterGeometryTest {
    private static final float EPS = 1.0e-4f;

    private static float lowerSlat(int index, float t) {
        return Mth.lerp(t, BlastShutterRenderer.lowerStow(), BlastShutterRenderer.lowerDeployed(index));
    }

    private static float upperSlat(int index, float t, int height) {
        return Mth.lerp(t, BlastShutterRenderer.upperStow(height),
                BlastShutterRenderer.upperDeployed(index, height));
    }

    private static int[] heights() {
        final int[] out = new int[BlastShutterBlock.MAX_HEIGHT - BlastShutterBlock.MIN_HEIGHT + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = BlastShutterBlock.MIN_HEIGHT + i;
        }
        return out;
    }

    @Test
    @DisplayName("a leaf plus its housing is exactly one block at the old two block height")
    void leafCoversItsOwnBlock() {
        assertEquals(16.0f,
                BlastShutterBlock.CASE_H + BlastShutterRenderer.SLATS * BlastShutterRenderer.SLAT_H,
                EPS, "the leaf and the housing do not add up to a block");
        assertEquals(BlastShutterRenderer.SLATS,
                BlastShutterRenderer.slatsPerLeaf(BlastShutterBlock.MIN_HEIGHT),
                "the general slat count disagrees with the two block one it has to reproduce");
    }

    @Test
    @DisplayName("two leaves and two housings are exactly the column, at every height")
    void leavesCoverTheColumn() {
        for (int height : heights()) {
            final float covered = 2.0f * BlastShutterRenderer.slatsPerLeaf(height)
                    * BlastShutterRenderer.SLAT_H + 2.0f * BlastShutterBlock.CASE_H;
            assertEquals(16.0f * height, covered, EPS,
                    "height " + height + ": the leaves and housings cover " + covered + " pixels");
        }
    }

    @Test
    @DisplayName("open, every slat is inside its housing")
    void stowedSlatsAreHidden() {
        for (int height : heights()) {
            final int slats = BlastShutterRenderer.slatsPerLeaf(height);
            final float top = 16.0f * height;
            for (int i = 0; i < slats; i++) {
                final float lower = lowerSlat(i, 0.0f);
                assertTrue(lower >= -EPS && lower + BlastShutterRenderer.SLAT_H
                                <= BlastShutterBlock.CASE_H + EPS,
                        "height " + height + ": lower slat " + i + " sticks out at " + lower);
                final float upper = upperSlat(i, 0.0f, height);
                assertTrue(upper >= top - BlastShutterBlock.CASE_H - EPS
                                && upper + BlastShutterRenderer.SLAT_H <= top + EPS,
                        "height " + height + ": upper slat " + i + " sticks out at " + upper);
            }
        }

        assertTrue(BlastShutterBlock.CASE_Z0 <= BlastShutterBlock.LEAF_Z0
                        && BlastShutterBlock.LEAF_Z1 <= BlastShutterBlock.CASE_Z1,
                "the leaf is not enclosed by the housing in depth");
    }

    @Test
    @DisplayName("sealed, the leaves tile the column and meet at its midpoint")
    void sealedLeavesMeet() {
        for (int height : heights()) {
            final int slats = BlastShutterRenderer.slatsPerLeaf(height);
            final float top = 16.0f * height;

            float previousTop = BlastShutterBlock.CASE_H;
            for (int i = 0; i < slats; i++) {
                final float bottom = lowerSlat(i, 1.0f);
                assertEquals(previousTop, bottom, EPS,
                        "height " + height + ": a gap or an overlap under lower slat " + i);
                previousTop = bottom + BlastShutterRenderer.SLAT_H;
            }
            assertEquals(top / 2.0f, previousTop, EPS,
                    "height " + height + ": the lower leaf does not reach the seam");

            float previousBottom = top - BlastShutterBlock.CASE_H;
            for (int i = 0; i < slats; i++) {
                final float slatTop = upperSlat(i, 1.0f, height) + BlastShutterRenderer.SLAT_H;
                assertEquals(previousBottom, slatTop, EPS,
                        "height " + height + ": a gap or an overlap over upper slat " + i);
                previousBottom = slatTop - BlastShutterRenderer.SLAT_H;
            }
            assertEquals(top / 2.0f, previousBottom, EPS,
                    "height " + height + ": the upper leaf does not reach the seam");
        }
    }

    @Test
    @DisplayName("the slats stay in order and never cross at any point of the travel")
    void slatsNeverCross() {
        for (int height : heights()) {
            final int slats = BlastShutterRenderer.slatsPerLeaf(height);
            for (float t = 0.0f; t <= 1.0f; t += 0.05f) {
                for (int i = 1; i < slats; i++) {
                    assertTrue(lowerSlat(i, t) >= lowerSlat(i - 1, t) - EPS,
                            "height " + height + ": lower slats crossed at t=" + t);
                    assertTrue(upperSlat(i, t, height) <= upperSlat(i - 1, t, height) + EPS,
                            "height " + height + ": upper slats crossed at t=" + t);
                }
            }
        }
    }

    @Test
    @DisplayName("no slat ever leaves the column it belongs to")
    void slatsStayInsideTheColumn() {
        for (int height : heights()) {
            final int slats = BlastShutterRenderer.slatsPerLeaf(height);
            final float top = 16.0f * height;
            for (float t = 0.0f; t <= 1.0f; t += 0.05f) {
                for (int i = 0; i < slats; i++) {
                    final float lower = lowerSlat(i, t);
                    final float upper = upperSlat(i, t, height);
                    assertTrue(lower >= -EPS && lower + BlastShutterRenderer.SLAT_H <= top + EPS,
                            "height " + height + ": lower slat " + i + " left the column at " + lower);
                    assertTrue(upper >= -EPS && upper + BlastShutterRenderer.SLAT_H <= top + EPS,
                            "height " + height + ": upper slat " + i + " left the column at " + upper);
                }
            }
        }
    }

    @Test
    @DisplayName("a taller column takes longer to seal, and a two block one is unchanged")
    void travelGrowsWithHeight() {
        for (ShutterTier tier : ShutterTier.all()) {
            assertEquals(tier.travelTicks(), tier.travelTicksFor(BlastShutterBlock.MIN_HEIGHT),
                    tier + " retimed the shutter everybody already has");
            int previous = tier.travelTicksFor(BlastShutterBlock.MIN_HEIGHT);
            for (int height = BlastShutterBlock.MIN_HEIGHT + 1;
                    height <= BlastShutterBlock.MAX_HEIGHT; height++) {
                final int ticks = tier.travelTicksFor(height);
                assertTrue(ticks > previous, tier + " at height " + height + " is not slower");
                previous = ticks;
            }

            assertEquals(tier.travelTicksFor(BlastShutterBlock.MIN_HEIGHT), tier.travelTicksFor(0));
            assertEquals(tier.travelTicksFor(BlastShutterBlock.MAX_HEIGHT),
                    tier.travelTicksFor(BlastShutterBlock.MAX_HEIGHT + 40));
        }
    }

    @Test
    @DisplayName("a bank grows across the opening, never through it")
    void bankAxisIsHorizontalAndPerpendicular() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final Direction axis = ShutterBank.widthAxis(facing);
            assertTrue(axis.getAxis().isHorizontal(), "a bank must grow horizontally");
            assertTrue(axis.getAxis() != facing.getAxis(),
                    "a bank growing along the facing axis would run into the wall");

            assertEquals(axis.getOpposite(), ShutterBank.widthAxis(facing.getOpposite()));
        }
    }
}
