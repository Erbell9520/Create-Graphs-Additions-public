package com.createstats.aerial;

import java.util.List;

import com.createstats.aerial.AerialFixtures.Scene;
import com.createstats.vccmk4.air.core.AltitudeLaw;
import com.createstats.vccmk4.air.core.PressureModel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightChartsTest {
    @Test
    void theBalloonsHoldExactlyTheWeightAtTheirCeiling() {
        assertEquals(1.0, FlightCharts.usableAt(159.0, 159.0, 0.004), 1.0e-12);
        assertEquals(Math.exp(0.4), FlightCharts.usableAt(59.0, 159.0, 0.004), 1.0e-12);
        assertTrue(FlightCharts.usableAt(200.0, 159.0, 0.004) < 1.0);
        assertEquals(0.0, FlightCharts.spareAt(159.0, 159.0, 0.004, 1144.0), 1.0e-9);
        assertTrue(FlightCharts.spareAt(96.0, 159.0, 0.004, 1144.0) > 0.0, "room below the ceiling");
        assertTrue(FlightCharts.spareAt(180.0, 159.0, 0.004, 1144.0) < 0.0, "short above it");
    }

    @Test
    void anUnknownCeilingOrLapseDrawsNothing() {
        assertTrue(Double.isNaN(FlightCharts.usableAt(100.0, Double.NaN, 0.004)));
        assertTrue(Double.isNaN(FlightCharts.usableAt(100.0, Double.NEGATIVE_INFINITY, 0.004)));
        assertTrue(Double.isNaN(FlightCharts.usableAt(100.0, 159.0, 0.0)));
        assertTrue(Double.isNaN(FlightCharts.holdAt(100.0, 159.0, 0.004, Double.NaN)));
    }

    @Test
    void theChartIsTheAerialsOwnCeilingLaw() {
        final double mass = 1144.0;
        final double g = 11.0;
        final PressureModel air = new PressureModel(96.0, 0.87, 0.004);
        final List<AltitudeLaw.Cell> cells = List.of(new AltitudeLaw.Cell(300.0, 900.0, 1.5, 0.87, 900.0),
                new AltitudeLaw.Cell(280.0, 700.0, 1.5, 0.87, 700.0));
        final double bc = AltitudeLaw.ceiling(mass, g, 0.0, cells, air);
        assertTrue(Double.isFinite(bc), "a finite ceiling to test against: " + bc);
        double held = 0.0;
        for (final AltitudeLaw.Cell c : cells) {
            held += c.maxGas() * c.strength();
        }
        for (final double y : new double[]{70.0, 96.0, 130.0, bc, bc + 40.0}) {
            final double aerial = AltitudeLaw.CEILING_RESERVE * held * g * air.at(y) / g;
            assertEquals(aerial, FlightCharts.holdAt(y, bc, air.lapse(), mass), aerial * 1.0e-9, "at y " + y);
        }
        assertEquals(AltitudeLaw.CEILING_RESERVE, FlightCharts.RESERVE, 0.0);
    }

    @Test
    void onlyLearnedLiftTakesWeightOffTheBalloons() {
        final AirframeReadout r = AirframeMath.read(AerialFixtures.full(Scene.CRUISE));
        final double hidden = r.snapshot.parts().learned().hidden();
        assertTrue(hidden > 0.0, "the Kestrel has learned some lift besides its balloons");
        assertEquals(r.carried - hidden / r.gravity, FlightCharts.mustHold(r), 1.0e-9);

        final AirframeReadout plain = AirframeMath.read(AerialFixtures.cruise());
        assertEquals(plain.carried, FlightCharts.mustHold(plain), 1.0e-9);
    }

    @Test
    void measuredDragMeetsThePushAtTheTopSpeed() {
        final AirframeReadout r = AirframeMath.read(AerialFixtures.cruise());
        assertTrue(Double.isFinite(r.topSpeed), "the cruise fixture measures drag");
        final FlightCharts.Drag d = FlightCharts.drag(r);
        assertEquals(FlightCharts.DragSource.MEASURED, d.source());
        assertEquals(r.topSpeed, d.top(), 1.0e-9);
        assertEquals(FlightCharts.pushAt(r, d.top()), d.slope() * d.top(), 1.0e-6 * d.slope() * d.top());

        assertTrue(FlightCharts.pushAt(r, d.top() * 0.5) > d.slope() * d.top() * 0.5);
        assertTrue(FlightCharts.pushAt(r, d.top() * 1.2) < d.slope() * d.top() * 1.2);
    }

    @Test
    void withoutForcesTheAerialsFullAheadDrawsTheDrag() {
        final AerialSnapshot full = AerialFixtures.full(Scene.CRUISE);
        final AerialSnapshot bare = new AerialSnapshot(full.gameTime(), full.name(), full.shipId(), full.commands(),
                full.body(), full.air(), full.mass(), List.of(), full.propellers(), full.balloons(), full.engine(),
                full.target(), full.autopilot(), full.docking(), full.traffic(), full.terrain(), full.route(),
                full.trail(), full.history(), full.envelope(), full.silhouette(), full.helm(), full.parts(),
                full.voyage());
        final AirframeReadout r = AirframeMath.read(bare);
        assertTrue(Double.isNaN(r.topSpeed), "no forces, no measured top speed");
        final FlightCharts.Drag d = FlightCharts.drag(r);
        assertEquals(FlightCharts.DragSource.LEARNED, d.source());
        assertEquals(bare.helm().fullAhead(), d.top(), 1.0e-9);
        assertEquals(FlightCharts.pushAt(r, d.top()), d.slope() * d.top(), 1.0e-6 * d.slope() * d.top());
    }

    @Test
    void noHelmAndNoForcesDrawsNoDrag() {
        final AerialSnapshot cruise = AerialFixtures.cruise();
        final AerialSnapshot bare = new AerialSnapshot(cruise.gameTime(), cruise.name(), cruise.shipId(),
                cruise.commands(), cruise.body(), cruise.air(), cruise.mass(), List.of(), cruise.propellers(),
                cruise.balloons(), cruise.engine(), cruise.target(), cruise.autopilot(), cruise.docking(),
                cruise.traffic(), cruise.terrain(), cruise.route(), cruise.trail(), cruise.history(),
                cruise.envelope(), cruise.silhouette());
        assertEquals(FlightCharts.DragSource.NONE, FlightCharts.drag(AirframeMath.read(bare)).source());
    }

    @Test
    void thePushIsGoneAtTheAirflowSpeed() {
        final AirframeReadout r = AirframeMath.read(AerialFixtures.cruise());
        assertTrue(Double.isFinite(r.propTopOut));
        assertTrue(FlightCharts.pushAt(r, 0.0) > 0.0);
        assertTrue(FlightCharts.pushAt(r, 0.0) > FlightCharts.pushAt(r, r.propTopOut * 0.5));

        assertEquals(0.0, FlightCharts.pushAt(r, 1000.0), 1.0e-9);
    }

    @Test
    void propsLimitWhenTheTopSpeedReachesTheirAirflow() {
        assertTrue(FlightCharts.propsLimit(11.5, 12.0));
        assertFalse(FlightCharts.propsLimit(5.1, 12.0));
        assertFalse(FlightCharts.propsLimit(Double.NaN, 12.0));
        assertFalse(FlightCharts.propsLimit(5.1, Double.NaN));
    }
}
