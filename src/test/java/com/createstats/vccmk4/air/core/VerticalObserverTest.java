package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalObserverTest {
    @Test
    void learnsEveryTermFromMixedFlight() {
        double mass = 600.0;
        double w = mass * 11.0;
        double[] truth = {360.0, 40.0, -0.15, 800.0};
        VerticalObserver o = new VerticalObserver();
        o.reset(mass, 11.0);
        Random rnd = new Random(7);
        for (int i = 0; i < 4000; i++) {
            double vy = 4.0 * Math.sin(i * 0.013);
            double airspeed = 10.0 + 8.0 * Math.sin(i * 0.004);
            double thrust = 1500.0 * Math.sin(i * 0.009) + 300.0;
            double p = 0.85 + 0.05 * Math.sin(i * 0.002);
            double[] phi = VerticalObserver.regressors(vy, airspeed, thrust, p);
            double r = 0.0;
            for (int k = 0; k < VerticalObserver.TERMS; k++) r += truth[k] * phi[k];
            o.observe(r + rnd.nextGaussian() * 0.005 * w, phi);
        }
        assertEquals(truth[VerticalObserver.DRAG], o.estimate(VerticalObserver.DRAG), 10.0);
        assertEquals(truth[VerticalObserver.WING], o.estimate(VerticalObserver.WING), 3.0);
        assertEquals(truth[VerticalObserver.THRUST], o.estimate(VerticalObserver.THRUST), 0.02);
        assertEquals(truth[VerticalObserver.CONSTANT], o.estimate(VerticalObserver.CONSTANT), 40.0);
    }

    @Test
    void wingsThatPressDownAreLearnedWithTheirSign() {
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        for (int i = 0; i < 2000; i++) {
            double airspeed = 12.0 + 6.0 * Math.sin(i * 0.01);
            double[] phi = VerticalObserver.regressors(0.0, airspeed, 0.0, 1.0);
            o.observe(-30.0 * airspeed, phi);
        }
        assertEquals(-30.0, o.estimate(VerticalObserver.WING), 1.0);
    }

    @Test
    void aClimbIsNotMistakenForWings() {
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        for (int i = 0; i < 2000; i++) {
            double vy = 3.0 * Math.sin(i * 0.02);
            o.observe(-360.0 * vy, VerticalObserver.regressors(vy, 0.0, 0.0, 1.0));
        }
        assertEquals(360.0, o.estimate(VerticalObserver.DRAG), 2.0);
        assertEquals(0.0, o.estimate(VerticalObserver.WING), 1.0e-9);
    }

    @Test
    void aCollisionIsNotLearnedFrom() {
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        for (int i = 0; i < 500; i++) o.observe(0.0, VerticalObserver.regressors(0.0, 0.0, 0.0, 1.0));
        double before = o.estimate(VerticalObserver.CONSTANT);
        assertFalse(o.observe(600.0 * 11.0 * 3.0, VerticalObserver.regressors(0.0, 0.0, 0.0, 1.0)));
        assertEquals(before, o.estimate(VerticalObserver.CONSTANT), 1.0e-9);
        assertEquals(1, o.skipped());
    }

    @Test
    void aLongHoverDoesNotWindUpTheWings() {
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        for (int i = 0; i < 5000; i++) o.observe(0.0, VerticalObserver.regressors(0.0, 0.0, 0.0, 1.0));

        assertTrue(o.variance(VerticalObserver.WING) <= o.prior(VerticalObserver.WING) * (1.0 + 1.0e-9));
        assertTrue(o.variance(VerticalObserver.CONSTANT) < o.prior(VerticalObserver.CONSTANT) * 0.01,
                "the constant, which the hover does excite, is known well");
    }

    @Test
    void restoreKeepsWhatWasLearned() {
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        o.restore(new double[] {300.0, 20.0, 0.1, 50.0});
        assertEquals(20.0, o.estimate(VerticalObserver.WING), 1.0e-12);
        assertEquals(0.5, o.dragPerMass(600.0), 1.0e-12);
    }

    @Test
    void theDragStartsFromTheShipsOwnEnvelopes() {
        double drag = VerticalObserver.physicsDrag(1142, 697.5);
        assertEquals(0.33 * 1142 + 0.09 * 697.5, drag, 1.0e-9);
        double p = Math.exp(-0.004 * (87.0 - 63.0));
        double deficit = 697.5 * 11.0 - 427.0 * 1.5 * p * 11.0;
        assertEquals(3.8, deficit / (0.33 * 1142 * p), 0.15, "the recorded sink, on the envelopes' drag");
        VerticalObserver o = new VerticalObserver();
        o.reset(697.5, 11.0, drag);
        assertEquals(drag, o.estimate(VerticalObserver.DRAG), 1.0e-9);
    }

    @Test
    void theDragNeverTurnsNegative() {
        VerticalObserver o = new VerticalObserver();
        double prior = VerticalObserver.physicsDrag(1142, 697.5);
        o.reset(697.5, 11.0, prior);
        for (int i = 0; i < 4000; i++) {
            double vy = 4.0 * Math.sin(i * 0.02);
            o.observe(664.0 * vy, VerticalObserver.regressors(vy, 0.0, 0.0, 1.0));
        }
        assertEquals(VerticalObserver.DRAG_FLOOR * prior, o.estimate(VerticalObserver.DRAG), 1.0e-9);
        assertTrue(o.estimate(VerticalObserver.DRAG) > 0.0);
    }

    @Test
    void hiddenLiftStaysInsideHalfTheWeight() {
        VerticalObserver o = new VerticalObserver();
        o.reset(697.5, 11.0, VerticalObserver.physicsDrag(1142, 697.5));
        for (int i = 0; i < 4000; i++) o.observe(697.5 * 11.0 * 0.9, VerticalObserver.regressors(0.0, 0.0, 0.0, 1.0));
        assertEquals(VerticalObserver.CONSTANT_SHARE * 697.5 * 11.0, o.estimate(VerticalObserver.CONSTANT), 1.0e-6);
    }

    @Test
    void aRestoredStateIsBroughtInsideTheBounds() {
        VerticalObserver o = new VerticalObserver();
        double prior = VerticalObserver.physicsDrag(1142, 697.5);
        o.reset(697.5, 11.0, prior);
        o.restore(new double[] {-664.0, 10.66, 5.0, 4035.6});
        assertEquals(VerticalObserver.DRAG_FLOOR * prior, o.estimate(VerticalObserver.DRAG), 1.0e-9);
        assertEquals(10.66, o.estimate(VerticalObserver.WING), 1.0e-12);
        assertEquals(VerticalObserver.LEAN_MAX, o.estimate(VerticalObserver.THRUST), 1.0e-12);
        assertEquals(VerticalObserver.CONSTANT_SHARE * 697.5 * 11.0, o.estimate(VerticalObserver.CONSTANT), 1.0e-6);
    }

    @Test
    void aRebuiltShipMovesTheDragBounds() {
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0, 100.0);
        o.restore(new double[] {80.0, 0.0, 0.0, 0.0});
        assertEquals(80.0, o.estimate(VerticalObserver.DRAG), 1.0e-12);
        o.setDragPrior(400.0);
        assertEquals(VerticalObserver.DRAG_FLOOR * 400.0, o.estimate(VerticalObserver.DRAG), 1.0e-9);
        assertEquals(VerticalObserver.DRAG_CEILING * 400.0, o.bounds(VerticalObserver.DRAG)[1], 1.0e-9);
    }

    @Test
    void aStepLoadReopensTheLearning() {
        double w = 600.0 * 11.0;
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        o.setLoadRoom(3.0 * w);
        double[] still = VerticalObserver.regressors(0.0, 0.0, 0.0, 1.0);
        for (int i = 0; i < 2000; i++) o.observe(0.0, still);
        for (int i = 0; i < VerticalObserver.REOPEN - 1; i++) assertFalse(o.observe(-1.2 * w, still), "a few far-out samples are bumps");
        assertTrue(o.observe(-1.2 * w, still), "the same again, all one way, is the ship changing");
        for (int i = 0; i < 100; i++) o.observe(-1.2 * w, still);
        assertEquals(-1.2 * w, o.estimate(VerticalObserver.CONSTANT), 0.01 * w);
        assertEquals(1.2 * w, o.load(), 0.01 * w);
        assertEquals(1, o.reopened());
    }

    @Test
    void bumpsBothWaysDoNotReopen() {
        double w = 600.0 * 11.0;
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        double[] still = VerticalObserver.regressors(0.0, 0.0, 0.0, 1.0);
        for (int i = 0; i < 2000; i++) o.observe(0.0, still);
        double before = o.estimate(VerticalObserver.CONSTANT);
        for (int i = 0; i < 40; i++) assertFalse(o.observe((i % 2 == 0 ? 3.0 : -3.0) * w, still));
        assertEquals(0, o.reopened());
        assertEquals(before, o.estimate(VerticalObserver.CONSTANT), 1.0e-9);
    }

    @Test
    void theLoadRoomOnlyWidens() {
        double w = 600.0 * 11.0;
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        assertEquals(-VerticalObserver.CONSTANT_SHARE * w, o.bounds(VerticalObserver.CONSTANT)[0], 1.0e-9);
        o.setLoadRoom(2.0 * w);
        o.setLoadRoom(0.1 * w);
        assertEquals(-2.0 * w, o.bounds(VerticalObserver.CONSTANT)[0], 1.0e-9, "a burner out for a tick does not narrow it");
        assertEquals(VerticalObserver.CONSTANT_SHARE * w, o.bounds(VerticalObserver.CONSTANT)[1], 1.0e-9);
        o.atMost(-5.0 * w);
        assertEquals(-2.0 * w, o.estimate(VerticalObserver.CONSTANT), 1.0e-9, "no heavier than all the ship could lift");
    }

    @Test
    void theWeighOffOnlyLowersAndATakeoffDropsTheLoad() {
        VerticalObserver o = new VerticalObserver();
        o.reset(600.0, 11.0);
        o.setLoadRoom(2.0 * 600.0 * 11.0);
        o.restore(new double[] {300.0, 0.0, 0.0, 300.0});
        assertFalse(o.atMost(500.0), "a bound above the estimate says nothing new");
        assertEquals(300.0, o.estimate(VerticalObserver.CONSTANT), 1.0e-9);
        assertTrue(o.atMost(-900.0));
        assertEquals(-900.0, o.estimate(VerticalObserver.CONSTANT), 1.0e-9);
        assertEquals(900.0, o.load(), 1.0e-9);
        o.dropLoad();
        assertEquals(0.0, o.estimate(VerticalObserver.CONSTANT), 1.0e-9);
        o.restore(new double[] {300.0, 0.0, 0.0, 300.0});
        o.dropLoad();
        assertEquals(300.0, o.estimate(VerticalObserver.CONSTANT), 1.0e-9, "levitite's lift is not a load");
    }
}
