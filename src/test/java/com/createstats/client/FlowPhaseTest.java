package com.createstats.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowPhaseTest {
    private static final double TURN = Math.PI * 2.0;

    private static double gap(double a, double b) {
        final double raw = Math.abs(a - b) % TURN;
        return Math.min(raw, TURN - raw);
    }

    @Test
    @DisplayName("a change of speed accelerates the wave rather than moving it")
    void changingSpeedDoesNotJump() {
        final FlowPhase phase = new FlowPhase();

        final double now = 1_234_567.0;
        final double slow = 1.5 * 0.05;
        final double fast = 3.9 * 0.05;

        final double before = phase.advance(now, slow);
        final double after = phase.advance(now + 0.05, fast);

        assertTrue(gap(before, after) < 0.01,
                "one twentieth of a tick at the new speed, and nothing else: was " + gap(before, after));
    }

    @Test
    @DisplayName("at a steady speed it is exactly the closed form, so segments agree without talking")
    void steadySpeedMatchesTheClosedForm() {
        final double rate = 3.1 * 0.05;
        final double start = 900_000.0;

        final FlowPhase early = new FlowPhase();
        early.advance(start, rate);

        double atEnd = 0.0;
        for (int frame = 1; frame <= 40; frame++) {
            atEnd = early.advance(start + frame * 0.5, rate);
        }

        final FlowPhase late = new FlowPhase();
        final double fresh = late.advance(start + 20.0, rate);

        assertTrue(gap(atEnd, fresh) < 1.0e-6,
                "a run must not show a seam where one chunk loaded later than the next");
    }

    @Test
    @DisplayName("it travels at the speed it is given")
    void itTravelsAtTheGivenSpeed() {
        final FlowPhase phase = new FlowPhase();
        final double rate = 0.1;
        final double start = phase.advance(1_000.0, rate);
        final double after = phase.advance(1_010.0, rate);

        assertEquals(1.0, gap(start, after), 1.0e-9,
                "ten ticks at a tenth of a radian each is one radian");
    }

    @Test
    @DisplayName("a world clock that has gone backwards re-anchors instead of running the wave in reverse")
    void aClockThatWentBackwardsReAnchors() {
        final FlowPhase phase = new FlowPhase();
        final double rate = 0.2;
        phase.advance(50_000.0, rate);
        final double after = phase.advance(10.0, rate);

        assertEquals(wrap(10.0 * rate), after, 1.0e-9);
    }

    @Test
    @DisplayName("the phase stays within one turn, so a long-lived world keeps its resolution")
    void thePhaseStaysWithinOneTurn() {
        final FlowPhase phase = new FlowPhase();
        double value = phase.advance(987_654_321.0, 0.195);
        for (int frame = 1; frame <= 200; frame++) {
            value = phase.advance(987_654_321.0 + frame, 0.195);
            assertTrue(value >= 0.0 && value < TURN, "out of range at frame " + frame + ": " + value);
        }
    }

    @Test
    @DisplayName("drawing the same frame twice does not advance it twice")
    void repeatedFramesDoNotDoubleAdvance() {
        final FlowPhase phase = new FlowPhase();
        phase.advance(2_000.0, 0.15);
        final double once = phase.advance(2_001.0, 0.15);
        final double twice = phase.advance(2_001.0, 0.15);

        assertEquals(once, twice, 1.0e-12);
    }

    private static double wrap(double radians) {
        final double turns = radians % TURN;
        return turns < 0.0 ? turns + TURN : turns;
    }
}
