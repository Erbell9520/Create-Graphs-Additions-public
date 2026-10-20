package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialPilotTest {
    private static final AerialPilot.Keys SEATED = new AerialPilot.Keys(true, 0.0, 0.0, 0.0, 0.0, false);

    private static AerialPilot.Keys keys(double throttle, double steer, double lift) {
        return new AerialPilot.Keys(true, throttle, steer, lift, 0.0, false);
    }

    private final AirPlant plant = new AirPlant();
    private final AerialPilot pilot = new AerialPilot();
    private final BurnerDither dither = new BurnerDither();

    private AerialPilot.Command run(AerialPilot.Keys k, double seconds) {
        return run(k, seconds, null);
    }

    private AerialPilot.Command run(AerialPilot.Keys k, double seconds, double[] destination) {
        AerialPilot.Command c = null;
        int ticks = (int) Math.round(seconds / AirPlant.DT);
        for (int i = 0; i < ticks; i++) c = plant.fly(pilot, dither, k, destination);
        return c;
    }

    private void tap(double throttle) {
        run(keys(throttle, 0.0, 0.0), 0.1);
        run(SEATED, 0.05);
    }

    private void airborne() {
        plant.trim();
        plant.signals = dither.signals(plant.gas, plant.dials);
        run(SEATED, 20.0);
    }

    @Test
    void offUntilSomeoneSits() {
        plant.trim();
        AerialPilot.Command c = run(AerialPilot.Keys.NONE, 1.0);
        assertEquals(AerialPilot.Mode.OFF, c.mode());
        assertTrue(c.handsOff());
    }

    @Test
    void sittingDownOnTheGroundLiftsItToAHover() {
        plant.rest();
        run(AerialPilot.Keys.NONE, 2.0);
        assertTrue(pilot.grounded());
        double start = plant.y;
        AerialPilot.Command c = run(SEATED, 40.0);
        assertEquals(AerialPilot.Mode.HELM, c.mode());
        assertEquals(start + AerialPilot.TAKEOFF_HOP, c.targetAltitude(), 1.0e-9);
        assertEquals(start + AerialPilot.TAKEOFF_HOP, plant.y, 0.5);
    }

    @Test
    void spaceMovesTheHeldAltitudeAndReleaseHoldsItExactly() {
        airborne();
        run(keys(0.0, 0.0, 1.0), 4.0);
        double held = pilot.targetAltitude();
        assertEquals(80.0 + 4.0 * Feel.STANDARD.climb, held, 0.6);
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 800; i++) {
            run(SEATED, 0.05);
            max = Math.max(max, plant.y);
        }
        assertEquals(held, pilot.targetAltitude(), 1.0e-9, "held exactly where the key came up (D4)");
        assertEquals(held, plant.y, 0.5);
        assertTrue(max - held <= 0.5, "overshoot " + (max - held));
    }

    @Test
    void leavingMidAirStopsAndAnchorsWhereItCameToRest() {
        airborne();
        tap(1.0);
        tap(1.0);
        run(SEATED, 30.0);
        assertTrue(plant.u > 3.0, "under way at " + plant.u);
        double altitude = pilot.targetAltitude();
        AerialPilot.Command c = run(AerialPilot.Keys.NONE, 0.05);
        assertEquals(AerialPilot.Mode.ANCHOR, c.mode());
        assertFalse(c.handsOff(), "a zeppelin left in the air is never handed back");
        run(AerialPilot.Keys.NONE, 40.0);
        double x = plant.x;
        double z = plant.z;
        double worst = 0.0;
        for (int i = 0; i < 1200; i++) {
            run(AerialPilot.Keys.NONE, 0.05);
            worst = Math.max(worst, Math.hypot(plant.x - x, plant.z - z));
        }
        assertTrue(worst < 2.0, "drifted " + worst + " blocks off its anchor");
        assertEquals(altitude, plant.y, 1.0);
        assertEquals(0.0, plant.u, 0.3);
    }

    @Test
    void theAnchorGoesDownInAWindToo() {
        airborne();

        plant.windZ = -1.5;
        run(AerialPilot.Keys.NONE, 20.0);
        assertEquals(AerialPilot.Mode.ANCHOR, pilot.mode());
        double x = plant.x;
        double z = plant.z;
        run(AerialPilot.Keys.NONE, 60.0);
        double drift = Math.hypot(plant.x - x, plant.z - z);
        assertTrue(drift < 5.0, "drifted " + drift + " blocks in a minute at anchor");
    }

    @Test
    void anAnchorWithNoEngineSaysTheShipDriftsAndStillHoldsItsHeight() {
        plant.maxForward = 0.0;
        plant.maxReverse = 0.0;
        plant.maxSide = 0.0;
        plant.maxYaw = 0.0;
        airborne();
        double altitude = pilot.targetAltitude();
        AerialPilot.Command c = run(AerialPilot.Keys.NONE, 10.0);
        assertEquals(AerialPilot.Mode.ANCHOR, c.mode());
        assertFalse(c.handsOff(), "the gas is held, not handed back");
        assertTrue(c.notes().contains(AerialPilot.NO_ANCHOR_ENGINE), "notes: " + c.notes());
        assertEquals(altitude, plant.y, 1.0);
    }

    @Test
    void anAnchorWithEnginesPromisesNothingItCannotDo() {
        airborne();
        AerialPilot.Command c = run(AerialPilot.Keys.NONE, 10.0);
        assertEquals(AerialPilot.Mode.ANCHOR, c.mode());
        assertFalse(c.notes().contains(AerialPilot.NO_ANCHOR_ENGINE), "notes: " + c.notes());
    }

    @Test
    void keepCourseKeepsTheSpeedAndHeading() {
        airborne();
        assertEquals(LeaveMode.KEEP_COURSE, pilot.cycleLeaveMode());
        tap(1.0);
        tap(1.0);
        run(SEATED, 40.0);
        double speed = plant.u;
        double heading = plant.heading;
        run(AerialPilot.Keys.NONE, 60.0);
        assertEquals(AerialPilot.Mode.KEEP_COURSE, pilot.mode());
        assertEquals(speed, plant.u, speed * 0.05);
        assertEquals(0.0, HeadingLaw.wrap(plant.heading - heading), 2.0);
    }

    @Test
    void goToFliesToTheTargetAndAnchorsThere() {
        airborne();
        pilot.cycleLeaveMode();
        assertEquals(LeaveMode.GO_TO, pilot.cycleLeaveMode());
        double[] target = {plant.x + 100.0, plant.z - 100.0};
        run(AerialPilot.Keys.NONE, 150.0, target);
        assertEquals(AerialPilot.Mode.ANCHOR, pilot.mode());
        assertTrue(Math.hypot(plant.x - target[0], plant.z - target[1]) < AerialPilot.ARRIVE + 3.0,
                "ended " + Math.hypot(plant.x - target[0], plant.z - target[1]) + " blocks from the target");
        assertEquals(80.0, plant.y, 1.0);
    }

    @Test
    void goToWithNoTargetHoldsItsSpotAndSaysWhy() {
        airborne();
        pilot.cycleLeaveMode();
        pilot.cycleLeaveMode();
        AerialPilot.Command c = run(AerialPilot.Keys.NONE, 5.0);
        assertEquals(AerialPilot.Mode.GO_TO, c.mode());
        assertTrue(c.notes().stream().anyMatch(n -> n.contains("Navigation Table")));
    }

    @Test
    void landingMoorsAndHandsEverythingBack() {
        airborne();
        int t = 0;
        while (!pilot.grounded() && t++ < 2400) run(keys(0.0, 0.0, -1.0), 0.05);
        assertTrue(pilot.grounded(), "never came down");
        AerialPilot.Command c = run(AerialPilot.Keys.NONE, 0.05);
        assertEquals(AerialPilot.Mode.MOORED, c.mode());
        assertTrue(c.handsOff());
    }

    @Test
    void theCeilingStopsTheClimbAndSaysWhy() {
        airborne();
        AerialPilot.Command c = null;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 2400; i++) {
            c = run(keys(0.0, 0.0, 1.0), 0.05);
            max = Math.max(max, plant.y);
        }
        assertTrue(Double.isFinite(c.ceiling()));
        assertTrue(max <= c.ceiling() + 0.5, "climbed to " + max + " past the ceiling " + c.ceiling());
        assertTrue(c.notes().stream().anyMatch(n -> n.startsWith("Ceiling")));
    }

    @Test
    void halfAheadPushesAHeavyShipFromTheFirstTicks() {
        plant.mass = 3000.0;
        plant.dragV = 0.6 * 3000.0;
        plant.capacity = 3000.0;

        plant.dials = new double[] {800.0, 800.0, 800.0, 800.0};
        plant.joined = new boolean[] {true, true, true, true};
        plant.signals = new int[4];
        plant.maxForward = 460.0;
        plant.maxReverse = 460.0;
        plant.dragF = 40.0;
        airborne();
        tap(1.0);
        tap(1.0);
        run(SEATED, 1.0);
        assertTrue(plant.forward > 0.4 * 460.0, "pushing " + plant.forward + " a second after ringing Half");

        run(SEATED, 60.0);
        assertTrue(plant.u > 2.5, "under way at " + plant.u + " after a minute at Half");
        run(SEATED, 240.0);
        double terminal = 0.5 * 460.0 / (plant.dragF * plant.pressure());
        assertEquals(terminal, plant.u, 0.1 * terminal, "at Half's own speed after five minutes");
        assertTrue(plant.y > plant.groundY + plant.keelBelow + 5.0, "still flying: " + plant.y);
    }

    @Test
    void stopBrakesToRestAndHolds() {
        airborne();
        for (int i = 0; i < 3; i++) tap(1.0);
        run(SEATED, 30.0);
        assertTrue(plant.u > 8.0, "at Full: " + plant.u);
        for (int i = 0; i < 3; i++) tap(-1.0);
        assertEquals(Telegraph.Notch.STOP, pilot.telegraph().notch());
        run(SEATED, 40.0);
        assertEquals(0.0, plant.u, 0.3, "Stop means stop");
    }

    @Test
    void asternBacksTheShipUp() {
        airborne();
        tap(-1.0);
        tap(-1.0);
        assertEquals(Telegraph.Notch.HALF_ASTERN, pilot.telegraph().notch());
        run(SEATED, 20.0);
        assertTrue(plant.u < -2.0, "backing at " + plant.u);
    }

    @Test
    void theEnginesAreSpooledNotKicked() {
        airborne();
        for (int i = 0; i < 3; i++) tap(1.0);
        AerialPilot.Command c = run(SEATED, 0.05);
        assertTrue(c.engine() < 0.6, "a Full order is answered over a second, not in a tick: " + c.engine());
        c = run(SEATED, 1.2);
        assertEquals(1.0, c.engine(), 1.0e-9);
    }

    @Test
    void enginesThatPushAcrossTheNoseAreCalledOut() {
        plant.maxForward = 0.0;
        plant.maxReverse = 0.0;
        plant.maxSide = 2000.0;
        airborne();
        AerialPilot.Command c = run(SEATED, 0.5);
        assertFalse(c.notes().stream().anyMatch(n -> n.contains("across the Aerial's nose")),
                "at Stop nothing is asked of the engines, so nothing is wrong yet: " + String.join(" | ", c.notes()));
        c = run(keys(1.0, 0.0, 0.0), 0.5);
        assertTrue(c.notes().stream().anyMatch(n -> n.contains("across the Aerial's nose")), String.join(" | ", c.notes()));
    }

    @Test
    void trimKeysMoveTheTrimAndTheBalloonsFollowIt() {
        airborne();
        run(new AerialPilot.Keys(true, 0.0, 0.0, 0.0, 0.0, false, 1.0), 1.0);
        assertEquals(AerialPilot.TRIM_RATE, pilot.setup().trim, 0.06);
    }

    @Test
    void thePlayersCapIsTheCeiling() {
        airborne();
        pilot.setup().altitudeCap = 90.0;
        AerialPilot.Command c = null;
        for (int i = 0; i < 1200; i++) c = run(keys(0.0, 0.0, 1.0), 0.05);
        assertEquals(90.0, c.ceiling(), 1.0e-9);
        assertTrue(plant.y < 90.6, "climbed to " + plant.y + " past the cap");
        assertTrue(c.notes().stream().anyMatch(n -> n.contains("your cap")));
    }

    private double worstDuring(double seconds) {
        double worst = 0.0;
        int ticks = (int) Math.round(seconds / AirPlant.DT);
        for (int i = 0; i < ticks; i++) {
            run(SEATED, 0.05);
            worst = Math.max(worst, Math.abs(plant.y - pilot.targetAltitude()));
        }
        return worst;
    }

    private static final double WING = 150.0;

    @Test
    void wingsThatLiftAreCountedAndTheirLossForeseenOnSlowing() {
        plant.wing = WING;
        airborne();
        for (int i = 0; i < 4; i++) tap(1.0);
        double worst = worstDuring(60.0);
        assertEquals(WING, pilot.vertical().estimate(VerticalObserver.WING), WING * 0.05, "wing lift learned");
        for (int i = 0; i < 3; i++) tap(-1.0);
        worst = Math.max(worst, worstDuring(60.0));
        assertTrue(worst < 1.0, "strayed " + worst + " blocks as the wings gained and lost their lift");
    }

    @Test
    void wingsThatPressDownAreCountedToo() {
        plant.wing = -WING;
        airborne();
        for (int i = 0; i < 4; i++) tap(1.0);
        double worst = worstDuring(60.0);
        assertEquals(-WING, pilot.vertical().estimate(VerticalObserver.WING), WING * 0.05, "wing push learned");
        for (int i = 0; i < 3; i++) tap(-1.0);
        worst = Math.max(worst, worstDuring(60.0));
        assertTrue(worst < 1.0, "strayed " + worst + " blocks while the wings pressed down");
    }

    @Test
    void engineThatLeansDownIsLearned() {
        plant.lean = -0.3;
        airborne();
        for (int i = 0; i < 4; i++) tap(1.0);
        double worst = worstDuring(60.0);
        for (int i = 0; i < 3; i++) tap(-1.0);
        worst = Math.max(worst, worstDuring(60.0));
        assertEquals(-0.3, pilot.vertical().estimate(VerticalObserver.THRUST), 0.05, "lean learned");
        assertTrue(worst < 1.0, "strayed " + worst + " blocks as the engines pushed it down");
    }

    private void owners() {
        plant.mass = 697.5;
        plant.capacity = 1924.0;
        plant.dials = new double[] {500.0, 500.0, 500.0, 500.0, 500.0, 500.0};
        plant.joined = new boolean[] {true, true, true, true, true, true};
        plant.signals = new int[6];
        plant.dragV = VerticalObserver.physicsDrag(1142, 697.5);
        pilot.dragPrior(plant.dragV);
    }

    @Test
    void aLaunchCaughtByItsPilotIsLetGoAndBroughtBack() {
        owners();
        airborne();
        double held = pilot.targetAltitude();
        plant.vy = 15.0;
        plant.gas = 1100.0;
        AerialPilot.Command c = run(SEATED, 0.05);
        assertTrue(c.gasTargets()[0] < 1.0, "the first thing asked of the burners is to let the gas go: " + c.gasTargets()[0]);
        double peak = plant.y;
        for (int i = 0; i < 1800; i++) {
            run(SEATED, 0.05);
            peak = Math.max(peak, plant.y);
        }
        assertTrue(peak < held + 80.0, "climbed to " + peak + ", " + (peak - held) + " past the height held");
        assertEquals(held, plant.y, 1.0, "back at the height held");
        assertEquals(held, pilot.targetAltitude(), 1.0e-9, "and still holding it");
    }

    @Test
    void aShipFallingFastIsCaughtAndBroughtBack() {
        owners();
        airborne();
        double held = pilot.targetAltitude();
        plant.vy = -10.0;
        plant.gas = 250.0;
        double low = plant.y;
        double high = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 1200; i++) {
            run(SEATED, 0.05);
            low = Math.min(low, plant.y);
            if (i > 200) high = Math.max(high, plant.y);
        }
        assertTrue(held - low < 25.0, "fell " + (held - low) + " blocks before it was caught");
        assertTrue(high - held < 1.0, "overshot the height held by " + (high - held));
        assertEquals(held, plant.y, 0.5);
    }

    @Test
    void aShipAsleepInTheAirTeachesTheObserverNothing() {
        owners();
        airborne();

        plant.vy = 0.0;
        pilot.tick(plant.sense(), SEATED, null);
        double[] before = pilot.vertical().state();
        for (int i = 0; i < 400; i++) {
            AerialPilot.Command c = pilot.tick(plant.sense(), SEATED, null);
            pilot.applied(0.0, 0.0, 0.0);
            plant.signals = dither.signals(c.gasTargets()[0], plant.dials);

            plant.gas = GasLaw.step(plant.gas, plant.target(), plant.capacity) * 0.998;
            plant.tick++;
        }
        assertTrue(pilot.frozen() >= 399, "the frozen readings are counted: " + pilot.frozen());
        double[] after = pilot.vertical().state();
        for (int k = 0; k < VerticalObserver.TERMS; k++) {
            assertEquals(before[k], after[k], 1.0e-9, "term " + k + " learned from a ship asleep");
        }
    }

    @Test
    void theLeaveToggleCycles() {
        assertEquals(LeaveMode.ANCHOR, pilot.leaveMode());
        assertEquals(LeaveMode.KEEP_COURSE, pilot.cycleLeaveMode());
        assertEquals(LeaveMode.GO_TO, pilot.cycleLeaveMode());
        assertEquals(LeaveMode.ANCHOR, pilot.cycleLeaveMode());
    }

    @Test
    void aSaveMidFlightWakesUpDoingWhatTheLeaveModeSays() {
        airborne();
        pilot.cycleLeaveMode();
        tap(1.0);
        run(SEATED, 10.0);
        AerialPilot.State st = pilot.state();
        assertEquals(AerialPilot.Mode.HELM.ordinal(), st.mode());
        AerialPilot loaded = new AerialPilot();
        loaded.restore(st);
        assertEquals(AerialPilot.Mode.KEEP_COURSE, loaded.mode());
        assertEquals(pilot.targetAltitude(), loaded.targetAltitude(), 1.0e-12);
        assertEquals(LeaveMode.KEEP_COURSE, loaded.leaveMode());
        assertEquals(pilot.cruise().drag(), loaded.cruise().drag(), 1.0e-12);
    }

    @Test
    void backingTellsTheFinsTheAirComesFromBehind() {
        airborne();
        AerialPilot.Command c = run(SEATED, 0.05);
        assertEquals(1.0, c.flow(), 1.0e-12);
        tap(-1.0);
        tap(-1.0);
        c = run(SEATED, 20.0);
        assertTrue(plant.u < -AerialPilot.ASTERN_FLOW, "backing at " + plant.u);
        assertEquals(-1.0, c.flow(), 1.0e-12);
        for (int i = 0; i < 4; i++) tap(1.0);
        int t = 0;
        while (plant.u < 1.0 && t++ < 2000) c = run(SEATED, 0.05);
        assertEquals(1.0, c.flow(), 1.0e-12, "under way ahead again at " + plant.u);
    }

    @Test
    void theNoTurnNoteComesOnlyWhenATurnIsAskedAndKnowsAboutRudders() {
        plant.maxYaw = 0.0;
        airborne();
        AerialPilot.Command c = run(SEATED, 0.5);
        assertFalse(c.notes().stream().anyMatch(n -> n.startsWith("Nothing can turn")), String.join(" | ", c.notes()));
        c = run(keys(0.0, 1.0, 0.0), 0.5);
        assertTrue(c.notes().stream().anyMatch(n -> n.startsWith("Nothing can turn")), String.join(" | ", c.notes()));
        pilot.rudders(1);
        c = run(keys(0.0, 1.0, 0.0), 0.5);
        assertTrue(c.notes().stream().anyMatch(n -> n.startsWith("Rudders steer only under way")), "a rudder at rest has no air to push");
        for (int i = 0; i < 3; i++) tap(1.0);
        run(SEATED, 20.0);
        c = run(keys(0.0, 1.0, 0.0), 0.5);
        assertFalse(c.notes().stream().anyMatch(n -> n.contains("turn")), "under way, rudders turn it: " + String.join(" | ", c.notes()));
    }

    @Test
    void aLeaveModePickedWhileFlyingAloneStartsAtOnce() {
        airborne();
        tap(1.0);
        tap(1.0);
        run(SEATED, 5.0);
        run(AerialPilot.Keys.NONE, 30.0);
        assertEquals(AerialPilot.Mode.ANCHOR, pilot.mode());
        double anchoredAt = plant.x;
        pilot.setLeaveMode(LeaveMode.ANCHOR);
        run(AerialPilot.Keys.NONE, 10.0);
        assertEquals(anchoredAt, plant.x, 1.0, "picking Anchor again keeps the spot");
        pilot.setLeaveMode(LeaveMode.KEEP_COURSE);
        assertEquals(AerialPilot.Mode.KEEP_COURSE, pilot.mode(), "at once, not at the next pilot's leaving");
        run(AerialPilot.Keys.NONE, 30.0);
        assertTrue(plant.u > 2.0, "on its way again at the Half it was rung to: " + plant.u);
        pilot.setLeaveMode(LeaveMode.ANCHOR);
        assertEquals(AerialPilot.Mode.ANCHOR, pilot.mode());
        run(SEATED, 1.0);
        pilot.setLeaveMode(LeaveMode.GO_TO);
        assertEquals(AerialPilot.Mode.HELM, pilot.mode(), "a seated pilot keeps the helm");
        assertEquals(LeaveMode.GO_TO, pilot.leaveMode());
        run(AerialPilot.Keys.NONE, 0.05);
        assertEquals(AerialPilot.Mode.GO_TO, pilot.mode(), "and the ship does it when they stand");
    }

    @Test
    void theTopSpeedDoesNotChangeWithHeight() {
        double f0 = 2000.0;
        double fade = 40.0;
        double low = pilot.topSpeed(senseAt(1.0, f0, fade));
        double high = pilot.topSpeed(senseAt(0.6, f0, fade));
        assertEquals(low, high, 1.0e-9 * low, "Full reaches the same speed higher up, where push and drag both thin");
        assertEquals(f0 / (CruiseLaw.DRAG_PRIOR * plant.mass + fade), low, 1.0e-9 * low);
    }

    private AerialPilot.Sense senseAt(double pressure, double f0, double fade) {
        AerialPilot.Sense s = plant.sense();
        pilot.cruise().reset(plant.mass);
        return new AerialPilot.Sense(s.y(), s.vy(), s.x(), s.z(), s.vx(), s.vz(), s.airForward(), s.airSide(), s.heading(),
                s.yawRate(), s.mass(), s.g(), s.yawInertia(), s.air(), pressure, s.cells(), s.passiveLift(), s.keelHeight(),
                s.worldTop(), new AerialPilot.Thrust(f0 * pressure, f0 * pressure, f0 * pressure, fade * pressure, 0.0, 0.0, 0.0,
                0.0));
    }

    @Test
    void aNotesHeadlineIsWhatIsWrong() {
        assertEquals("Lift short", AerialPilot.headline("Lift short: even full burners cannot hold this. Lighten the ship or add burners."));
        assertEquals("Held under your cap at y 90", AerialPilot.headline("Held under your cap at y 90 (the Lift page)."));
        assertEquals("G1 is held by another controller",
                AerialPilot.headline("G1 is held by another controller (a second console, or a Flight Control Computer), so this one cannot drive it."));
        assertEquals("No colon here", AerialPilot.headline("No colon here"));
        assertEquals("", AerialPilot.headline(null));
    }

    @Test
    void aShipWithNoTurningEnginesGetsAProportionalRudder() {
        plant.maxYaw = 0.0;
        airborne();
        pilot.holdHeading(HeadingLaw.normalise(plant.heading + 3.0));
        AerialPilot.Command c = run(SEATED, 0.05);
        assertTrue(c.turn() > 0.1 && c.turn() < 0.95, "a 3 degree error asks a part of the throw, not all of it: " + c.turn());
        c = run(keys(0.0, -1.0, 0.0), 0.05);
        assertEquals(-1.0, c.turn(), 1.0e-9, "the pilot's own key is the whole throw while the ship is not yet turning");
    }

    @Test
    void aHeldKeyEasesTheRudderOnceTheShipTurnsFastEnough() {
        airborne();
        HeadingLaw law = new HeadingLaw();
        law.moment(1.0, 0.0, 0.0, plant.inertia, Feel.STANDARD.turn, AirPlant.DT);
        assertEquals(1.0, law.rudder(0.0, Feel.STANDARD.turn), 1.0e-9, "starting a turn: the whole throw");
        law.moment(1.0, 0.0, Feel.STANDARD.turn, plant.inertia, Feel.STANDARD.turn, AirPlant.DT);
        assertEquals(1.0, law.rudder(Feel.STANDARD.turn, Feel.STANDARD.turn), 1.0e-9, "at the rate asked: still the whole throw");
        law.moment(1.0, 0.0, 1.5 * Feel.STANDARD.turn, plant.inertia, Feel.STANDARD.turn, AirPlant.DT);
        double past = law.rudder(1.5 * Feel.STANDARD.turn, Feel.STANDARD.turn);
        assertTrue(past < 0.75, "turning half as fast again as asked, the rudder eases: " + past);
        plant.yawRate = 1.5 * Feel.STANDARD.turn;
        AerialPilot.Command c = run(keys(0.0, 1.0, 0.0), 0.05);
        assertTrue(c.turn() < 1.0, "the pilot's key goes through the same law: " + c.turn());
    }

    private double takeOff(double seconds) {
        double start = plant.y;
        double peak = start;
        int ticks = (int) Math.round(seconds / AirPlant.DT);
        for (int i = 0; i < ticks; i++) {
            plant.fly(pilot, dither, SEATED, null);
            peak = Math.max(peak, plant.y);
        }
        return peak - start;
    }

    @Test
    void aBodyRestingOnTheDeckIsWeighedOffAndTheShipLifts() {
        plant.rest();
        plant.deckLoad = 0.5 * plant.mass;
        run(AerialPilot.Keys.NONE, 2.0);
        double start = plant.y;
        boolean weighed = false;
        for (int i = 0; i < 1800; i++) weighed |= plant.fly(pilot, dither, SEATED, null).weighing();
        assertTrue(weighed, "the console was told it weighed off");
        assertEquals(start + AerialPilot.TAKEOFF_HOP, plant.y, 0.5, "off the ground and holding its hop");
        double weight = plant.deckLoad * AirPlant.G;
        assertEquals(weight, pilot.vertical().load(), 0.1 * weight, "the load weighed is the body's weight");
        AerialPilot.Command c = run(SEATED, 0.05);
        assertFalse(c.weighing(), "flying, it weighs nothing more");
        assertTrue(c.notes().stream().noneMatch(n -> n.startsWith("Weighing off")), "and says nothing of it");
    }

    @Test
    void anEmptyShipWeighsNoLoadThatIsNotThere() {
        plant.rest();
        run(AerialPilot.Keys.NONE, 2.0);
        double rise = takeOff(40.0);
        assertTrue(rise < AerialPilot.TAKEOFF_HOP + 0.6, "climbed " + rise + " for a hop of " + AerialPilot.TAKEOFF_HOP);
        assertTrue(pilot.vertical().load() < 0.02 * plant.mass * AirPlant.G, "weighed " + pilot.vertical().load() + " pN of nothing");
    }

    @Test
    void aBodySetDownInFlightIsLearnedAndTheHeightHeld() {
        plant.groundY = 20.0;
        airborne();
        double held = pilot.targetAltitude();
        plant.deckLoad = 0.4 * plant.mass;
        double low = plant.y;
        for (int i = 0; i < 1800; i++) {
            run(SEATED, 0.05);
            low = Math.min(low, plant.y);
        }
        assertTrue(pilot.vertical().reopened() > 0, "the step reopened the learning");
        double weight = plant.deckLoad * AirPlant.G;
        assertEquals(weight, pilot.vertical().load(), 0.1 * weight, "the load learned in the air");

        assertTrue(held - low < 16.0, "sank " + (held - low) + " blocks before the gas caught up");
        assertEquals(held, plant.y, 0.5, "back at the height held");
    }

    @Test
    void aBodyLiftedOffWhileParkedIsNotFlownAgain() {
        plant.rest();
        plant.deckLoad = 0.5 * plant.mass;
        run(AerialPilot.Keys.NONE, 2.0);
        run(SEATED, 90.0);
        assertTrue(pilot.vertical().load() > 0.4 * plant.deckLoad * AirPlant.G, "flew with its load weighed");
        int t = 0;
        while (!pilot.grounded() && t++ < 2400) run(keys(0.0, 0.0, -1.0), 0.05);
        assertTrue(pilot.grounded(), "never came down");
        run(AerialPilot.Keys.NONE, 3.0);
        plant.deckLoad = 0.0;
        double rise = takeOff(40.0);
        assertTrue(rise < AerialPilot.TAKEOFF_HOP + 0.6, "the empty ship climbed " + rise + " for a hop of " + AerialPilot.TAKEOFF_HOP);
    }

    @Test
    void aShipHeldByAStaffStandsByAndStaysWhereItIsLetGo() {
        airborne();
        run(AerialPilot.Keys.NONE, 30.0);
        assertEquals(AerialPilot.Mode.ANCHOR, pilot.mode());
        double[] learned = pilot.vertical().state().clone();
        double drag = pilot.cruise().drag();
        double y0 = plant.y;
        pilot.staff(true);

        for (int i = 0; i < 200; i++) {
            AerialPilot.Command c = plant.fly(pilot, dither, AerialPilot.Keys.NONE, null);
            for (int a = 0; a < c.wrench().length; a++) assertEquals(0.0, c.wrench()[a], 0.0, "no push, no turn while held");
            assertTrue(c.notes().stream().anyMatch(n -> n.contains("physics staff")), "and says why");
            plant.u = 4.0;
            plant.vy = 1.2;
            plant.y = y0 + 1.2 * AirPlant.DT * (i + 1);
            plant.z -= 4.0 * AirPlant.DT;
        }
        assertEquals(learned.length, pilot.vertical().state().length);
        for (int k = 0; k < learned.length; k++) assertEquals(learned[k], pilot.vertical().state()[k], 1.0e-12, "nothing learned");
        assertEquals(drag, pilot.cruise().drag(), 1.0e-12);
        pilot.staff(false);
        plant.u = 0.0;
        plant.vy = 0.0;
        double x = plant.x;
        double z = plant.z;
        double y = plant.y;
        run(AerialPilot.Keys.NONE, 60.0);
        assertEquals(AerialPilot.Mode.ANCHOR, pilot.mode());
        assertTrue(Math.hypot(plant.x - x, plant.z - z) < 3.0, "anchored where it was let go, off by "
                + Math.hypot(plant.x - x, plant.z - z));
        assertEquals(y, plant.y, 1.0, "at the height it was let go at");
    }

    @Test
    void goToTurnsOntoATargetAbeamRatherThanCirclingIt() {
        plant.maxYaw = 6000.0;
        airborne();
        pilot.cycleLeaveMode();
        assertEquals(LeaveMode.GO_TO, pilot.cycleLeaveMode());
        double[] target = {plant.x + 60.0, plant.z};
        double closest = Double.POSITIVE_INFINITY;
        double opened = 0.0;
        int ticks = 0;
        for (; ticks < 20 * 240; ticks++) {
            run(AerialPilot.Keys.NONE, 0.05, target);
            if (pilot.mode() == AerialPilot.Mode.ANCHOR) break;
            double d = Math.hypot(plant.x - target[0], plant.z - target[1]);
            closest = Math.min(closest, d);
            opened = Math.max(opened, d - closest);
        }
        assertEquals(AerialPilot.Mode.ANCHOR, pilot.mode(), "arrived rather than circling");
        assertTrue(opened < 3.0, "the target drew away again by " + opened + " blocks: a loop");
        assertTrue(ticks < 20 * 90, "in " + ticks / 20 + " s");
        run(AerialPilot.Keys.NONE, 40.0, target);
        assertTrue(Math.hypot(plant.x - target[0], plant.z - target[1]) < AerialPilot.ARRIVE,
                "held " + Math.hypot(plant.x - target[0], plant.z - target[1]) + " blocks from the target");
    }

    @Test
    void stopFromFullNeverPushesAhead() {
        airborne();
        for (int i = 0; i < 3; i++) tap(1.0);
        run(SEATED, 30.0);
        assertTrue(plant.u > 8.0, "at Full: " + plant.u);
        for (int i = 0; i < 3; i++) tap(-1.0);
        double worst = Double.NEGATIVE_INFINITY;
        while (plant.u > 1.0) {
            run(SEATED, 0.05);
            worst = Math.max(worst, plant.forward);
        }
        assertTrue(worst <= 1.0e-9, "pushed " + worst + " ahead while slowing from Full");
    }

    @Test
    void aBodyTooHeavyToLiftIsSaidSo() {
        plant.rest();
        plant.deckLoad = 3.0 * plant.mass;
        run(AerialPilot.Keys.NONE, 2.0);
        double start = plant.y;
        AerialPilot.Command c = run(SEATED, 90.0);
        assertEquals(start, plant.y, 0.05, "it never left the ground");
        assertTrue(c.notes().stream().anyMatch(n -> n.startsWith("Held down")), "and says why: " + c.notes());
        assertTrue(c.notes().stream().noneMatch(n -> n.startsWith("Ceiling")), "not a ceiling: " + c.notes());
    }
}
