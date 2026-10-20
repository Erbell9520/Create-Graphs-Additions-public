package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkTrimTest {
    @Test
    void aSlackRopeHoldsTheTrailerBackAndAStretchedOnePushesItOn() {
        assertTrue(LinkTrim.rope(100, -0.5, 0.0) < 0.0, "slack: the trailer is too close");
        assertTrue(LinkTrim.rope(100, 0.3, 0.0) > 0.0, "stretched: the rope is pulling it");
        assertEquals(0.0, LinkTrim.rope(100, 0.0, 0.0), 1e-9, "just taut pulls on nothing");
        assertTrue(LinkTrim.rope(100, 0.0, 1.0) > 0.0, "tightening fast: ease it in before the jerk");
    }

    @Test
    void theTrimIsLimitedInProportionToTheTrailersMass() {
        assertEquals(LinkTrim.LIMIT * 100, LinkTrim.rope(100, 50.0, 0.0), 1e-9);
        assertEquals(-LinkTrim.LIMIT * 40, LinkTrim.rope(40, -50.0, -9.0), 1e-9);
        assertEquals(0.0, LinkTrim.rope(0, 1.0, 0.0), 1e-9);
    }

    @Test
    void theSpringsForceIsItsOwnHookesLaw() {
        assertEquals(72.5, LinkTrim.springForce(1.0, 1.0), 1e-9, "a medium spring, one block over");
        assertEquals(-72.5 * 8 * 0.25, LinkTrim.springForce(-0.25, 8.0), 1e-9, "a large one, compressed");
        double trim = LinkTrim.spring(1000, 0.5, 0.0, 1.0);
        assertEquals(LinkTrim.SPRING_SHARE * 72.5 * 0.5, trim, 1e-9, "the trailer takes most of the spring's pull over");
        assertTrue(LinkTrim.spring(1000, 0.0, 0.5, 1.0) > 0.0, "stretching: damped");
    }

    @Test
    void aSpringBreaksAtFourTimesItsLengthAndTwo() {
        assertEquals(14.0, LinkTrim.snapLength(3.0), 1e-9);
        assertEquals(0.5, LinkTrim.snapShare(7.0, 3.0), 1e-9);
        assertEquals(0.0, LinkTrim.snapShare(-1.0, 3.0), 1e-9);
    }

    @Test
    void aLinkThatReadsItsOwnPullIsCorrectedOnThePull() {
        assertEquals(LinkTrim.SPRING_SHARE * 100.0, LinkTrim.stretch(1000, 100.0, 0.0), 1e-9);
        assertEquals(LinkTrim.spring(1000, 0.5, 0.2, 1.0), LinkTrim.stretch(1000, LinkTrim.springForce(0.5, 1.0), 0.2), 1e-9,
                "a spring is one such link");
        assertEquals(0.0, LinkTrim.stretch(1000, Double.NaN, 0.0), 1e-9);
    }

    @Test
    void theCorrectionFollowsWhatTheLinkReads() {
        assertEquals(LinkTrim.rope(100, -0.5, 0.1), LinkTrim.of(LinkKind.Reading.GAP, 100, -0.5, Double.NaN, 0.1), 1e-9);
        assertEquals(LinkTrim.stretch(100, 40.0, 0.1), LinkTrim.of(LinkKind.Reading.STRETCH, 100, 0.5, 40.0, 0.1), 1e-9);
        assertEquals(0.0, LinkTrim.of(LinkKind.Reading.ANGLE, 100, 12.0, 0.0, 0.0), 1e-9, "an angle is steered, not pulled on");
        assertEquals(0.0, LinkTrim.of(LinkKind.Reading.NONE, 100, 0.0, 0.0, 0.0), 1e-9);
        assertEquals(0.0, LinkTrim.of(LinkKind.Reading.GAP, 100, Double.NaN, 0.0, 0.0), 1e-9, "nothing read, nothing done");
    }

    @Test
    void everyLinkKindSaysWhatItIs() {
        for (LinkKind k : LinkKind.values()) {
            assertTrue(k.key().matches("[a-z_]+"), k + " has a lang-safe key");
            if (k.rigid()) assertEquals(LinkKind.Reading.NONE, k.reading(), k + " is rigid, so it has nothing to read");
            if (k.rigid()) assertTrue(k.holds(), k + " is rigid, so it holds what hangs on it");
            assertFalse(k.noun().isBlank(), k + " has a name for the server's lines");
            assertFalse(k.towedAs().isBlank(), k + " says how a trailer hangs on it");
        }
        assertTrue(LinkKind.HITCH.holds(), "a pod on a swivel is a part");
        assertFalse(LinkKind.ROPE.holds(), "a crate on a rope is a load");
        assertFalse(LinkKind.SPRING.holds(), "and on springs");
        assertTrue(LinkKind.DOCK.stifferThan(LinkKind.JOINT));
        assertTrue(LinkKind.JOINT.stifferThan(LinkKind.HITCH));
        assertTrue(LinkKind.HITCH.stifferThan(LinkKind.SPRING));
        assertTrue(LinkKind.SPRING.stifferThan(LinkKind.ROPE));
        assertTrue(LinkKind.ROPE.stifferThan(null));
        assertFalse(LinkKind.ROPE.pushes(), "a rope only pulls");
    }
}
