package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleSetupTest {
    private static final AxleRole S = AxleRole.STEER;
    private static final AxleRole F = AxleRole.FIXED;

    @Test
    void presetsFillTheRoles() {
        assertArrayEquals(new AxleRole[]{S, F}, SteerPreset.FRONT.roles(2));
        assertArrayEquals(new AxleRole[]{S, F, F}, SteerPreset.FRONT.roles(3));
        assertArrayEquals(new AxleRole[]{S, S, F}, SteerPreset.TWIN_FRONT.roles(3));
        assertArrayEquals(new AxleRole[]{S, F}, SteerPreset.TWIN_FRONT.roles(2), "twin front needs three axles");
        assertArrayEquals(new AxleRole[]{S, F, S}, SteerPreset.FRONT_REAR.roles(3));
        assertArrayEquals(new AxleRole[]{S, S}, SteerPreset.FRONT_REAR.roles(2));
        assertArrayEquals(new AxleRole[]{S, S, S, S}, SteerPreset.ALL.roles(4));
        assertArrayEquals(new AxleRole[]{F, F, F}, SteerPreset.SKID.roles(3));
        assertArrayEquals(new AxleRole[]{S}, SteerPreset.ALL.roles(1));
        assertEquals(0, SteerPreset.FRONT.roles(0).length);
    }

    @Test
    void clickingAnAxleMakesItCustomUntilALayoutMatchesAgain() {
        VehicleSetup v = new VehicleSetup();
        v.applyPreset(SteerPreset.FRONT, 3);
        v.setAxleRole(1, S, 3);
        assertEquals(SteerPreset.TWIN_FRONT, v.preset, "front plus second is twin front");
        v.setAxleRole(2, S, 3);
        assertEquals(SteerPreset.ALL, v.preset);
        v.setAxleRole(1, AxleRole.FREE, 3);
        assertEquals(SteerPreset.CUSTOM, v.preset);
        assertArrayEquals(new AxleRole[]{S, AxleRole.FREE, S}, v.rolesFor(3));
        v.setAxleRole(1, F, 3);
        assertEquals(SteerPreset.FRONT_REAR, v.preset);
    }

    @Test
    void customRolesSurviveAnAxleCountChange() {
        VehicleSetup v = new VehicleSetup();
        v.applyPreset(SteerPreset.CUSTOM, 3);
        v.customRoles = new AxleRole[]{S, S, AxleRole.FREE};
        assertArrayEquals(new AxleRole[]{S, S, AxleRole.FREE, F}, v.rolesFor(4), "a new rear axle is fixed");
        assertArrayEquals(new AxleRole[]{S, S}, v.rolesFor(2));
        assertArrayEquals(new AxleRole[]{S}, v.rolesFor(1));
    }

    @Test
    void crabAndSkidLeaveOnAnyRoleClick() {
        VehicleSetup v = new VehicleSetup();
        v.applyPreset(SteerPreset.CRAB, 2);
        v.setAxleRole(1, F, 2);
        assertEquals(SteerPreset.FRONT, v.preset, "steer plus fixed is plainly front steer");
        v.applyPreset(SteerPreset.SKID, 2);
        v.setAxleRole(0, S, 2);
        assertEquals(SteerPreset.FRONT, v.preset);
    }

    @Test
    void driveDefaultsOnAndPadsForNewAxles() {
        VehicleSetup v = new VehicleSetup();
        assertArrayEquals(new boolean[]{true, true, true}, v.drivenFor(3));
        v.setAxleDriven(1, false, 3);
        assertArrayEquals(new boolean[]{true, false, true, true}, v.drivenFor(4));
        v.setAxleDriven(7, false, 3);
        assertArrayEquals(new boolean[]{true, false, true}, v.drivenFor(3), "out of range is ignored");
    }

    private static List<VehicleSetup.InputPart> swivels(long... pos) {
        List<VehicleSetup.InputPart> out = new java.util.ArrayList<>();
        for (long p : pos) out.add(new VehicleSetup.InputPart(p, false, null));
        return out;
    }

    @Test
    void swivelInputsAreHandedOutInScanOrderAroundTheChoices() {
        VehicleSetup v = new VehicleSetup();
        List<VehicleSetup.InputPart> parts = swivels(10L, 20L, 30L, 40L, 50L);
        Map<Long, PartInput> in = v.inputs(parts);
        assertEquals(PartInput.AUX_A, in.get(10L));
        assertEquals(PartInput.AUX_B, in.get(20L));
        assertEquals(PartInput.AUX_C, in.get(30L), "a crane's third joint gets a key too");
        assertEquals(PartInput.AUX_D, in.get(40L));
        assertEquals(PartInput.OFF, in.get(50L), "past the fourth, off");
        v.swivelInputs.put(10L, PartInput.OFF);
        assertEquals(PartInput.AUX_A, v.inputs(parts).get(20L), "the first free bearing takes A");
        v.swivelInputs.put(30L, PartInput.AUX_A);
        assertEquals(PartInput.AUX_B, v.inputs(parts).get(20L), "A is claimed by hand, so the free one gets B");
        v.swivelInputs.put(40L, PartInput.STEER);
        assertEquals(PartInput.AUX_C, v.inputs(parts).get(50L), "a bearing on Steer takes no auxiliary");
    }

    @Test
    void machinesTakeTheAuxiliariesTheBearingsLeave() {
        VehicleSetup v = new VehicleSetup();
        List<VehicleSetup.InputPart> parts = new java.util.ArrayList<>(swivels(10L, 20L));
        parts.add(new VehicleSetup.InputPart(900L, true, null));
        parts.add(new VehicleSetup.InputPart(901L, true, null));
        Map<Long, PartInput> in = v.inputs(parts);
        assertEquals(PartInput.AUX_A, in.get(10L));
        assertEquals(PartInput.AUX_B, in.get(20L));
        assertEquals(PartInput.AUX_C, in.get(900L), "a forklift's mast after its two bearings");
        assertEquals(PartInput.AUX_D, in.get(901L));
        v.machineInputs.put(901L, PartInput.STEER);
        assertEquals(PartInput.STEER, v.inputs(parts).get(901L), "a turret on the steering keys");
        assertEquals(PartInput.AUX_C, v.inputs(parts).get(900L));
    }

    @Test
    void aJointSteersAHitchSwingsFreeAndNeitherTakesAKey() {
        VehicleSetup v = new VehicleSetup();
        List<VehicleSetup.InputPart> parts = List.of(
                new VehicleSetup.InputPart(1L, false, PartInput.STEER),
                new VehicleSetup.InputPart(2L, false, PartInput.OFF),
                new VehicleSetup.InputPart(3L, false, null));
        Map<Long, PartInput> in = v.inputs(parts);
        assertEquals(PartInput.STEER, in.get(1L), "an articulated loader's joint follows the wheel");
        assertEquals(PartInput.OFF, in.get(2L), "a trailer's hitch is left to swing");
        assertEquals(PartInput.AUX_A, in.get(3L), "the bed still gets the first auxiliary");
        v.swivelInputs.put(2L, PartInput.AUX_B);
        assertEquals(PartInput.AUX_B, v.inputs(parts).get(2L), "the player's choice beats the proposal");
    }

    @Test
    void jetsAndLightsOnAnAuxiliaryKeepItFromAutoParts() {
        VehicleSetup v = new VehicleSetup();
        v.jetRoles.put(50L, JetRole.AUX_A);
        v.linkSources.put(60L, LinkSource.AUX_B_MINUS);
        Map<Long, PartInput> in = v.inputs(swivels(10L, 20L));
        assertEquals(PartInput.AUX_C, in.get(10L), "A has a jet and B a horn, so the bed does not land on them");
        assertEquals(PartInput.AUX_D, in.get(20L));
    }

    @Test
    void theProposalFollowsTheVehicleUntilThePlayerPicks() {
        assertEquals(SteerPreset.FRONT, SteerPreset.proposal(2, false, false));
        assertEquals(SteerPreset.FRONT, SteerPreset.proposal(3, false, false), "a 6x6 pivots about its tandem");
        assertEquals(SteerPreset.TWIN_FRONT, SteerPreset.proposal(4, false, false), "an 8x8 steers its front two");
        assertEquals(SteerPreset.NONE, SteerPreset.proposal(1, true, false), "an articulated loader steers with its joint");
        assertEquals(SteerPreset.SKID, SteerPreset.proposal(5, false, true), "a tank's sides run on their own governors");
        assertEquals(SteerPreset.FRONT, SteerPreset.proposal(2, false, true), "a car with a governor per side still steers");
        assertEquals(SteerPreset.NONE, SteerPreset.proposal(1, true, true), "the joint wins");
        VehicleSetup v = new VehicleSetup();
        assertTrue(v.presetAuto);
        v.proposePreset(SteerPreset.TWIN_FRONT);
        assertEquals(SteerPreset.TWIN_FRONT, v.preset);
        v.applyPreset(SteerPreset.ALL, 4);
        assertFalse(v.presetAuto);
        v.proposePreset(SteerPreset.FRONT);
        assertEquals(SteerPreset.ALL, v.preset, "a choice is never re-proposed away");
        v.resetToAuto();
        assertTrue(v.presetAuto);
        v.presetAuto = true;
        v.setAxleRole(0, F, 2);
        assertFalse(v.presetAuto, "an axle click is a choice too");
    }

    @Test
    void rearAndNoneFillTheirRoles() {
        assertArrayEquals(new AxleRole[]{F, S}, SteerPreset.REAR.roles(2), "a forklift steers from the back");
        assertArrayEquals(new AxleRole[]{F, F, S}, SteerPreset.REAR.roles(3));
        assertArrayEquals(new AxleRole[]{F, F}, SteerPreset.NONE.roles(2));
        assertEquals(SteerPreset.REAR, SteerPreset.matching(new AxleRole[]{F, S}, SteerPreset.FRONT));
        assertEquals(SteerPreset.NONE, SteerPreset.matching(new AxleRole[]{F, F}, SteerPreset.FRONT), "nothing steering is None");
        assertEquals(SteerPreset.SKID, SteerPreset.matching(new AxleRole[]{F, F}, SteerPreset.SKID), "but a skid click keeps skid");
        assertEquals(SteerPreset.DISPLAY.size() + 1, SteerPreset.values().length, "every layout but Custom has a button");
        assertEquals(6, SteerPreset.CUSTOM.ordinal(), "ordinals are persisted: new layouts go after Custom");
    }

    @Test
    void liftHoldsOnAHovercraftUntilThePlayerSays() {
        VehicleSetup v = new VehicleSetup();
        assertTrue(v.liftHoldFor(true), "jets drive: a hovercraft keeps its fans up");
        assertFalse(v.liftHoldFor(false), "on wheels the lift keys are a jump jet");
        v.liftHold = false;
        assertFalse(v.liftHoldFor(true));
    }

    @Test
    void machinesProposeTheirKindAndLightsStartOff() {
        VehicleSetup v = new VehicleSetup();
        assertFalse(v.machineSettingFor(7L, false).latched(), "a piston runs while held");
        assertTrue(v.machineSettingFor(7L, true).latched(), "a drill is a switch");
        v.machineSettings.put(7L, MachineAxis.Setting.SPRUNG.withRpm(128));
        assertEquals(128, v.machineSettingFor(7L, true).rpm());
        assertEquals(LinkSource.OFF, v.linkSourceFor(8L), "a receiver is left alone until the player picks");
        assertFalse(v.linkToggle(8L));
    }

    @Test
    void latchedOrSprungFollowsTheInputUntilThePlayerSays() {
        VehicleSetup v = new VehicleSetup();
        assertEquals(AuxAxis.Setting.LATCHED, v.auxSettingFor(1L, PartInput.AUX_A), "a tipping bed stays put");
        assertEquals(AuxAxis.Setting.SPRUNG, v.auxSettingFor(1L, PartInput.AUX_B), "a spoiler springs back, as in 1.14.38");
        assertEquals(AuxAxis.Setting.SPRUNG, v.auxSettingFor(1L, PartInput.STEER), "a rudder follows the wheel");
        v.auxSettings.put(1L, AuxAxis.Setting.SPRUNG.withLatched(true));
        assertEquals(true, v.auxSettingFor(1L, PartInput.STEER).latched());
    }

    @Test
    void jetsDefaultToAutoAndOverridesStick() {
        VehicleSetup v = new VehicleSetup();
        assertEquals(JetRole.AUTO, v.jetRoleFor(5L));
        v.jetRoles.put(5L, JetRole.THROTTLE);
        assertEquals(JetRole.THROTTLE, v.jetRoleFor(5L));
        assertFalse(v.jetInverted(5L));
        v.jetInverted.put(5L, true);
        assertTrue(v.jetInverted(5L));
        assertFalse(v.governorFlipped(9L));
        v.governorFlipped.put(9L, true);
        assertTrue(v.governorFlipped(9L));
    }

    @Test
    void legacyRolesReadAsTheirNewMeaning() {
        assertEquals(JetRole.AUTO, JetRole.fromLegacyOrdinal(0), "boost was every jet; auto is what boost should have been");
        assertEquals(JetRole.THROTTLE, JetRole.fromLegacyOrdinal(1));
        assertEquals(JetRole.OFF, JetRole.fromLegacyOrdinal(2));
        assertEquals(PartInput.AUX_A, PartInput.fromLegacyOrdinal(0));
        assertEquals(PartInput.AUX_B, PartInput.fromLegacyOrdinal(1));
        assertEquals(PartInput.OFF, PartInput.fromLegacyOrdinal(2));
        for (JetRole r : JetRole.values()) assertEquals(r, JetRole.byKey(r.key()));
        for (PartInput in : PartInput.values()) assertEquals(in, PartInput.byKey(in.key()));
    }

    @Test
    void missingPartsAreForgottenAndCounted() {
        VehicleSetup v = new VehicleSetup();
        v.jetRoles.put(1L, JetRole.OFF);
        v.jetInverted.put(4L, true);
        v.swivelInputs.put(2L, PartInput.AUX_B);
        v.auxSettings.put(2L, AuxAxis.Setting.SPRUNG);
        v.governorFlipped.put(3L, true);
        v.machineInputs.put(5L, PartInput.AUX_C);
        v.machineSettings.put(5L, MachineAxis.Setting.LATCHED);
        v.linkSources.put(6L, LinkSource.BRAKE);
        v.linkToggle.put(6L, true);
        assertEquals(8, v.forgetMissing(Set.of(1L)));
        assertEquals(JetRole.OFF, v.jetRoleFor(1L));
        assertTrue(v.swivelInputs.isEmpty());
        assertTrue(v.auxSettings.isEmpty());
        assertTrue(v.jetInverted.isEmpty());
        assertTrue(v.machineInputs.isEmpty() && v.machineSettings.isEmpty());
        assertTrue(v.linkSources.isEmpty() && v.linkToggle.isEmpty());
    }

    @Test
    void resetToAutoForgetsEverything() {
        VehicleSetup v = new VehicleSetup();
        v.applyPreset(SteerPreset.ALL, 3);
        v.keymap.setInverted(Control.STEER, true);
        v.options = v.options.withMaxRpm(64);
        v.jetRoles.put(1L, JetRole.OFF);
        v.jetsSteerWithoutBoost = true;
        v.resetToAuto();
        assertEquals(SteerPreset.FRONT, v.preset);
        assertTrue(v.keymap.isDefault());
        assertEquals(DriveModel.Options.DEFAULT, v.options);
        assertTrue(v.jetRoles.isEmpty());
        assertFalse(v.jetsSteerWithoutBoost);
    }

    @Test
    void aTrailersModeIsRememberedAndTheOldestGoPastTheCap() {
        VehicleSetup v = new VehicleSetup();
        java.util.UUID first = new java.util.UUID(0L, 0L);
        v.setTrailerMode(first, TrailerMode.MIRROR);
        for (int i = 1; i <= VehicleSetup.MAX_TRAILER_MODES; i++) v.setTrailerMode(new java.util.UUID(0L, i), TrailerMode.OFF);
        assertEquals(VehicleSetup.MAX_TRAILER_MODES, v.trailerModes.size());
        assertEquals(TrailerMode.ASSIST, v.trailerModeFor(first), "the oldest choice went, so it is back on the default");
        v.setTrailerMode(new java.util.UUID(0L, 1L), TrailerMode.MIRROR);
        v.setTrailerMode(new java.util.UUID(0L, 99L), TrailerMode.OFF);
        assertEquals(TrailerMode.MIRROR, v.trailerModeFor(new java.util.UUID(0L, 1L)), "choosing again makes it the newest");
        assertEquals(TrailerMode.ASSIST, v.trailerModeFor(null));
    }

    @Test
    void aTrailersModeSurvivesARescanWithoutIt() {
        VehicleSetup v = new VehicleSetup();
        java.util.UUID trailer = new java.util.UUID(7L, 7L);
        v.setTrailerMode(trailer, TrailerMode.OFF);
        v.forgetMissing(Set.of());
        assertEquals(TrailerMode.OFF, v.trailerModeFor(trailer), "unhitched today, coupled again tomorrow");
        v.resetToAuto();
        assertEquals(TrailerMode.ASSIST, v.trailerModeFor(trailer), "Reset to auto forgets it");
    }

    @Test
    void aCouplerGetsTheNextFreeAuxiliaryOnAToggle() {
        VehicleSetup v = new VehicleSetup();
        v.couplers.add(81L);
        v.couplers.add(82L);
        Map<Long, PartInput> parts = v.inputs(List.of(new VehicleSetup.InputPart(1L, false, null)));
        assertEquals(PartInput.AUX_A, parts.get(1L), "the bed takes Aux A first");
        v.proposeCouplers(parts);
        assertEquals(LinkSource.AUX_B_PLUS, v.linkSourceFor(81L));
        assertEquals(LinkSource.AUX_C_PLUS, v.linkSourceFor(82L));
        assertTrue(v.linkToggle(81L), "a coupler on a held key would drop the trailer on release");
        v.linkSources.put(82L, LinkSource.AUX_D_MINUS);
        v.proposeCouplers(parts);
        assertEquals(LinkSource.AUX_D_MINUS, v.linkSourceFor(82L), "the player's choice wins");
        assertTrue(v.linkToggle(82L));
        v.linkToggle.put(82L, false);
        assertFalse(v.linkToggle(82L));
        assertEquals(LinkSource.OFF, v.linkSourceFor(83L), "an ordinary light is still Off until picked");
        assertFalse(v.linkToggle(83L));
    }

    @Test
    void aCouplerSetOffStaysOff() {
        VehicleSetup v = new VehicleSetup();
        v.couplers.add(81L);
        Map<Long, PartInput> parts = v.inputs(List.of());
        v.proposeCouplers(parts);
        assertEquals(LinkSource.AUX_A_PLUS, v.linkSourceFor(81L), "proposed the first free auxiliary");
        v.setLinkSource(81L, LinkSource.OFF);
        v.proposeCouplers(parts);
        assertEquals(LinkSource.OFF, v.linkSourceFor(81L), "Off is the player's choice, not a gap for the proposal");
        v.setLinkSource(81L, LinkSource.BRAKE);
        assertEquals(LinkSource.BRAKE, v.linkSourceFor(81L));
        v.setLinkSource(90L, LinkSource.LEFT);
        v.setLinkSource(90L, LinkSource.OFF);
        assertFalse(v.linkSources.containsKey(90L), "an ordinary light set Off leaves no entry behind");
        assertEquals(LinkSource.OFF, v.linkSourceFor(90L));
    }

    @Test
    void aHeadlightsFullBeamKeyIsAToggleUntilThePlayerMakesItAFlash() {
        VehicleSetup v = new VehicleSetup();
        v.proposeLamps(Map.of(91L, LinkSource.HEADLIGHT, 92L, LinkSource.REVERSE));
        assertTrue(v.linkToggle(91L), "a full beam stalk is pushed and left, not held");
        assertFalse(v.linkToggle(92L), "a reverse light has no key to toggle");
        v.linkToggle.put(91L, false);
        assertFalse(v.linkToggle(91L), "the player's choice: full beam only while held, a flash");
        v.setLinkSource(93L, LinkSource.HEADLIGHT);
        assertTrue(v.linkToggle(93L), "picked on the console, it toggles too");
    }

    @Test
    void theGreetingIsOnUntilTurnedOffAndResetToAutoTurnsItBackOn() {
        VehicleSetup v = new VehicleSetup();
        assertTrue(v.greeting, "the lamps greet a driver out of the box");
        v.greeting = false;
        v.resetToAuto();
        assertTrue(v.greeting);
    }
}
