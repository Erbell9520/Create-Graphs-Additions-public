package com.createstats.aerial;

import java.util.List;
import java.util.Set;

import com.createstats.aerial.AerialAlerts.Alert;
import com.createstats.aerial.AerialAlerts.Code;
import com.createstats.aerial.AerialAlerts.Level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialAlertsTest {
    private static AirframeReadout flying() {
        final AirframeReadout r = new AirframeReadout(AerialFixtures.cruise());
        r.airMeasured = true;
        r.landed = false;
        r.agl = 40.0;
        r.support = 1.05;
        r.verticalSpeed = 0.0;
        return r;
    }

    private static Level level(List<Alert> alerts, Code code) {
        for (final Alert a : alerts) {
            if (a.code() == code) {
                return a.level();
            }
        }
        return null;
    }

    @Test
    void sinkRateIsJudgedAsSecondsToTheGroundWithHysteresis() {
        final AirframeReadout r = flying();
        r.agl = 44.0;
        r.verticalSpeed = -4.0;

        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.SINK_RATE));

        r.agl = 52.0;
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.SINK_RATE));
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(Code.SINK_RATE), 0.0), Code.SINK_RATE));

        r.agl = 16.0;
        assertEquals(Level.CRITICAL, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.SINK_RATE));

        r.agl = 400.0;
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.SINK_RATE));
    }

    @Test
    void terrainOnThePredictedPath() {
        final AirframeReadout r = flying();
        r.terrain = new AirframeReadout.TerrainCheck(-3.0, 4.0, 4.0, Double.NaN, Double.NaN, Double.NaN, false);
        assertEquals(Level.CRITICAL, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.TERRAIN));
        r.terrain = new AirframeReadout.TerrainCheck(-3.0, 9.0, 9.0, Double.NaN, Double.NaN, Double.NaN, false);
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.TERRAIN));
        r.terrain = new AirframeReadout.TerrainCheck(2.0, 6.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false);
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.TERRAIN));
        r.terrain = new AirframeReadout.TerrainCheck(4.0, 6.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false);
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.TERRAIN));
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(Code.TERRAIN), 0.0), Code.TERRAIN));
    }

    @Test
    void liftShortIsCriticalBelowNinetyAndACautionOnlyWhileSinking() {
        final AirframeReadout r = flying();
        r.support = 0.89;
        assertEquals(Level.CRITICAL, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LIFT_SHORT));
        r.support = 0.97;
        r.verticalSpeed = 0.5;
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LIFT_SHORT), "climbing on momentum");
        r.verticalSpeed = -0.5;
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LIFT_SHORT));
    }

    @Test
    void aLandedShipRaisesNoFlightAlerts() {
        final AirframeReadout r = flying();
        r.landed = true;
        r.support = 0.2;
        r.verticalSpeed = -1.0;
        r.agl = 0.5;
        final List<Alert> alerts = AerialAlerts.evaluate(r, Set.of(), 0.0);
        assertNull(level(alerts, Code.LIFT_SHORT));
        assertNull(level(alerts, Code.SINK_RATE));
        assertNull(level(alerts, Code.TERRAIN));
    }

    @Test
    void trafficConflictBySecondsToClosestApproach() {
        final AirframeReadout r = flying();
        final AerialSnapshot.Traffic t = new AerialSnapshot.Traffic("Skiff", null, V3.ZERO, V3.ZERO, 6.0);
        r.traffic.add(new AirframeReadout.TrafficState(t, 80.0, 2.0, 30.0, 8.0, 9.0, 12.0, true));
        final List<Alert> alerts = AerialAlerts.evaluate(r, Set.of(), 0.0);
        assertEquals(Level.CRITICAL, level(alerts, Code.TRAFFIC));
        assertTrue(alerts.get(0).text().contains("Skiff"));
        r.traffic.clear();
        r.traffic.add(new AirframeReadout.TrafficState(t, 200.0, 2.0, 30.0, 8.0, 20.0, 12.0, true));
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.TRAFFIC));
    }

    @Test
    void aStaleFeedIsItsOwnAlert() {
        final AirframeReadout r = flying();
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 2.5), Code.NO_FEED));
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(), 4.0), Code.NO_FEED));
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(Code.NO_FEED), 2.5), Code.NO_FEED));
    }

    @Test
    void balloonsPropsAndControl() {
        final AirframeReadout r = AirframeMath.read(AerialFixtures.sinking());
        final List<Alert> alerts = AerialAlerts.evaluate(r, Set.of(), 0.0);
        assertEquals(Level.CAUTION, level(alerts, Code.BALLOON), "balloon 2 is short and not filling");
        assertEquals(Level.CAUTION, level(alerts, Code.PROP_LAG), "prop 3 lags on a 94% stressed shaft");
        assertEquals(Level.CAUTION, level(alerts, Code.CONTROL_LIMIT), "lift authority at 100%");

        for (int i = 1; i < alerts.size(); i++) {
            assertTrue(alerts.get(i - 1).level().compareTo(alerts.get(i).level()) >= 0);
        }
        assertEquals(Level.CRITICAL, AerialAlerts.worst(alerts));
    }

    @Test
    void lowSpeedWarnsWithMarginBeforeTheLiftGoesShort() {
        final AirframeReadout r = flying();
        r.minHoldSpeed = 10.8;
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LOW_SPEED),
                "11.4 is inside a tenth of 10.8");
        r.minHoldSpeed = 10.0;
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LOW_SPEED));
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(Code.LOW_SPEED), 0.0), Code.LOW_SPEED),
                "held until 15% clear");

        r.minHoldSpeed = 14.0;
        r.topSpeed = 12.2;
        boolean named = false;
        for (final Alert a : AerialAlerts.evaluate(r, Set.of(), 0.0)) {
            named |= a.code() == Code.LOW_SPEED && a.text().contains("top speed 12.2");
        }
        assertTrue(named);
        r.landed = true;
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LOW_SPEED), "parked is not slow");
    }

    @Test
    void aBalloonWithItsBurnersOutSaysSo() {
        final AirframeReadout r = flying();
        final AerialSnapshot.Balloon b = new AerialSnapshot.Balloon("Fore", "hot_air", V3.ZERO, 500.0, 200.0, 400.0,
                500.0, 0.0, 0.0, 1.0);
        r.balloons.add(new AirframeReadout.BalloonState(b, 0.5, 0.3, Double.NaN, true, true));
        boolean said = false;
        for (final Alert a : AerialAlerts.evaluate(r, Set.of(), 0.0)) {
            said |= a.code() == Code.BALLOON && a.level() == Level.CAUTION && a.text().contains("burners out");
        }
        assertTrue(said, "out is a different job from outpaced, and the alert names which");
    }

    @Test
    void controlLimitOnlyWhileTheLoopIsFlying() {
        final AirframeReadout engaged = flying();
        engaged.maxAuthority = 0.99;
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(engaged, Set.of(), 0.0), Code.CONTROL_LIMIT));

        final AerialSnapshot s = AerialFixtures.cruise();
        final AerialSnapshot.Autopilot off = new AerialSnapshot.Autopilot("OFF", false, Double.NaN, Double.NaN,
                Double.NaN, V3.NAN, 0.99, 0.99, 0.99, 0.99);
        final AirframeReadout manual = AirframeMath.read(new AerialSnapshot(s.gameTime(), s.name(), s.shipId(),
                s.commands(), s.body(), s.air(), s.mass(), s.forces(), s.propellers(), s.balloons(), s.engine(),
                s.target(), off, s.docking(), s.traffic(), s.terrain(), s.route(), s.trail(), s.history(),
                s.envelope(), s.silhouette()));
        assertNull(level(AerialAlerts.evaluate(manual, Set.of(), 0.0), Code.CONTROL_LIMIT));
    }

    @Test
    void aTargetAboveTheCeilingIsSaid() {
        final AirframeReadout r = new AirframeReadout(AerialFixtures.cruise());
        r.airMeasured = true;
        r.reserve = 0.03;
        r.ceilingY = 131.0;
        final List<Alert> alerts = AerialAlerts.evaluate(r, Set.of(), 0.0);
        assertEquals(Level.CAUTION, level(alerts, Code.CEILING));

        r.reserve = -0.05;
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.CEILING));
    }

    @Test
    void anInfeasibleCornerSaysHowSlowToGo() {
        final AirframeReadout r = flying();
        r.route = new AirframeReadout.RouteState("R", 1, 3, 0.0, 0.2, 10.0, 1.0,
                List.of(new AirframeReadout.Corner(1, 90.0, 12.0, 20.0, 34.0, false, 7.0)));
        final List<Alert> alerts = AerialAlerts.evaluate(r, Set.of(), 0.0);
        assertEquals(Level.ADVISORY, level(alerts, Code.ROUTE));
        boolean found = false;
        for (final Alert a : alerts) {
            found |= a.code() == Code.ROUTE && a.text().contains("7.0 m/s");
        }
        assertTrue(found);
    }

    @Test
    void fuelThresholds() {
        final AirframeReadout r = flying();
        r.enduranceSeconds = 100.0;
        assertEquals(Level.CAUTION, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LOW_FUEL));
        r.enduranceSeconds = 20.0;
        assertEquals(Level.CRITICAL, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LOW_FUEL));
        r.enduranceSeconds = 3000.0;
        assertNull(level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.LOW_FUEL));
    }

    @Test
    void noAirDataIsAnAdvisoryNotASilence() {
        final AirframeReadout r = flying();
        r.airMeasured = false;
        assertEquals(Level.ADVISORY, level(AerialAlerts.evaluate(r, Set.of(), 0.0), Code.NO_AIR_DATA));
    }

    @Test
    void textIsPlainAsciiWithNoDashes() {
        final List<Alert> alerts = AerialAlerts.evaluate(AirframeMath.read(AerialFixtures.sinking()), Set.of(), 5.0);
        assertFalse(alerts.isEmpty());
        for (final Alert a : alerts) {
            for (final char c : a.text().toCharArray()) {
                assertTrue(c >= 32 && c < 127, "non ASCII in " + a.text());
            }
        }
        assertEquals("41 min", AerialAlerts.duration(2460.0));
        assertEquals("2:05", AerialAlerts.duration(125.0));
        assertEquals("40 s", AerialAlerts.duration(40.0));
        assertEquals(AerialAlerts.codes(alerts).size(), alerts.size());
    }
}
