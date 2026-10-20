package com.createstats.vccmk4.air.core;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightIntentTest {
    private static FlightIntent.Now helm(double y, double held, double vy) {
        return new FlightIntent.Now(AerialPilot.Mode.HELM, LeaveMode.ANCHOR, false, y, held, held, vy, 16.0, 16.0, 0.0,
                Double.NaN, Double.NaN, "", 46.0);
    }

    private static FlightIntent.Now goTo(double heading, double bearing, double dist, double speed) {
        return new FlightIntent.Now(AerialPilot.Mode.GO_TO, LeaveMode.GO_TO, false, 142.0, 142.0, 142.0, 0.0, heading,
                bearing, speed, dist, bearing, "LODESTONE", 46.0);
    }

    @Test
    void theMocksLines() {
        assertEquals("HOLDING 142, HEADING 016", FlightIntent.banner(helm(142.0, 142.0, 0.0), null, null).text());
        assertEquals("CLIMBING TO 150  3 s", FlightIntent.banner(helm(142.6, 150.0, 2.6), null, null).text());
        assertEquals("TURNING TO 052, THEN 312 b TO THE LODESTONE",
                FlightIntent.banner(goTo(38.0, 52.0, 312.0, 4.2), null, null).text());
        assertEquals("312 b TO THE LODESTONE  1:10", FlightIntent.banner(goTo(52.0, 52.0, 312.0, 4.457), null, null).text());
    }

    @Test
    void theMostUrgentThingComesFirst() {
        FlightIntent.Now now = helm(142.0, 142.0, 0.0);
        FlightIntent.Line wrong = FlightIntent.banner(now, "Lift short: even full burners cannot hold this.", null);
        assertTrue(wrong.alert());
        assertEquals("LIFT SHORT: EVEN FULL BURNERS CANNOT HOLD THIS.", wrong.text(), "in full, never cut");
        FlightIntent.Now held = new FlightIntent.Now(AerialPilot.Mode.ANCHOR, LeaveMode.ANCHOR, true, 142.0, 142.0, 142.0,
                0.0, 16.0, 16.0, 0.0, Double.NaN, Double.NaN, "", 46.0);
        assertEquals("HELD BY A STAFF: STANDING BY, ENGINES IDLE", FlightIntent.banner(held, "Something", null).text());
        TerrainGuard.Result ridge = new TerrainGuard.Result(TerrainGuard.Verdict.CLIMB, 151.0, Double.POSITIVE_INFINITY,
                147.0, 60.0, 147.0, 60.0, Double.POSITIVE_INFINITY);
        FlightIntent.Line t = FlightIntent.banner(goTo(52.0, 52.0, 140.0, 4.0), "Lift short: x", ridge);
        assertEquals("TERRAIN AHEAD: RIDGE AT 147 IN 60 b. CLIMBING TO CLEAR IT BY 4.", t.text());
        assertTrue(t.alert());
        FlightIntent.Line pilot = FlightIntent.banner(helm(142.0, 142.0, 0.0), null, ridge);
        assertTrue(pilot.text().endsWith("CLIMB OR TURN."), "at the helm, a warning: " + pilot.text());
    }

    @Test
    void settlingOntoTheGroundIsItsOwnLine() {
        FlightIntent.Now landing = new FlightIntent.Now(AerialPilot.Mode.HELM, LeaveMode.ANCHOR, false, 66.1, 64.4, 64.4,
                -0.3, 16.0, 16.0, 0.0, Double.NaN, Double.NaN, "", 1.7);
        assertEquals("SETTLING ONTO THE GROUND  1.7 b TO GO", FlightIntent.banner(landing, null, null).text());
    }

    @Test
    void anEtaIsSecondsThenMinutesAndNeverAPromiseItCannotKeep() {
        assertEquals("3 s", FlightIntent.eta(7.8, 2.6));
        assertEquals("1:10", FlightIntent.eta(312.0, 4.457));
        assertEquals("", FlightIntent.eta(100.0, 0.0), "not closing: no time");
        assertEquals("", FlightIntent.eta(100.0, -1.0));
        assertEquals("", FlightIntent.eta(1.0e6, 0.1), "over an hour: no time");
        assertEquals("LODESTONE  312 b  1:10", FlightIntent.targetLabel("LODESTONE", 312.0, 4.457));
    }

    @Test
    void theModeLines() {
        assertEquals(List.of("HELM", "LEAVE: ANCHOR"),
                List.of(FlightIntent.modeLines(AerialPilot.Mode.HELM, LeaveMode.ANCHOR, false)));
        assertEquals(List.of("GO TO", "PILOT LEFT THE HELM"),
                List.of(FlightIntent.modeLines(AerialPilot.Mode.GO_TO, LeaveMode.GO_TO, false)));
        assertEquals(List.of("HELD", "BY A PHYSICS STAFF"),
                List.of(FlightIntent.modeLines(AerialPilot.Mode.ANCHOR, LeaveMode.ANCHOR, true)));
    }

    @Test
    void aLongReportLineIsWrappedAtWords() {
        String line = "! The engines push across the Aerial's nose: turn the nose on the console's Ship page, or with a wrench.";
        List<String> w = FlightIntent.wrap(line, 40);
        assertTrue(w.size() > 1);
        for (String piece : w) assertTrue(piece.length() <= 40, "'" + piece + "' is " + piece.length());
        assertFalse(w.get(0).startsWith(" "));
        assertTrue(w.get(1).startsWith("  "), "the rest indented under the first");
        assertEquals(line.replace(" ", ""), String.join("", w).replace(" ", ""), "nothing lost");
        assertEquals(List.of("short"), FlightIntent.wrap("short", 40));
    }
}
