package com.createstats.vccmk4.air.core;

import com.createstats.vccmk4.air.core.AerialCockpitMotion.Frame;
import com.createstats.vccmk4.air.core.AerialCockpitMotion.Lamp;
import com.createstats.vccmk4.air.core.AerialCockpitMotion.Pose;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialCockpitMotionTest {
    private static final double E = 1.0e-9;

    private static AerialCockpitState state(boolean piloted, AerialPilot.Mode mode, LeaveMode leave, double order,
                                            double turn, double climb, double air, double climbRate, double yawRate) {
        return AerialCockpitState.of(piloted, true, false, mode, leave, order, order, turn, climb, 0.0, 100.0, 100.0,
                climbRate, air, 0.0, Double.NaN, 0.0, 0.0, yawRate, 8.0);
    }

    @Test
    void theAirSpeedNeedleRunsClockwiseFromItsStopToSixteenBlocksASecond() {
        assertEquals(-135.0, AerialCockpitMotion.asiDegrees(0.0), E, "at rest on its stop, lower left");
        assertEquals(0.0, AerialCockpitMotion.asiDegrees(8.0), E, "straight up at the assumed top speed");
        assertEquals(135.0, AerialCockpitMotion.asiDegrees(16.0), E);
        assertEquals(135.0, AerialCockpitMotion.asiDegrees(40.0), E, "and no further: its pin");
        assertEquals(AerialCockpitMotion.asiDegrees(3.0), AerialCockpitMotion.asiDegrees(-3.0), E,
                "a ship backing reads its speed through the air, as a pitot does not care which way it blows");
    }

    @Test
    void theAltimeterTurnsOnceAHundredBlocksAndNeverWrapsOnItsOwn() {
        assertEquals(360.0, AerialCockpitMotion.altitudeDegrees(100.0), E);
        assertEquals(720.0 + 36.0, AerialCockpitMotion.altitudeDegrees(210.0), E,
                "unwrapped, so the needle climbs through 200 instead of spinning back");
        assertEquals(-36.0, AerialCockpitMotion.altitudeDegrees(-10.0), E);
    }

    @Test
    void theRateOfClimbReadsLevelAtNineOClockAndPinsAtFourBlocksASecond() {
        assertEquals(-90.0, AerialCockpitMotion.vsiDegrees(0.0), E);
        assertEquals(0.0, AerialCockpitMotion.vsiDegrees(4.0), E, "a full climb at twelve");
        assertEquals(-180.0, AerialCockpitMotion.vsiDegrees(-4.0), E, "a full descent at six");
        assertEquals(0.0, AerialCockpitMotion.vsiDegrees(9.0), E);
    }

    @Test
    void theTurnNeedleSitsOnItsSideMarkAtTheFeelsOwnRate() {
        Feel feel = Feel.STANDARD;
        assertEquals(AerialCockpitMotion.TURN_MARK_DEG, AerialCockpitMotion.turnDegrees(feel.turn, feel.turn), E,
                "the mark is the rate the Aerial turns at with the rudders over: the Feel's");
        assertEquals(-AerialCockpitMotion.TURN_MARK_DEG, AerialCockpitMotion.turnDegrees(-feel.turn, feel.turn), E);
        assertEquals(AerialCockpitMotion.TURN_PIN_DEG, AerialCockpitMotion.turnDegrees(10.0 * feel.turn, feel.turn), E);
        assertEquals(0.0, AerialCockpitMotion.turnDegrees(5.0, 0.0), E, "an unknown Feel reads no turn, never a division by zero");
    }

    @Test
    void theHandControlsFollowTheOrdersTheAerialGives() {
        assertEquals(60.0, AerialCockpitMotion.yokeRollDegrees(1.0), E, "a full rudder order to the right");
        assertEquals(-60.0, AerialCockpitMotion.yokeRollDegrees(-3.0), E);
        assertEquals(AerialCockpitMotion.YOKE_SLIDE_UNITS, AerialCockpitMotion.yokeSlideUnits(1.0), E, "pulled all the way for a full climb");
        assertEquals(-0.5 * AerialCockpitMotion.YOKE_SLIDE_UNITS, AerialCockpitMotion.yokeSlideUnits(-0.5), E);
        assertTrue(AerialCockpitMotion.YOKE_SLIDE_UNITS < 1.0,
                "the shaft sits a unit into its column head at rest: a unit of travel would pull it clear or bury it flush");
        assertEquals(35.0, AerialCockpitMotion.leverDegrees(1.0), E, "Full Ahead");
        assertEquals(8.75, AerialCockpitMotion.leverDegrees(0.25), E, "a notch is a quarter of the swing");
        assertEquals(-35.0, AerialCockpitMotion.leverDegrees(-1.0), E, "Full Astern");
        assertEquals(135.0, AerialCockpitMotion.trimDegrees(1.0), E, "three eighths of a turn each way");
    }

    private static double number(String src, String name) {
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(name) + " = (-?[0-9.]+)").matcher(src);
        assertTrue(m.find(), "the generator no longer defines " + name);
        return Double.parseDouble(m.group(1));
    }

    private static double[] triple(String src, String names) {
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(names) + " = (-?[0-9.]+), (-?[0-9.]+), (-?[0-9.]+)").matcher(src);
        assertTrue(m.find(), "the generator no longer defines " + names);
        return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3))};
    }

    @Test
    void theYokesVerticalOrderIsThePlansOwnReferenceClimb() {
        AltitudeLaw.Limits lim = Feel.STANDARD.altitude();
        double[][] cases = {{80.0, 0.0, 100.0}, {80.0, 2.5, 100.0}, {120.0, -1.0, 100.0}, {100.0, 0.0, 100.0},
                {64.0, 4.0, 64.5}};
        for (double[] c : cases) {
            AltitudeLaw.Plan plan = AltitudeLaw.plan(c[0], c[1], c[2], 1000.0, 9.81, 0.0, List.of(), lim, 0.0, 0.0);
            double mine = AltitudeLaw.referenceSpeed(AltitudeLaw.lookaheadError(c[0], c[1], c[2]), lim);
            assertEquals(plan.vRef(), mine, E, "y " + c[0] + " vy " + c[1] + " held " + c[2]);
        }
        assertTrue(lim.climb() > 0.0, "the yoke's travel is that climb over this, which must not be zero");
    }

    private static double[] q(double ax, double ay, double az, double deg) {
        double h = Math.toRadians(deg) / 2.0;
        double s = Math.sin(h);
        return new double[]{ax * s, ay * s, az * s, Math.cos(h)};
    }

    @Test
    void aShipThatHasNotTurnedReadsItsConsolesOwnFacingLevel() {
        double[] id = {0, 0, 0, 1};
        assertArrayNear(new double[]{0, 0, 0}, AerialCockpitMotion.attitude(id[0], id[1], id[2], id[3], 0, -1));
        assertArrayNear(new double[]{90, 0, 0}, AerialCockpitMotion.attitude(id[0], id[1], id[2], id[3], 1, 0));
        assertArrayNear(new double[]{180, 0, 0}, AerialCockpitMotion.attitude(id[0], id[1], id[2], id[3], 0, 1));
        assertArrayNear(new double[]{270, 0, 0}, AerialCockpitMotion.attitude(id[0], id[1], id[2], id[3], -1, 0));
        for (int[] f : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            assertEquals(AerialCockpitMotion.attitude(0, 0, 0, 1, f[0], f[1])[0], AerialCockpitMotion.facingHeading(f[0], f[1]), E);
        }
    }

    @Test
    void aShipTurnedRightReadsAHeadingToTheEast() {
        double[] r = q(0, 1, 0, -90.0);
        assertEquals(90.0, AerialCockpitMotion.attitude(r[0], r[1], r[2], r[3], 0, -1)[0], 1.0e-6);
        double[] l = q(0, 1, 0, 30.0);
        assertEquals(330.0, AerialCockpitMotion.attitude(l[0], l[1], l[2], l[3], 0, -1)[0], 1.0e-6);
    }

    @Test
    void aNoseUpShipReadsAPositivePitchAndARightWingDownAPositiveRoll() {
        double[] up = q(1, 0, 0, 10.0);
        double[] a = AerialCockpitMotion.attitude(up[0], up[1], up[2], up[3], 0, -1);
        assertEquals(10.0, a[1], 1.0e-6, "nose up");
        assertEquals(0.0, a[2], 1.0e-6);
        double[] bank = q(0, 0, 1, -12.0);
        double[] b = AerialCockpitMotion.attitude(bank[0], bank[1], bank[2], bank[3], 0, -1);
        assertEquals(12.0, b[2], 1.0e-6, "the right hand (east) down");
        assertEquals(0.0, b[1], 1.0e-6);

        double[] side = AerialCockpitMotion.attitude(up[0], up[1], up[2], up[3], 1, 0);
        assertEquals(0.0, side[1], 1.0e-6, "the east console's nose stays level");
        assertEquals(10.0, side[2], 1.0e-6, "and it rolls right: the attitude is the console's, not the ship's");
    }

    private static void assertArrayNear(double[] want, double[] got) {
        for (int i = 0; i < want.length; i++) assertEquals(want[i], got[i], 1.0e-6, "component " + i);
    }

    @Test
    void theEarthIsTheCircleBelowTheHorizonAtAnyPitchAndTilesWithNoGap() {
        double r = 1.75;
        for (double drop : new double[]{-1.5, -0.6, 0.0, 0.3, 1.5}) {
            double[] s = AerialCockpitMotion.earthStrips(drop, r, AerialCockpitMotion.EARTH_STRIP);
            assertTrue(s.length > 0 && s.length % 4 == 0);
            assertEquals(-drop, s[3], E, "the top strip's top edge is the horizon line, lowered by a nose-up pitch");
            assertEquals(-r, s[s.length - 3], E, "the bottom one ends on the circle");
            double area = 0.0;
            for (int i = 0; i < s.length; i += 4) {
                assertTrue(s[i + 3] - s[i + 1] <= AerialCockpitMotion.EARTH_STRIP + E, "no strip taller than the step");
                assertEquals(-s[i], s[i + 2], E, "centred under the dial's centre");
                double mid = (s[i + 1] + s[i + 3]) / 2.0;
                assertEquals(Math.sqrt(r * r - mid * mid), s[i + 2], 1.0e-6, "as wide as the circle at its middle");
                if (i > 0) assertEquals(s[i - 3], s[i + 3], E, "each strip starts where the one above it ends");
                area += (s[i + 2] - s[i]) * (s[i + 3] - s[i + 1]);
            }
            double h = r + (-drop);
            double segment = r * r * Math.acos((r - h) / r) - (r - h) * Math.sqrt(2.0 * r * h - h * h);
            assertEquals(segment, area, 0.02 * Math.PI * r * r, "the strips' area is the circle's segment below the line");
        }
    }

    @Test
    void theEarthNeverLeavesItsDialAtAnyRoll() {
        double r = 1.75;
        double[] s = AerialCockpitMotion.earthStrips(-0.4, r, AerialCockpitMotion.EARTH_STRIP);
        for (int i = 0; i < s.length; i += 4) {
            for (double[] c : new double[][]{{s[i], s[i + 1]}, {s[i], s[i + 3]}, {s[i + 2], s[i + 1]}, {s[i + 2], s[i + 3]}}) {
                assertTrue(Math.hypot(c[0], c[1]) <= r + 0.085, "corner " + c[0] + ", " + c[1]);
            }
        }
        assertEquals(0, AerialCockpitMotion.earthStrips(5.0, r, 0.125).length, "the horizon below the dial: all sky");
        double[] all = AerialCockpitMotion.earthStrips(-5.0, r, 0.125);
        assertEquals(r, all[3], E, "the horizon above it: all earth");
        assertEquals(1.5, AerialCockpitMotion.horizonDrop(40.0), E, "the earth's travel stops at fifteen degrees");
        assertTrue(AerialCockpitMotion.horizonDrop(AerialCockpitMotion.HORIZON_PITCH_STOP) < r,
                "at the stop a sliver of the other colour still shows, so the pilot can tell up from down");
    }

    @Test
    void eachLampIsLitByTheAerialsOwnState() {
        AerialCockpitState helmAnchor = state(true, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 0);
        assertTrue(AerialCockpitMotion.lit(Lamp.HELM, helmAnchor));
        assertTrue(AerialCockpitMotion.lit(Lamp.ANCHOR_ARMED, helmAnchor), "armed: what the ship takes up if the pilot stands");
        assertFalse(AerialCockpitMotion.lit(Lamp.ANCHOR_ENGAGED, helmAnchor));
        assertFalse(AerialCockpitMotion.lit(Lamp.KEEP_ARMED, helmAnchor));
        AerialCockpitState keeping = state(false, AerialPilot.Mode.KEEP_COURSE, LeaveMode.KEEP_COURSE, 0.5, 0, 0, 4, 0, 0);
        assertTrue(AerialCockpitMotion.lit(Lamp.KEEP_ENGAGED, keeping), "engaged: the ship flies itself in it");
        assertFalse(AerialCockpitMotion.lit(Lamp.HELM, keeping));
        assertFalse(AerialCockpitMotion.lit(Lamp.KEEP_ARMED, keeping));
        AerialCockpitState moored = state(false, AerialPilot.Mode.MOORED, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 0);
        for (Lamp l : Lamp.values()) assertEquals(l == Lamp.MOORED, AerialCockpitMotion.lit(l, moored), l.name());
        AerialCockpitState caution = AerialCockpitState.of(false, false, true, AerialPilot.Mode.OFF, LeaveMode.ANCHOR, 0,
                0, 0, 0, 0, 0, Double.NaN, 0, 0, 0, Double.NaN, 0, 0, 0, 8);
        assertTrue(AerialCockpitMotion.lit(Lamp.CAUTION, caution), "the master caution on any alert");
        for (Lamp l : Lamp.values()) assertFalse(AerialCockpitMotion.lit(l, AerialCockpitState.IDLE), "nothing heard: dark");
        assertTrue(AerialCockpitMotion.lightsOn(keeping), "the instrument lights while the Aerial flies the ship");
        assertFalse(AerialCockpitMotion.lightsOn(moored));
    }

    @Test
    void aTwoColourLensShowsOnlyItsBrighterColourSoItsQuadsNeverFight() {
        Pose p = new Pose();
        p.tick(state(true, AerialPilot.Mode.HELM, LeaveMode.GO_TO, 0, 0, 0, 0, 0, 0));
        Frame armed = p.frame(1.0f);
        assertEquals(1.0, armed.shown(Lamp.GOTO_ARMED), E);
        assertEquals(0.0, armed.shown(Lamp.GOTO_ENGAGED), E);

        p.tick(state(false, AerialPilot.Mode.GO_TO, LeaveMode.GO_TO, 0, 0, 0, 0, 0, 0));
        for (int t = 0; t < 6; t++) {
            Frame f = p.frame(1.0f);
            boolean white = f.shown(Lamp.GOTO_ARMED) > 0.0;
            boolean green = f.shown(Lamp.GOTO_ENGAGED) > 0.0;
            assertFalse(white && green, "tick " + t + ": never both at once");
            p.tick(state(false, AerialPilot.Mode.GO_TO, LeaveMode.GO_TO, 0, 0, 0, 0, 0, 0));
        }
        assertEquals(1.0, p.frame(1.0f).shown(Lamp.GOTO_ENGAGED), E);
        assertEquals(p.frame(1.0f).lamp(Lamp.HELM), p.frame(1.0f).shown(Lamp.HELM), E, "a lamp with its own lens is as lit");
    }

    @Test
    void aLampLightsInTwoTicksAndDiesInFourAndAHaloIsTheBrightnessSquared() {
        assertEquals(2, (int) Math.round(1.0 / AerialCockpitMotion.LAMP_UP));
        assertEquals(4, (int) Math.round(1.0 / AerialCockpitMotion.LAMP_DOWN));
        assertEquals(Math.round(255 * AerialCockpitMotion.HALO_ALPHA), AerialCockpitMotion.haloAlpha(1.0));
        assertEquals(Math.round(255 * AerialCockpitMotion.HALO_ALPHA * 0.25), AerialCockpitMotion.haloAlpha(0.5));
        assertEquals(0, AerialCockpitMotion.haloAlpha(-1.0));
    }

    @Test
    void theFirstStateAClientHearsIsWhereItStartsWithNoSwingAndNoLampTest() {
        Pose p = new Pose();
        AerialCockpitState s = state(true, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0.5, 0.4, 0.3, 6.0, 1.5, 4.0);
        p.tick(s);
        Frame f = p.frame(0.0f);
        assertEquals(AerialCockpitMotion.asiDegrees(s.air()), f.asi, E, "a player walking up to a ship in flight");
        assertEquals(AerialCockpitMotion.leverDegrees(s.order()), f.lever, E);
        assertEquals(AerialCockpitMotion.yokeRollDegrees(s.turn()), f.yokeRoll, E);
        assertEquals(AerialCockpitMotion.yokeSlideUnits(s.climb()), f.yokeSlide, E);
        assertEquals(AerialCockpitMotion.vsiDegrees(s.climbRate()), f.vsi, E);
        assertEquals(AerialCockpitMotion.turnDegrees(s.yawRate(), s.feelTurn()), f.turn, E);
        assertFalse(p.testing(), "sees no lamp test: the console did not just come on");
        assertEquals(1.0, f.lamp(Lamp.HELM), E);
        assertEquals(0.0, f.lamp(Lamp.CAUTION), E);
    }

    @Test
    void takingTheHelmRunsTheLampTestAlongEveryLampOnce() {
        Pose p = new Pose();
        p.tick(state(false, AerialPilot.Mode.ANCHOR, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 0));
        AerialCockpitState seated = state(true, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 0);
        boolean[] seen = new boolean[Lamp.COUNT];
        p.tick(seated);
        assertTrue(p.testing(), "a pilot sat down");
        for (int t = 0; t < AerialCockpitMotion.lampTestTicks() + 8; t++) {
            Frame f = p.frame(1.0f);
            for (Lamp l : Lamp.values()) if (f.lamp(l) > 0.99) seen[l.ordinal()] = true;
            p.tick(seated);
        }
        for (Lamp l : Lamp.values()) assertTrue(seen[l.ordinal()], l + " was tested");
        assertFalse(p.testing());
        Frame after = p.frame(1.0f);
        assertEquals(0.0, after.lamp(Lamp.CAUTION), E, "and after the test only what is true stays lit");
        assertEquals(0.0, after.lamp(Lamp.MOORED), E);
        assertEquals(1.0, after.lamp(Lamp.HELM), E);
        assertEquals(1.0, after.lamp(Lamp.ANCHOR_ARMED), E);
    }

    @Test
    void aNeedleRunsPastAStepByAHairAndSettlesAndNeverPastItsPin() {
        Pose p = new Pose();
        p.tick(state(false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 0));
        AerialCockpitState fast = state(false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 8.0, 0, 0);
        double target = AerialCockpitMotion.asiDegrees(8.0);
        double peak = -1.0e9;
        for (int t = 0; t < 60; t++) {
            p.tick(fast);
            peak = Math.max(peak, p.frame(1.0f).asi);
        }
        double over = (peak - target) / (target - AerialCockpitMotion.asiDegrees(0.0));
        assertTrue(over > 0.02 && over < 0.2, "a mass on a hairspring in oil: past by a little, " + over);
        assertEquals(target, p.frame(1.0f).asi, 0.01, "then settled");
        AerialCockpitState flat = state(false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 30.0, 0, 0);
        for (int t = 0; t < 60; t++) {
            p.tick(flat);
            assertTrue(p.frame(1.0f).asi <= AerialCockpitMotion.ASI_ZERO_DEG + AerialCockpitMotion.ASI_SPAN_DEG + E,
                    "the needle rests on its pin, it does not bounce through it");
        }
    }

    @Test
    void theHandControlsNeverOvershootAnOrder() {
        Pose p = new Pose();
        p.tick(state(true, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 0));
        AerialCockpitState full = state(true, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 1.0, 1.0, 1.0, 0, 0, 0);
        double lastLever = 0.0;
        for (int t = 0; t < 40; t++) {
            p.tick(full);
            Frame f = p.frame(1.0f);
            assertTrue(f.lever <= 35.0 + E && f.yokeRoll <= 60.0 + E && f.yokeSlide <= 1.0 + E, "tick " + t);
            assertTrue(f.lever >= lastLever - E, "and never comes back on its way there");
            lastLever = f.lever;
        }
        assertEquals(35.0, lastLever, 0.01);
    }

    @Test
    void aHeadingThroughNorthTurnsTheShortWay() {
        Pose p = new Pose();
        p.tick(AerialCockpitState.of(false, true, false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 64,
                Double.NaN, 0, 0, 350.0, Double.NaN, 0, 0, 0, 8));
        for (int t = 0; t < 40; t++) {
            p.tick(AerialCockpitState.of(false, true, false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 64,
                    Double.NaN, 0, 0, 10.0, Double.NaN, 0, 0, 0, 8));
            double h = p.frame(1.0f).heading;
            assertTrue(h >= 350.0 - E && h <= 370.0 + E, "twenty degrees right through north, not 340 left: " + h);
        }
        assertEquals(370.0, p.frame(1.0f).heading, 0.01);
    }

    @Test
    void anUnsyncedConsoleReadsItsOwnFacingAndAFrameInterpolatesTheLastTwoTicks() {
        Pose p = new Pose();
        p.restingHeading(270.0);
        assertEquals(270.0, p.frame(0.5f).heading, E, "a console no server has spoken to: a Ponder scene, a fresh chunk");
        assertEquals(AerialCockpitMotion.asiDegrees(0.0), p.frame(0.5f).asi, E, "its air speed needle on its stop");
        p.tick(state(false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0, 0, 0, 0, 0));
        p.tick(state(false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 1.0, 0, 0, 0, 0, 0));
        double a = p.frame(0.0f).lever;
        double b = p.frame(1.0f).lever;
        assertTrue(b > a, "the lever moved this tick");
        assertEquals((a + b) / 2.0, p.frame(0.5f).lever, E, "half way between them half way through the tick");
    }
}
