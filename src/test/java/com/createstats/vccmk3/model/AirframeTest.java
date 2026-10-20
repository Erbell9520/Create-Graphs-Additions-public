package com.createstats.vccmk3.model;

import com.createstats.vccmk3.hardware.DeviceCategory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirframeTest {
    @Test
    void everyAirframeInitialises() {
        Airframe[] all = Airframe.values();
        assertTrue(all.length > 0, "no airframes at all");
        for (Airframe frame : all) {
            assertNotNull(frame.label(), "airframe has no label");
            assertNotNull(frame.shortLabel(), "airframe has no short label");
            assertFalse(frame.slotList().isEmpty(), frame + " has no slots");
        }
    }

    @Test
    void everySlotResolvesByItsOwnKey() {
        for (Airframe frame : Airframe.values()) {
            for (SlotSpec spec : frame.slotList()) {
                assertSame(spec, frame.slot(spec.key()),
                        frame + " cannot find its own slot " + spec.key());
                assertTrue(frame.hasSlot(spec.key()));
            }
        }
    }

    @Test
    void everySlotAcceptsSomething() {
        for (Airframe frame : Airframe.values()) {
            for (SlotSpec spec : frame.slotList()) {
                assertFalse(spec.accepts().isEmpty(), frame + " slot " + spec.key() + " accepts nothing");
                boolean anyReal = false;
                for (DeviceCategory category : DeviceCategory.values()) {
                    if (spec.accepts(category)) anyReal = true;
                }
                assertTrue(anyReal, frame + " slot " + spec.key() + " accepts no real category");
            }
        }
    }

    @Test
    void slotKeysAreUniqueWithinAnAirframe() {
        for (Airframe frame : Airframe.values()) {
            Set<String> seen = new HashSet<>();
            for (SlotSpec spec : frame.slotList()) {
                assertTrue(seen.add(spec.key()), frame + " has two slots keyed " + spec.key());
            }
            assertEquals(seen.size(), frame.slotList().size());
        }
    }

    @Test
    void hybridSlotsAreFunctionsNotPlaces() {
        for (SlotSpec spec : Airframe.HYBRID.slotList()) {
            String key = spec.key();
            for (String place : new String[] {"FRONT", "REAR", "MID", "LEFT", "RIGHT", "PORT", "STBD"}) {
                assertFalse(key.contains(place),
                        "hybrid slot " + key + " names a position, which the console already knows");
            }
        }
    }

    @Test
    void groundVehiclesDoNotClaimFlightAxes() {
        for (SlotSpec spec : Airframe.GROUND.slotList()) {
            assertTrue(spec.axis() == ControlAxis.FORWARD || spec.axis() == ControlAxis.AUX,
                    "ground slot " + spec.key() + " claims " + spec.axis());
        }
    }

    @Test
    void airshipsDoNotClaimRoll() {
        for (SlotSpec spec : Airframe.AIRSHIP.slotList()) {
            assertFalse(spec.axis() == ControlAxis.ROLL,
                    "airship slot " + spec.key() + " claims roll, which an airship cannot do");
        }
    }

    @Test
    void rollAndPitchSurfacesDemandLift() {
        for (Airframe frame : Airframe.values()) {
            for (SlotSpec spec : frame.slotList()) {
                if (!spec.isSurface() || spec.accepts().size() != 1) continue;
                if (spec.axis() == ControlAxis.ROLL || spec.axis() == ControlAxis.PITCH) {
                    assertTrue(spec.wantsLift(),
                            frame + " slot " + spec.key() + " serves " + spec.axis()
                                    + " but does not require lifting sails");
                }
            }
        }
    }

    @Test
    void ordinalRoundTripIsStable() {
        for (Airframe frame : Airframe.values()) {
            assertSame(frame, Airframe.byOrdinal(frame.ordinal()));
            assertSame(frame, Airframe.safeValueOf(frame.name(), Airframe.FIXED_WING));
        }
        assertSame(Airframe.FIXED_WING, Airframe.byOrdinal(-1));
        assertSame(Airframe.FIXED_WING, Airframe.byOrdinal(999));
        assertSame(Airframe.FIXED_WING, Airframe.safeValueOf("nonsense", Airframe.FIXED_WING));
    }
}
