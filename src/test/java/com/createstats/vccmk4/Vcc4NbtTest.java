package com.createstats.vccmk4;

import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.VehicleSetup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Vcc4NbtTest {
    @Test
    void a1_14_38SaveIsReadInTheNewTerms() {
        CompoundTag old = new CompoundTag();
        old.putInt("Preset", 1);
        CompoundTag props = new CompoundTag();
        props.putInt("11", 0);
        props.putInt("12", 1);
        props.putInt("13", 2);
        old.put("PropRoles", props);
        CompoundTag aux = new CompoundTag();
        aux.putInt("21", 1);
        aux.putInt("22", 2);
        old.put("AuxRoles", aux);

        old.putIntArray("Keymap", new int[]{1, 2, 0, 4, 3, 0, 5, 0, 0, 6, 0, 0, 7, 8, 0, 5, 0, 0});

        VehicleSetup s = new VehicleSetup();
        Vcc4Nbt.read(old, s);
        assertEquals(JetRole.AUTO, s.jetRoleFor(11L), "boost becomes auto");
        assertEquals(JetRole.THROTTLE, s.jetRoleFor(12L));
        assertEquals(JetRole.OFF, s.jetRoleFor(13L));
        assertEquals(PartInput.AUX_B, s.swivelInputs.get(21L));
        assertEquals(PartInput.OFF, s.swivelInputs.get(22L), "old OFF is not the new Aux C");
        assertEquals(5, s.keymap.plus(Control.AUX_B));
        assertFalse(s.keymap.bound(Control.LIFT), "controls the old save never had keep their defaults");
    }

    @Test
    void aNewSaveRoundTrips() {
        VehicleSetup s = new VehicleSetup();
        s.jetRoles.put(31L, JetRole.AUX_C);
        s.jetInverted.put(31L, true);
        s.swivelInputs.put(41L, PartInput.STEER);
        s.jetsSteerWithoutBoost = true;
        s.keymap.setPlus(Control.LIFT, KeyIds.key(82));
        CompoundTag tag = Vcc4Nbt.write(s);
        assertEquals(Vcc4Nbt.VERSION, tag.getInt("Version"));

        VehicleSetup back = new VehicleSetup();
        Vcc4Nbt.read(tag, back);
        assertEquals(JetRole.AUX_C, back.jetRoleFor(31L));
        assertTrue(back.jetInverted(31L));
        assertEquals(PartInput.STEER, back.swivelInputs.get(41L));
        assertTrue(back.jetsSteerWithoutBoost);
        assertEquals(KeyIds.key(82), back.keymap.plus(Control.LIFT));
    }

    @Test
    void everything1_14_43AddedRoundTrips() {
        VehicleSetup s = new VehicleSetup();
        s.applyPreset(SteerPreset.REAR, 2);
        s.options = s.options.withThrottleTicks(12).withHoldWhenStopped(false).withParkBrake(false);
        s.liftHold = true;
        s.machineInputs.put(51L, PartInput.AUX_C);
        s.machineSettings.put(51L, MachineAxis.Setting.LATCHED.withRpm(96).withInverted(true));
        s.linkSources.put(61L, LinkSource.LEFT);
        s.linkToggle.put(62L, true);

        VehicleSetup back = new VehicleSetup();
        Vcc4Nbt.read(Vcc4Nbt.write(s), back);
        assertEquals(SteerPreset.REAR, back.preset);
        assertFalse(back.presetAuto, "a picked layout stays picked");
        assertEquals(12, back.options.throttleTicks());
        assertFalse(back.options.holdWhenStopped());
        assertFalse(back.options.parkBrake());
        assertEquals(Boolean.TRUE, back.liftHold);
        assertEquals(PartInput.AUX_C, back.machineInputs.get(51L));
        assertEquals(new MachineAxis.Setting(96, true, true), back.machineSettings.get(51L));
        assertEquals(LinkSource.LEFT, back.linkSourceFor(61L));
        assertTrue(back.linkToggle(62L));

        VehicleSetup auto = new VehicleSetup();
        Vcc4Nbt.read(Vcc4Nbt.write(new VehicleSetup()), auto);
        assertTrue(auto.presetAuto, "a new console keeps proposing");
        assertNull(auto.liftHold, "and the lift lever stays automatic");
    }

    @Test
    void a1_14_39SaveKeepsItsLayoutAndTakesTheNewDefaults() {
        CompoundTag v2 = new CompoundTag();
        v2.putInt("Version", 2);
        v2.putInt("Preset", SteerPreset.FRONT.ordinal());
        v2.putInt("MaxRpm", 96);
        VehicleSetup s = new VehicleSetup();
        Vcc4Nbt.read(v2, s);
        assertEquals(SteerPreset.FRONT, s.preset);
        assertFalse(s.presetAuto, "not re-proposed under a vehicle the player already set up");
        assertEquals(96, s.options.maxRpm());
        assertEquals(DriveModel.Options.DEFAULT.throttleTicks(), s.options.throttleTicks());
        assertTrue(s.options.parkBrake(), "old consoles get the park brake");
        assertTrue(s.options.holdWhenStopped());
        assertTrue(s.machineInputs.isEmpty() && s.linkSources.isEmpty());
    }

    @Test
    void trailerModesRoundTripInTheOrderTheyWereChosen() {
        VehicleSetup s = new VehicleSetup();
        UUID a = new UUID(1L, 2L);
        UUID b = new UUID(3L, 4L);
        s.setTrailerMode(a, TrailerMode.MIRROR);
        s.setTrailerMode(b, TrailerMode.OFF);
        VehicleSetup back = new VehicleSetup();
        Vcc4Nbt.read(Vcc4Nbt.write(s), back);
        assertEquals(TrailerMode.MIRROR, back.trailerModeFor(a));
        assertEquals(TrailerMode.OFF, back.trailerModeFor(b));
        assertEquals(List.of(a, b), List.copyOf(back.trailerModes.keySet()), "oldest first, so the cap drops the oldest");
        assertEquals(TrailerMode.ASSIST, back.trailerModeFor(new UUID(9L, 9L)), "a trailer nobody chose for is on Assist");
    }

    @Test
    void a1_14_43SaveHasEveryTrailerOnAssist() {
        CompoundTag v3 = new CompoundTag();
        v3.putInt("Version", 3);
        v3.putBoolean("PresetAuto", true);
        VehicleSetup s = new VehicleSetup();
        Vcc4Nbt.read(v3, s);
        assertTrue(s.trailerModes.isEmpty());
        assertEquals(TrailerMode.ASSIST, s.trailerModeFor(new UUID(5L, 6L)));
    }

    @Test
    void theThrottleReleaseRoundTripsAndAnOlderSaveFollowsItsRamp() {
        VehicleSetup s = new VehicleSetup();
        s.options = s.options.withThrottleTicks(14).withReleaseTicks(30);
        VehicleSetup back = new VehicleSetup();
        Vcc4Nbt.read(Vcc4Nbt.write(s), back);
        assertEquals(14, back.options.throttleTicks());
        assertEquals(30, back.options.releaseTicks(), "a release of its own survives a save");

        VehicleSetup following = new VehicleSetup();
        Vcc4Nbt.read(Vcc4Nbt.write(new VehicleSetup()), following);
        assertEquals(DriveModel.Options.AS_RAMP, following.options.releaseTicks(), "as ramp survives a save too");

        CompoundTag v4 = Vcc4Nbt.write(s);
        v4.remove("ReleaseTicks");
        VehicleSetup old = new VehicleSetup();
        Vcc4Nbt.read(v4, old);
        assertEquals(DriveModel.Options.AS_RAMP, old.options.releaseTicks(), "an older save follows its ramp");
        assertEquals(14, old.options.effectiveReleaseTicks(), "so it eases off over the ticks it pulls away in");
    }

    @Test
    void couplersKeepTheirToggleThroughASave() {
        CompoundTag tag = Vcc4Nbt.couplers(java.util.Map.of(71L, true, 72L, false));
        assertEquals(java.util.Map.of(71L, true, 72L, false), Vcc4Nbt.readCouplers(tag));
    }

    @Test
    void aCouplerSetOffStaysOffThroughASave() {
        VehicleSetup s = new VehicleSetup();
        s.couplers.add(73L);
        s.setLinkSource(73L, LinkSource.OFF);
        CompoundTag tag = Vcc4Nbt.write(s);
        tag.getCompound("LinkSources").putString("74", "a_source_no_version_wrote");

        VehicleSetup back = new VehicleSetup();
        Vcc4Nbt.read(tag, back);

        back.couplers.add(73L);
        back.couplers.add(74L);
        back.proposeCouplers(back.inputs(List.of()));
        assertEquals(LinkSource.OFF, back.linkSourceFor(73L), "the proposal does not take it back after a reload");
        assertEquals(LinkSource.AUX_A_PLUS, back.linkSourceFor(74L), "an unknown key is dropped, not read as Off");
    }

    @Test
    void aVehicleLightSetOffStaysOffThroughASaveAndAnUntouchedOneKeepsItsProposal() {
        VehicleSetup s = new VehicleSetup();
        s.proposeLamps(java.util.Map.of(81L, LinkSource.TAIL_BRAKE, 82L, LinkSource.LEFT));
        s.setLinkSource(81L, LinkSource.OFF);
        CompoundTag tag = Vcc4Nbt.write(s);

        VehicleSetup back = new VehicleSetup();
        Vcc4Nbt.read(tag, back);

        back.proposeLamps(java.util.Map.of(81L, LinkSource.TAIL_BRAKE, 82L, LinkSource.LEFT));
        assertEquals(LinkSource.OFF, back.linkSourceFor(81L), "the proposal does not take a lamp set Off back");
        assertEquals(LinkSource.LEFT, back.linkSourceFor(82L), "one never touched keeps its job");
        back.setLinkSource(82L, LinkSource.TAIL_BRAKE);
        assertEquals(LinkSource.TAIL_BRAKE, back.linkSourceFor(82L), "and a choice replaces it");
    }

    @Test
    void theGreetingRoundTripsAndASaveFromBeforeItGreets() {
        VehicleSetup s = new VehicleSetup();
        s.greeting = false;
        s.keymap.setPlus(Control.BEAM, KeyIds.key(66));
        VehicleSetup back = new VehicleSetup();
        Vcc4Nbt.read(Vcc4Nbt.write(s), back);
        assertFalse(back.greeting, "turned off stays off");
        assertEquals(KeyIds.key(66), back.keymap.plus(Control.BEAM), "and the Full beam key keeps its key");

        CompoundTag old = Vcc4Nbt.write(new VehicleSetup());
        old.remove("Greeting");
        VehicleSetup older = new VehicleSetup();
        older.greeting = false;
        Vcc4Nbt.read(old, older);
        assertTrue(older.greeting, "a 1.14.67 save has no Greeting and greets, the default");
        assertFalse(older.keymap.bound(Control.BEAM), "nor a Full beam key: none until the player picks one");
    }

    @Test
    void anEmptyTagIsAutoEverywhere() {
        VehicleSetup s = new VehicleSetup();
        s.jetRoles.put(1L, JetRole.OFF);
        Vcc4Nbt.read(new CompoundTag(), s);
        assertTrue(s.jetRoles.isEmpty());
        assertTrue(s.keymap.isDefault());
        assertTrue(s.presetAuto);
    }
}
