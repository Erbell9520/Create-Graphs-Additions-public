package com.createstats.vccmk4.air.core;

import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialPartsTest {
    private static AerialParts.SwivelSpec swivel(long pos, double lon, double up, double right) {
        return new AerialParts.SwivelSpec(pos, lon, up, right, true, false);
    }

    @Test
    void aFinAtTheTailIsARudderOrAnElevatorByItsHinge() {
        AerialParts.Assigned a = AerialParts.assign(new AerialSetup(), List.of(
                swivel(1, -8.0, 1.0, 0.0),
                swivel(2, -8.0, 0.0, 1.0),
                swivel(3, -8.0, 0.0, -1.0)), List.of());
        assertEquals(AerialSetup.SurfaceRole.RUDDER, a.surfaces().get(1L).role());
        assertEquals(AerialParts.RUDDER_THROW, a.surfaces().get(1L).degrees());
        assertEquals(AerialSetup.SurfaceRole.ELEVATOR, a.surfaces().get(2L).role());
        assertEquals(AerialSetup.SurfaceRole.ELEVATOR, a.surfaces().get(3L).role(), "a hinge pointing left is across the ship too");
        assertEquals(AerialParts.ELEVATOR_THROW, a.surfaces().get(2L).degrees());
    }

    @Test
    void anythingElseIsAToolOnTheNextFreeAuxiliaryKey() {
        AerialParts.Assigned a = AerialParts.assign(new AerialSetup(), List.of(
                swivel(1, 1.0, 1.0, 0.0),
                new AerialParts.SwivelSpec(2, -9.0, 1.0, 0.0, false, false)),
                List.of(new AerialParts.MachineSpec(10, false), new AerialParts.MachineSpec(11, true)));
        assertEquals(AerialSetup.SurfaceRole.TOOL, a.surfaces().get(1L).role(), "near the centre: a crane, not a rudder");
        assertEquals(PartInput.AUX_A, a.surfaces().get(1L).input());
        assertEquals(AerialSetup.SurfaceRole.TOOL, a.surfaces().get(2L).role(), "on a body another bearing turns: an arm's second joint");
        assertEquals(PartInput.AUX_B, a.surfaces().get(2L).input());
        assertEquals(PartInput.AUX_C, a.tools().get(10L).input());
        assertEquals(PartInput.AUX_D, a.tools().get(11L).input());
        assertEquals(MachineAxis.Setting.LATCHED, a.tools().get(11L).setting(), "a runner starts latched, the ground Mk IV's rule");
    }

    @Test
    void aBattleBridgesGunsAreGivenNoKey() {
        AerialParts.Assigned a = AerialParts.assign(new AerialSetup(), List.of(),
                List.of(new AerialParts.MachineSpec(10, false, true), new AerialParts.MachineSpec(11, false),
                        new AerialParts.MachineSpec(12, false, true)));
        assertEquals(PartInput.OFF, a.tools().get(10L).input(), "the Bridge aims it");
        assertEquals(PartInput.AUX_A, a.tools().get(11L).input(), "the winch gets the first key, the guns took none");
        assertEquals(PartInput.OFF, a.tools().get(12L).input());
        AerialSetup keyed = new AerialSetup();
        keyed.tools.put(10L, new AerialSetup.Tool(PartInput.AUX_C, MachineAxis.Setting.SPRUNG));
        AerialParts.Assigned b = AerialParts.assign(keyed, List.of(), List.of(new AerialParts.MachineSpec(10, false, true)));
        assertEquals(PartInput.AUX_C, b.tools().get(10L).input(), "a key the player gave it stands");
    }

    @Test
    void keysThePlayerGaveAwayAreSkipped() {
        AerialSetup setup = new AerialSetup();
        setup.tools.put(10L, new AerialSetup.Tool(PartInput.AUX_A, MachineAxis.Setting.SPRUNG));
        setup.lights.put(99L, new AerialSetup.Light(AirLight.AUX_B_PLUS, false));
        AerialParts.Assigned a = AerialParts.assign(setup, List.of(swivel(1, 0.0, 1.0, 0.0)),
                List.of(new AerialParts.MachineSpec(10, false), new AerialParts.MachineSpec(11, false),
                        new AerialParts.MachineSpec(12, false), new AerialParts.MachineSpec(13, false)));
        assertEquals(PartInput.AUX_A, a.tools().get(10L).input(), "the player's own choice stands");
        assertEquals(PartInput.AUX_C, a.surfaces().get(1L).input(), "A is the winch's, B the light's");
        assertEquals(PartInput.AUX_D, a.tools().get(11L).input());
        assertEquals(PartInput.OFF, a.tools().get(12L).input(), "no key left: off, never doubled up");
        assertEquals(PartInput.OFF, a.tools().get(13L).input());
    }

    @Test
    void aBearingAMachineTurnsIsLeftToTheMachine() {
        AerialParts.Assigned a = AerialParts.assign(new AerialSetup(),
                List.of(new AerialParts.SwivelSpec(1, -8.0, 1.0, 0.0, true, true)), List.of());
        assertEquals(AerialSetup.SurfaceRole.OFF, a.surfaces().get(1L).role());
    }

    @Test
    void aRudderSwingsItsTrailingEdgeToTheTurn() {
        AerialSetup.Surface rudder = new AerialSetup.Surface(AerialSetup.SurfaceRole.RUDDER, 30, false, PartInput.OFF, false);
        AerialParts.SwivelSpec aft = swivel(1, -8.0, 1.0, 0.0);
        assertEquals(30.0, AerialParts.target(rudder, aft, 1.0, 0.0), 1.0e-9, "right turn, hinge up, aft: a positive angle");
        assertEquals(-15.0, AerialParts.target(rudder, aft, -0.5, 0.0), 1.0e-9);
        assertEquals(-30.0, AerialParts.target(rudder.withInvert(true), aft, 1.0, 0.0), 1.0e-9);
        assertEquals(-30.0, AerialParts.target(rudder, swivel(1, -8.0, -1.0, 0.0), 1.0, 0.0), 1.0e-9, "a bearing upside down");
        assertEquals(-30.0, AerialParts.target(rudder, swivel(1, 8.0, 1.0, 0.0), 1.0, 0.0), 1.0e-9, "ahead of the centre, a canard");
        assertEquals(30.0, AerialParts.target(rudder, aft, 4.0, 0.0), 1.0e-9, "never past its throw");
        assertEquals(0.0, AerialParts.target(rudder, aft, 0.0, 1.0), 1.0e-9, "a rudder ignores the trim");
    }

    @Test
    void anElevatorRaisesItsTrailingEdgeForNoseUp() {
        AerialSetup.Surface elevator = new AerialSetup.Surface(AerialSetup.SurfaceRole.ELEVATOR, 20, false, PartInput.OFF, false);
        AerialParts.SwivelSpec aft = swivel(2, -8.0, 0.0, 1.0);

        assertEquals(-20.0, AerialParts.target(elevator, aft, 0.0, 1.0), 1.0e-9);
        assertEquals(10.0, AerialParts.target(elevator, aft, 0.0, -0.5), 1.0e-9);
        assertEquals(0.0, AerialParts.target(elevator, aft, 1.0, 0.0), 1.0e-9, "an elevator ignores the turn");
    }

    @Test
    void backingThrowsEveryFinTheOtherWay() {
        AerialSetup.Surface rudder = new AerialSetup.Surface(AerialSetup.SurfaceRole.RUDDER, 30, false, PartInput.OFF, false);
        AerialSetup.Surface elevator = new AerialSetup.Surface(AerialSetup.SurfaceRole.ELEVATOR, 20, false, PartInput.OFF, false);
        AerialParts.SwivelSpec aftUp = swivel(1, -8.0, 1.0, 0.0);
        AerialParts.SwivelSpec aftAcross = swivel(2, -8.0, 0.0, 1.0);
        for (AerialSetup.Surface s : List.of(rudder, rudder.withInvert(true))) {
            double ahead = AerialParts.target(s, aftUp, 0.6, 0.0, 1.0);
            assertEquals(ahead, AerialParts.target(s, aftUp, 0.6, 0.0), 1.0e-12, "the four-argument form is the air from ahead");
            assertEquals(-ahead, AerialParts.target(s, aftUp, 0.6, 0.0, -1.0), 1.0e-12);
        }
        assertEquals(-AerialParts.target(elevator, aftAcross, 0.0, 0.5, 1.0), AerialParts.target(elevator, aftAcross, 0.0, 0.5, -1.0),
                1.0e-12);
        assertTrue(Math.abs(AerialParts.target(rudder, aftUp, 0.6, 0.0, -1.0)) > 0.0, "backing still steers, the other way");
    }

    @Test
    void finsSlewRatherThanSnap() {
        assertEquals(AerialParts.SLEW_DEG, AerialParts.slew(0.0, 30.0), 1.0e-9);
        assertEquals(-AerialParts.SLEW_DEG, AerialParts.slew(0.0, -30.0), 1.0e-9);
        assertEquals(1.0, AerialParts.slew(0.0, 1.0), 1.0e-9);
        assertEquals(5.0, AerialParts.slew(5.0, Double.NaN), 1.0e-9);
        double a = 0.0;
        int ticks = 0;
        while (a < 30.0 && ticks < 100) {
            a = AerialParts.slew(a, 30.0);
            ticks++;
        }
        assertEquals(10, ticks, "thirty degrees at three a tick");
        assertTrue(AerialParts.auxSetting(new AerialSetup.Surface(AerialSetup.SurfaceRole.TOOL, 45, true, PartInput.AUX_A, true)).latched());
    }
}
