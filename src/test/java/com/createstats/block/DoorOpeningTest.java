package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorOpeningTest {
    @Test
    @DisplayName("ROLL_UP is a lone-leaf opening, not a pair one")
    void rollUpIsSingle() {
        assertEquals(DoorOpening.ROLL_UP, DoorOpening.ROLL_UP.single());
        assertFalse(DoorOpening.ROLL_UP.pairOnly());
        assertEquals(DoorOpening.ROLL_UP, DoorOpening.ROLL_UP.effective(true));
        assertEquals(DoorOpening.ROLL_UP, DoorOpening.ROLL_UP.effective(false));
    }

    @Test
    @DisplayName("byName round-trips ROLL_UP and still falls back to SWING")
    void byName() {
        assertEquals(DoorOpening.ROLL_UP, DoorOpening.byName("ROLL_UP"));
        assertEquals(DoorOpening.SWING, DoorOpening.byName("nonsense"));
    }

    @Test
    @DisplayName("the wrench cycles through industrial iron's three openings and wraps")
    void nextCyclesIndustrialIron() {
        final Set<DoorOpening> offered = EnumSet.of(
                DoorOpening.SWING, DoorOpening.SLIDE, DoorOpening.ROLL_UP);
        DoorOpening style = DoorOpening.SWING;
        style = style.next(false, offered);
        assertEquals(DoorOpening.SLIDE, style);
        style = style.next(false, offered);
        assertEquals(DoorOpening.ROLL_UP, style);
        style = style.next(false, offered);
        assertEquals(DoorOpening.SWING, style);
    }

    @Test
    @DisplayName("next never lands on a style the material does not offer")
    void nextStaysInsideOffered() {
        final Set<DoorOpening> offered = EnumSet.of(DoorOpening.SLIDE, DoorOpening.ROLL_UP);
        for (DoorOpening from : DoorOpening.values()) {
            final DoorOpening picked = from.next(false, offered);
            assertTrue(offered.contains(picked) || picked == from,
                    from + " cycled to unoffered " + picked);
        }
    }

    @Test
    @DisplayName("ROLL_UP sits last, after PLUG_RETRACT, so the wrench reaches it")
    void rollUpIsLastInWrenchOrder() {
        final DoorOpening[] order = DoorOpening.values();
        assertEquals(DoorOpening.ROLL_UP, order[order.length - 1]);
        assertEquals(DoorOpening.PLUG_RETRACT, order[order.length - 2]);
    }
}
