package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.function.LongToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunDriveTest {
    private static final double YAW = GunDrive.yawLaw();
    private static final double PITCH_SOUTH = GunDrive.pitchLaw(true, false);

    private static final class Rig {
        final double law;
        final double k;
        double field;
        int commanded;
        int applied;
        long lastRebuild = -100;
        double lo = -1e9;
        double hi = 1e9;

        boolean stalled;

        boolean deadLine;
        int maxWrongWay;

        Rig(double law, double k) {
            this.law = law;
            this.k = k;
        }

        double interfaceRpm() {
            return deadLine ? 0.0 : k * applied;
        }

        void tick(long now) {
            if (commanded != applied && now - lastRebuild >= GunDrive.PERIOD) {
                applied = commanded;
                lastRebuild = now;
            }
            if (!stalled) field = Math.max(lo, Math.min(hi, field + law * interfaceRpm()));
        }
    }

    private static double run(GunDrive.Axis a, Rig rig, double driveLaw, LongToDoubleFunction wanted, double maxRate,
                              long from, int ticks) {
        double overshoot = 0.0;
        double start = rig.field;
        double firstWanted = wanted.applyAsDouble(from);
        double dir = Math.signum(firstWanted - start);
        for (long now = from; now < from + ticks; now++) {
            double before = rig.field;
            rig.tick(now);
            boolean atTop = rig.field >= rig.hi - 1e-9;
            boolean atBottom = rig.field <= rig.lo + 1e-9;
            GunDrive.observe(a, driveLaw, rig.field - before, rig.applied, rig.interfaceRpm(), atTop || atBottom, false, now);
            double w = wanted.applyAsDouble(now);
            rig.commanded = GunDrive.command(a, driveLaw, w, rig.field, maxRate, atTop, atBottom, now);
            if (dir != 0) overshoot = Math.max(overshoot, (rig.field - w) * dir);
            if (dir != 0) rig.maxWrongWay = (int) Math.max(rig.maxWrongWay, Math.ceil((start - rig.field) * dir * 100));
        }
        return overshoot;
    }

    @Test
    void cbcsPitchSignFollowsTheFacing() {
        assertEquals(-GunDrive.DEG_PER_RPM_TICK, GunDrive.pitchLaw(true, false), 1e-12, "south");
        assertEquals(GunDrive.DEG_PER_RPM_TICK, GunDrive.pitchLaw(false, false), 1e-12, "north");
        assertEquals(GunDrive.DEG_PER_RPM_TICK, GunDrive.pitchLaw(true, true), 1e-12, "east");
        assertEquals(-GunDrive.DEG_PER_RPM_TICK, GunDrive.pitchLaw(false, true), 1e-12, "west");
        assertEquals(-GunDrive.DEG_PER_RPM_TICK, GunDrive.yawLaw(), 1e-12, "yaw turns by minus the interface");
    }

    @Test
    void aOneToOneLineSettlesNinetyDegreesWithoutOvershoot() {
        GunDrive.Axis a = new GunDrive.Axis(0, 0, true);
        Rig rig = new Rig(YAW, 1.0);
        double over = run(a, rig, YAW, t -> 90.0, GunDrive.RATE_AUTOCANNON, 0, 120);
        assertEquals(90.0, rig.field, 0.2);
        assertTrue(over < 0.3, "overshoot " + over);
        assertEquals(1.0, a.ratio(), 1e-9);
    }

    @Test
    void everyTickOrderSettlesWithoutOvershootOrAFalseFault() {
        for (String order : new String[]{"GMB", "GBM", "MGB", "MBG", "BGM", "BMG"}) {
            for (double k : new double[]{1.0, 2.0}) {
                for (double rate : new double[]{GunDrive.RATE_AUTOCANNON, GunDrive.RATE_BIG_CANNON}) {
                    GunDrive.Axis a = new GunDrive.Axis(0, 0, true);
                    Rig rig = new Rig(YAW, k);
                    double seen = 0.0;
                    double over = 0.0;
                    for (long now = 0; now < 200; now++) {
                        for (char c : order.toCharArray()) {
                            if (c == 'G') {
                                if (rig.commanded != rig.applied && now - rig.lastRebuild >= GunDrive.PERIOD) {
                                    rig.applied = rig.commanded;
                                    rig.lastRebuild = now;
                                }
                            } else if (c == 'M') {
                                rig.field += YAW * rig.interfaceRpm();
                            } else {
                                GunDrive.observe(a, YAW, rig.field - seen, rig.applied, rig.interfaceRpm(), false, false, now);
                                seen = rig.field;
                                rig.commanded = GunDrive.command(a, YAW, 90.0, rig.field, rate, false, false, now);
                                assertFalse(a.faulted(now), order + " k=" + k + ": no fault on a healthy line");
                            }
                        }
                        over = Math.max(over, rig.field - 90.0);
                    }
                    String what = order + " k=" + k + " rate=" + rate;
                    assertEquals(90.0, rig.field, 0.2, what);
                    assertTrue(over < 0.05, what + " overshoot " + over);
                }
            }
        }
    }

    @Test
    void theFirstPeriodIsAProbeAndTheRatioIsReadOneTickIntoMotion() {
        GunDrive.Axis a = new GunDrive.Axis(0, 0, false);
        Rig rig = new Rig(PITCH_SOUTH, 2.5);
        int first = GunDrive.command(a, PITCH_SOUTH, 30.0, 0.0, GunDrive.RATE_BIG_CANNON, false, false, 0);
        assertTrue(Math.abs(first) <= GunDrive.PROBE_RPM, "probe " + first);
        rig.commanded = first;
        for (long now = 1; now <= 3 && !a.known(); now++) {
            double before = rig.field;
            rig.tick(now);
            GunDrive.observe(a, PITCH_SOUTH, rig.field - before, rig.applied, rig.interfaceRpm(), false, false, now);
        }
        assertEquals(2.5, a.ratio(), 1e-9, "a 2.5:1 line is read, not learned");
    }

    @Test
    void aGearedLineIsDrivenAtTheKindsBarrelRate() {
        GunDrive.Axis a = new GunDrive.Axis(0, 0, true);
        Rig rig = new Rig(YAW, 2.0);
        run(a, rig, YAW, t -> 90.0, GunDrive.RATE_BIG_CANNON, 0, 200);
        assertEquals(90.0, rig.field, 0.2);

        assertEquals(16, a.cap(YAW, GunDrive.RATE_BIG_CANNON));
    }

    @Test
    void aReversedLineIsReadAndTurnsTheRightWayAfterTheProbe() {
        GunDrive.Axis a = new GunDrive.Axis(0, 0, true);
        Rig rig = new Rig(YAW, -1.0);
        run(a, rig, YAW, t -> -60.0, GunDrive.RATE_AUTOCANNON, 0, 120);
        assertEquals(-60.0, rig.field, 0.2);
        assertEquals(-1.0, a.ratio(), 1e-9);
        assertTrue(rig.maxWrongWay <= 60, "wrong way at most 0.6 degrees, the probe: " + rig.maxWrongWay / 100.0);
    }

    @Test
    void aSavedRatioSkipsTheProbe() {
        GunDrive.Axis a = new GunDrive.Axis(-1.0, 0, true);
        Rig rig = new Rig(YAW, -1.0);
        run(a, rig, YAW, t -> -60.0, GunDrive.RATE_AUTOCANNON, 0, 120);
        assertEquals(-60.0, rig.field, 0.2);
        assertEquals(0, rig.maxWrongWay, "known line: never a tick the wrong way");
    }

    @Test
    void aMountThatTurnsAgainstTheLawFlipsItsSenseOnceAndSettles() {
        GunDrive.Axis a = new GunDrive.Axis(0, 0, true);
        Rig rig = new Rig(-YAW, 1.0);
        run(a, rig, YAW, t -> 45.0, GunDrive.RATE_AUTOCANNON, 0, 200);
        assertEquals(-1, a.sense());
        assertEquals(45.0, rig.field, 0.2);
        assertFalse(a.faulted(200));
    }

    @Test
    void aBarrelThatDoesNotFollowItsTurningInterfaceFaultsAndIsLetGo() {
        GunDrive.Axis a = new GunDrive.Axis(0, 0, true);
        Rig rig = new Rig(YAW, 1.0);
        rig.stalled = true;
        long faultAt = -1;
        for (long now = 0; now < 200 && faultAt < 0; now++) {
            double before = rig.field;
            rig.tick(now);
            GunDrive.observe(a, YAW, rig.field - before, rig.applied, rig.interfaceRpm(), false, false, now);
            rig.commanded = GunDrive.command(a, YAW, 90.0, rig.field, GunDrive.RATE_AUTOCANNON, false, false, now);
            if (a.faulted(now)) faultAt = now;
        }
        assertTrue(faultAt > 0 && faultAt < 60, "faulted at " + faultAt);
        assertFalse(a.lineFault());
        assertEquals(0, GunDrive.command(a, YAW, 90.0, 0.0, GunDrive.RATE_AUTOCANNON, false, false, faultAt + 2));
        assertTrue(a.faulted(faultAt + GunDrive.FAULT_HOLD - 1));
        assertFalse(a.faulted(faultAt + GunDrive.FAULT_HOLD));
    }

    @Test
    void aGovernorWhoseOutputNeverReachesTheMountIsADeadLine() {
        GunDrive.Axis a = new GunDrive.Axis(0, 0, false);
        Rig rig = new Rig(PITCH_SOUTH, 1.0);
        rig.deadLine = true;
        long faultAt = -1;
        for (long now = 0; now < 100 && faultAt < 0; now++) {
            double before = rig.field;
            rig.tick(now);
            GunDrive.observe(a, PITCH_SOUTH, rig.field - before, rig.applied, rig.interfaceRpm(), false, false, now);
            rig.commanded = GunDrive.command(a, PITCH_SOUTH, 20.0, rig.field, GunDrive.RATE_BIG_CANNON, false, false, now);
            if (a.faulted(now)) faultAt = now;
        }
        assertTrue(faultAt > 0, "a dead line faults");
        assertTrue(a.lineFault());
        assertFalse(a.known(), "nothing was read from a line that never turned");
    }

    @Test
    void aMovingTargetIsFollowedWithLittleLag() {
        GunDrive.Axis a = new GunDrive.Axis(1.0, 0, true);
        Rig rig = new Rig(YAW, 1.0);
        LongToDoubleFunction wanted = t -> 10.0 + 0.4 * t;
        run(a, rig, YAW, wanted, GunDrive.RATE_AUTOCANNON, 0, 200);
        double lag = Math.abs(GunDrive.wrap180(wanted.applyAsDouble(199) - rig.field));
        assertTrue(lag < 0.5, "lag " + lag);
    }

    @Test
    void aSteadyCrossingNeverRunsTheBarrelAheadOfTheTarget() {
        GunDrive.Axis a = new GunDrive.Axis(1.0, 0, true);
        Rig rig = new Rig(YAW, 1.0);
        LongToDoubleFunction wanted = t -> 5.0 + 0.52 * t;
        double ahead = 0.0;
        for (long now = 0; now < 240; now++) {
            double before = rig.field;
            rig.tick(now);
            GunDrive.observe(a, YAW, rig.field - before, rig.applied, rig.interfaceRpm(), false, false, now);
            rig.commanded = GunDrive.command(a, YAW, wanted.applyAsDouble(now), rig.field, GunDrive.RATE_AUTOCANNON, false, false, now);
            if (now > 60) ahead = Math.max(ahead, rig.field - wanted.applyAsDouble(now));
        }
        assertTrue(ahead < 0.05, "ahead of the target by " + ahead);
    }

    @Test
    void neverPushesFurtherIntoAPitchLimit() {
        GunDrive.Axis a = new GunDrive.Axis(1.0, 0, false);
        Rig rig = new Rig(PITCH_SOUTH, 1.0);
        rig.hi = 10.0;
        run(a, rig, PITCH_SOUTH, t -> 25.0, GunDrive.RATE_BIG_CANNON, 0, 200);
        assertEquals(10.0, rig.field, 1e-9);
        assertEquals(0, rig.commanded, "at the limit the governor is told 0, not a push CBC would swallow");
        assertFalse(a.faulted(200), "a limit is not a stall");
    }

    @Test
    void speedingUpIsRampedAndSlowingIsNot() {
        GunDrive.Axis a = new GunDrive.Axis(1.0, 0, true);
        Rig rig = new Rig(YAW, 1.0);
        int prev = 0;
        for (long now = 0; now < 40; now++) {
            double before = rig.field;
            rig.tick(now);
            GunDrive.observe(a, YAW, rig.field - before, rig.applied, rig.interfaceRpm(), false, false, now);
            int u = GunDrive.command(a, YAW, 170.0, rig.field, GunDrive.RATE_AUTOCANNON, false, false, now);
            if (Math.abs(u) > Math.abs(prev)) assertTrue(Math.abs(u) - Math.abs(prev) <= GunDrive.RAMP, prev + " -> " + u);
            prev = u;
            rig.commanded = u;
        }
        assertEquals(64, Math.abs(prev), "an autocannon on a 1:1 line reaches 64 RPM");
    }

    @Test
    void aMisreadSavedRatioStartsUnread() {
        assertFalse(new GunDrive.Axis(1000.0, 1, true).known());
        assertFalse(new GunDrive.Axis(Double.NaN, 1, true).known());
        assertTrue(new GunDrive.Axis(-0.5, -1, true).known());
        assertEquals(-1, new GunDrive.Axis(-0.5, -1, true).sense());
    }

    @Test
    void relearnForgetsTheLineAndTheFault() {
        GunDrive.Axis a = new GunDrive.Axis(2.0, -1, true);
        a.relearn();
        assertFalse(a.known());
        assertEquals(1, a.sense());
    }
}
