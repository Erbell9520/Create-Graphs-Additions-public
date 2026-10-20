package com.createstats.chunkloader.look;

import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownVoiceTest {
    private static CrownState state(int held, CrownHold.Phase phase, double store, boolean feed) {
        return CrownMotionTest.state(held, held, phase, store, feed, 0);
    }

    private static CrownState fed(int n) {
        return CrownMotionTest.fed(n);
    }

    private static List<String> run(CrownVoice v, CrownState s, int n) {
        final List<String> out = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            v.tick(s);
            for (int i = 0; i < v.shotCount(); i++) {
                final CrownVoice.Shot shot = v.shot(i);
                final String name = switch (shot.sound()) {
                    case LATCH -> " latch ";
                    case UNLATCH -> " unlatch ";
                    case LAND -> " land ";
                };
                out.add(t + name + shot.pitch());
            }
        }
        return out;
    }

    private static long count(List<String> shots, String sound) {
        return shots.stream().filter(x -> x.contains(" " + sound + " ")).count();
    }

    @Test
    @DisplayName("a crown that comes into earshot says nothing about how it got there, and a steady one hums alone")
    void noReplayAndASteadyHum() {
        final CrownVoice v = new CrownVoice();
        final List<String> shots = run(v, fed(25), 200);
        assertTrue(shots.isEmpty(), "a full crown met for the first time played " + shots);
        assertEquals(CrownVoice.HUM_VOLUME, v.hum(), 1e-6);
    }

    @Test
    @DisplayName("a dark crown is silent")
    void darkIsSilent() {
        final CrownVoice v = new CrownVoice();
        final List<String> shots = run(v, state(0, CrownHold.Phase.DARK, 0.0, false), 100);
        assertTrue(shots.isEmpty(), "a dark crown played " + shots);
        assertEquals(0f, v.hum(), 1e-6);
    }

    @Test
    @DisplayName("each chunk taken is one latch, on the tick its sector latches, never on the tick the count changed")
    void aLatchPerChunk() {
        final CrownVoice v = new CrownVoice();
        run(v, fed(1), 5);

        final List<String> shots = run(v, fed(11), 200);
        assertEquals(10, count(shots, "latch"), "ten chunks taken must be ten latches: " + shots);
        assertTrue(!shots.get(0).startsWith("0 "), "the first latch played on the tick the count changed: " + shots);

        assertEquals(2, shots.stream().filter(x -> x.contains(" latch ") && x.endsWith(" " + CrownVoice.RING_PITCH)).count(),
                "two new rings: " + shots);
    }

    @Test
    @DisplayName("a chunk let go opens its latch; the ULTIMATE yoke locks on the deepest latch; the fold lands on the shutter")
    void unlatchLockAndLand() {
        final CrownVoice v = new CrownVoice();
        run(v, fed(24), 5);
        final List<String> up = run(v, fed(25), 120);
        assertEquals(2, count(up, "latch"), "the 25th sector, then the yoke locking: " + up);
        assertTrue(up.get(up.size() - 1).endsWith(" " + CrownVoice.LOCK_PITCH), "the lock is the last and lowest: " + up);
        final List<String> down = run(v, fed(22), 200);
        assertEquals(3, count(down, "unlatch"), "three chunks let go, three latches opening: " + down);
        assertEquals(0, count(down, "latch"), "letting go latched something: " + down);

        final List<String> fold = run(v, state(0, CrownHold.Phase.DARK, 0.0, false), 120);
        assertEquals(1, fold.size(), "a blackout is the landing and nothing else: " + fold);
        assertEquals(1, count(fold, "land"), "a blackout is the landing and nothing else: " + fold);
        assertEquals(0f, v.hum(), 1e-6, "dark: no hum");
    }

    @Test
    @DisplayName("the hum holds still through a pick, a let-go and a ride-through, though the rings dip and wind")
    void theHumNeverMoves() {
        final CrownVoice v = new CrownVoice();
        run(v, fed(1), 40);

        final List<String> taken = new ArrayList<>();
        for (int t = 0; t < 200; t++) {
            final double store = t < 5 ? 0.48 : Math.min(1.0, 0.48 + (t - 5) / 120.0);
            taken.addAll(run(v, state(2, CrownHold.Phase.FED, store, true), 1));
            assertEquals(CrownVoice.HUM_VOLUME, v.hum(), 1e-6, "a pick moved the hum, at " + t);
        }
        assertEquals(1, count(taken, "latch"), "one chunk taken, one latch: " + taken);
        final List<String> let = run(v, state(1, CrownHold.Phase.FED, 1.0, true), 120);
        assertEquals(1, count(let, "unlatch"), "one chunk let go, one latch opening: " + let);
        for (int t = 0; t <= 100; t++) {
            assertTrue(run(v, state(1, CrownHold.Phase.RIDING, 1.0 - t / 100.0, false), 1).isEmpty(),
                    "a ride-through played a one-shot, at " + t);
            assertEquals(CrownVoice.HUM_VOLUME, v.hum(), 1e-6, "a ride-through moved the hum, at " + t);
        }
    }
}
