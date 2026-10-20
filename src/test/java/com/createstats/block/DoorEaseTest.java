package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorEaseTest {
    private static final int STEPS = 2000;

    private static final float EPSILON = 1.0e-4f;

    @Test
    @DisplayName("every curve is pinned at both ends, in both directions")
    void curvesArePinned() {
        for (final DoorEase ease : DoorEase.values()) {
            for (final boolean opening : new boolean[]{true, false}) {
                final String what = ease + (opening ? " opening" : " closing");
                assertEquals(0.0f, ease.curve(0.0f, opening), EPSILON, what + " must be shut at 0");
                assertEquals(1.0f, ease.curve(1.0f, opening), EPSILON, what + " must be open at 1");
            }
        }
    }

    @Test
    @DisplayName("no curve ever leaves [0, 1], which is what stops a swing rotating through a wall")
    void curvesStayInRange() {
        for (final DoorEase ease : DoorEase.values()) {
            for (final boolean opening : new boolean[]{true, false}) {
                for (int i = 0; i <= STEPS; i++) {
                    final float t = (float) i / STEPS;
                    final float v = ease.curve(t, opening);
                    assertTrue(v >= -EPSILON && v <= 1.0f + EPSILON,
                            ease + (opening ? " opening" : " closing")
                                    + " left [0,1] at t=" + t + " with " + v);
                }
            }
        }
    }

    @Test
    @DisplayName("a curve is finite everywhere, so no motion can be handed a NaN pose")
    void curvesAreFinite() {
        for (final DoorEase ease : DoorEase.values()) {
            for (final boolean opening : new boolean[]{true, false}) {
                for (int i = 0; i <= STEPS; i++) {
                    final float v = ease.curve((float) i / STEPS, opening);
                    assertTrue(Float.isFinite(v), ease + " produced " + v);
                }
            }
        }
    }

    @Test
    @DisplayName("SERVO is symmetric: a motor does not care which way it turns")
    void servoIsSymmetric() {
        for (int i = 0; i <= STEPS; i++) {
            final float t = (float) i / STEPS;
            assertEquals(DoorEase.SERVO.curve(t, true), DoorEase.SERVO.curve(t, false), EPSILON,
                    "SERVO differed by direction at t=" + t);
        }
    }

    @Test
    @DisplayName("SWING rebounds off the frame on the way shut, and only on the way shut")
    void swingReboundsOnlyWhenClosing() {
        boolean rebounded = false;
        for (int i = 0; i <= STEPS; i++) {
            final float t = (float) i / STEPS;
            final float open = DoorEase.SWING.curve(t, true);
            final float shut = DoorEase.SWING.curve(t, false);
            assertEquals(DoorEase.smoothstep(t), open, EPSILON,
                    "an opening swing should be plain smoothstep at t=" + t);
            if (shut > open + 1.0e-3f) {
                rebounded = true;
            }
        }
        assertTrue(rebounded, "a closing swing should spring back off the jamb somewhere");
    }

    @Test
    @DisplayName("DRUM falls faster than it climbs: a shutter is wound up and dropped")
    void drumIsAsymmetric() {
        final float climbing = DoorEase.DRUM.curve(0.5f, true);
        final float falling = DoorEase.DRUM.curve(0.5f, false);
        assertTrue(falling > climbing + 0.05f,
                "expected the fall to lead the climb at the midpoint, got climb=" + climbing
                        + " fall=" + falling);
    }

    @Test
    @DisplayName("every curve is non-decreasing except for the swing's one deliberate rebound")
    void curvesAreMonotonicApartFromTheRebound() {
        for (final DoorEase ease : DoorEase.values()) {
            for (final boolean opening : new boolean[]{true, false}) {
                if (ease == DoorEase.SWING && !opening) {
                    continue;
                }
                float previous = ease.curve(0.0f, opening);
                for (int i = 1; i <= STEPS; i++) {
                    final float v = ease.curve((float) i / STEPS, opening);
                    assertTrue(v >= previous - EPSILON,
                            ease + (opening ? " opening" : " closing")
                                    + " went backwards at t=" + (float) i / STEPS);
                    previous = v;
                }
            }
        }
    }
}
