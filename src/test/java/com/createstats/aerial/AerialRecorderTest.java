package com.createstats.aerial;

import java.util.List;

import com.createstats.aerial.AerialSnapshot.Envelope;
import com.createstats.aerial.AerialSnapshot.Fix;
import com.createstats.aerial.AerialSnapshot.Route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialRecorderTest {
    private static void fly(AerialFlightSim sim, AerialRecorder rec, double seconds) {
        for (int i = 0; i < Math.round(seconds * 20.0); i++) {
            sim.step();
            rec.observe(sim.snapshot());
        }
    }

    private static AerialFlightSim atSeaLevel() {
        return new AerialFlightSim(new V3(0.0, AerialFlightSim.SEA, 0.0), 0.0);
    }

    @Test
    void topSpeedIsLearnedFromLevelFlight() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 60.0);
        final double truth = sim.trueTopSpeed(1.0);
        assertEquals(truth, rec.envelope().topSpeed(), truth * 0.01);

        assertEquals(truth, AirframeMath.read(sim.snapshot()).topSpeed, truth * 0.01);
    }

    @Test
    void turnRadiusIsSpeedOverYawRate() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 40.0);
        sim.yawCommand = 9.0;
        fly(sim, rec, 20.0);
        final Envelope env = rec.envelope();
        final double speed = sim.velocity.horizontalLength();
        final int bucket = (int) Math.floor(speed / AerialRecorder.BUCKET_WIDTH);
        final double truth = speed / Math.toRadians(9.0);
        assertEquals(truth, env.turnRadius()[bucket], truth * 0.02);
        assertEquals(9.0, env.maxYawRate(), 0.01);

        assertEquals(truth, AirframeMath.turnRadiusAt(env, (bucket + 0.5) * AerialRecorder.BUCKET_WIDTH),
                truth * 0.02);
    }

    @Test
    void climbAndSinkAreSustainedFiguresInsideWhatWasFlown() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 20.0);
        sim.targetAltitude += 40.0;
        double peakClimb = 0.0;
        for (int i = 0; i < 600; i++) {
            sim.step();
            rec.observe(sim.snapshot());
            peakClimb = Math.max(peakClimb, sim.velocity.y());
        }
        sim.targetAltitude -= 60.0;
        double peakSink = 0.0;
        for (int i = 0; i < 600; i++) {
            sim.step();
            rec.observe(sim.snapshot());
            peakSink = Math.min(peakSink, sim.velocity.y());
        }
        final Envelope env = rec.envelope();
        assertTrue(env.bestClimb() > 1.0, "climbed: " + env.bestClimb());
        assertTrue(env.bestClimb() <= peakClimb + 1.0e-9, "never above the peak");
        assertTrue(env.maxSink() < -1.0, "sank: " + env.maxSink());
        assertTrue(env.maxSink() >= peakSink - 1.0e-9, "never below the peak");
        assertTrue(env.maxDecel() > 0.0 || Double.isNaN(env.maxDecel()));
    }

    @Test
    void oneWildSampleCannotSetARecord() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 60.0);
        final double before = rec.envelope().topSpeed();

        sim.step();
        final AerialSnapshot s = sim.snapshot();
        rec.observe(s.withBody(new AerialSnapshot.Body(s.body().position(), s.body().orientation(), s.body().nose(),
                s.body().velocity().times(3.0), s.body().spin())));
        fly(sim, rec, 5.0);
        assertEquals(before, rec.envelope().topSpeed(), before * 0.005);
    }

    @Test
    void theTrailIsSpacedAndThinsItsOldestHalfWhenFull() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 60.0);
        final List<Fix> fixes = rec.trail().fixes();
        for (int i = 2; i < fixes.size(); i++) {
            final double d = fixes.get(i).position().minus(fixes.get(i - 1).position()).length();
            assertTrue(d >= AerialRecorder.TRAIL_METRES - 1.0e-9, "fix " + i + " only " + d + " m on");
        }
        final V3 start = fixes.get(0).position();
        fly(sim, rec, 700.0);
        final List<Fix> many = rec.trail().fixes();
        assertTrue(many.size() <= AerialSnapshot.Trail.MAX_FIXES);
        assertEquals(start, many.get(0).position(), "the start of the trail survives thinning");
        for (int i = 1; i < many.size(); i++) {
            assertTrue(many.get(i).gameTime() > many.get(i - 1).gameTime(), "oldest first");
        }

        final long gap = many.get(many.size() - 1).gameTime() - many.get(many.size() - 2).gameTime();
        assertTrue(gap <= 2L * AerialRecorder.TRAIL_TICKS, "recent gap " + gap);
    }

    @Test
    void historyIsAFixedRateRingOldestFirst() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 20.0);
        sim.targetAltitude += 30.0;
        fly(sim, rec, 400.0);
        final AerialSnapshot.History h = rec.history();
        assertEquals(AerialSnapshot.History.MAX_SAMPLES, h.size());
        assertEquals(AerialRecorder.HISTORY_TICKS, h.periodTicks());
        assertEquals(AerialFlightSim.SEA + 30.0, h.altitude()[h.size() - 1], 0.5, "newest last");
        assertTrue(Float.isNaN(h.crossTrack()[0]), "no route, no cross-track");
    }

    @Test
    void aFlownSquareBecomesARouteThroughItsCorners() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 30.0);
        for (int side = 0; side < 3; side++) {
            sim.yawCommand = 9.0;
            fly(sim, rec, 10.0);
            sim.yawCommand = 0.0;
            fly(sim, rec, 30.0);
        }
        final Route route = rec.trailAsRoute("Square");
        assertTrue(route.points().size() >= 5 && route.points().size() <= AerialRecorder.ROUTE_POINTS,
                "points: " + route.points().size());
        assertEquals(rec.trail().fixes().get(0).position(), route.points().get(0).position());
        final List<Fix> fixes = rec.trail().fixes();
        assertEquals(fixes.get(fixes.size() - 1).position(), route.points().get(route.points().size() - 1).position());

        final double top = sim.trueTopSpeed(1.0);
        assertEquals(top, route.points().get(route.points().size() - 1).speed(), top * 0.15);

        final AerialSnapshot s = sim.snapshot();
        final AirframeReadout r = AirframeMath.read(new AerialSnapshot(s.gameTime(), s.name(), s.shipId(),
                s.commands(), s.body(), s.air(), s.mass(), s.forces(), s.propellers(), s.balloons(), s.engine(),
                s.target(), s.autopilot(), s.docking(), s.traffic(), s.terrain(), route, rec.trail(), rec.history(),
                rec.envelope(), s.silhouette()));
        for (final AirframeReadout.Corner c : r.route.corners()) {
            assertTrue(c.feasible(), "the ship flew this corner, so it can: " + c);
        }
    }

    @Test
    void itSurvivesASaveAndLoad() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 30.0);
        sim.yawCommand = 9.0;
        fly(sim, rec, 15.0);
        final AerialRecorder back = AerialRecorder.load(rec.save());
        assertArrayEquals(AerialCodec.section(carrier(rec), AerialCodec.Section.TRAIL),
                AerialCodec.section(carrier(back), AerialCodec.Section.TRAIL));
        assertArrayEquals(AerialCodec.section(carrier(rec), AerialCodec.Section.HISTORY),
                AerialCodec.section(carrier(back), AerialCodec.Section.HISTORY));
        assertArrayEquals(AerialCodec.section(carrier(rec), AerialCodec.Section.ENVELOPE),
                AerialCodec.section(carrier(back), AerialCodec.Section.ENVELOPE));

        assertEquals(0, AerialRecorder.load(new byte[]{9, 9, 9}).trail().fixes().size());
    }

    private static AerialSnapshot carrier(AerialRecorder r) {
        return new AerialSnapshot(0L, "", null, 0, null, null, null, null, null, null, null, null, null, null, null,
                null, null, r.trail(), r.history(), r.envelope(), null);
    }

    @Test
    void commandsClearWhatTheySay() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 30.0);
        assertFalse(rec.trail().fixes().isEmpty());
        assertTrue(rec.apply(AerialCommand.CLEAR_TRAIL));
        assertTrue(rec.trail().fixes().isEmpty());
        assertTrue(rec.envelope().known());
        assertTrue(rec.apply(AerialCommand.RESET_ENVELOPE));
        assertFalse(rec.envelope().known());
        assertTrue(Double.isNaN(rec.envelope().topSpeed()));
        assertFalse(rec.apply(AerialCommand.SAVE_TRAIL_AS_ROUTE), "storing a route is the backend's job");
    }

    @Test
    void timeRunningBackwardsRestartsTheWindowsInsteadOfInventingARate() {
        final AerialFlightSim sim = atSeaLevel();
        final AerialRecorder rec = new AerialRecorder();
        fly(sim, rec, 30.0);
        sim.tick = 10L;
        fly(sim, rec, 10.0);
        final Envelope env = rec.envelope();

        assertTrue(!(env.maxAccel() > 5.0), "accel " + env.maxAccel());
        assertTrue(!(env.maxDecel() > 5.0), "decel " + env.maxDecel());
    }
}
