package com.createstats.vacuum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoseSwirlXpTest {
    @Test
    void theHoseReachesEightBlocksAndNoFurther() {
        final double[] port = {0.5, 64.5, 0.5};
        assertTrue(HoseTether.reaches(port, new double[] {8.5, 64.5, 0.5}), "exactly eight");
        assertFalse(HoseTether.reaches(port, new double[] {8.6, 64.5, 0.5}));
        assertTrue(HoseTether.reaches(port, new double[] {4.5, 68.5, 4.5}), "diagonal, within eight");
    }

    @Test
    void aTautHoseDoesNotSagAndASlackOneSagsButNotThroughTheFloor() {
        assertEquals(0.0, HoseTether.sag(HoseTether.LENGTH, 5.0), 1e-9, "all eight blocks used: taut");
        assertTrue(HoseTether.sag(2.0, 10.0) > HoseTether.sag(6.0, 10.0), "closer ends, deeper loop");
        assertEquals(0.4, HoseTether.sag(1.0, 0.4), 1e-9, "never below the floor");
    }

    @Test
    void theHoseStartsAtThePortAndEndsInTheHand() {
        final double[] port = {0, 1, 0};
        final double[] head = {4, 2, 3};
        final double[] a = HoseTether.point(port, head, 1.5, 0.0);
        final double[] b = HoseTether.point(port, head, 1.5, 1.0);
        assertEquals(0.0, HoseTether.distance(a, port), 1e-9);
        assertEquals(0.0, HoseTether.distance(b, head), 1e-9);

        assertEquals(0.0, HoseTether.point(port, head, 1.5, 0.5)[1], 1e-9, "dips by the sag at the middle");
    }

    @Test
    void theSwirlStartsWhereTheItemWasAndEndsInTheMouth() {
        final double[] from = {3, 1, -2};
        final double[] to = {0.5, 0.6, 0.1};
        final double[] s = SwirlPath.at(from, to, 0.6, 1.5, 0.3, 0.0);
        final double[] e = SwirlPath.at(from, to, 0.6, 1.5, 0.3, 1.0);
        assertEquals(0.0, HoseTether.distance(s, from), 1e-9);
        assertEquals(0.0, HoseTether.distance(e, to), 1e-9);
    }

    @Test
    void theSwirlNeverStraysFurtherThanItsRadiusFromTheLine() {
        final double[] from = {0, 0, 0};
        final double[] to = {0, 0, -5};
        for (int i = 0; i <= 50; i++) {
            final double[] p = SwirlPath.at(from, to, 0.8, 2.0, 0.0, i / 50.0);
            final double off = Math.hypot(p[0], p[1]);
            assertTrue(off <= 0.8 + 1e-9, "t=" + i / 50.0 + " off " + off);
        }
    }

    @Test
    void theSwirlSpeedsUpIntoTheMouth() {
        assertTrue(SwirlPath.eased(0.2) < 0.2);
        assertTrue(SwirlPath.eased(0.9) - SwirlPath.eased(0.8) > SwirlPath.eased(0.2) - SwirlPath.eased(0.1));
        assertEquals(1.0, SwirlPath.eased(3.0), 1e-9);
        assertEquals(0.0, SwirlPath.eased(-1.0), 1e-9);
    }

    @Test
    void threePointsMakeOneNuggetExactlyAsCreateGivesThemBack() {
        final XpCondenser c = new XpCondenser();
        assertEquals(7, c.add(7));
        assertEquals(2, c.nuggetsReady());
        assertEquals(2, c.takeNuggets(5));
        assertEquals(1, c.points(), "the remainder waits for the next orb");
    }

    @Test
    void theCondenserConservesExperience() {
        final XpCondenser c = new XpCondenser();
        int in = 0;
        int nuggets = 0;
        for (int orb : new int[] {1, 3, 7, 17, 37, 2}) {
            in += c.add(orb);
            nuggets += c.takeNuggets(Integer.MAX_VALUE);
        }
        assertEquals(in, nuggets * XpCondenser.POINTS_PER_NUGGET + c.points(), "no point made or lost");
    }

    @Test
    void aFullCondenserLeavesOrbsOnTheFloor() {
        final XpCondenser c = new XpCondenser();
        assertEquals(XpCondenser.CAPACITY, c.add(XpCondenser.CAPACITY + 50));
        assertEquals(0, c.add(10));
        assertEquals(1.0f, c.charge(), 1e-6f);
    }
}
