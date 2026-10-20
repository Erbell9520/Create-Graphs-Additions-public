package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarageDoorCurlTest {
    private static final float EPSILON = 1.0e-4f;

    private static float[] sweep() {
        final float[] p = new float[41];
        for (int i = 0; i < p.length; i++) {
            p[i] = i / (float) (p.length - 1);
        }
        return p;
    }

    @Test
    @DisplayName("a curtain is a whole number of slats at every legal height")
    void slatCountIsWhole() {
        for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
            assertEquals(h * 4, GarageDoorCurl.slatCount(h), "height " + h);
            assertEquals(0, (h * GarageDoorCurl.PX) % GarageDoorCurl.SLAT_PITCH,
                    "height " + h + " ends on a partial slat");
        }
    }

    @Test
    @DisplayName("a slat below the lintel is always full size, at every openness")
    void slatsNeverShrink() {
        for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
            final float lintel = GarageDoorCurl.lintelPx(h);
            for (final float p : sweep()) {
                for (int i = 0; i < GarageDoorCurl.slatCount(h); i++) {
                    final float base = GarageDoorCurl.slatBasePx(i, p, h);
                    if (base + GarageDoorCurl.SLAT_FACE <= lintel) {
                        assertEquals(GarageDoorCurl.SLAT_FACE, GarageDoorCurl.visibleFace(i, p, h), EPSILON,
                                "h=" + h + " p=" + p + " slat=" + i);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the bottom bar sits on the threshold shut and reaches the lintel open")
    void bottomBarTravelsExactlyTheOpening() {
        for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
            assertEquals(0.0f, GarageDoorCurl.bottomBarPx(0.0f, h), EPSILON);
            assertEquals(GarageDoorCurl.lintelPx(h), GarageDoorCurl.bottomBarPx(1.0f, h), EPSILON);
        }
    }

    @Test
    @DisplayName("slats are clipped into the hood rather than popping out of existence")
    void curtainIsClippedNotPopped() {
        final int h = 4;
        final int top = GarageDoorCurl.slatCount(h) - 1;

        final float travelPerStep = GarageDoorCurl.lintelPx(h) / (sweep().length - 1);
        float previous = GarageDoorCurl.visibleFace(top, 0.0f, h);
        for (final float p : sweep()) {
            final float face = GarageDoorCurl.visibleFace(top, p, h);
            assertTrue(face <= previous + EPSILON, "the top slat grew back at p=" + p);
            assertTrue(face >= 0.0f, "negative face at p=" + p);
            assertTrue(previous - face <= travelPerStep + EPSILON,
                    "the top slat jumped further than the curtain moved, at p=" + p);
            previous = face;
        }
        assertEquals(0.0f, GarageDoorCurl.visibleFace(top, 1.0f, h), EPSILON);
    }

    @Test
    @DisplayName("the barrel starts bare and only ever fattens")
    void barrelGrowsMonotonically() {
        assertEquals(GarageDoorCurl.DRUM_R0, GarageDoorCurl.barrelRadiusPx(0.0f), EPSILON);
        float previous = GarageDoorCurl.barrelRadiusPx(0.0f);
        for (float w = 0.0f; w <= 96.0f; w += 1.0f) {
            final float r = GarageDoorCurl.barrelRadiusPx(w);
            assertTrue(r >= previous - EPSILON, "the barrel shrank at wound=" + w);
            previous = r;
        }
    }

    @Test
    @DisplayName("the barrel is at its entry point with nothing wound, and turns from there")
    void barrelAngleStartsAtZeroAndAdvances() {
        assertEquals(0.0f, GarageDoorCurl.barrelAngle(0.0f), EPSILON);
        assertTrue(GarageDoorCurl.barrelAngle(64.0f) > GarageDoorCurl.barrelAngle(32.0f));
    }

    @Test
    @DisplayName("the height cap is the last height whose barrel fits the hood bore")
    void heightCapIsDerivedNotChosen() {
        assertTrue(GarageDoorCurl.fitsHood(GarageDoorFrame.MAX_DROP),
                "the cap does not fit its own hood: "
                        + GarageDoorCurl.barrelDiameterPx(GarageDoorFrame.MAX_DROP) + "px");
        assertFalse(GarageDoorCurl.fitsHood(GarageDoorFrame.MAX_DROP + 1),
                "the cap is lower than it needs to be: "
                        + GarageDoorCurl.barrelDiameterPx(GarageDoorFrame.MAX_DROP + 1) + "px");
    }

    @Test
    @DisplayName("a taller door takes longer, because it has further to go")
    void moveTicksScaleWithTravel() {
        int previous = 0;
        for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
            final int ticks = GarageDoorCurl.moveTicks(h);
            assertTrue(ticks > previous, "height " + h + " is not slower than " + (h - 1));
            previous = ticks;
        }

        final float ratio = GarageDoorCurl.moveTicks(GarageDoorFrame.MAX_DROP)
                / (float) GarageDoorCurl.moveTicks(GarageDoorFrame.MIN_HEIGHT);
        assertTrue(ratio > 2.0f && ratio < 8.0f, "duration ratio was " + ratio);
    }

    @Test
    @DisplayName("the vision course is a real slat, never the bottom bar and never off the top")
    void visionCourseIsInsideTheCurtain() {
        for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
            final int course = GarageDoorCurl.visionCourse(h);

            assertTrue(course > 0, "height " + h + " puts the vision slots in the bottom bar");
            assertTrue(course < GarageDoorCurl.slatCount(h),
                    "height " + h + " puts the vision slots above the lintel, where nothing draws");
        }
    }

    @Test
    @DisplayName("the vision course is proportional on a short door and at eye level on a tall one")
    void visionCourseIsWhereSomebodyCanSeeThroughIt() {
        for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
            final int course = GarageDoorCurl.visionCourse(h);
            final float blocks = course * GarageDoorCurl.SLAT_PITCH / (float) GarageDoorCurl.PX;
            final float up = course / (float) GarageDoorCurl.slatCount(h);
            assertTrue(up <= 0.75f, "height " + h + " puts the slots " + up + " of the way up");
            assertTrue(blocks <= 2.0f,
                    "height " + h + " puts the slots " + blocks + " blocks up, out of anyone's sight");
            if (GarageDoorCurl.slatCount(h) * 2 / 3 <= GarageDoorCurl.EYE_COURSE) {
                assertTrue(up > 0.55f,
                        "height " + h + " is short enough to be proportional and is not");
            } else {
                assertEquals(GarageDoorCurl.EYE_COURSE, course,
                        "height " + h + " should have settled at eye level");
            }
        }
    }
}
