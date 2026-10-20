package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunStateTest {
    private static GunState.Inputs ok(int mode) {
        return new GunState.Inputs(true, true, mode, true, false, false, true, true, true, true, false, false, false,
                false, true, 0, true, false, false, false, false, false);
    }

    private static GunState.Inputs with(GunState.Inputs b, String field, Object v) {
        return new GunState.Inputs(
                field.equals("mountPresent") ? (Boolean) v : b.mountPresent(),
                field.equals("assembled") ? (Boolean) v : b.assembled(),
                field.equals("mode") ? (Integer) v : b.mode(),
                field.equals("powered") ? (Boolean) v : b.powered(),
                field.equals("fixed") ? (Boolean) v : b.fixed(),
                field.equals("handAimed") ? (Boolean) v : b.handAimed(),
                field.equals("yawDrive") ? (Boolean) v : b.yawDrive(),
                field.equals("pitchDrive") ? (Boolean) v : b.pitchDrive(),
                field.equals("yawTurning") ? (Boolean) v : b.yawTurning(),
                field.equals("pitchTurning") ? (Boolean) v : b.pitchTurning(),
                field.equals("heldElsewhere") ? (Boolean) v : b.heldElsewhere(),
                field.equals("overstressed") ? (Boolean) v : b.overstressed(),
                field.equals("aimFault") ? (Boolean) v : b.aimFault(),
                field.equals("unsafe") ? (Boolean) v : b.unsafe(),
                field.equals("hasTarget") ? (Boolean) v : b.hasTarget(),
                field.equals("solution") ? (Integer) v : b.solution(),
                field.equals("onTarget") ? (Boolean) v : b.onTarget(),
                field.equals("blocked") ? (Boolean) v : b.blocked(),
                field.equals("empty") ? (Boolean) v : b.empty(),
                field.equals("loading") ? (Boolean) v : b.loading(),
                field.equals("weaponsFree") ? (Boolean) v : b.weaponsFree(),
                field.equals("firing") ? (Boolean) v : b.firing());
    }

    @Test
    void aHandAimedGunSaysSoBeforeAnyDriveFault() {
        GunState.Inputs b = with(with(ok(GunState.MODE_TRACK), "handAimed", true), "pitchDrive", false);
        assertEquals(GunState.HAND_AIMED, GunState.of(b));
        assertEquals(GunState.HOLD, GunState.of(with(b, "mode", GunState.MODE_HOLD)), "Hold still means hands off");
        assertEquals(GunState.NOT_ASSEMBLED, GunState.of(with(b, "assembled", false)));
        assertEquals(GunState.READY, GunState.of(with(with(b, "handAimed", false), "pitchDrive", true)));
    }

    @Test
    void eachSingleFaultGivesItsState() {
        GunState.Inputs b = ok(GunState.MODE_TRACK);
        assertEquals(GunState.READY, GunState.of(b));
        assertEquals(GunState.DOWN, GunState.of(with(b, "mountPresent", false)));
        assertEquals(GunState.NOT_ASSEMBLED, GunState.of(with(b, "assembled", false)));
        assertEquals(GunState.HOLD, GunState.of(with(b, "mode", GunState.MODE_HOLD)));
        assertEquals(GunState.NO_POWER, GunState.of(with(b, "powered", false)));
        assertEquals(GunState.NO_YAW_DRIVE, GunState.of(with(b, "yawDrive", false)));
        assertEquals(GunState.NO_PITCH_DRIVE, GunState.of(with(b, "pitchDrive", false)));
        assertEquals(GunState.NO_ROTATION, GunState.of(with(b, "pitchTurning", false)));
        assertEquals(GunState.HELD_ELSEWHERE, GunState.of(with(b, "heldElsewhere", true)));
        assertEquals(GunState.OVERSTRESSED, GunState.of(with(b, "overstressed", true)));
        assertEquals(GunState.AIM_FAULT, GunState.of(with(b, "aimFault", true)));
        assertEquals(GunState.UNSAFE, GunState.of(with(b, "unsafe", true)));
        assertEquals(GunState.IDLE, GunState.of(with(b, "hasTarget", false)));
        assertEquals(GunState.OUT_OF_RANGE, GunState.of(with(b, "solution", 1)));
        assertEquals(GunState.OUT_OF_ARC, GunState.of(with(b, "solution", 2)));
        assertEquals(GunState.SLEWING, GunState.of(with(b, "onTarget", false)));
        assertEquals(GunState.BLOCKED, GunState.of(with(b, "blocked", true)));
        assertEquals(GunState.EMPTY, GunState.of(with(b, "empty", true)));
        assertEquals(GunState.LOADING, GunState.of(with(b, "loading", true)));
        assertEquals(GunState.FIRING, GunState.of(with(b, "firing", true)));
    }

    @Test
    void driveFaultsShowWithNoTarget() {
        GunState.Inputs idle = with(ok(GunState.MODE_TRACK), "hasTarget", false);
        assertEquals(GunState.NO_PITCH_DRIVE, GunState.of(with(idle, "pitchDrive", false)));
        assertEquals(GunState.AXIS_PITCH, GunState.faultAxis(with(idle, "pitchDrive", false)));
    }

    @Test
    void theFirstStateInTheTableWinsWhenSeveralApply() {
        GunState.Inputs many = with(with(with(ok(GunState.MODE_TRACK), "empty", true), "onTarget", false), "pitchDrive", false);
        assertEquals(GunState.NO_PITCH_DRIVE, GunState.of(many));
        assertEquals(GunState.SLEWING, GunState.of(with(with(ok(GunState.MODE_TRACK), "empty", true), "onTarget", false)));
    }

    @Test
    void anUnsafeLoadShowsWithNoTargetAndOutranksEverythingThatWaitsOnATarget() {
        GunState.Inputs unsafe = with(ok(GunState.MODE_FIRE), "unsafe", true);
        assertEquals(GunState.UNSAFE, GunState.of(with(unsafe, "hasTarget", false)));
        assertEquals(GunState.UNSAFE, GunState.of(with(with(with(unsafe, "onTarget", false), "blocked", true), "empty", true)));
        assertEquals(GunState.UNSAFE, GunState.of(with(with(unsafe, "fixed", true), "onTarget", false)));

        assertEquals(GunState.HOLD, GunState.of(with(unsafe, "mode", GunState.MODE_HOLD)));
        assertEquals(GunState.NO_POWER, GunState.of(with(unsafe, "powered", false)));
        assertEquals(GunState.NO_PITCH_DRIVE, GunState.of(with(unsafe, "pitchDrive", false)));
        assertEquals(HeaderStatus.Lamp.RED, GunState.UNSAFE.lamp());
        assertFalse(GunState.UNSAFE.salvoReady(), "a salvo never fires an unsafe load");
        assertFalse(GunState.UNSAFE.ready());

        assertEquals(GunState.values().length - 1, GunState.UNSAFE.ordinal());
        assertEquals(21, GunState.FIRING.ordinal());
    }

    @Test
    void aFixedMountHasNoDriveStatesAndIsOffBoreInsteadOfSlewing() {
        GunState.Inputs fixed = with(with(with(ok(GunState.MODE_TRACK), "fixed", true), "yawDrive", false), "pitchDrive", false);
        assertEquals(GunState.READY, GunState.of(fixed));
        assertEquals(GunState.OFF_BORE, GunState.of(with(fixed, "onTarget", false)));
    }

    @Test
    void fireModeIsSafeUntilWeaponsAreFree() {
        GunState.Inputs fire = ok(GunState.MODE_FIRE);
        assertEquals(GunState.SAFE, GunState.of(fire));
        assertEquals(GunState.READY, GunState.of(with(fire, "weaponsFree", true)));
        assertTrue(GunState.SAFE.salvoReady(), "a salvo ignores Safe");
        assertTrue(GunState.READY.salvoReady());
        assertFalse(GunState.SLEWING.salvoReady());
        assertFalse(GunState.SAFE.ready(), "the comparator's Guns ready counts READY and FIRING only");
    }

    @Test
    void lampsFollowWhoHasToAct() {
        assertEquals(HeaderStatus.Lamp.RED, GunState.NO_YAW_DRIVE.lamp());
        assertEquals(HeaderStatus.Lamp.AMBER, GunState.SLEWING.lamp());
        assertEquals(HeaderStatus.Lamp.GREEN, GunState.READY.lamp());
        assertEquals(HeaderStatus.Lamp.GREY, GunState.IDLE.lamp());
        assertEquals("held_elsewhere", GunState.HELD_ELSEWHERE.key());
    }
}
