package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialPonderReplayTest {
    private static double max(AerialPonderFlight.Recording r, int from, int to) {
        double m = Double.NEGATIVE_INFINITY;
        for (int t = from; t < to; t++) m = Math.max(m, r.y(t));
        return m;
    }

    @Test
    void theHopParksOnThreeBlocks() {
        AerialPonderFlight.Recording r = AerialPonderFlight.fly(AerialPonderChapters.FirstFlight.script());
        double hop = r.restY() + AerialPilot.TAKEOFF_HOP;
        assertEquals(hop, r.held(AerialPonderChapters.FirstFlight.SIT + 1), 1.0e-9, "sitting down holds three blocks up");
        assertTrue(max(r, 0, r.length()) <= hop + 0.05, "no overshoot past the mark: " + (max(r, 0, r.length()) - hop));
        assertEquals(hop, r.y(r.length() - 1), 0.05, "parked on the mark");
    }

    @Test
    void theHopWaitsForItsGasAndTheCaptionsComeInOrder() {
        AerialPonderFlight.Recording r = AerialPonderFlight.fly(AerialPonderChapters.FirstFlight.script());
        int sit = AerialPonderChapters.FirstFlight.SIT;
        int off = AerialPonderChapters.FirstFlight.off(r);
        int parked = AerialPonderChapters.FirstFlight.parked(r);
        assertTrue(r.y(sit - 1) <= r.restY() + 1.0e-9, "on the ground until the pilot sits");

        assertTrue(off - sit >= 80 && off - sit <= 200, "off the ground " + (off - sit) / 20.0 + " s after sitting");
        assertTrue(r.signal(sit, 0) > 0 && r.signal(sit, 1) > 0, "the burners light as the pilot sits");
        assertTrue(AerialPonderChapters.FirstFlight.GAS_CAPTION < off,
                "the caption that says it lifts once there is gas enough comes before it lifts");
        assertTrue(parked < AerialPonderChapters.FirstFlight.END - 80, "parked with time for the payoff: " + parked);
    }

    @Test
    void bothClimbsStopOnTheMark() {
        AerialPonderFlight.Recording r = AerialPonderFlight.fly(AerialPonderChapters.Altitude.script());
        int raise = AerialPonderChapters.Altitude.RAISE;
        int compare = AerialPonderChapters.Altitude.COMPARE;
        int load = AerialPonderChapters.Altitude.LOAD;
        double first = r.held(compare - 1);
        double second = r.held(load - 1);
        assertTrue(first - r.held(raise - 1) > 5.0, "the first climb is a climb: " + (first - r.held(raise - 1)));
        assertTrue(max(r, raise, compare) <= first + 0.05, "first climb overshoots by " + (max(r, raise, compare) - first));
        assertTrue(max(r, compare, load) <= second + 0.05, "second climb overshoots by " + (max(r, compare, load) - second));
        assertTrue(AerialPonderChapters.Altitude.arrived(r) + 90 <= load - 16,
                "the Aerial parks and its caption ends before the cargo comes aboard");
    }

    @Test
    void theLeverFlownTwinGoesPastTheMark() {
        AerialPonderFlight.Recording r = AerialPonderFlight.fly(AerialPonderChapters.Altitude.script());
        AerialPonderFlight.Recording twin = AerialPonderChapters.Altitude.twin(r);
        double mark = r.held(AerialPonderChapters.Altitude.END - 1);
        double peak = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < twin.length(); k++) peak = Math.max(peak, twin.y(k));
        assertTrue(peak - mark > 1.0, "flown by the plan's baseline it really overshoots: " + (peak - mark));
        int past = AerialPonderChapters.Altitude.past(r, twin);
        assertTrue(past < AerialPonderChapters.Altitude.END - 150, "it goes past in time for its caption: " + past);
        assertEquals(r.y(AerialPonderChapters.Altitude.COMPARE), twin.y(0), 0.1, "it starts beside the Aerial");
    }

    @Test
    void theBaselineIsThePlansOwn() {
        AerialPonderFlight.Recording twin = AerialPonderFlight.lever(20 * 150, 40, 60.0);
        double target = twin.held(twin.length() - 1);
        double peak = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < twin.length(); k++) peak = Math.max(peak, twin.y(k));
        assertEquals(11.3, peak - target, 1.0, "overshoot on the plan's climb");
        int settled = twin.length() - 1;
        while (settled > 0 && Math.abs(twin.y(settled) - target) < 0.5) settled--;
        assertEquals(94.0, (settled + 1 - 40) / 20.0, 6.0, "within half a block from, seconds");
    }

    @Test
    void cargoIsAnsweredAtOnceAndTheHeightComesBack() {
        AerialPonderFlight.Recording r = AerialPonderFlight.fly(AerialPonderChapters.Altitude.script());
        int load = AerialPonderChapters.Altitude.LOAD;
        int before = r.signal(load - 1, 0) + r.signal(load - 1, 1);
        int after = 0;
        for (int t = load; t < load + 5; t++) after = Math.max(after, r.signal(t, 0) + r.signal(t, 1));
        assertTrue(after > before, "the burners go up within a quarter second of the load: " + before + " to " + after);
        double held = r.held(load);
        double low = Double.POSITIVE_INFINITY;
        for (int t = load; t < r.length(); t++) low = Math.min(low, r.y(t));
        assertTrue(held - low < 3.0, "a shallow dip: " + (held - low));
        assertEquals(held, r.y(r.length() - 1), 0.5, "back on the mark by the end");
    }

    @Test
    void theEnginesAnswerInAboutASecond() {
        AerialPonderFlight.Recording r = AerialPonderFlight.fly(AerialPonderChapters.Telegraph.script());
        int full = AerialPonderChapters.Telegraph.FULL;
        assertEquals(1.0, r.order(full + 1), 1.0e-9, "Full rung");
        assertTrue(r.engine(full + 1) < 0.7, "not there at once: " + r.engine(full + 1));
        assertEquals(1.0, r.engine(full + 12), 1.0e-9, "there within a little over half a second from Half");

        AerialPonderFlight.Recording s = AerialPonderFlight.fly(new AerialPonderFlight.Script(AerialPonderFlight.Start.HOVERING, 80)
                .tap(10, 1).tap(13, 1).tap(16, 1));
        int reached = 10;
        while (reached < 79 && s.engine(reached) < 1.0 - 1.0e-9) reached++;
        assertEquals(20, reached - 10, 3, "Stop to Full in ticks");
    }

    @Test
    void everyOrderRingsTheBell() {
        AerialPonderFlight.Recording r = AerialPonderFlight.fly(AerialPonderChapters.Telegraph.script());

        assertEquals(8, r.rings(r.length() - 1) - r.rings(0));
        assertTrue(r.rang(AerialPonderChapters.Telegraph.SLOW) || r.rang(AerialPonderChapters.Telegraph.SLOW + 1));
    }

    @Test
    void stopBrakesToRestAndAsternBacksUp() {
        AerialPonderFlight.Recording r = AerialPonderFlight.fly(AerialPonderChapters.Telegraph.script());
        int stop = AerialPonderChapters.Telegraph.STOP;
        int astern = AerialPonderChapters.Telegraph.ASTERN;
        assertTrue(r.airForward(stop - 1) > 5.0, "under way at Full: " + r.airForward(stop - 1));
        boolean braked = false;
        for (int t = stop + 20; t < stop + 120; t++) braked |= r.push(t) < -0.05;
        assertTrue(braked, "the engines brake: the push turns astern");
        assertEquals(0.0, r.airForward(astern - 1), 0.3, "at rest before astern is rung");
        assertTrue(r.airForward(AerialPonderChapters.Telegraph.REST - 1) < -0.5, "backing: " + r.airForward(AerialPonderChapters.Telegraph.REST - 1));
        assertEquals(0.0, r.airForward(r.length() - 1), 0.3, "at rest again at the end");
    }

    @Test
    void everyChapterReplaysQuickly() {
        AerialPonderFlight.fly(AerialPonderChapters.Telegraph.script());
        long t0 = System.nanoTime();
        AerialPonderFlight.fly(AerialPonderChapters.FirstFlight.script());
        AerialPonderFlight.Recording a = AerialPonderFlight.fly(AerialPonderChapters.Altitude.script());
        AerialPonderChapters.Altitude.twin(a);
        AerialPonderFlight.fly(AerialPonderChapters.Telegraph.script());
        double ms = (System.nanoTime() - t0) / 1.0e6;

        assertTrue(ms < 1500.0, "three chapters replayed in " + ms + " ms");
    }
}
