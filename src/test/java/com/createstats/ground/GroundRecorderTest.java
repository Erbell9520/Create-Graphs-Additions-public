package com.createstats.ground;

import java.util.Arrays;
import java.util.List;

import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.ground.GroundSnapshot.Body;
import com.createstats.ground.GroundSnapshot.Drive;
import com.createstats.ground.GroundSnapshot.EventKind;
import com.createstats.ground.GroundSnapshot.Identity;
import com.createstats.ground.GroundSnapshot.Log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundRecorderTest {
    static GroundSnapshot at(long tick, double z, double speed, String driver, List<String> problems, Log log) {
        final Identity id = new Identity("Test", null, "", VehicleKind.CAR, GroundCommand.all(), true, true, "");
        final Body body = new Body(new V3(0, 64, z), Quat.IDENTITY, Body.NORTH, new V3(0, 0, -speed), 0.0, speed,
                V3.ZERO, Body.STANDARD_GRAVITY);
        final Drive drive = new Drive(driver, driver.isEmpty() ? Long.MIN_VALUE : 0L, true, !driver.isEmpty(), false,
                false, driver.isEmpty() ? 0.0 : 1.0, 0.0, false, false, false, 'D', 30.0, 0.0, "", 0, new float[0]);
        return new GroundSnapshot(tick, id, body, log, drive, null, null, null, null, null, null, null, null, null,
                problems, null, null, null, null, null, null, null);
    }

    static GroundSnapshot at(long tick, double z, double speed, String driver) {
        return at(tick, z, speed, driver, List.of(), Log.NONE);
    }

    static byte[] recorded(GroundRecorder r) {
        return GroundCodec.encode(GroundSnapshot.empty(0L).withRecorded(r.odometer(), r.trail(), r.history(),
                r.events(), r.envelope()), GroundCodec.RECORDED);
    }

    @Test
    void theOdometerCountsBlocksDrivenFromPositions() {
        final GroundRecorder r = new GroundRecorder();
        for (int i = 0; i <= 100; i++) {
            r.observe(at(1000L + i * 4L, -i * 1.0, 5.0, "Ann"));
        }
        assertEquals(100.0, r.odometer().odometer(), 1e-9);
        assertEquals(100.0, r.odometer().trip(), 1e-9);
        assertEquals(400L, r.odometer().tripTicks());
    }

    @Test
    void itKeepsItsOwnCadence() {
        final GroundRecorder r = new GroundRecorder();
        for (int t = 0; t <= 400; t++) {
            r.observe(at(1000L + t, -t * 0.25, 5.0, "Ann"));
        }
        assertEquals(100.0, r.odometer().odometer(), 1e-9);
    }

    @Test
    void aTeleportAddsNothing() {
        final GroundRecorder r = new GroundRecorder();
        r.observe(at(1000L, 0.0, 0.0, "Ann"));
        r.observe(at(1004L, -1.0, 5.0, "Ann"));
        r.observe(at(1008L, -500.0, 5.0, "Ann"));
        r.observe(at(1012L, -501.0, 5.0, "Ann"));
        assertEquals(2.0, r.odometer().odometer(), 1e-9);
    }

    @Test
    void aSleepingBodyWhoseVelocityIsFrozenAddsNothing() {
        final GroundRecorder r = new GroundRecorder();
        for (int i = 0; i < 50; i++) {
            r.observe(at(1000L + i * 4L, 0.0, 3.2, ""));
        }
        assertEquals(0.0, r.odometer().odometer(), 0.0);
    }

    @Test
    void aTowedVehicleCountsItsBlocksButNotItsDrivingTime() {
        final GroundRecorder r = new GroundRecorder();
        for (int i = 0; i <= 10; i++) {
            r.observe(at(1000L + i * 4L, -i, 5.0, ""));
        }
        assertEquals(10.0, r.odometer().odometer(), 1e-9);
        assertEquals(0L, r.odometer().tripTicks());
    }

    @Test
    void theTopSpeedIsTheOneHeldForASecond() {
        final GroundRecorder r = new GroundRecorder();
        double z = 0.0;
        for (int i = 0; i < 60; i++) {
            final double v = i == 30 ? 40.0 : 8.0;
            z -= v * 0.2;
            r.observe(at(1000L + i * 4L, z, v, "Ann"));
        }
        assertEquals(8.0, r.odometer().topSpeed(), 1e-9, "one spiky sample is not a top speed");
        assertEquals(8.0, r.envelope().topSpeed(), 1e-9);
    }

    @Test
    void helmAndLeaveAreEvents() {
        final GroundRecorder r = new GroundRecorder();
        r.observe(at(1000L, 0.0, 0.0, ""));
        r.observe(at(1004L, 0.0, 0.0, "Ann"));
        r.observe(at(1008L, 0.0, 0.0, ""));
        final List<GroundSnapshot.Event> e = r.events().list();
        assertEquals(2, e.size());
        assertEquals(EventKind.HELM, e.get(0).kind());
        assertEquals("Ann", e.get(0).text());
        assertEquals(EventKind.LEFT, e.get(1).kind());
        assertEquals("Ann", e.get(1).text());
    }

    @Test
    void theFirstSampleSetsTheMemoryWithoutAStory() {
        final GroundRecorder r = new GroundRecorder();
        r.observe(at(1000L, 0.0, 0.0, "Ann", List.of("G1 gets no rotation"), Log.NONE));
        r.observe(at(1004L, 0.0, 0.0, "Ann", List.of("G1 gets no rotation"), Log.NONE));
        r.observe(at(1008L, 0.0, 0.0, "Ann", List.of("G1 gets no rotation"), Log.NONE));
        assertTrue(r.events().list().isEmpty());
    }

    @Test
    void aProblemIsLoggedOnlyOnceItHasStood() {
        final GroundRecorder r = new GroundRecorder();
        r.observe(at(1000L, 0.0, 0.0, "Ann"));
        r.observe(at(1004L, 0.0, 0.0, "Ann", List.of("G1 gets no rotation"), Log.NONE));
        r.observe(at(1008L, 0.0, 0.0, "Ann"));
        assertTrue(r.events().list().stream().noneMatch(x -> x.kind() == EventKind.PROBLEM), "a flicker is not a story");
        r.observe(at(1012L, 0.0, 0.0, "Ann", List.of("G1 gets no rotation"), Log.NONE));
        r.observe(at(1016L, 0.0, 0.0, "Ann", List.of("G1 gets no rotation"), Log.NONE));
        assertEquals(1L, r.events().list().stream().filter(x -> x.kind() == EventKind.PROBLEM).count());
        r.observe(at(1020L, 0.0, 0.0, "Ann"));
        assertTrue(r.events().list().stream().noneMatch(x -> x.kind() == EventKind.CLEARED), "nor is a gap");
        r.observe(at(1024L, 0.0, 0.0, "Ann"));
        assertEquals(1L, r.events().list().stream().filter(x -> x.kind() == EventKind.CLEARED).count());
    }

    @Test
    void anImpactIsLoggedOnceAtItsStartWithItsPeak() {
        final GroundRecorder r = new GroundRecorder();
        r.observe(at(1000L, 0.0, 5.0, "Ann"));
        r.observe(at(1004L, 0.0, 0.0, "Ann", List.of(), new Log(0.5, 6.1, 1, 6.1, 0, 0)));
        r.observe(at(1008L, 0.0, 0.0, "Ann", List.of(), new Log(0.5, 7.4, 5, 7.4, 0, 0)));
        r.observe(at(1012L, 0.0, 0.0, "Ann", List.of(), new Log(0.5, 7.4, 9, 7.4, 0, 0)));
        final List<GroundSnapshot.Event> impacts = r.events().list().stream()
                .filter(x -> x.kind() == EventKind.IMPACT).toList();
        assertEquals(1, impacts.size());
        assertEquals(7.4f, impacts.get(0).value(), 1e-6f);
        assertEquals(1003L, impacts.get(0).tick());
    }

    @Test
    void aJumpIsLoggedWhenItLands() {
        final GroundRecorder r = new GroundRecorder();
        r.observe(at(1000L, 0.0, 5.0, "Ann"));
        r.observe(at(1004L, 0.0, 5.0, "Ann", List.of(), new Log(0.5, 0, -1, 0, 0.2, 0)));
        r.observe(at(1008L, 0.0, 5.0, "Ann", List.of(), new Log(0.5, 0, -1, 0, 0.4, 0)));
        assertTrue(r.events().list().isEmpty(), "still in the air");
        r.observe(at(1012L, 0.0, 5.0, "Ann", List.of(), new Log(0.5, 0, -1, 0, 0.0, 0.45)));
        assertEquals(EventKind.AIRTIME, r.events().list().get(0).kind());
        assertEquals(0.45f, r.events().list().get(0).value(), 1e-6f);
    }

    @Test
    void theHistoryIsBoundedAndNewestLast() {
        final GroundRecorder r = new GroundRecorder();
        for (int i = 0; i < 1000; i++) {
            r.observe(at(1000L + i * 4L, -i * 0.4, (i % 100) * 0.1, "Ann"));
        }
        final GroundSnapshot.History h = r.history();
        assertEquals(GroundSnapshot.History.MAX_SAMPLES, h.size());
        assertEquals(GroundRecorder.HISTORY_TICKS, h.periodTicks());

        assertEquals(4996L, h.lastTick());
        assertEquals(9.9f, h.speed()[h.size() - 1], 1e-5f);
        assertEquals(9.6f, h.speed()[h.size() - 2], 1e-5f);
    }

    @Test
    void theTrailThinsTheOldHalfAndKeepsItsStart() {
        final GroundRecorder r = new GroundRecorder();
        for (int i = 0; i < 20_000; i++) {
            r.observe(at(1000L + i * 20L, -i * 5.0, 5.0, "Ann"));
        }
        final List<GroundSnapshot.Fix> fixes = r.trail().fixes();
        assertTrue(fixes.size() <= GroundSnapshot.Trail.MAX_FIXES);
        assertTrue(fixes.size() >= GroundSnapshot.Trail.MAX_FIXES * 3 / 4, "thinning halves only the old half");
        assertEquals(0.0, fixes.get(0).position().z(), 0.0, "where it started stays on the map");
        assertEquals(-19_999 * 5.0, fixes.get(fixes.size() - 1).position().z(), 0.0);
        for (int i = 1; i < fixes.size(); i++) {
            assertTrue(fixes.get(i).gameTime() > fixes.get(i - 1).gameTime(), "oldest first");
        }
    }

    @Test
    void aSaveLoadsBackWithEveryMeter() {
        final GroundRecorder r = GroundDriveSim.lap(3, 20_000L);
        final GroundRecorder back = GroundRecorder.load(r.save());
        assertTrue(r.odometer().odometer() > 1000.0, "three minutes of laps is a real distance");
        assertEquals(r.odometer().odometer(), back.odometer().odometer(), 0.0, "the odometer is saved to the bit");
        assertEquals(r.odometer().trip(), back.odometer().trip(), 0.0);
        assertEquals(r.odometer().tripTicks(), back.odometer().tripTicks());
        assertEquals(r.odometer().topSpeed(), back.odometer().topSpeed(), 1e-5);
        assertEquals(r.events().list().size(), back.events().list().size());
        assertArrayEquals(recorded(r), recorded(back));
    }

    @Test
    void theOdometerCarriesOnAcrossASave() {
        GroundRecorder r = new GroundRecorder();
        for (int i = 0; i <= 10; i++) {
            r.observe(at(1000L + i * 4L, -i, 5.0, "Ann"));
        }
        r = GroundRecorder.load(r.save());

        for (int i = 0; i <= 10; i++) {
            r.observe(at(9000L + i * 4L, 300.0 - i, 5.0, "Ann"));
        }
        assertEquals(20.0, r.odometer().odometer(), 1e-9);
    }

    @Test
    void aDamagedSaveLoadsAsAFreshRecorder() {
        final byte[] good = GroundDriveSim.lap(1, 9_000L).save();
        assertTrue(GroundRecorder.load(good).odometer().odometer() > 0.0, "the undamaged save loads");
        for (final int at : new int[]{0, 7, good.length / 2, good.length - 5, good.length - 1}) {
            final byte[] bad = good.clone();
            bad[at] ^= 0x10;
            final GroundRecorder r = GroundRecorder.load(bad);
            assertEquals(0.0, r.odometer().odometer(), 0.0, "flip at " + at);
            assertTrue(r.events().list().isEmpty(), "flip at " + at);
            assertTrue(r.trail().fixes().isEmpty(), "flip at " + at);
            assertEquals(0, r.history().size(), "flip at " + at);
        }
        assertEquals(0.0, GroundRecorder.load(Arrays.copyOf(good, good.length - 1)).odometer().odometer(), 0.0);
        assertEquals(0.0, GroundRecorder.load(new byte[]{1, 2, 3}).odometer().odometer(), 0.0);

        assertEquals(0.0, GroundRecorder.load(new byte[0]).odometer().odometer(), 0.0);
        assertEquals(0.0, GroundRecorder.load(null).odometer().odometer(), 0.0);
    }

    @Test
    void resettingTheTripKeepsTheOdometer() {
        final GroundRecorder r = new GroundRecorder();
        for (int i = 0; i <= 10; i++) {
            r.observe(at(1000L + i * 4L, -i, 5.0, "Ann"));
        }
        assertTrue(r.apply(GroundCommand.RESET_TRIP, 2000L));
        assertEquals(0.0, r.odometer().trip(), 0.0);
        assertEquals(0L, r.odometer().tripTicks());
        assertTrue(Double.isNaN(r.odometer().tripTop()));
        assertEquals(10.0, r.odometer().odometer(), 1e-9);
        assertEquals(2000L, r.odometer().tripStart());
        assertTrue(r.events().list().stream().anyMatch(x -> x.kind() == EventKind.TRIP));
    }

    @Test
    void clearingTheTrailAndForgettingTheLearnedLeaveTheOdometer() {
        final GroundRecorder r = GroundDriveSim.lap(1, 9_000L);
        final double odometer = r.odometer().odometer();
        final double top = r.odometer().topSpeed();
        assertFalse(r.trail().fixes().isEmpty());
        assertTrue(Double.isFinite(r.envelope().topSpeed()));
        assertTrue(r.apply(GroundCommand.CLEAR_TRAIL, 9_001L));
        assertTrue(r.trail().fixes().isEmpty());
        assertTrue(r.apply(GroundCommand.RESET_LEARNED, 9_001L));
        assertTrue(Double.isNaN(r.envelope().topSpeed()));
        assertEquals(0.0, r.envelope().seconds(), 0.0);
        assertEquals(odometer, r.odometer().odometer(), 0.0);
        assertEquals(top, r.odometer().topSpeed(), 0.0, "the all-time top speed is the odometer's, not the learned");
        assertFalse(r.apply(null, 9_002L));
    }

    @Test
    void aNewRecordIsLoggedOnceWhenItSettles() {
        final GroundRecorder r = GroundDriveSim.lap(2, 12_000L);
        final List<GroundSnapshot.Event> records = r.events().list().stream()
                .filter(x -> x.kind() == EventKind.TOP_SPEED).toList();
        assertEquals(1, records.size(), "one event at the top, not one per half block a second on the way up");

        assertEquals(896.0 / 60.0, records.get(0).value(), 0.1);
    }

    @Test
    void theLapsTellTheirStory() {
        final GroundRecorder r = GroundDriveSim.lap(2, 12_000L);
        final List<GroundSnapshot.Event> e = r.events().list();
        assertEquals(EventKind.HELM, e.get(0).kind());
        assertEquals("Bolat", e.get(0).text());
        assertEquals(2L, e.stream().filter(x -> x.kind() == EventKind.IMPACT).count(), "a kerb a lap");
        assertEquals(2L, e.stream().filter(x -> x.kind() == EventKind.PROBLEM).count());
        assertEquals(2L, e.stream().filter(x -> x.kind() == EventKind.CLEARED).count());
        for (int i = 1; i < e.size(); i++) {
            assertTrue(e.get(i).tick() >= e.get(i - 1).tick() || e.get(i).kind() == EventKind.TOP_SPEED
                    || e.get(i).kind() == EventKind.IMPACT, "in the order it happened, except the two dated back");
        }
    }
}
