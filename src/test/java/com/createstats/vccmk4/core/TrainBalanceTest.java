package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainBalanceTest {
    private static List<WheelForce.Wheel> wheels(int driven, int free) {
        List<WheelForce.Wheel> out = new ArrayList<>();
        for (int i = 0; i < driven; i++) out.add(WheelForce.Wheel.typical(true));
        for (int i = 0; i < free; i++) out.add(WheelForce.Wheel.typical(false));
        return out;
    }

    @Test
    void theTractorAloneAcceleratesByItsOwnForceOverItsOwnMass() {
        assertEquals(2.0, TrainBalance.tractorAcceleration(100, 260, 15, 4), 1e-9);
        assertEquals(0.0, TrainBalance.tractorAcceleration(0, 260, 15, 4), 1e-9, "no mass, no answer, no nonsense");
    }

    @Test
    void aTrailerTwiceAsHeavyIsAskedForTwiceTheForce() {
        List<TrainBalance.Body> train = List.of(
                new TrainBalance.Body(-1, 100, wheels(4, 0)),
                new TrainBalance.Body(0, 200, wheels(2, 2)));
        int[] carriers = TrainBalance.carriers(train);
        assertEquals(400.0, TrainBalance.demand(1, train, carriers, 2.0, 5.0, 1), 1e-9);
    }

    @Test
    void atCruiseATrailerPaysOnlyItsOwnDragAndMatchesTheTractorsRpm() {
        List<WheelForce.Wheel> tw = wheels(4, 0);
        double v = 10.0;
        double drag = WheelForce.rolling(tw);

        double tractorMount = drag * v / WheelForce.drivePerSpeed(tw);
        double a0 = TrainBalance.tractorAcceleration(100, WheelForce.drivePerSpeed(tw) * tractorMount, drag, v);
        assertEquals(0.0, a0, 1e-9);
        List<TrainBalance.Body> train = List.of(new TrainBalance.Body(-1, 100, tw), new TrainBalance.Body(0, 300, wheels(4, 0)));
        double demand = TrainBalance.demand(1, train, TrainBalance.carriers(train), a0, v, 1);
        TrainBalance.Allocation a = TrainBalance.allocate(demand, v, train.get(1).wheels(), 256, false);
        assertEquals(tractorMount, a.mountSpeed(), 1e-9, "same wheels at cruise: same RPM, whatever the mass");
        assertEquals(0.0, a.brake(), 1e-9);
    }

    @Test
    void unpoweredTrailersAreCarriedByThePoweredOneAheadOfThem() {
        List<TrainBalance.Body> train = List.of(
                new TrainBalance.Body(-1, 100, wheels(4, 0)),
                new TrainBalance.Body(0, 150, wheels(2, 0)),
                new TrainBalance.Body(1, 80, wheels(0, 2)),
                new TrainBalance.Body(2, 60, wheels(0, 2)));
        assertArrayEquals(new int[]{-1, 1, 1, 1}, TrainBalance.carriers(train));
        List<TrainBalance.Body> headless = List.of(
                new TrainBalance.Body(-1, 100, wheels(4, 0)),
                new TrainBalance.Body(0, 80, wheels(0, 2)),
                new TrainBalance.Body(1, 150, wheels(2, 0)));
        assertArrayEquals(new int[]{-1, -1, 2}, TrainBalance.carriers(headless), "an unpowered trailer right behind the tractor is the tractor's");
    }

    @Test
    void aCarrierTakesOnPropulsionButNeverTheBrakingOfWhatItCarries() {
        List<TrainBalance.Body> train = List.of(
                new TrainBalance.Body(-1, 100, wheels(4, 0)),
                new TrainBalance.Body(0, 150, wheels(2, 0)),
                new TrainBalance.Body(1, 80, wheels(0, 2)));
        int[] carriers = TrainBalance.carriers(train);
        double v = 6.0;
        double pulling = TrainBalance.demand(1, train, carriers, 1.5, v, 1);
        assertEquals(150 * 1.5 + 80 * 1.5 + 30.0 * v, pulling, 1e-9, "its own mass, the carried mass and the carried drag");
        double slowing = TrainBalance.demand(1, train, carriers, -3.0, v, 1);
        assertEquals(150 * -3.0 * (1 + TrainBalance.BRAKE_LEAD), slowing, 1e-9, "the carried trailer brakes itself");
    }

    @Test
    void whileSlowingEveryTrailerIsAskedToSlowAShadeHarder() {
        assertEquals(-2.2, TrainBalance.lead(-2.0, 1), 1e-9);
        assertEquals(2.2, TrainBalance.lead(2.0, -1), 1e-9, "slowing while reversing is a positive acceleration");
        assertEquals(2.0, TrainBalance.lead(2.0, 1), 1e-9, "no lead while speeding up");
    }

    @Test
    void aForceAgainstTheRollIsTakenByTheBrakes() {
        List<WheelForce.Wheel> w = wheels(2, 2);
        double v = 8.0;

        TrainBalance.Allocation a = TrainBalance.allocate(-600, v, w, 256, false);
        assertEquals(0.0, a.mountSpeed(), 1e-9);
        double brakeK = WheelForce.brakeCapacity(w);
        assertEquals(120.0 / (brakeK * v), a.brake(), 1e-9);
        assertEquals(0.0, a.unmet(), 1e-9);
    }

    @Test
    void reversingTheSameForceIsDriveNotBrake() {
        List<WheelForce.Wheel> w = wheels(2, 0);
        TrainBalance.Allocation a = TrainBalance.allocate(-300, -4.0, w, 256, false);
        assertTrue(a.mountSpeed() < 0.0, "backwards along a backwards roll is propulsion");
        assertEquals(0.0, a.brake(), 1e-9);
    }

    @Test
    void whatTheWheelsCannotMakeIsLeftForTheJets() {
        List<WheelForce.Wheel> w = wheels(1, 1);
        TrainBalance.Allocation a = TrainBalance.allocate(2000, 2.0, w, 100, false);
        assertEquals(100.0, a.mountSpeed(), 1e-9);
        assertEquals(2000 + 30.0 * 2.0 - 175.0, a.unmet(), 1e-9);
        TrainBalance.Allocation none = TrainBalance.allocate(500, 2.0, wheels(0, 2), 256, false);
        assertEquals(0.0, none.mountSpeed(), 1e-9, "an unpowered trailer cannot drive");
        assertTrue(none.unmet() > 0.0);
    }

    @Test
    void heldAtRestEveryTrailerHoldsFullBrake() {
        TrainBalance.Allocation a = TrainBalance.allocate(0, 0.1, wheels(2, 2), 256, true);
        assertEquals(1.0, a.brake(), 1e-9);
        assertEquals(15, a.brakeSignal());
    }

    @Test
    void theBrakeSignalRoundsUp() {
        assertEquals(1, new TrainBalance.Allocation(0, 0.01, 0, 0).brakeSignal());
        assertEquals(0, new TrainBalance.Allocation(0, 0.0, 0, 0).brakeSignal());
        assertEquals(8, new TrainBalance.Allocation(0, 0.5, 0, 0).brakeSignal());
    }

    @Test
    void assistTakesTheTrailerOffTheTractorFromRestToCruise() {
        Run assist = run(true, 180, wheels(4, 0), 300, false);
        Run mirror = run(false, 180, wheels(4, 0), 300, false);
        assertTrue(mirror.peakBar() > 150.0, "copying the RPM loads the bar with the trailer's inertia: " + mirror.peakBar());
        assertTrue(assist.peakBar() < mirror.peakBar() * 0.05, "Assist loads the bar " + assist.peakBar() + " against Mirror's " + mirror.peakBar());
        assertEquals(mirror.speed(), assist.speed(), mirror.speed() * 0.5, "both reach a cruise");
    }

    @Test
    void assistStopsWithTheLinkInTension() {
        Run stop = run(true, 80, wheels(2, 2), 300, true);
        assertTrue(stop.minBarWhileBraking() >= -1.0e-6, "the trailer pushed the tractor: " + stop.minBarWhileBraking());
        assertTrue(stop.speed() < 1.0, "the train stopped: " + stop.speed());
    }

    private record Run(double peakBar, double minBarWhileBraking, double speed) {
    }

    private static Run run(boolean assist, double m1, List<WheelForce.Wheel> w1, int driveTicks, boolean thenBrake) {
        double m0 = 100;
        List<WheelForce.Wheel> w0 = wheels(4, 0);
        List<TrainBalance.Body> train = List.of(new TrainBalance.Body(-1, m0, w0), new TrainBalance.Body(0, m1, w1));
        int[] carriers = TrainBalance.carriers(train);
        double v = 0.0;
        double dt = 0.05;
        double peak = 0.0;
        double minBraking = Double.MAX_VALUE;
        int total = thenBrake ? driveTicks + 200 : driveTicks;
        for (int tick = 0; tick < total; tick++) {
            boolean braking = tick >= driveTicks;
            double mount0 = braking ? 0.0 : 128.0;
            double b0 = braking ? 1.0 : 0.0;
            double[] f0 = TrainBalance.wheelForces(w0, new double[]{mount0, mount0, mount0, mount0}, new double[]{b0, b0, b0, b0});
            double a0 = TrainBalance.tractorAcceleration(m0, f0[0], f0[1], v);
            double[] mounts = new double[w1.size()];
            double[] brakes = new double[w1.size()];
            double mount1;
            double b1;
            if (assist) {
                double demand = TrainBalance.demand(1, train, carriers, a0, v, TrainBalance.travel(v));
                TrainBalance.Allocation a = TrainBalance.allocate(demand, v, w1, 256, braking && Math.abs(v) < TrainBalance.REST_SPEED);
                mount1 = a.mountSpeed();
                b1 = a.brake();
            } else {
                mount1 = mount0;
                b1 = b0;
            }
            for (int i = 0; i < w1.size(); i++) {
                mounts[i] = w1.get(i).driven() ? mount1 : 0.0;
                brakes[i] = b1;
            }
            double[] f1 = TrainBalance.wheelForces(w1, mounts, brakes);
            double tractorNet = f0[0] - f0[1] * v;
            double trailerNet = f1[0] - f1[1] * v;
            double a = (tractorNet + trailerNet) / (m0 + m1);

            double bar = m1 * a - trailerNet;
            if (tick > 2 && !braking) peak = Math.max(peak, Math.abs(bar));
            if (braking && Math.abs(v) > TrainBalance.REST_SPEED) minBraking = Math.min(minBraking, bar);
            v += a * dt;
        }
        return new Run(peak, minBraking == Double.MAX_VALUE ? 0.0 : minBraking, v);
    }

    @Test
    void aTrailerThatPushesItselfOnJetsIsNobodyElsesToCarry() {
        List<TrainBalance.Body> train = List.of(
                new TrainBalance.Body(-1, 100, wheels(4, 0)),
                new TrainBalance.Body(0, 150, wheels(2, 0)),
                new TrainBalance.Body(1, 80, wheels(0, 2), true),
                new TrainBalance.Body(2, 60, wheels(0, 2)));
        assertArrayEquals(new int[]{-1, 1, 2, 2}, TrainBalance.carriers(train));
        double v = 6.0;
        assertEquals(150 * 1.5, TrainBalance.demand(1, train, TrainBalance.carriers(train), 1.5, v, 1), 1e-9,
                "the trailer ahead pays only for itself");
        assertEquals(80 * 1.5 + 60 * 1.5 + 30.0 * v, TrainBalance.demand(2, train, TrainBalance.carriers(train), 1.5, v, 1), 1e-9,
                "the jet trailer pays for itself and the unpowered one behind it");
    }
}
