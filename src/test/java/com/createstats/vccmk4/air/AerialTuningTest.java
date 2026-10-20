package com.createstats.vccmk4.air;

import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.Feel;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.PartInput;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialTuningTest {
    @Test
    void theTuningIsTheFiveShipWideChoicesAndNothingElse() {
        final CompoundTag tuning = AerialNbt.tuning(tunedShip());
        assertEquals(Set.of("Feel", "Leave", "Cap", "Bell", "Keymap"), tuning.getAllKeys());
    }

    @Test
    void aTuningReadsBackAsItWasWritten() {
        final AerialSetup s = tunedShip();
        final AerialNbt.Tuning back = AerialNbt.readTuning(AerialNbt.tuning(s));
        assertNotNull(back);
        assertSame(Feel.SPORTY, back.feel());
        assertSame(LeaveMode.GO_TO, back.leave());
        assertEquals(180.0, back.altitudeCap());
        assertFalse(back.bell());
        assertArrayEquals(s.keymap.toInts(), back.keymap().toInts());
    }

    @Test
    void aShipWithNoCeilingCopiesNoCeiling() {
        final AerialSetup s = tunedShip();
        s.altitudeCap = Double.NaN;
        final CompoundTag tuning = AerialNbt.tuning(s);
        assertFalse(tuning.contains("Cap"));
        final AerialNbt.Tuning back = AerialNbt.readTuning(tuning);
        assertNotNull(back);
        assertTrue(Double.isNaN(back.altitudeCap()), "no ceiling pastes as no ceiling, not as y 0");
    }

    @Test
    void aTagThatIsNotATuningIsNotOne() {
        assertNull(AerialNbt.readTuning(new CompoundTag()));
    }

    @Test
    void aPrintedConsoleTakesTheTuningAndStartsItsPartsFromTheProposal() {
        final AerialSetup printed = new AerialSetup();
        printed.engines.put(77L, new AerialSetup.Engine(AerialSetup.EngineJob.LIFT, false, 50));
        AerialNbt.readSetup(AerialNbt.tuning(tunedShip()), printed);
        assertSame(Feel.SPORTY, printed.feel);
        assertSame(LeaveMode.GO_TO, printed.leave);
        assertEquals(180.0, printed.altitudeCap);
        assertFalse(printed.bell);
        assertEquals(KeyIds.key(265), printed.keymap.plus(Control.TRIM));
        assertTrue(printed.engines.isEmpty());
        assertTrue(printed.surfaces.isEmpty());
        assertEquals(0, printed.noseTurn);
        assertFalse(printed.noseSet, "a printed console's nose follows its own engines");
        assertEquals(0.0, printed.trim);
    }

    @Test
    void theFullSaveStillCarriesTheNoseTheTrimAndEveryPart() {
        final CompoundTag full = AerialNbt.setup(tunedShip());
        for (final String key : AerialNbt.tuning(tunedShip()).getAllKeys()) {
            assertTrue(full.contains(key), "the save lost the tuning key " + key);
        }
        for (final String key : new String[]{"Nose", "NoseSet", "Trim", "Engines", "BalloonsOff", "Surfaces",
                "Tools", "Lights"}) {
            assertTrue(full.contains(key), "the save lost " + key);
        }
        final AerialSetup back = new AerialSetup();
        AerialNbt.readSetup(full, back);
        assertEquals(3, back.noseTurn);
        assertEquals(-0.35, back.trim, 1.0e-9);
        assertEquals(AerialSetup.EngineJob.LIFT, back.engine(11L).job());
    }

    private static AerialSetup tunedShip() {
        final AerialSetup s = new AerialSetup();
        s.feel = Feel.SPORTY;
        s.leave = LeaveMode.GO_TO;
        s.noseTurn = 3;
        s.noseSet = true;
        s.setTrim(-0.35);
        s.altitudeCap = 180.0;
        s.bell = false;
        s.keymap.set(Control.TRIM, 0, KeyIds.key(265));
        s.engines.put(11L, new AerialSetup.Engine(AerialSetup.EngineJob.LIFT, false, 50));
        s.balloonOff.put(21L, true);
        s.surfaces.put(31L, new AerialSetup.Surface(AerialSetup.SurfaceRole.TOOL, 45, true, PartInput.AUX_C, true));
        s.lights.put(51L, new AerialSetup.Light(AirLight.STROBE, false));
        return s;
    }
}
