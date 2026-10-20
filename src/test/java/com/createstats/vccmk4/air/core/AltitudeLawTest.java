package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltitudeLawTest {
    private static final AltitudeLaw.Limits PLAN_LIMITS = new AltitudeLaw.Limits(4.0, 0.5);

    private static double[] fly(boolean cargo) {
        AirPlant pl = new AirPlant();
        pl.trim();
        VerticalObserver obs = new VerticalObserver();
        obs.reset(pl.mass, AirPlant.G);
        BurnerDither dither = new BurnerDither();
        double target = cargo ? 80.0 : 140.0;
        pl.signals = dither.signals(pl.gas, pl.dials);
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        int last = -1;
        double prevVy = 0.0;
        double prevMass = 0.0;
        double prevP = 0.0;
        double prevGas = 0.0;
        for (int k = 0; k < 2400; k++) {
            if (cargo && k == 100) pl.mass = 720.0;
            double p = pl.pressure();
            if (k > 0) {
                double ay = (pl.vy - prevVy) / AirPlant.DT;
                double known = AirPlant.G * GasLaw.STRENGTH * 0.5 * (prevGas + pl.gas) * prevP - prevMass * AirPlant.G;
                obs.observe(prevMass * ay - known, VerticalObserver.regressors(prevVy, 0.0, 0.0, prevP));
            }
            prevVy = pl.vy;
            prevMass = pl.mass;
            prevP = p;
            prevGas = pl.gas;
            double hidden = obs.predict(VerticalObserver.regressors(pl.vy, 0.0, 0.0, p));
            double dragRate = obs.estimate(VerticalObserver.DRAG) * p / pl.mass;
            AltitudeLaw.Plan plan = AltitudeLaw.plan(pl.y, pl.vy, target, pl.mass, AirPlant.G, hidden,
                    List.of(new AltitudeLaw.Cell(pl.gas, pl.capacity, GasLaw.STRENGTH, p, pl.maxGas())), PLAN_LIMITS, 0.0,
                    dragRate);
            pl.signals = dither.signals(plan.targets()[0], pl.dials);
            pl.step();
            max = Math.max(max, pl.y);
            min = Math.min(min, pl.y);
            if (Math.abs(pl.y - target) >= 0.5) last = k;
        }
        double extreme = cargo ? target - min : Math.max(0.0, max - target);
        return new double[] {extreme, (last + 1) / 20.0, obs.dragPerMass(600.0), pl.y};
    }

    @Test
    void climbsSixtyBlocksWithoutOvershoot() {
        double[] r = fly(false);
        assertTrue(r[0] <= 0.5, "overshoot " + r[0]);
        assertTrue(r[1] <= 30.0, "within half a block only from " + r[1] + " s");
        assertEquals(140.0, r[3], 0.5);
    }

    @Test
    void learnsTheDragFromAWrongStart() {
        double[] r = fly(false);
        assertEquals(0.6, r[2], 0.01, "drag per kpg, started from " + VerticalObserver.DRAG_PRIOR);
    }

    @Test
    void cargoDipsLessThanTheFirstAttempt() {
        double[] r = fly(true);

        assertTrue(r[0] <= 2.5, "dip " + r[0]);
        assertTrue(r[1] <= 30.0, "back within half a block only from " + r[1] + " s");
    }

    @Test
    void lookAheadIsTheGasNearTargetTimeConstant() {
        assertEquals(1.5, AltitudeLaw.LOOKAHEAD_S, 1.0e-9);
        assertEquals(0.0, AltitudeLaw.lookaheadError(100.0, 2.0, 103.0), 1.0e-9);
    }

    @Test
    void referenceSpeedCanStopOnTheMark() {
        AltitudeLaw.Limits l = new AltitudeLaw.Limits(3.0, 0.4);
        assertEquals(3.0, AltitudeLaw.referenceSpeed(100.0, l), 1.0e-9);
        assertEquals(-Math.sqrt(2.0 * 0.4 * 2.0), AltitudeLaw.referenceSpeed(-2.0, l), 1.0e-9);
        assertEquals(0.0, AltitudeLaw.referenceSpeed(0.0, l), 1.0e-9);
    }

    @Test
    void ceilingKeepsAReserve() {
        PressureModel air = PressureModel.sable(63.0);
        List<AltitudeLaw.Cell> cells = List.of(new AltitudeLaw.Cell(0.0, 900.0, 1.5, 1.0, 900.0));

        double expect = 63.0 + Math.log(AltitudeLaw.CEILING_RESERVE * 1.5 * 900.0 / 600.0) / PressureModel.SABLE_LAPSE;
        assertEquals(expect, AltitudeLaw.ceiling(600.0, 11.0, 0.0, cells, air), 1.0e-6);
        assertEquals(239.4, expect, 0.1);
        assertTrue(AltitudeLaw.ceiling(700.0, 11.0, 0.0, cells, air) < expect);
    }

    @Test
    void ceilingRisesWithHiddenLiftAndVanishesWhenItCarriesEverything() {
        PressureModel air = PressureModel.sable(63.0);
        List<AltitudeLaw.Cell> cells = List.of(new AltitudeLaw.Cell(0.0, 900.0, 1.5, 1.0, 900.0));
        double plain = AltitudeLaw.ceiling(600.0, 11.0, 0.0, cells, air);
        assertTrue(AltitudeLaw.ceiling(600.0, 11.0, 1000.0, cells, air) > plain);
        assertEquals(Double.POSITIVE_INFINITY, AltitudeLaw.ceiling(600.0, 11.0, 6600.0, cells, air));
    }

    @Test
    void tooHeavyAtAnyHeightIsSaidSo() {
        PressureModel air = PressureModel.sable(63.0);
        List<AltitudeLaw.Cell> cells = List.of(new AltitudeLaw.Cell(0.0, 900.0, 1.5, 1.0, 900.0));
        assertEquals(Double.NEGATIVE_INFINITY, AltitudeLaw.ceiling(5000.0, 11.0, 0.0, cells, air));
        assertTrue(Double.isNaN(AltitudeLaw.ceiling(600.0, 11.0, 0.0, List.of(), air)));
    }

    @Test
    void balloonsShareByTheirGreatestLift() {
        List<AltitudeLaw.Cell> cells = List.of(
                new AltitudeLaw.Cell(300.0, 600.0, 1.5, 1.0, 600.0),
                new AltitudeLaw.Cell(150.0, 300.0, 1.5, 1.0, 300.0));

        AltitudeLaw.Plan plan = AltitudeLaw.plan(100.0, 0.0, 100.0, 450.0 * 1.5, 11.0, 0.0, cells,
                new AltitudeLaw.Limits(3.0, 0.4));
        assertEquals(300.0, plan.targets()[0], 1.0);
        assertEquals(150.0, plan.targets()[1], 1.0);
    }

    @Test
    void aShipThrownUpFastIsLetGoNotHeldUp() {
        double drag = VerticalObserver.physicsDrag(1142, 697.5);
        List<AltitudeLaw.Cell> cells = List.of(new AltitudeLaw.Cell(1100.0, 1924.0, 1.5, 0.9, 1924.0));
        double hidden = drag * -15.0 * 0.9;
        AltitudeLaw.Plan plan = AltitudeLaw.plan(100.0, 15.0, 100.0, 697.5, 11.0, hidden, cells, Feel.STANDARD.altitude(),
                0.0, drag * 0.9 / 697.5);
        assertTrue(plan.accel() < -Feel.STANDARD.lift, "brakes harder than the comfort limit: " + plan.accel());
        assertEquals(0.0, plan.targets()[0], 1.0e-9, "every burner out while it sheds the climb");
    }

    @Test
    void aShipFallingFastIsCaughtHard() {
        AltitudeLaw.Limits l = Feel.STANDARD.altitude();
        double a = AltitudeLaw.accel(AltitudeLaw.referenceSpeed(-3.0, l), -10.0, 11.0, l, 0.57);
        assertTrue(a > 5.0, "a fall ten blocks a second is braked at " + a);
    }

    @Test
    void insideTheFeelsSpeedsTheLawIsGentle() {
        AltitudeLaw.Limits l = Feel.STANDARD.altitude();
        double rate = 0.57;
        assertEquals(l.accel(), AltitudeLaw.accel(3.0, 0.0, 11.0, l, rate), 1.0e-12, "from rest, climbing");
        assertEquals(-l.accel(), AltitudeLaw.accel(-3.0, 0.0, 11.0, l, rate), 1.0e-12, "from rest, sinking");
        assertEquals(l.accel(), AltitudeLaw.accel(3.0, 1.0, 11.0, l, rate), 1.0e-12, "climbing, not yet at the climb rate");
        assertEquals(-l.accel(), AltitudeLaw.accel(-3.0, -1.0, 11.0, l, rate), 1.0e-12, "sinking, not yet at the sink rate");
        assertEquals(l.accel() + rate * 1.0, AltitudeLaw.accel(0.0, -1.0, 11.0, l, rate), 1.0e-12,
                "sinking a block a second it should not: the comfort limit on top of the drag, no more");
    }

    @Test
    void targetsStayInsideWhatTheHeatersCanGive() {
        List<AltitudeLaw.Cell> cells = List.of(new AltitudeLaw.Cell(100.0, 900.0, 1.5, 1.0, 500.0));
        AltitudeLaw.Plan up = AltitudeLaw.plan(100.0, 0.0, 200.0, 5000.0, 11.0, 0.0, cells, new AltitudeLaw.Limits(3.0, 0.4));
        assertEquals(500.0, up.targets()[0], 1.0e-9);
        assertTrue(up.short_());
        AltitudeLaw.Plan down = AltitudeLaw.plan(100.0, 0.0, 0.0, 1.0, 11.0, 0.0, cells, new AltitudeLaw.Limits(3.0, 0.4));
        assertTrue(down.targets()[0] >= 0.0);
    }
}
