package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunArcTest {
    private static final GunArc BOW = new GunArc(-60, 60, GunArc.LOWEST, GunArc.HIGHEST);

    @Test
    void theDefaultHasNoStopsAndNoLimitsSoAnOldSaveAimsAsItDid() {
        assertTrue(GunArc.FREE.free());
        assertTrue(GunArc.FREE.allRound());
        assertTrue(GunArc.FREE.fullElevation());
        assertEquals(360, GunArc.FREE.width());
        for (int b = -180; b <= 180; b += 15) assertTrue(GunArc.FREE.trains(b, 0.0), "bearing " + b);
        assertEquals(-137.0, GunArc.FREE.steer(100.0, -137.0), 1e-9, "all round, the drive's short way is fine");
    }

    @Test
    void bearingsNormaliseToMinus179Through180() {
        assertEquals(180, GunArc.norm(180));
        assertEquals(180, GunArc.norm(-180));
        assertEquals(-170, GunArc.norm(190));
        assertEquals(170, GunArc.norm(-190));
        assertEquals(0, GunArc.norm(720));
        assertEquals(180.0, GunArc.norm(-180.0), 1e-9);
        assertEquals(-179.5, GunArc.norm(180.5), 1e-9);
    }

    @Test
    void aBowArcCoversTheBowAndNotTheStern() {
        assertEquals(120, BOW.width());
        assertTrue(BOW.trains(0, 0.0));
        assertTrue(BOW.trains(-60, 0.0));
        assertTrue(BOW.trains(60, 0.0));
        assertFalse(BOW.trains(61, 0.0));
        assertFalse(BOW.trains(-61, 0.0));
        assertFalse(BOW.trains(180, 0.0));
        assertTrue(BOW.trains(60.8, GunArc.SLACK), "a barrel settled just past the stop still counts");
        assertTrue(BOW.trains(-60.8, GunArc.SLACK));
    }

    @Test
    void anArcAcrossTheSternWrapsThroughOneEighty() {
        GunArc aft = new GunArc(120, -120, GunArc.LOWEST, GunArc.HIGHEST);
        assertEquals(120, aft.width());
        assertTrue(aft.trains(180, 0.0));
        assertTrue(aft.trains(-150, 0.0));
        assertTrue(aft.trains(150, 0.0));
        assertFalse(aft.trains(0, 0.0));
        assertFalse(aft.trains(100, 0.0));
    }

    @Test
    void aTargetOutsideTheArcIsAimedAtTheNearerStop() {
        assertEquals(60.0, BOW.aimBearing(100, 0), 1e-9);
        assertEquals(-60.0, BOW.aimBearing(-100, 0), 1e-9);
        assertEquals(25.0, BOW.aimBearing(25, 0), 1e-9, "inside the arc the target's own bearing");

        assertEquals(60.0, BOW.aimBearing(175, -50), 1e-9);
    }

    @Test
    void aTargetAsternDoesNotSwingTheGunAcrossItsArcEveryTimeItsBearingWobbles() {
        assertEquals(-60.0, BOW.aimBearing(178, -50), 1e-9);
        assertEquals(-60.0, BOW.aimBearing(-178, -50), 1e-9);
        assertEquals(60.0, BOW.aimBearing(178, 50), 1e-9);
        assertEquals(60.0, BOW.aimBearing(-178, 50), 1e-9);
    }

    @Test
    void steerGoesStraightToTheAimWhenTheShortWayRunsInsideTheArc() {
        GunArc a = new GunArc(-90, 90, GunArc.LOWEST, GunArc.HIGHEST);
        assertEquals(40.0, a.steer(10, 40), 1e-9);
        assertEquals(-80.0, a.steer(60, -80), 1e-9);
    }

    @Test
    void steerSendsTheDriveTheLongWayRoundThroughTheArc() {
        GunArc a = new GunArc(-150, 150, GunArc.LOWEST, GunArc.HIGHEST);
        double point = a.steer(140, -140);
        assertTrue(FireSolution.wrap(point, 140) < 0, "the drive must turn to port, through the bow: " + point);
        assertEquals(GunArc.LEG, Math.abs(FireSolution.wrap(point, 140)), 1e-9);
    }

    @Test
    void aBarrelDrivenTheShortWayToEachSteerPointNeverEntersTheDeadSector() {
        Random rnd = new Random(1964);
        for (int trial = 0; trial < 4000; trial++) {
            int from = GunArc.norm(rnd.nextInt(72) * 5);
            int width = GunArc.MIN_WIDTH + rnd.nextInt(69) * 5;
            GunArc arc = new GunArc(from, GunArc.norm(from + width), GunArc.LOWEST, GunArc.HIGHEST);
            double barrel = arc.from() + rnd.nextDouble() * arc.width();
            double target = rnd.nextDouble() * 360.0 - 180.0;
            double rate = 1.0 + rnd.nextDouble() * 9.0;
            double aim = arc.aimBearing(target, barrel);
            for (int step = 0; step < 400; step++) {
                double point = arc.steer(barrel, aim);
                double err = FireSolution.wrap(point, barrel);
                barrel = GunArc.norm(barrel + Math.signum(err) * Math.min(rate, Math.abs(err)));
                assertTrue(arc.trains(barrel, 1e-6), "arc " + arc + " target " + target + " entered the dead sector at " + barrel);
            }
            assertEquals(0.0, FireSolution.wrap(barrel, aim), 1e-6, "arc " + arc + " never reached " + aim);
        }
    }

    @Test
    void aBarrelOutsideItsArcIsSentToTheNearerStop() {
        assertEquals(60.0, BOW.steer(100, 0), 1e-9);
        assertEquals(-60.0, BOW.steer(-170, 0), 1e-9, "-170 is 110 short of the port stop and 130 past the starboard one");
        assertEquals(10.0, BOW.steer(-60.5, 10), 1e-9, "within the slack a barrel counts as at its stop");
    }

    @Test
    void elevationIsClampedToTheTighterOfTheArcAndTheMount() {
        GunArc a = new GunArc(180, 180, -10, 30);
        assertTrue(a.elevates(30, 0.0));
        assertFalse(a.elevates(31, 0.0));
        assertEquals(30.0, a.aimElevation(45, -30, 60), 1e-9);
        assertEquals(-10.0, a.aimElevation(-20, -30, 60), 1e-9);
        assertEquals(0.0, a.aimElevation(-5, 0, 60), 1e-9, "the mount cannot depress: its limit wins");
        GunArc high = new GunArc(180, 180, 60, 90);
        assertEquals(45.0, high.aimElevation(80, -30, 45), 1e-9, "an arc the mount cannot reach leaves the mount's limits");
    }

    @Test
    void theGunsPageEditsKeepTheArcSane() {
        GunArc a = GunArc.FREE.with(GunArc.FIELD_AROUND, 32);
        assertEquals(-30, a.from(), "centred on the nearest 5 degrees");
        assertEquals(90, a.to());
        assertSame(a, a.with(GunArc.FIELD_FROM, 85), "a stop 5 degrees from the other is refused");
        assertSame(a, a.with(GunArc.FIELD_TO, -30), "closing the arc into all round is refused");
        assertEquals(80, a.with(GunArc.FIELD_TO, 80).to());
        assertTrue(a.with(GunArc.FIELD_ALL_ROUND, 1).allRound());
        assertSame(a, a.with(GunArc.FIELD_ALL_ROUND, 0), "stops come from FIELD_AROUND, not from un-ticking");
        GunArc limited = a.with(GunArc.FIELD_FULL, 0);
        assertEquals(GunArc.LIMIT_LOW, limited.low());
        assertEquals(GunArc.LIMIT_HIGH, limited.high());
        assertTrue(limited.with(GunArc.FIELD_FULL, 1).fullElevation());
        assertEquals(25, limited.with(GunArc.FIELD_LOW, 40).low(), "the low limit stays MIN_SPAN under the high one");
        assertEquals(-5, limited.with(GunArc.FIELD_HIGH, -40).high());
        assertEquals(350, GunArc.FREE.with(GunArc.FIELD_FROM, 0).width(), "a stop set on a gun with none leaves it nearly all round");
    }

    @Test
    void anArcNarrowerThanTheMinimumIsWidenedWhenItIsRead() {
        GunArc narrow = new GunArc(10, 13, -90, 90);
        assertEquals(GunArc.MIN_WIDTH, narrow.width());
        GunArc squashed = new GunArc(180, 180, 40, 41);
        assertEquals(GunArc.MIN_SPAN, squashed.high() - squashed.low());
    }

    @Test
    void anEditSurvivesTheActionPacketsNumber() {
        for (int field = 0; field <= GunArc.FIELD_FULL; field++) {
            for (int value = -512; value <= 511; value += 7) {
                int packed = GunArc.pack(field, value);
                assertEquals(field, GunArc.packedField(packed));
                assertEquals(value, GunArc.packedValue(packed));
            }
        }
        assertEquals(511, GunArc.packedValue(GunArc.pack(GunArc.FIELD_TO, 9999)), "clamped, never bleeding into the field");
    }
}
