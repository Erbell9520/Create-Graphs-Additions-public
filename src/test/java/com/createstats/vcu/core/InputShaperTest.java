package com.createstats.vcu.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputShaperTest {
    @Test
    void groundDrivesAndSteersOnSprungKeys() {
        InputShaper s = new InputShaper();
        InputShaper.Demand d = s.shape(Scheme.GROUND, PilotInput.FORWARD | PilotInput.RIGHT, false);
        assertEquals(1.0, d.wheelThrottle(), 0.0);
        assertEquals(1.0, d.wheelSteer(), 0.0);
        assertEquals(1.0, d.axes()[Axis6.YAW.index()], 0.0);
        d = s.shape(Scheme.GROUND, 0, false);
        assertEquals(0.0, d.wheelThrottle(), 0.0);
        assertEquals(0.0, d.wheelSteer(), 0.0);
    }

    @Test
    void planeStickDivesOnWAndThrottleLatches() {
        InputShaper s = new InputShaper();
        InputShaper.Demand d = s.shape(Scheme.PLANE, PilotInput.FORWARD, false);
        assertEquals(-1.0, d.axes()[Axis6.PITCH.index()], 0.0);
        for (int i = 0; i < 60; i++) s.shape(Scheme.PLANE, PilotInput.UP, false);
        d = s.shape(Scheme.PLANE, 0, false);
        assertEquals(1.0, d.axes()[Axis6.FORWARD.index()], 0.0, "throttle stays where it was left");
        for (int i = 0; i < 25; i++) s.shape(Scheme.PLANE, PilotInput.DOWN, false);
        assertEquals(0.5, s.throttle(), 1.0e-9);
    }

    @Test
    void hoverTiltsWithoutAPusherAndPushesWithOne() {
        InputShaper s = new InputShaper();
        InputShaper.Demand tilt = s.shape(Scheme.HOVER, PilotInput.FORWARD, false);
        assertEquals(-1.0, tilt.axes()[Axis6.PITCH.index()], 0.0);
        assertEquals(0.0, tilt.axes()[Axis6.FORWARD.index()], 0.0);
        InputShaper.Demand push = s.shape(Scheme.HOVER, PilotInput.FORWARD, true);
        assertEquals(1.0, push.axes()[Axis6.FORWARD.index()], 0.0);
        assertEquals(0.0, push.axes()[Axis6.PITCH.index()], 0.0);
    }

    @Test
    void collectiveClampsAndBoostPassesThrough() {
        InputShaper s = new InputShaper();
        for (int i = 0; i < 200; i++) s.shape(Scheme.AIRSHIP, PilotInput.UP, false);
        assertEquals(1.0, s.collective(), 0.0);
        InputShaper.Demand d = s.shape(Scheme.AIRSHIP, PilotInput.BOOST, false);
        assertTrue(d.boost());
        assertEquals(1.0, d.lift(), 0.0);
        s.reset();
        assertEquals(0.0, s.collective(), 0.0);
    }

    @Test
    void schemeFollowsTheHardware() {
        assertEquals(Scheme.AIRSHIP, Scheme.detect(new Scheme.Traits(0, 0, 2, false, 1, 0, 3, 3)));
        assertEquals(Scheme.PLANE, Scheme.detect(new Scheme.Traits(3, 0, 5, true, 0, 0, 2, 2)));
        assertEquals(Scheme.HOVER, Scheme.detect(new Scheme.Traits(0, 0, 0, false, 0, 4, 0, 4)));
        assertEquals(Scheme.GROUND, Scheme.detect(new Scheme.Traits(16, 16, 0, false, 0, 0, 2, 2)),
                "a truck with jets is still a truck");
        assertEquals(Scheme.AIRSHIP, Scheme.detect(new Scheme.Traits(0, 0, 0, false, 0, 0, 1, 1)),
                "a levitite board with a pusher flies like an airship");
    }
}
