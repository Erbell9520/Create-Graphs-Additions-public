package com.createstats.vccmk4.air.core;

import com.createstats.vcu.core.Axis6;
import com.createstats.vcu.core.BodyFrame;
import com.createstats.vcu.core.V3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrustSplitTest {
    private static final BodyFrame FRAME = BodyFrame.of(new V3(0.0, 0.0, -1.0), V3.UNIT_Y, V3.ZERO);
    private static final V3 FORWARD = new V3(0.0, 0.0, -1.0);

    private static ThrustSplit.Actuator prop(V3 at, V3 dir, double lo, double hi) {
        return new ThrustSplit.Actuator(ThrustSplit.column(FRAME, List.of(at), List.of(dir), new double[] {1.0}), lo, hi);
    }

    private static double[] care(double... c) {
        return c;
    }

    @Test
    void anExactBoxNeedNotHoldZeroAndThePropsTakeTheRest() {
        double[] col = ThrustSplit.column(FRAME, List.of(new V3(0.0, -2.0, 8.0)), List.of(FORWARD), new double[] {1.0});
        List<ThrustSplit.Actuator> a = List.of(
                prop(new V3(0.0, -2.0, 6.0), FORWARD, -500.0, 1000.0),
                new ThrustSplit.Actuator(col, 380.0, 420.0, true));
        double[] target = new double[Axis6.COUNT];
        target[Axis6.FORWARD.index()] = 100.0;
        ThrustSplit.Result r = ThrustSplit.solve(a, target, care(1, 0, 1, 0, 0, 1), 6600.0, 66000.0);
        assertEquals(380.0, r.force()[1], 1.0e-6, "the thruster only as far down as its ramp reaches");
        assertEquals(-280.0, r.force()[0], 1.0, "the governor takes up the rest");
        assertEquals(100.0, r.achieved()[Axis6.FORWARD.index()], 1.0);

        List<ThrustSplit.Actuator> plain = List.of(a.get(0), new ThrustSplit.Actuator(col, 380.0, 420.0));
        assertTrue(ThrustSplit.solve(plain, target, care(1, 0, 1, 0, 0, 1), 6600.0, 66000.0).force()[1] < 380.0);
    }

    @Test
    void twoEnginesShareTheForwardPush() {
        List<ThrustSplit.Actuator> a = List.of(
                prop(new V3(-3.0, -2.0, 8.0), FORWARD, -500.0, 1000.0),
                prop(new V3(3.0, -2.0, 8.0), FORWARD, -500.0, 1000.0));
        double[] target = new double[Axis6.COUNT];
        target[Axis6.FORWARD.index()] = 1000.0;
        ThrustSplit.Result r = ThrustSplit.solve(a, target, care(1, 0, 1, 0, 0, 1), 6600.0, 66000.0);
        assertEquals(500.0, r.force()[0], 1.0);
        assertEquals(500.0, r.force()[1], 1.0);
        assertEquals(1000.0, r.achieved()[Axis6.FORWARD.index()], 1.0);
    }

    @Test
    void turningUsesTheEnginesAgainstEachOther() {
        List<ThrustSplit.Actuator> a = List.of(
                prop(new V3(-3.0, -2.0, 8.0), FORWARD, -500.0, 1000.0),
                prop(new V3(3.0, -2.0, 8.0), FORWARD, -500.0, 1000.0));
        double[] target = new double[Axis6.COUNT];
        target[Axis6.YAW.index()] = 1200.0;
        ThrustSplit.Result r = ThrustSplit.solve(a, target, care(1, 0, 1, 0, 0, 1), 6600.0, 66000.0);

        assertTrue(r.force()[0] > 0.0 && r.force()[1] < 0.0);
        assertEquals(1200.0, r.achieved()[Axis6.YAW.index()], 5.0);
        assertEquals(0.0, r.achieved()[Axis6.FORWARD.index()], 5.0);
    }

    @Test
    void aGondolaEngineBelowTheCentreStillPushes() {
        List<ThrustSplit.Actuator> a = List.of(prop(new V3(0.0, -6.0, 6.0), FORWARD, 0.0, 1000.0));
        double[] target = new double[Axis6.COUNT];
        target[Axis6.FORWARD.index()] = 800.0;
        ThrustSplit.Result free = ThrustSplit.solve(a, target, care(1, 0, 1, 0, 0, 1), 6600.0, 66000.0);
        assertEquals(800.0, free.force()[0], 1.0);
        ThrustSplit.Result strict = ThrustSplit.solve(a, target, care(1, 0, 1, 1, 1, 1), 6600.0, 66000.0);
        assertTrue(strict.force()[0] < 100.0, "weighted in, pitch forbids the ship its own engine: " + strict.force()[0]);
    }

    @Test
    void anAxisNobodyCanReachIsLeftAlone() {
        List<ThrustSplit.Actuator> a = List.of(prop(new V3(0.0, 0.0, 8.0), FORWARD, 0.0, 1000.0));
        double[] target = new double[Axis6.COUNT];
        target[Axis6.RIGHT.index()] = 500.0;
        ThrustSplit.Result r = ThrustSplit.solve(a, target, care(1, 0, 1, 0, 0, 1), 6600.0, 66000.0);
        assertEquals(0.0, r.force()[0], 1.0);
    }

    @Test
    void authorityIsWhatTheBoxesAllow() {
        List<ThrustSplit.Actuator> a = List.of(
                prop(new V3(-3.0, 0.0, 8.0), FORWARD, -500.0, 1000.0),
                prop(new V3(3.0, 0.0, 8.0), FORWARD, -500.0, 1000.0));
        assertEquals(2000.0, ThrustSplit.authority(a, true)[Axis6.FORWARD.index()], 1.0e-6);
        assertEquals(1000.0, ThrustSplit.authority(a, false)[Axis6.FORWARD.index()], 1.0e-6);
    }
}
