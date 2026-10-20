package com.createstats.aerial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.createstats.aerial.AerialSnapshot.Air;
import com.createstats.aerial.AerialSnapshot.Balloon;
import com.createstats.aerial.AerialSnapshot.Body;
import com.createstats.aerial.AerialSnapshot.Docking;
import com.createstats.aerial.AerialSnapshot.Envelope;
import com.createstats.aerial.AerialSnapshot.Force;
import com.createstats.aerial.AerialSnapshot.Mass;
import com.createstats.aerial.AerialSnapshot.Propeller;
import com.createstats.aerial.AerialSnapshot.Route;
import com.createstats.aerial.AerialSnapshot.Target;
import com.createstats.aerial.AerialSnapshot.Terrain;
import com.createstats.aerial.AerialSnapshot.Traffic;
import com.createstats.aerial.AerialSnapshot.Waypoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirframeMathTest {
    private static final double EPS = 1.0e-6;
    private static final V3 NORTH = new V3(0.0, 0.0, -1.0);

    private static Builder ship() {
        return new Builder();
    }

    @Test
    void attitudeReadsBackTheRotationItWasBuiltFrom() {
        final double[][] cases = {{0, 0, 0}, {72, 3.5, -12}, {245, -8, 30}, {359, 20, 5}, {90, 0, 0}};
        for (final double[] c : cases) {
            final AirframeReadout r = ship().orientation(AerialFlightSim.orientation(c[0], c[1], c[2])).read();
            assertEquals(c[0], r.heading, 1.0e-6, "heading of " + java.util.Arrays.toString(c));
            assertEquals(c[1], r.pitch, 1.0e-6, "pitch of " + java.util.Arrays.toString(c));
            assertEquals(c[2], r.bank, 1.0e-6, "bank of " + java.util.Arrays.toString(c));
        }
    }

    @Test
    void theNoseIsTheBlocksFacingNotTheBodyAxes() {
        final AirframeReadout r = ship().nose(new V3(1.0, 0.0, 0.0)).read();
        assertEquals(90.0, r.heading, EPS);
        assertEquals(0.0, r.bank, EPS);

        assertEquals(1.0, r.right.z(), EPS);
    }

    @Test
    void rightWingDownIsAPositiveBank() {
        final AirframeReadout r = ship().orientation(AerialFlightSim.orientation(0.0, 0.0, 20.0)).read();
        assertTrue(r.right.y() < 0.0, "the right wing is the low one");
        assertEquals(20.0, r.bank, EPS);
    }

    @Test
    void quaternionHelpersAgree() {
        final Quat q = AerialFlightSim.orientation(123.0, 17.0, -41.0);
        final V3 v = new V3(1.5, -2.0, 7.25);
        final V3 back = q.inverseRotate(q.rotate(v));
        assertEquals(v.x(), back.x(), 1.0e-9);
        assertEquals(v.y(), back.y(), 1.0e-9);
        assertEquals(v.z(), back.z(), 1.0e-9);
        final Quat a = Quat.axisAngle(V3.UP, 0.0);
        final Quat b = Quat.axisAngle(V3.UP, -Math.toRadians(90.0));
        final V3 mid = a.slerp(b, 0.5).rotate(NORTH);
        assertEquals(45.0, AirframeMath.headingOf(mid), 1.0e-9);
    }

    @Test
    void trackDriftAndTurnRate() {
        final double h = Math.toRadians(79.0);
        final AirframeReadout r = ship()
                .orientation(AerialFlightSim.orientation(72.0, 0.0, 0.0))
                .velocity(new V3(Math.sin(h) * 13.0, 1.2, -Math.cos(h) * 13.0))
                .spin(new V3(0.0, -0.1, 0.0))
                .read();
        assertEquals(79.0, r.track, 1.0e-9);
        assertEquals(7.0, r.drift, 1.0e-9);
        assertEquals(13.0, r.groundSpeed, 1.0e-9);
        assertEquals(1.2, r.verticalSpeed, 1.0e-9);

        assertEquals(Math.toDegrees(0.1), r.turnRate, 1.0e-9);
        assertEquals(130.0, r.turnRadius, 1.0e-9);
    }

    @Test
    void windIsGroundMinusAirAndSplitsAgainstTheNose() {
        final double h = Math.toRadians(72.0);
        final V3 fwd = new V3(Math.sin(h), 0.0, -Math.cos(h));
        final double to = Math.toRadians(140.0);
        final V3 wind = new V3(Math.sin(to), 0.0, -Math.cos(to)).times(2.3);
        final V3 air = fwd.times(11.4);
        final AirframeReadout r = ship().orientation(AerialFlightSim.orientation(72.0, 0.0, 0.0))
                .velocity(air.plus(wind)).air(air, true).read();
        assertEquals(2.3, r.windSpeed, 1.0e-9);
        assertEquals(320.0, r.windFrom, 1.0e-9);

        assertEquals(-2.3 * Math.cos(Math.toRadians(68.0)), r.headwind, 1.0e-9);
        assertEquals(2.3 * Math.sin(Math.toRadians(68.0)), r.crosswind, 1.0e-9);
        assertEquals(11.4, r.indicatedAirspeed, 1.0e-9);

        assertTrue(r.drift > 0.0);
    }

    @Test
    void noAirDataMeansNoWindRatherThanCalm() {
        final AirframeReadout r = ship().velocity(new V3(0.0, 0.0, -10.0)).air(new V3(0.0, 0.0, -10.0), false).read();
        assertFalse(r.airMeasured);
        assertTrue(Double.isNaN(r.windSpeed), "an unmeasured wind is unknown, not zero");
    }

    @Test
    void heightAboveGroundIsFromTheKeelAndTheNearerOfHeightmapAndRay() {
        final Terrain flat = flat(100.0);
        final AerialSnapshot.Silhouette keel = AerialSilhouette.of(List.of(new V3(0.5, -3.5, 0.5),
                new V3(0.5, 2.5, 0.5)), NORTH);
        AirframeReadout r = ship().position(new V3(4.0, 120.0, 4.0)).terrain(flat).silhouette(keel).read();
        assertEquals(4.0, r.keelDrop, EPS);
        assertEquals(16.0, r.agl, EPS);

        final Terrain withRay = new Terrain(flat.originX(), flat.originZ(), flat.cell(), flat.size(), flat.heights(),
                flat.groundY(), 9.0);
        r = ship().position(new V3(4.0, 120.0, 4.0)).terrain(withRay).silhouette(keel).read();
        assertEquals(9.0, r.agl, EPS);
    }

    @Test
    void terrainInterpolatesBetweenCellCentres() {
        final short[] h = new short[4];
        h[0] = 100;
        h[1] = 110;
        h[2] = 120;
        h[3] = 130;
        final Terrain t = new Terrain(0.0, 0.0, 10.0, 2, h, Double.NaN, Double.NaN);
        assertEquals(100.0, t.heightAt(5.0, 5.0), EPS);
        assertEquals(115.0, t.heightAt(10.0, 10.0), EPS);
        assertEquals(130.0, t.heightAt(15.0, 15.0), EPS);
        assertTrue(Double.isNaN(t.heightAt(-50.0, -50.0)));
    }

    @Test
    void supportIsTheVerticalForceOverWeightAndIgnoresDrag() {
        final double w = 1000.0 * 11.0;
        final AirframeReadout r = ship()
                .force("balloon_lift", new V3(0.0, 0.64 * w, 0.0))
                .force("propulsion", new V3(0.0, 0.31 * w, -0.22 * w))
                .force("lift", new V3(0.0, 0.12 * w, 0.0))
                .force("drag", new V3(0.0, 0.5 * w, 0.0))
                .force("gravity", new V3(0.0, -w, 0.0))
                .read();
        assertEquals(w, r.weight, EPS);
        assertEquals(1.07, r.support, 1.0e-12);
        assertEquals(0.07, r.reserve, 1.0e-12);
        assertEquals(0.5, r.dragShare, 1.0e-12);
        assertEquals(0.22, r.forwardThrustShare, 1.0e-12);
        assertEquals(3, r.shares.size(), "gravity and drag are not support");
        assertEquals("balloon_lift", r.shares.get(0).group());
        assertEquals(0.64, r.shares.get(0).share(), 1.0e-12);
    }

    @Test
    void aGravityGroupStandsInForAnUnreadMass() {
        final AirframeReadout r = ship().mass(Double.NaN).force("gravity", new V3(0.0, -2200.0, 0.0))
                .force("balloon_lift", new V3(0.0, 2200.0, 0.0)).read();
        assertEquals(200.0, r.mass, EPS);
        assertEquals(1.0, r.support, EPS);
    }

    @Test
    void theCeilingIsWhereSupportScaledByPressureFallsToOne() {
        final double y = 142.0;
        final double p = AerialFlightSim.pressure(y);
        final double w = 11000.0;
        final AirframeReadout r = ship().position(new V3(0.0, y, 0.0))
                .force("balloon_lift", new V3(0.0, 1.07 * w, 0.0))
                .pressure(p, AerialFlightSim.pressure(y + Air.PROBE)).read();
        assertEquals(0.004, r.lapse, 1.0e-12);
        assertEquals(y + Math.log(1.07) / 0.004, r.ceilingY, 1.0e-9);

        final AirframeReadout thin = ship().position(new V3(0.0, y, 0.0))
                .force("balloon_lift", new V3(0.0, 1.07 * w, 0.0))
                .pressure(1.0, Math.exp(-0.008 * Air.PROBE)).read();
        assertEquals(y + Math.log(1.07) / 0.008, thin.ceilingY, 1.0e-9);

        final AirframeReadout low = ship().position(new V3(0.0, y, 0.0))
                .force("balloon_lift", new V3(0.0, 0.92 * w, 0.0)).read();
        assertTrue(low.ceilingY < y);
    }

    @Test
    void balloonsAloneFloatWhereTheirLiftAtPressureEqualsTheMass() {
        final double y = 142.0;
        final double p = AerialFlightSim.pressure(y);

        final AirframeReadout r = ship().position(new V3(0.0, y, 0.0)).pressure(p, AerialFlightSim.pressure(y + 16))
                .balloon(new Balloon("B", "hot_air", V3.ZERO, 1036.0, 900.0, 900.0, 1000.0, 0.0, 10.0, p)).read();
        final double expected = y + Math.log(1036.0 * p / 1000.0) / 0.004;
        assertEquals(expected, r.balloonFloatY, 1.0e-9);
        assertTrue(r.balloonFloatY < y);
    }

    @Test
    void lapseFallsBackToSablesDefaultWithOneSample() {
        assertEquals(0.004, AirframeMath.lapseOf(0.9, Double.NaN), 0.0);
        assertEquals(0.004, AirframeMath.lapseOf(Double.NaN, 0.8), 0.0);
        assertTrue(Double.isNaN(AirframeMath.equilibrium(100.0, -1.0, 0.004)));
    }

    @Test
    void liftAheadOfTheCentreOfMassPitchesTheNoseUp() {
        final double w = 11000.0;
        final AirframeReadout r = ship().position(new V3(10.0, 100.0, 10.0))

                .force("balloon_lift", new V3(0.0, w, 0.0), new V3(10.0, 104.0, 8.8)).read();
        assertEquals(1.2, r.balanceArm, 1.0e-9);
        assertEquals(1.2 * w, r.pitchMoment, 1.0e-6);
        assertTrue(r.pitchMoment > 0.0, "nose up");
    }

    @Test
    void aPropGivesItsStaticThrustScaledByAirflowAndPressure() {
        final Propeller prop = new Propeller("P", "small", V3.ZERO, NORTH, 1000.0, 20.0, Double.NaN, 0.8, 200.0,
                Double.NaN, true);
        final AirframeReadout r = ship().air(new V3(0.0, 0.0, -5.0), true).velocity(new V3(0.0, 0.0, -5.0))
                .prop(prop).read();
        final AirframeReadout.PropState s = r.props.get(0);
        assertEquals(5.0, s.axisSpeed(), EPS);
        assertEquals((20.0 - 5.0) / 20.0 * 0.8, s.delivered(), EPS);
        assertEquals(1000.0 * 0.75 * 0.8, s.thrust(), EPS);
        assertFalse(s.saturated());

        final AirframeReadout fast = ship().air(new V3(0.0, 0.0, -19.0), true).velocity(new V3(0.0, 0.0, -19.0))
                .prop(prop).read();
        assertTrue(fast.props.get(0).saturated());
    }

    @Test
    void aLaggingPropIsBlamedOnItsShaftOnlyWhenTheShaftIsOverloaded() {
        final AirframeReadout r = ship()
                .prop(prop("A", -3, 128.0, 0.4)).prop(prop("B", 3, 128.0, 0.4))
                .prop(prop("C", -3, 96.0, 0.94)).prop(prop("D", 3, 110.0, 0.5)).read();
        assertFalse(r.props.get(0).lagging());
        assertTrue(r.props.get(2).lagging(), "96 is under 85% of the median 119");
        assertTrue(r.props.get(2).overloaded());
        assertFalse(r.props.get(3).lagging(), "110 is 92% of the median");
    }

    @Test
    void thrustOnTheRightTurnsTheNoseLeft() {
        final Propeller right = new Propeller("R", "small", new V3(3.0, 0.0, 0.0), NORTH, 100.0, 50.0, 1.0, 1.0, 100.0,
                Double.NaN, true);
        final AirframeReadout r = ship().prop(right).read();
        assertEquals(-300.0, r.yawTorque, 1.0e-9);
        assertEquals(1.0, r.asymmetry, 1.0e-9);
        final AirframeReadout balanced = ship().prop(right)
                .prop(new Propeller("L", "small", new V3(-3.0, 0.0, 0.0), NORTH, 100.0, 50.0, 1.0, 1.0, 100.0,
                        Double.NaN, true)).read();
        assertEquals(0.0, balanced.yawTorque, 1.0e-9);
        assertEquals(0.0, balanced.asymmetry, 1.0e-9);
    }

    @Test
    void topSpeedIsWhereForwardThrustLeftMeetsLinearDrag() {
        final AerialFlightSim sim = new AerialFlightSim(new V3(0.0, 63.0, 0.0), 0.0);
        final double expected = sim.trueTopSpeed(1.0);

        final double u = 6.0;
        final Builder b = ship().air(new V3(0.0, 0.0, -u), true).velocity(new V3(0.0, 0.0, -u))
                .force("drag", new V3(0.0, 0.0, sim.drag * u));
        for (int i = 0; i < 4; i++) {
            b.prop(new Propeller("P" + i, "bearing", V3.ZERO, NORTH, sim.propThrust, sim.airflow, Double.NaN, 1.0,
                    128.0, Double.NaN, true));
        }
        final AirframeReadout r = b.read();
        assertEquals(expected, r.topSpeed, 1.0e-6);
        assertEquals(sim.airflow, r.propTopOut, 1.0e-9);
    }

    @Test
    void noMeasuredDragMeansNoTopSpeedRatherThanAGuess() {
        final AirframeReadout r = ship().air(new V3(0.0, 0.0, -6.0), true)
                .prop(prop("A", 0, 128.0, 0.4)).read();
        assertTrue(Double.isNaN(r.topSpeed));
    }

    @Test
    void aClimbDoesNotCutTheTopSpeed() {
        final AerialFlightSim sim = new AerialFlightSim(new V3(0.0, 63.0, 0.0), 0.0);
        final double u = 6.0;
        final Builder level = ship().air(new V3(0.0, 0.0, -u), true).velocity(new V3(0.0, 0.0, -u))
                .force("drag", new V3(0.0, 0.0, sim.drag * u));
        final Builder climbing = ship().air(new V3(0.0, 3.0, -u), true).velocity(new V3(0.0, 3.0, -u))
                .force("drag", new V3(0.0, -4.0 * sim.drag * 3.0, sim.drag * u));
        for (final Builder b : new Builder[]{level, climbing}) {
            for (int i = 0; i < 4; i++) {
                b.prop(new Propeller("P" + i, "bearing", V3.ZERO, NORTH, sim.propThrust, sim.airflow, Double.NaN,
                        1.0, 128.0, Double.NaN, true));
            }
        }
        assertEquals(sim.trueTopSpeed(1.0), level.read().topSpeed, 1.0e-6);
        assertEquals(sim.trueTopSpeed(1.0), climbing.read().topSpeed, 1.0e-6);
    }

    @Test
    void aLiftFanGearedSlowerThanThePushersIsNotLagging() {
        final V3 up = new V3(0.0, 1.0, 0.0);
        final AirframeReadout r = ship().prop(prop("A", -3, 128.0, 0.4)).prop(prop("B", 3, 128.0, 0.4))
                .prop(new Propeller("F1", "bearing", new V3(-4.0, 3.0, 0.0), up, 100.0, 25.0, 1.0, 1.0, 90.0, 0.4, true))
                .prop(new Propeller("F2", "bearing", new V3(4.0, 3.0, 0.0), up, 100.0, 25.0, 1.0, 1.0, 90.0, 0.4, true))
                .prop(new Propeller("F3", "bearing", new V3(0.0, 3.0, 4.0), up, 100.0, 25.0, 1.0, 1.0, 60.0, 0.4, true))
                .read();
        assertFalse(r.props.get(2).lagging(), "90 against its own kind, not against the pushers' 128");
        assertTrue(r.props.get(4).lagging(), "60 is under 85% of the fans' median 90");
        assertFalse(r.props.get(0).lagging());
    }

    @Test
    void theSlowestHoldingSpeedIsWhereLinearSailLiftMakesUpTheRest() {
        final double w = 11000.0;
        final AirframeReadout r = ship().air(new V3(0.0, 0.0, -10.0), true).velocity(new V3(0.0, 0.0, -10.0))
                .force("balloon_lift", new V3(0.0, 0.9 * w, 0.0)).force("lift", new V3(0.0, 0.2 * w, 0.0)).read();
        assertEquals(5.0, r.minHoldSpeed, 1.0e-9);

        final AirframeReadout climbing = ship().air(new V3(0.0, 3.0, -10.0), true)
                .velocity(new V3(0.0, 3.0, -10.0)).force("balloon_lift", new V3(0.0, 0.9 * w, 0.0))
                .force("lift", new V3(0.0, 0.2 * w, 0.0)).read();
        assertEquals(5.0, climbing.minHoldSpeed, 1.0e-9);

        final AirframeReadout hover = ship().air(new V3(0.0, 0.0, -10.0), true).velocity(new V3(0.0, 0.0, -10.0))
                .force("balloon_lift", new V3(0.0, 1.02 * w, 0.0)).force("lift", new V3(0.0, 0.2 * w, 0.0)).read();
        assertTrue(Double.isNaN(hover.minHoldSpeed));

        final AirframeReadout noSails = ship().air(new V3(0.0, 0.0, -10.0), true)
                .force("balloon_lift", new V3(0.0, 0.9 * w, 0.0)).read();
        assertTrue(Double.isNaN(noSails.minHoldSpeed));
    }

    @Test
    void theSpeedTrendIsEveryForceAlongTheNose() {
        final double w = 11000.0;
        final AirframeReadout level = ship().air(new V3(0.0, 0.0, -10.0), true).velocity(new V3(0.0, 0.0, -10.0))
                .force("propulsion", new V3(0.0, 0.0, -1100.0)).force("drag", new V3(0.0, 0.0, 660.0))
                .force("balloon_lift", new V3(0.0, w, 0.0)).force("gravity", new V3(0.0, -w, 0.0)).read();
        assertEquals(0.44, level.forwardAccel, 1.0e-9);

        final double pitch = Math.toRadians(10.0);
        final AirframeReadout up = ship().orientation(AerialFlightSim.orientation(0.0, 10.0, 0.0))
                .air(new V3(0.0, 0.0, -10.0), true).velocity(new V3(0.0, 0.0, -10.0))
                .force("propulsion", new V3(0.0, 0.0, -1100.0)).force("drag", new V3(0.0, 0.0, 660.0))
                .force("gravity", new V3(0.0, -w, 0.0)).read();
        assertEquals(((1100.0 - 660.0) * Math.cos(pitch) - w * Math.sin(pitch)) / 1000.0, up.forwardAccel, 1.0e-9);

        final AirframeReadout implied = ship().orientation(AerialFlightSim.orientation(0.0, 10.0, 0.0))
                .air(new V3(0.0, 0.0, -10.0), true).velocity(new V3(0.0, 0.0, -10.0))
                .force("propulsion", new V3(0.0, 0.0, -1100.0)).force("drag", new V3(0.0, 0.0, 660.0)).read();
        assertEquals(up.forwardAccel, implied.forwardAccel, 1.0e-9);
    }

    @Test
    void aShortShipSettlesWhereVerticalDragMakesUpTheShortfall() {
        final double w = 11000.0;
        final AirframeReadout r = ship().air(new V3(0.0, -2.0, 0.0), true).velocity(new V3(0.0, -2.0, 0.0))
                .force("balloon_lift", new V3(0.0, 0.92 * w, 0.0)).force("drag", new V3(0.0, 900.0, 0.0)).read();
        assertEquals(0.08 * w / 450.0, r.steadySink, 1.0e-9);
        assertEquals(0.92, r.support, 1.0e-9, "and the drag is still not counted as support");

        final AirframeReadout level = ship().air(new V3(0.0, 0.0, -10.0), true).velocity(new V3(0.0, 0.0, -10.0))
                .force("balloon_lift", new V3(0.0, 0.92 * w, 0.0)).force("drag", new V3(0.0, 0.0, 900.0)).read();
        assertTrue(Double.isNaN(level.steadySink));

        final AirframeReadout carried = ship().air(new V3(0.0, -2.0, 0.0), true).velocity(new V3(0.0, -2.0, 0.0))
                .force("balloon_lift", new V3(0.0, 1.01 * w, 0.0)).force("drag", new V3(0.0, 900.0, 0.0)).read();
        assertTrue(Double.isNaN(carried.steadySink));
    }

    @Test
    void groundStillRisingWhereTheScanStopsIsNotARidge() {
        final int size = 16;
        final double cell = 8.0;
        final short[] h = new short[size * size];
        for (int iz = 0; iz < size; iz++) {
            for (int ix = 0; ix < size; ix++) {
                final double north = -(-64.0 + (iz + 0.5) * cell);
                h[iz * size + ix] = (short) Math.round(40.0 + 0.25 * north);
            }
        }
        final AirframeReadout r = ship().position(new V3(0.0, 100.0, 0.0)).velocity(new V3(0.0, 0.0, -10.0))
                .terrain(new Terrain(-64.0, -64.0, cell, size, h, 40.0, Double.NaN)).read();
        assertTrue(r.terrain.risingAtEdge(), "the highest ground is the last ground known: " + r.terrain);
        assertEquals(r.terrain.scanned(), r.terrain.ridgeDistance(), 1.0e-9);
        assertTrue(r.terrain.scanned() < AirframeMath.PROFILE_LENGTH);

        final AirframeReadout crest = ship().velocity(new V3(0.0, 0.0, -10.0)).terrain(ridge()).read();
        assertFalse(crest.terrain.risingAtEdge());
        assertEquals(120.0, crest.terrain.ridgeDistance(), 4.0);
    }

    @Test
    void balloonFillTimeToTargetAndStarvation() {
        final AirframeReadout r = ship()
                .balloon(new Balloon("A", "hot_air", V3.ZERO, 500.0, 450.0, 900.0, 1000.0, 9.0, 10.0, 1.0))
                .balloon(new Balloon("B", "hot_air", V3.ZERO, 500.0, 640.0, 900.0, 1000.0, 0.0, 10.0, 1.0))
                .balloon(new Balloon("C", "hot_air", V3.ZERO, 500.0, 900.0, 900.0, 1000.0, 0.0, 10.0, 1.0)).read();
        assertEquals(0.5, r.balloons.get(0).fill(), EPS);
        assertEquals(50.0, r.balloons.get(0).timeToTarget(), EPS);
        assertFalse(r.balloons.get(0).starved());
        assertTrue(r.balloons.get(1).starved(), "short of target and not filling");
        assertFalse(r.balloons.get(2).starved(), "a full balloon is not starved");
        assertEquals(0.5, r.balloons.get(0).liftShare(), EPS);
    }

    @Test
    void aBalloonWithNoGasGoingInHasItsBurnersOut() {
        final AirframeReadout r = ship()
                .balloon(new Balloon("Lit", "hot_air", V3.ZERO, 500.0, 640.0, 900.0, 1000.0, 0.0, 10.0, 1.0))
                .balloon(new Balloon("Out", "hot_air", V3.ZERO, 500.0, 640.0, 900.0, 1000.0, 0.0, 0.0, 1.0)).read();
        assertTrue(r.balloons.get(0).starved() && !r.balloons.get(0).burnersOut(), "lit, and still losing ground");
        assertTrue(r.balloons.get(1).starved() && r.balloons.get(1).burnersOut(), "nothing going in at all");
    }

    @Test
    void noPressureReadFallsBackToSablesCurveFromSeaLevel() {
        final AirframeReadout r = ship().position(new V3(0.0, 142.0, 0.0)).pressure(Double.NaN, Double.NaN)
                .balloon(new Balloon("B", "hot_air", V3.ZERO, 1000.0, 900.0, 900.0, 1000.0, 0.0, 10.0, Double.NaN))
                .read();
        assertTrue(r.pressureEstimated);
        assertEquals(Math.exp(-0.004 * 79.0), r.pressure, 1.0e-12);
        assertEquals(Math.exp(-0.004 * 79.0), r.balloons.get(0).liftShare(), 1.0e-12,
                "the balloon is weighed at the estimate, not at sea level");
        assertFalse(ship().read().pressureEstimated, "a read pressure is not an estimate");
    }

    @Test
    void theShipIsNotItsOwnTraffic() {
        final UUID own = new UUID(7L, 7L);
        final AirframeReadout r = ship().id(own)
                .traffic(new Traffic("Self", own, V3.ZERO, V3.ZERO, 12.0))
                .traffic(new Traffic("Other", new UUID(7L, 8L), new V3(60.0, 0.0, 0.0), V3.ZERO, 8.0)).read();
        assertEquals(1, r.traffic.size());
        assertEquals("Other", r.traffic.get(0).traffic().name());
    }

    @Test
    void enduranceAndRangeFromTheEngines() {
        final AirframeReadout r = ship().velocity(new V3(0.0, 0.0, -10.0))
                .engine(new AerialSnapshot.Engine(1, 600.0, 0.8, false)).read();
        assertEquals(600.0, r.enduranceSeconds, EPS);
        assertEquals(6000.0, r.rangeMetres, EPS);
        assertTrue(Double.isNaN(ship().read().enduranceSeconds), "no engines, no endurance");
    }

    @Test
    void navBearingRelativeBearingClosingAndEta() {
        final double b = Math.toRadians(81.0);
        final V3 target = new V3(Math.sin(b) * 612.0, 0.0, -Math.cos(b) * 612.0);
        final double h = Math.toRadians(81.0);
        final AirframeReadout r = ship().orientation(AerialFlightSim.orientation(72.0, 0.0, 0.0))
                .velocity(new V3(Math.sin(h) * 12.0, 0.0, -Math.cos(h) * 12.0))
                .target(new Target("Harbor", target, "nav_table")).read();
        assertNotNull(r.nav);
        assertEquals(612.0, r.nav.distance(), 1.0e-9);
        assertEquals(81.0, r.nav.bearing(), 1.0e-9);
        assertEquals(9.0, r.nav.relativeBearing(), 1.0e-9);
        assertEquals(12.0, r.nav.closing(), 1.0e-9);
        assertEquals(51.0, r.nav.eta(), 1.0e-9);
        assertNull(ship().read().nav);
    }

    @Test
    void crossTrackIsPositiveRightOfTheLeg() {
        final V3 a = V3.ZERO;
        final V3 b = new V3(0.0, 0.0, -100.0);

        assertEquals(3.0, AirframeMath.crossTrack(a, b, new V3(3.0, 0.0, -50.0)), EPS);
        assertEquals(-2.0, AirframeMath.crossTrack(a, b, new V3(-2.0, 0.0, -50.0)), EPS);
    }

    @Test
    void routeProgressAndCornerChecksAgainstTheLearnedTurn() {
        final List<Waypoint> w = new ArrayList<>();
        w.add(new Waypoint(V3.ZERO, 12.0));
        w.add(new Waypoint(new V3(0.0, 0.0, -40.0), 12.0));
        w.add(new Waypoint(new V3(40.0, 0.0, -40.0), 12.0));
        w.add(new Waypoint(new V3(40.0, 0.0, 0.0), 12.0));
        final float[] radius = AerialSnapshot.nanArray(Envelope.BUCKETS);
        radius[3] = 14.0f;
        radius[4] = 22.0f;
        radius[6] = 34.0f;
        final Envelope env = new Envelope(600.0, 16.0, 2.9, -4.1, 9.2, 1.1, 1.8, radius, 2.0);
        final AirframeReadout r = ship().position(new V3(0.0, 0.0, -20.0)).velocity(new V3(0.0, 0.0, -12.0))
                .route(new Route("Test", w, 1)).envelope(env).read();
        assertNotNull(r.route);
        assertEquals(20.0 / 120.0, r.route.progress(), 1.0e-9);
        assertEquals(20.0, r.route.toNext(), 1.0e-9);
        assertEquals(2, r.route.corners().size());
        final AirframeReadout.Corner c = r.route.corners().get(0);
        assertEquals(90.0, c.angle(), 1.0e-9);

        assertEquals(23.0, c.allowedRadius(), 1.0e-9);
        assertEquals(34.0, c.shipRadius(), 1.0e-9);
        assertFalse(c.feasible());

        assertEquals(9.0, c.suggestedSpeed(), 1.0e-9);
    }

    @Test
    void aCornerWithTheWholeOfBothLegsTakesAWiderTurn() {
        final List<Waypoint> w = List.of(new Waypoint(V3.ZERO, 12.0), new Waypoint(new V3(0.0, 0.0, -40.0), 12.0),
                new Waypoint(new V3(40.0, 0.0, -40.0), 12.0));
        final float[] radius = AerialSnapshot.nanArray(Envelope.BUCKETS);
        radius[6] = 34.0f;
        final Envelope env = new Envelope(600.0, 16.0, 2.9, -4.1, 9.2, 1.1, 1.8, radius, 2.0);
        final AirframeReadout.Corner c = ship().velocity(new V3(0.0, 0.0, -12.0)).route(new Route("L", w, 1))
                .envelope(env).read().route.corners().get(0);
        assertEquals(43.0, c.allowedRadius(), 1.0e-9);
        assertTrue(c.feasible());
    }

    @Test
    void aShallowBendTakesItsSlackAsTrackErrorNotAsRun() {
        final List<Waypoint> w = List.of(new Waypoint(V3.ZERO, 12.0), new Waypoint(new V3(0.0, 0.0, -10.0), 12.0),
                new Waypoint(new V3(200.0 * Math.sin(Math.toRadians(20.0)), 0.0,
                        -10.0 - 200.0 * Math.cos(Math.toRadians(20.0))), 12.0));
        final float[] radius = AerialSnapshot.nanArray(Envelope.BUCKETS);
        radius[6] = 100.0f;
        final Envelope env = new Envelope(600.0, 16.0, 2.9, -4.1, 9.2, 1.1, 1.8, radius, 2.0);
        final AirframeReadout.Corner c = ship().velocity(new V3(0.0, 0.0, -12.0)).route(new Route("B", w, 1))
                .envelope(env).read().route.corners().get(0);
        final double half = Math.tan(Math.toRadians(10.0));
        assertEquals((10.0 + 3.0 / Math.sin(Math.toRadians(20.0))) / half, c.allowedRadius(), 1.0e-6);
        assertTrue(c.feasible(), "a 100 m turn fits in 106: " + c);
    }

    @Test
    void aSampledCircleTheShipFlewPassesItsOwnCheck() {
        final List<Waypoint> w = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            final double a = Math.toRadians(i * 20.0);
            w.add(new Waypoint(new V3(70.0 * Math.sin(a), 0.0, -70.0 * Math.cos(a)), 12.0));
        }
        final float[] radius = AerialSnapshot.nanArray(Envelope.BUCKETS);
        radius[6] = 70.0f;
        final Envelope env = new Envelope(600.0, 16.0, 2.9, -4.1, 9.2, 1.1, 1.8, radius, 2.0);
        for (final AirframeReadout.Corner c : ship().velocity(new V3(0.0, 0.0, -12.0)).route(new Route("C", w, 1))
                .envelope(env).read().route.corners()) {
            assertTrue(c.feasible(), "a circle of the ship's own radius: " + c);
        }
    }

    @Test
    void turnRadiusLookupInterpolatesAndErrsTowardSlowingDown() {
        final float[] radius = AerialSnapshot.nanArray(Envelope.BUCKETS);
        radius[2] = 10.0f;
        radius[3] = 20.0f;
        radius[6] = 40.0f;
        final Envelope env = new Envelope(1.0, 1.0, 1.0, -1.0, 1.0, 1.0, 1.0, radius, 2.0);
        assertEquals(10.0, AirframeMath.turnRadiusAt(env, 5.0), EPS);
        assertEquals(15.0, AirframeMath.turnRadiusAt(env, 6.0), EPS);

        assertEquals(40.0, AirframeMath.turnRadiusAt(env, 9.0), EPS);
        assertTrue(Double.isNaN(AirframeMath.turnRadiusAt(env, 20.0)));
        assertEquals(7.0, AirframeMath.fastestTurning(env, 25.0), EPS);
        assertTrue(Double.isNaN(AirframeMath.fastestTurning(env, 5.0)));
    }

    @Test
    void closestApproachOfTwoShipsOnCrossingTracks() {
        final Traffic other = new Traffic("Skiff", new UUID(1, 1), new V3(30.0, 0.0, -100.0), new V3(0.0, 0.0, 10.0),
                6.0);
        final AirframeReadout r = ship().velocity(new V3(0.0, 0.0, -10.0)).traffic(other).read();
        final AirframeReadout.TrafficState t = r.traffic.get(0);
        assertEquals(Math.hypot(30.0, 100.0), t.distance(), 1.0e-9);
        assertEquals(5.0, t.cpaTime(), 1.0e-9);
        assertEquals(30.0, t.cpaDistance(), 1.0e-9);

        assertFalse(t.conflict());
        final Traffic close = new Traffic("Skiff", new UUID(1, 1), new V3(10.0, 0.0, -100.0), new V3(0.0, 0.0, 10.0),
                6.0);
        assertTrue(ship().velocity(new V3(0.0, 0.0, -10.0)).traffic(close).read().traffic.get(0).conflict());
    }

    @Test
    void dockingOffsetsInOurOwnAxes() {
        final AirframeReadout r = ship().docking(new Docking("Tower", false, new V3(0.7, 0.4, -4.6),
                new V3(0.0, 0.0, 0.8))).read();
        assertEquals(4.6, r.dock.along(), 1.0e-9);
        assertEquals(0.7, r.dock.lateral(), 1.0e-9);
        assertEquals(0.4, r.dock.vertical(), 1.0e-9);
        final double dist = Math.sqrt(0.49 + 0.16 + 21.16);
        assertEquals(dist, r.dock.distance(), 1.0e-9);
        assertEquals(4.6 * 0.8 / dist, r.dock.closing(), 1.0e-9);
        assertEquals(dist / (4.6 * 0.8 / dist), r.dock.timeToContact(), 1.0e-9);
    }

    @Test
    void thePredictedPathIsAnArcWhenTurning() {
        final double speed = 10.0;
        final double rate = Math.toRadians(9.0);
        final AirframeReadout r = ship().velocity(new V3(0.0, 0.0, -speed)).spin(new V3(0.0, -rate, 0.0)).read();
        final V3 end = r.predicted.get(r.predicted.size() - 1);
        final double radius = speed / rate;

        assertEquals(radius, end.x(), 0.05);
        assertEquals(-radius, end.z(), 0.05);
        assertEquals(1 + (int) (AirframeMath.PREDICT_SECONDS / AirframeMath.PREDICT_STEP), r.predicted.size());
    }

    @Test
    void terrainCheckFindsTheImpactAndTheRidge() {
        final Terrain ground = flat(100.0);
        final AirframeReadout r = ship().position(new V3(0.0, 110.0, 0.0)).velocity(new V3(0.0, -2.0, 0.0))
                .terrain(ground).read();
        assertEquals(5.0, r.terrain.impactSeconds(), 1.0e-9);
        assertEquals(-10.0, r.terrain.minClearance(), 1.0e-9);
        final AirframeReadout level = ship().position(new V3(0.0, 130.0, 0.0)).terrain(ridge()).read();
        assertTrue(Double.isNaN(level.terrain.impactSeconds()));
        assertEquals(120.0, level.terrain.ridgeDistance(), 8.0);
        assertEquals(10.0, level.terrain.ridgeClearance(), 1.0e-9);
    }

    @Test
    void stopDistanceFromTheLearnedDeceleration() {
        final Envelope env = new Envelope(60.0, 16.0, 3.0, -4.0, 9.0, 1.0, 2.0, AerialSnapshot.nanArray(16), 2.0);
        final AirframeReadout r = ship().velocity(new V3(0.0, 0.0, -12.0)).envelope(env).read();
        assertEquals(36.0, r.stopDistance, 1.0e-9);
    }

    @Test
    void autopilotAuthorityIsTheHighestKnownAxis() {
        final AerialSnapshot.Autopilot a = new AerialSnapshot.Autopilot("ROUTE", true, 140.0, 12.0, 81.0, V3.NAN,
                0.62, 0.88, Double.NaN, 0.71);
        assertEquals(0.88, ship().autopilot(a).read().maxAuthority, EPS);
        assertEquals("yaw", AerialAlerts.axisAtLimit(a));
    }

    @Test
    void theFixturesReadAsTheirScenarioSays() {
        final AirframeReadout cruise = AirframeMath.read(AerialFixtures.cruise());
        assertEquals(1.07, cruise.support, 1.0e-6);
        assertEquals(72.0, cruise.heading, 1.0e-6);
        assertEquals(320.0, cruise.windFrom, 1.0e-6);

        final double cross = 2.3 * Math.sin(Math.toRadians(68.0));
        final double along = 11.4 + 2.3 * Math.cos(Math.toRadians(68.0));
        assertEquals(Math.toDegrees(Math.atan2(cross, along)), cruise.drift, 0.05);
        final AirframeReadout sinking = AirframeMath.read(AerialFixtures.sinking());
        assertEquals(0.92, sinking.support, 1.0e-6);
        assertTrue(sinking.balloons.get(1).starved());
    }

    private static Propeller prop(String name, double x, double rpm, double stress) {
        return new Propeller(name, "bearing", new V3(x, 0.0, 0.0), NORTH, 100.0, 25.0, 1.0, 1.0, rpm, stress, true);
    }

    private static Terrain flat(double y) {
        final int size = 16;
        final short[] h = new short[size * size];
        java.util.Arrays.fill(h, (short) y);
        return new Terrain(-64.0, -64.0, 8.0, size, h, y, Double.NaN);
    }

    private static Terrain ridge() {
        final int size = 48;
        final double cell = 8.0;
        final short[] h = new short[size * size];
        final double oz = -300.0;
        for (int iz = 0; iz < size; iz++) {
            final double z = oz + (iz + 0.5) * cell;
            for (int ix = 0; ix < size; ix++) {
                h[iz * size + ix] = (short) (Math.abs(z + 120.0) < 4.1 ? 120 : 100);
            }
        }
        return new Terrain(-192.0, oz, cell, size, h, 100.0, Double.NaN);
    }

    private static final class Builder {
        UUID id;
        V3 position = V3.ZERO;
        Quat orientation = Quat.IDENTITY;
        V3 nose = NORTH;
        V3 velocity = V3.ZERO;
        V3 spin = V3.ZERO;
        V3 air;
        boolean airMeasured = true;
        double pressure = 1.0;
        double pressureAbove = Math.exp(-0.004 * Air.PROBE);
        double mass = 1000.0;
        final List<Force> forces = new ArrayList<>();
        final List<Propeller> props = new ArrayList<>();
        final List<Balloon> balloons = new ArrayList<>();
        final List<Traffic> traffic = new ArrayList<>();
        AerialSnapshot.Engine engine;
        Target target;
        AerialSnapshot.Autopilot autopilot;
        Docking docking;
        Terrain terrain;
        Route route;
        Envelope envelope;
        AerialSnapshot.Silhouette silhouette;

        Builder id(UUID u) { this.id = u; return this; }
        Builder position(V3 p) { this.position = p; return this; }
        Builder orientation(Quat q) { this.orientation = q; return this; }
        Builder nose(V3 n) { this.nose = n; return this; }
        Builder velocity(V3 v) { this.velocity = v; return this; }
        Builder spin(V3 s) { this.spin = s; return this; }
        Builder air(V3 a, boolean measured) { this.air = a; this.airMeasured = measured; return this; }
        Builder pressure(double p, double above) { this.pressure = p; this.pressureAbove = above; return this; }
        Builder mass(double m) { this.mass = m; return this; }
        Builder force(String g, V3 f) { this.forces.add(new Force(g, f, V3.NAN)); return this; }
        Builder force(String g, V3 f, V3 c) { this.forces.add(new Force(g, f, c)); return this; }
        Builder prop(Propeller p) { this.props.add(p); return this; }
        Builder balloon(Balloon b) { this.balloons.add(b); return this; }
        Builder traffic(Traffic t) { this.traffic.add(t); return this; }
        Builder engine(AerialSnapshot.Engine e) { this.engine = e; return this; }
        Builder target(Target t) { this.target = t; return this; }
        Builder autopilot(AerialSnapshot.Autopilot a) { this.autopilot = a; return this; }
        Builder docking(Docking d) { this.docking = d; return this; }
        Builder terrain(Terrain t) { this.terrain = t; return this; }
        Builder route(Route r) { this.route = r; return this; }
        Builder envelope(Envelope e) { this.envelope = e; return this; }
        Builder silhouette(AerialSnapshot.Silhouette s) { this.silhouette = s; return this; }

        AirframeReadout read() {
            final AerialSnapshot s = new AerialSnapshot(100L, "Test", this.id, 0,
                    new Body(this.position, this.orientation, this.nose, this.velocity, this.spin),
                    new Air(this.airMeasured, this.air != null ? this.air : this.velocity, this.pressure,
                            this.pressureAbove, 63.0, new V3(0.0, -11.0, 0.0)),
                    new Mass(this.mass, V3.NAN, -1), this.forces, this.props, this.balloons, this.engine, this.target,
                    this.autopilot, this.docking, this.traffic, this.terrain, this.route, null, null, this.envelope,
                    this.silhouette);
            return AirframeMath.read(s);
        }
    }
}
