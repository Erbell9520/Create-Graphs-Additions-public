package com.createstats.vccmk4;

import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.VehicleSetup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Vcc4TuningTest {
    private static final UUID TRAILER = UUID.fromString("00000000-0000-0000-0000-00000000c0de");

    private static final List<String> PER_PART = List.of("JetRoles", "JetInverted", "SwivelInputs",
            "AuxSettings", "MachineInputs", "MachineSettings", "LinkSources", "LinkToggle", "GovernorFlipped",
            "GovernorExcluded", "TrailerModes");

    @Test
    void theTuningCarriesNoPart() {
        final CompoundTag tuning = Vcc4Nbt.tuning(ownersVehicle());
        for (final String key : PER_PART) {
            assertFalse(tuning.contains(key), key + " names this vehicle's parts and must not travel");
        }
        assertEquals(Vcc4Nbt.VERSION, tuning.getInt("Version"));
    }

    @Test
    void aPasteTakesTheTuningAndKeepsTheTargetsOwnParts() {
        final VehicleSetup target = anotherVehicle();
        assertTrue(Vcc4Nbt.applyTuning(Vcc4Nbt.tuning(ownersVehicle()), target));

        assertSame(SteerPreset.ALL, target.preset);
        assertFalse(target.presetAuto, "a layout the driver picked stays picked");
        assertEquals(60, target.options.throttleTicks());
        assertEquals(20, target.options.steerTicks());
        assertFalse(target.options.parkBrake());
        assertEquals(KeyIds.key(82), target.keymap.plus(Control.LIFT));
        assertTrue(target.keymap.inverted(Control.STEER));
        assertTrue(target.jetsSteerWithoutBoost);
        assertEquals(Boolean.FALSE, target.liftHold);
        assertFalse(target.greeting);
        assertArrayEquals(new boolean[]{true, false, true}, target.drivenFor(3));

        assertEquals(JetRole.AUX_C, target.jetRoleFor(901L), "the target's own jet keeps its role");
        assertSame(JetRole.AUTO, target.jetRoleFor(11L), "the copied vehicle's jet is not carried");
        assertTrue(target.governorFlipped(902L));
        assertSame(LinkSource.LEFT, target.linkSourceFor(903L));
        assertSame(TrailerMode.MIRROR, target.trailerModeFor(TRAILER));
    }

    @Test
    void anAutoLayoutStaysAutoSoTheTargetProposesForItsOwnAxles() {
        final VehicleSetup source = new VehicleSetup();
        source.options = source.options.withThrottleTicks(30);
        final VehicleSetup target = anotherVehicle();
        target.applyPreset(SteerPreset.REAR, 2);
        assertTrue(Vcc4Nbt.applyTuning(Vcc4Nbt.tuning(source), target));
        assertTrue(target.presetAuto, "an untouched source layout is the proposal, made again on the target");
        assertEquals(30, target.options.throttleTicks());
    }

    @Test
    void aPrintedConsoleLoadsTheTuningAndStartsItsPartsFromTheProposal() {
        final VehicleSetup printed = anotherVehicle();
        Vcc4Nbt.read(Vcc4Nbt.tuning(ownersVehicle()), printed);
        assertSame(SteerPreset.ALL, printed.preset);
        assertEquals(60, printed.options.throttleTicks());
        assertEquals(KeyIds.key(82), printed.keymap.plus(Control.LIFT));
        assertTrue(printed.jetRoles.isEmpty());
        assertTrue(printed.governorFlipped.isEmpty());
        assertTrue(printed.linkSources.isEmpty());
        assertTrue(printed.trailerModes.isEmpty());
    }

    @Test
    void aTagThatIsNotATuningChangesNothing() {
        final VehicleSetup target = anotherVehicle();
        final CompoundTag before = Vcc4Nbt.write(target);
        assertFalse(Vcc4Nbt.applyTuning(new CompoundTag(), target));
        assertEquals(before, Vcc4Nbt.write(target));
    }

    @Test
    void theFullSaveStillCarriesTheTuningAndEveryPart() {
        final VehicleSetup s = ownersVehicle();
        final CompoundTag full = Vcc4Nbt.write(s);
        for (final String key : Vcc4Nbt.tuning(s).getAllKeys()) {
            assertTrue(full.contains(key), "the save lost the tuning key " + key);
        }
        for (final String key : PER_PART) {
            assertTrue(full.contains(key), "the save lost the per-part key " + key);
        }
        final VehicleSetup back = new VehicleSetup();
        Vcc4Nbt.read(full, back);
        assertSame(SteerPreset.ALL, back.preset);
        assertEquals(60, back.options.throttleTicks());
        assertSame(JetRole.OFF, back.jetRoleFor(11L));
        assertSame(PartInput.STEER, back.swivelInputs.get(12L));
    }

    private static VehicleSetup ownersVehicle() {
        final VehicleSetup s = new VehicleSetup();
        s.applyPreset(SteerPreset.ALL, 3);
        s.setAxleDriven(1, false, 3);
        s.options = s.options.withThrottleTicks(60).withSteerTicks(20).withParkBrake(false);
        s.keymap.setPlus(Control.LIFT, KeyIds.key(82));
        s.keymap.setInverted(Control.STEER, true);
        s.jetsSteerWithoutBoost = true;
        s.liftHold = false;
        s.greeting = false;
        s.jetRoles.put(11L, JetRole.OFF);
        s.swivelInputs.put(12L, PartInput.STEER);
        return s;
    }

    private static VehicleSetup anotherVehicle() {
        final VehicleSetup s = new VehicleSetup();
        s.jetRoles.put(901L, JetRole.AUX_C);
        s.governorFlipped.put(902L, true);
        s.linkSources.put(903L, LinkSource.LEFT);
        s.setTrailerMode(TRAILER, TrailerMode.MIRROR);
        return s;
    }
}
