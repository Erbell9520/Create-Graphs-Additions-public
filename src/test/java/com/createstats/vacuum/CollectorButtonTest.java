package com.createstats.vacuum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorButtonTest {
    @Test
    void theLensAndItsBezelTakeTheClick() {
        assertTrue(CollectorButton.hit(5.0, 2.0, CollectorButton.Z0), "the lens standing out, switched off");
        assertTrue(CollectorButton.hit(5.0, 2.0, CollectorButton.LATCHED), "the lens latched in");
        assertTrue(CollectorButton.hit(3.6, 0.6, 0.5), "the bezel's corner");
        assertTrue(CollectorButton.hit(5.0, 3.5, 0.6), "the bezel's top");
        assertTrue(CollectorButton.hit(6.5, 2.0, 0.8), "the bezel's side");
    }

    @Test
    void theRestOfTheMachineIsNotTheButton() {
        assertFalse(CollectorButton.hit(10.0, 2.0, 1.0), "the maker's plate beside it");
        assertFalse(CollectorButton.hit(5.0, 2.0, 15.0), "the back of the housing");
        assertFalse(CollectorButton.hit(5.0, 3.5, 2.0), "the deck behind it");
        assertFalse(CollectorButton.hit(5.0, 9.75, 0.0), "the mouth, where the filter is set");
        assertFalse(CollectorButton.hit(5.0, 12.0, 3.0), "the damper handle, where the reach is set");
    }

    @Test
    void theLensLatchesInWhenOnAndStandsOutWhenOff() {
        assertTrue(CollectorButton.lensFront(false) < CollectorButton.lensFront(true),
                "switched off, the lens stands further out than when it is latched in");
        assertTrue(CollectorButton.lensFront(true) < 0.5, "latched in, it still stands proud of its bezel");
    }
}
