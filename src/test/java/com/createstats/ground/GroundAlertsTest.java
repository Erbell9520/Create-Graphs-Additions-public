package com.createstats.ground;

import java.util.ArrayList;
import java.util.List;

import com.createstats.driverhelmet.DriveLog;
import com.createstats.driverhelmet.LedBar;
import com.createstats.ground.GroundAlerts.Alert;
import com.createstats.ground.GroundAlerts.Code;
import com.createstats.ground.GroundAlerts.Level;
import com.createstats.ground.GroundSnapshot.Governor;
import com.createstats.ground.GroundSnapshot.Supply;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.ground.GroundSnapshot.Wheel;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkTrim;
import com.createstats.vccmk4.core.TrailerMode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundAlertsTest {
    static List<Alert> eval(GroundSnapshot s, List<Code> held, double age) {
        return GroundAlerts.evaluate(DriveMath.read(s), held, age);
    }

    static boolean has(List<Alert> alerts, Code code) {
        for (final Alert a : alerts) {
            if (a.code() == code) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aHealthyCarRaisesNothing() {
        assertTrue(eval(GroundFixtures.car(), List.of(), 0.0).isEmpty());
        assertTrue(eval(GroundFixtures.realistic(), List.of(), 0.0).isEmpty());
    }

    @Test
    void theFeedGoesStaleAfterThreeSecondsAndClearsUnderTwo() {
        assertFalse(has(eval(GroundFixtures.car(), List.of(), 2.9), Code.NO_FEED));
        assertTrue(has(eval(GroundFixtures.car(), List.of(), 3.1), Code.NO_FEED));
        assertTrue(has(eval(GroundFixtures.car(), List.of(Code.NO_FEED), 2.5), Code.NO_FEED));
        assertFalse(has(eval(GroundFixtures.car(), List.of(Code.NO_FEED), 1.9), Code.NO_FEED));
    }

    @Test
    void aLostLinkIsCriticalAndFirst() {
        final List<Alert> a = eval(GroundFixtures.lostLink(), List.of(), 0.0);
        assertEquals(Code.LINK, a.get(0).code());
        assertEquals(Level.CRITICAL, a.get(0).level());
        assertEquals("LINK", a.get(0).name());
    }

    @Test
    void aSpringNearItsBreakingLengthSnapsCritical() {
        final List<Alert> a = eval(GroundFixtures.snapping(), List.of(), 0.0);
        assertEquals(Code.SNAP, a.get(0).code());
        assertEquals(Level.CRITICAL, a.get(0).level());
    }

    static GroundSnapshot springAt(float share) {
        final GroundSnapshot s = GroundFixtures.roadTrain();
        final List<Unit> units = new ArrayList<>();
        for (final Unit u : s.units()) {
            units.add(u.index() == 2 ? new Unit(u.index(), u.parent(), u.role(), LinkKind.SPRING, u.mode(), u.placed(),
                    u.foreign(), u.x(), u.z(), u.yaw(), u.articulation(), u.rate(), 5f, Float.NaN, share, 0, u.wheels(),
                    u.mass(), u.name()) : u);
        }
        return new GroundSnapshot(s.gameTime(), s.identity(), s.body(), s.log(), s.drive(), s.setup(), s.mass(), s.wheels(),
                s.governors(), s.jets(), units, s.ties(), s.lights(), s.machines(), s.problems(), s.terrain(), s.odometer(),
                s.trail(), s.history(), s.events(), s.envelope(), s.shapes());
    }

    @Test
    void springStrainHasHysteresisOnBothRules() {
        assertFalse(has(eval(springAt(0.68f), List.of(), 0), Code.STRAIN));
        assertTrue(has(eval(springAt((float) LinkTrim.SNAP_WARN + 0.01f), List.of(), 0), Code.STRAIN));
        assertTrue(has(eval(springAt(0.67f), List.of(Code.STRAIN), 0), Code.STRAIN), "held until 5 points under");
        assertFalse(has(eval(springAt(0.64f), List.of(Code.STRAIN), 0), Code.STRAIN));
        assertTrue(has(eval(springAt(0.91f), List.of(), 0), Code.SNAP));
        assertFalse(has(eval(springAt(0.88f), List.of(), 0), Code.SNAP));
        assertTrue(has(eval(springAt(0.88f), List.of(Code.SNAP), 0), Code.SNAP));
    }

    @Test
    void aRunawayIsCriticalAndClearsAtHalfItsSpeed() {
        final List<Alert> a = eval(GroundFixtures.runaway(), List.of(), 0.0);
        assertEquals(Code.RUNAWAY, a.get(0).code());
        assertEquals(Level.CRITICAL, a.get(0).level());
        assertTrue(eval(GroundFixtures.parked(), List.of(Code.RUNAWAY), 0.0).stream().noneMatch(x -> x.code() == Code.RUNAWAY));
        assertTrue(eval(GroundFixtures.towed(), List.of(), 0.0).stream().noneMatch(x -> x.code() == Code.RUNAWAY),
                "towed is moving on purpose");
    }

    @Test
    void anImpactStaysFourSeconds() {
        assertTrue(has(eval(GroundFixtures.impact(), List.of(), 0.0), Code.IMPACT));
        final GroundSnapshot old = GroundFixtures.impact();
        final GroundSnapshot late = new GroundSnapshot(old.gameTime(), old.identity(), old.body(),
                new GroundSnapshot.Log(0.4, 7.8, GroundAlerts.IMPACT_TICKS + 1, 7.8, 0.0, 0.0), old.drive(), old.setup(),
                old.mass(), old.wheels(), old.governors(), old.jets(), old.units(), old.ties(), old.lights(), old.machines(),
                old.problems(), old.terrain(), old.odometer(), old.trail(), old.history(), old.events(), old.envelope(),
                old.shapes());
        assertFalse(has(eval(late, List.of(), 0.0), Code.IMPACT));
        assertEquals(5.0, DriveLog.IMPACT_G, 0.0, "the helmet's own threshold");
    }

    @Test
    void airborneAfterTheHelmetsJumpLength() {
        assertTrue(has(eval(GroundFixtures.airborne(), List.of(), 0.0), Code.AIRBORNE));
    }

    @Test
    void wheelspinAndLockupAtTheHelmetsThreshold() {
        final List<Alert> braking = eval(GroundFixtures.braking(), List.of(), 0.0);
        assertTrue(has(braking, Code.LOCKUP), "an icy front tyre dragging under braking");
        assertEquals(0.30, LedBar.WARN_SLIP, 0.0);
        final GroundSnapshot car = GroundFixtures.car();
        final List<Wheel> wheels = new ArrayList<>();
        for (final Wheel w : car.wheels()) {
            wheels.add(new Wheel(w.unit(), w.axle(), w.governor(), w.x(), w.y(), w.z(), w.driven(), w.steered(), w.radius(),
                    w.capMass(), w.strength(), w.angle(), w.applied(), w.actual(), 0.32f, 0.3f, w.load(), false, 0));
        }
        final GroundSnapshot spin = new GroundSnapshot(car.gameTime(), car.identity(), car.body(), car.log(), car.drive(),
                car.setup(), car.mass(), wheels, car.governors(), car.jets(), car.units(), car.ties(), car.lights(),
                car.machines(), car.problems(), car.terrain(), car.odometer(), car.trail(), car.history(), car.events(),
                car.envelope(), car.shapes());
        assertTrue(has(eval(spin, List.of(), 0.0), Code.WHEELSPIN));
    }

    @Test
    void aStarvedGovernorIsTheSupplyAlert() {
        final GroundSnapshot car = GroundFixtures.car();
        final List<Governor> govs = List.of(new Governor("G1", 0, 0f, 0f, 0, 128, 1f, 4, 0, false, Supply.NO_ROTATION, true));
        final GroundSnapshot s = new GroundSnapshot(car.gameTime(), car.identity(), car.body(), car.log(), car.drive(),
                car.setup(), car.mass(), car.wheels(), govs, car.jets(), car.units(), car.ties(), car.lights(),
                car.machines(), car.problems(), car.terrain(), car.odometer(), car.trail(), car.history(), car.events(),
                car.envelope(), car.shapes());
        final List<Alert> a = eval(s, List.of(), 0.0);
        assertTrue(has(a, Code.SUPPLY));
        assertTrue(a.stream().filter(x -> x.code() == Code.SUPPLY).findFirst().get().text().contains("G1"));
    }

    @Test
    void theOrderIsSeverityThenTheActionBarsOrder() {
        assertTrue(Code.LINK.ordinal() < Code.SWAY.ordinal());
        assertTrue(Code.SWAY.ordinal() < Code.STRAIN.ordinal());
        assertTrue(Code.STRAIN.ordinal() < Code.BREAKAWAY.ordinal());
        assertTrue(Code.BREAKAWAY.ordinal() < Code.SUPPLY.ordinal());
        assertTrue(Code.IMPACT.ordinal() < Code.SUPPLY.ordinal());
        final List<Alert> a = eval(GroundFixtures.lostLink(), List.of(), 5.0);
        for (int i = 1; i < a.size(); i++) {
            assertTrue(a.get(i - 1).level().compareTo(a.get(i).level()) >= 0, "most severe first");
        }
    }

    @Test
    void theHeadlineSkipsAdvisories() {
        final List<Alert> a = eval(GroundFixtures.airborne(), List.of(), 0.0);
        assertEquals(Level.ADVISORY, GroundAlerts.worst(a));
        assertNull(GroundAlerts.headline(a));
        assertEquals(Code.LINK, GroundAlerts.headline(eval(GroundFixtures.lostLink(), List.of(), 0.0)).code());
    }

    @Test
    void anAlertsNameAndDetailSplitAtTheDoubleSpace() {
        final Alert a = new Alert(Code.SUPPLY, Level.CAUTION, "SUPPLY  G1 gets no rotation");
        assertEquals("SUPPLY", a.name());
        assertEquals("G1 gets no rotation", a.detail());
    }

    @Test
    void jetsOutOfFuelAreACautionLowIsAnAdvisory() {
        final List<Alert> a = eval(GroundFixtures.hovercraft(), List.of(), 0.0);
        assertTrue(a.stream().anyMatch(x -> x.code() == Code.FUEL && x.level() == Level.ADVISORY));
    }

    @Test
    void aRolloverNeedsMotionAndIsLabelledModelled() {
        final GroundSnapshot car = GroundFixtures.car();
        final DriveReadout r = DriveMath.read(car);
        assertTrue(r.stability > 1.0, "the fixture car is a wide, low one");
        r.rolloverShare = GroundAlerts.ROLLOVER_RAISE + 0.01;
        final List<Alert> a = GroundAlerts.evaluate(r, List.of(), 0.0);
        assertTrue(a.stream().anyMatch(x -> x.code() == Code.ROLLOVER && x.text().contains("modelled")));
        r.rolloverShare = GroundAlerts.ROLLOVER_CLEAR + 0.01;
        assertFalse(GroundAlerts.evaluate(r, List.of(), 0.0).stream().anyMatch(x -> x.code() == Code.ROLLOVER));
        assertTrue(GroundAlerts.evaluate(r, List.of(Code.ROLLOVER), 0.0).stream().anyMatch(x -> x.code() == Code.ROLLOVER));
    }

    @Test
    void unitRolesKeepTheirMeaning() {
        final Unit u = new Unit(1, 0, BodyRole.TRAILER, LinkKind.HITCH, TrailerMode.ASSIST, true, false, 0, 0, 0, 0, 0,
                Float.NaN, Float.NaN, Float.NaN, Unit.FLAG_SWAYING, 4, 1f, "B2");
        assertTrue(u.has(Unit.FLAG_SWAYING));
        assertFalse(u.has(Unit.FLAG_LOST));
    }
}
