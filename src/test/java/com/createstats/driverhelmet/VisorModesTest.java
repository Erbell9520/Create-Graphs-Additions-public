package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisorModesTest {
    private static final boolean UP = true;
    private static final boolean DOWN = false;

    @Test
    void visorUpIsCreateGogglesAndNothingElse() {
        assertTrue(VisorModes.gogglesActive(UP));
        assertFalse(VisorModes.hudActive(UP));
        assertFalse(VisorModes.nightVisionKeyAccepted(UP));
    }

    @Test
    void visorDownIsTheHudAndNotGoggles() {
        assertFalse(VisorModes.gogglesActive(DOWN));
        assertTrue(VisorModes.hudActive(DOWN));
        assertTrue(VisorModes.nightVisionKeyAccepted(DOWN));
    }

    @Test
    void theHudFollowsTheDriverIntoTheChaseViewsButTheGlassDoesNot() {
        final boolean firstPerson = true;
        final boolean chaseView = false;
        final boolean riding = true;
        final boolean onFoot = false;
        assertTrue(VisorModes.hudInView(DOWN, firstPerson, onFoot));
        assertTrue(VisorModes.hudInView(DOWN, firstPerson, riding));
        assertTrue(VisorModes.hudInView(DOWN, chaseView, riding), "F5 in a vehicle keeps the HUD");
        assertFalse(VisorModes.hudInView(DOWN, chaseView, onFoot), "F5 on foot: nothing to drive");
        for (boolean camera : new boolean[]{firstPerson, chaseView}) {
            for (boolean seat : new boolean[]{riding, onFoot}) {
                assertFalse(VisorModes.hudInView(UP, camera, seat), "visor up is goggles, never the HUD");
            }
        }
        assertTrue(VisorModes.glassInView(firstPerson));
        assertFalse(VisorModes.glassInView(chaseView), "no tint or eyeport drawn over a view of the model");
    }

    @Test
    void nightVisionNeedsTheHelmetOnTheVisorDownAndTheSwitchOn() {
        assertTrue(VisorModes.nightVisionActive(true, DOWN, true));
        assertFalse(VisorModes.nightVisionActive(true, UP, true), "raising the visor takes it away");
        assertFalse(VisorModes.nightVisionActive(true, DOWN, false));
        assertFalse(VisorModes.nightVisionActive(false, DOWN, true), "a helmet in a chest gives nobody night vision");
    }

    @Test
    void aFlickWaitsForTheLastStroke() {
        assertTrue(VisorModes.acceptsFlick(0L, 1000L));
        assertFalse(VisorModes.acceptsFlick(1000L, 1000L + VisorMotion.STROKE_LOCK_TICKS - 1));
        assertTrue(VisorModes.acceptsFlick(1000L, 1000L + VisorMotion.STROKE_LOCK_TICKS));
    }
}
