package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduLoadModelTest {
    private static final class Machine {
        final int capacity;
        final int burn;
        int stored;

        Machine(int capacity, int burn, int stored) {
            this.capacity = capacity;
            this.burn = burn;
            this.stored = stored;
        }

        int step(int offered) {
            final int taken = Math.max(0, Math.min(offered, capacity - stored));
            stored += taken;
            stored = Math.max(0, stored - burn);
            return taken;
        }
    }

    private static void run(PduLoadModel model, long key, Machine machine, int offer, int ticks) {
        for (int t = 0; t < ticks; t++) {
            model.sample(key, machine.stored, machine.capacity, t);
            model.delivered(key, machine.step(offer));
        }
    }

    @Test
    @DisplayName("it works out what a machine burns without being told, from the buffer alone")
    void learnsTheRealDrawFromTheBufferDelta() {
        final PduLoadModel model = new PduLoadModel();

        final Machine machine = new Machine(8000, 96, 4000);
        run(model, 1L, machine, 96, 40);
        assertEquals(96, model.steady(1L), "the buffer delta is the measurement");
    }

    @Test
    @DisplayName("over-feeding a machine does not inflate what it is thought to cost")
    void generosityDoesNotChangeTheAnswer() {
        final PduLoadModel model = new PduLoadModel();
        final Machine machine = new Machine(8000, 96, 0);
        run(model, 1L, machine, 4000, 60);
        assertEquals(96, model.steady(1L), "it burns 96 however much it is offered");
    }

    @Test
    @DisplayName("starving a machine does not deflate it either, which is what stops the ratchet")
    void starvingDoesNotMakeItLookCheap() {
        final PduLoadModel model = new PduLoadModel();
        final Machine warm = new Machine(8000, 96, 8000);
        run(model, 1L, warm, 96, 40);
        assertEquals(96, model.steady(1L));

        run(model, 1L, warm, 32, 40);
        assertEquals(96, model.steady(1L), "it is still trying to burn 96, and the buffer says so");
    }

    @Test
    @DisplayName("nothing is claimed until there is enough evidence")
    void saysNothingUntilItKnows() {
        final PduLoadModel model = new PduLoadModel();
        model.setTuning(new PduLoadModel.Tuning(10, 0.2f, 0.995f, 512));
        final Machine machine = new Machine(8000, 96, 4000);
        assertEquals(-1, model.steady(1L), "never seen it");
        run(model, 1L, machine, 96, 5);
        assertEquals(-1, model.steady(1L), "seen it, not for long enough");
        assertFalse(model.knows(1L));
        run(model, 1L, machine, 96, 10);
        assertTrue(model.knows(1L));
        assertEquals(96, model.steady(1L));
    }

    @Test
    @DisplayName("a duty cycled machine is priced at what it costs while working, not at its average")
    void holdsThePeakThroughAnIdleSpell() {
        final PduLoadModel model = new PduLoadModel();
        final Machine machine = new Machine(8000, 400, 8000);
        run(model, 1L, machine, 400, 40);
        assertEquals(400, model.steady(1L));

        final Machine idle = new Machine(8000, 0, machine.stored);
        run(model, 1L, idle, 0, 20);
        assertTrue(model.steady(1L) > 300,
                "a second between jobs must not reprice it as free, got " + model.steady(1L));
    }

    @Test
    @DisplayName("a machine that stops for good is eventually forgotten as expensive")
    void thePeakDoesDecayEventually() {
        final PduLoadModel model = new PduLoadModel();
        final Machine machine = new Machine(8000, 400, 8000);
        run(model, 1L, machine, 400, 40);
        final Machine off = new Machine(8000, 0, machine.stored);
        run(model, 1L, off, 0, 600);
        assertTrue(model.steady(1L) < 40,
                "half a minute switched off should not still hold budget, got " + model.steady(1L));
    }

    @Test
    @DisplayName("a device fed by something else as well claims nothing rather than lying")
    void refusesToGuessWhenSomethingElseIsFeedingIt() {
        final PduLoadModel model = new PduLoadModel();

        for (int t = 0; t < 40; t++) {
            model.sample(1L, 100 + t * 50, 8000, t);
            model.delivered(1L, 0);
        }
        assertEquals(-1, model.steady(1L));
    }

    @Test
    @DisplayName("a device with no buffer is never tracked, because there is nothing to difference")
    void ignoresBufferlessDevices() {
        final PduLoadModel model = new PduLoadModel();
        for (int t = 0; t < 40; t++) {
            model.sample(1L, 0, 0, t);
            model.delivered(1L, 16);
        }
        assertEquals(-1, model.steady(1L));
        assertEquals(0, model.size());
    }

    @Test
    @DisplayName("a mined machine's estimate does not outlive it")
    void retainDropsWhatIsGone() {
        final PduLoadModel model = new PduLoadModel();
        run(model, 1L, new Machine(8000, 96, 4000), 96, 40);
        run(model, 2L, new Machine(8000, 200, 4000), 200, 40);
        assertEquals(2, model.size());
        model.retain(Set.of(2L));
        assertEquals(1, model.size());
        assertEquals(-1, model.steady(1L), "whatever is wired here next must not inherit this");
        assertEquals(200, model.steady(2L));
    }

    @Test
    @DisplayName("the map cannot grow without bound on a branch being rewired")
    void tracksNoMoreThanItsCap() {
        final PduLoadModel model = new PduLoadModel();
        model.setTuning(new PduLoadModel.Tuning(10, 0.2f, 0.995f, 16));
        for (long key = 0; key < 100; key++) {
            model.sample(key, 100, 8000, 0);
        }
        assertEquals(16, model.size());
    }

    @Test
    @DisplayName("a stale entry is swept when no device set is to hand")
    void sweepsByAge() {
        final PduLoadModel model = new PduLoadModel();
        model.sample(1L, 100, 8000, 0);
        model.sample(2L, 100, 8000, 500);
        model.sweep(500, 100);
        assertEquals(1, model.size());
        assertEquals(0, model.samples(1L));
    }
}
