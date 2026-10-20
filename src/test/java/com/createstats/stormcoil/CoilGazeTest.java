package com.createstats.stormcoil;

import com.createstats.stormcoil.CoilGaze.Kind;
import com.createstats.stormcoil.CoilGaze.Look;
import com.createstats.stormcoil.CoilGaze.Strand;
import com.createstats.stormcoil.CoilWatch.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilGazeTest {
    private static final int[] NONE = new int[0];

    private static double angle(Strand s, double x, double y, double z) {
        final double l = Math.sqrt(x * x + y * y + z * z);
        return Math.acos(Math.max(-1, Math.min(1, (s.x() * x + s.y() * y + s.z() * z) / l)));
    }

    private static Strand of(CoilGaze g, int id) {
        for (Strand s : g.strands(1f)) {
            if (s.id() == id) {
                return s;
            }
        }
        throw new AssertionError("no strand on " + id);
    }

    @Test
    @DisplayName("nothing near: three strands wander, grown out of the core, never jumping")
    void wanderAlone() {
        final CoilGaze g = new CoilGaze();
        List<Strand> before = null;
        for (long t = 0; t < 400; t++) {
            g.tick(t, List.of(), NONE, false);
            final List<Strand> now = g.strands(1f);
            assertEquals(CoilGaze.MIN, now.size());
            for (Strand s : now) {
                assertEquals(Kind.FREE, s.kind());
                assertEquals(1.0, Math.sqrt(s.x() * s.x() + s.y() * s.y() + s.z() * s.z()), 1e-9);
            }
            if (before != null) {
                for (int i = 0; i < now.size(); i++) {
                    final Strand a = before.get(i);
                    final Strand b = now.get(i);
                    assertTrue(angle(b, a.x(), a.y(), a.z()) < 0.2, "a free strand jumped at " + t);
                }
            }
            before = now;
        }
        assertTrue(g.strands(1f).get(0).weight() > 0.99, "grown in");
        final double drift = angle(g.strands(1f).get(0), CoilGaze.wander(0, 0)[0], CoilGaze.wander(0, 0)[1],
                CoilGaze.wander(0, 0)[2]);
        assertTrue(drift > 0.05, "a wandering strand stood still");
    }

    @Test
    @DisplayName("a target pulls a strand onto it fast; a spared thing pulls one slowly")
    void targetFastSparedSlow() {
        final CoilGaze g = new CoilGaze();

        final List<Look> first = List.of(new Look(10, Verdict.TARGET, 0, -2, -6), new Look(20, Verdict.SPARED, 3, -1, 5));
        for (long t = 0; t < 200; t++) {
            g.tick(t, first, NONE, false);
        }
        final List<Look> looks = List.of(new Look(10, Verdict.TARGET, 6, -2, 0), new Look(20, Verdict.SPARED, -5, -1, 3));
        for (long t = 200; t < 206; t++) {
            g.tick(t, looks, NONE, false);
        }
        final double target = angle(of(g, 10), 6, -2, 0);
        final double spared = angle(of(g, 20), -5, -1, 3);
        assertTrue(target < 0.15, "the target's strand is not on it after six ticks: " + target);
        assertTrue(spared > target, "the spared strand moved as fast as the target's");
        assertEquals(Kind.TARGET, of(g, 10).kind());
        assertEquals(Kind.SPARED, of(g, 20).kind());
        for (long t = 206; t < 320; t++) {
            g.tick(t, looks, NONE, false);
        }
        assertTrue(angle(of(g, 20), -5, -1, 3) < 0.45, "the spared strand touches toward its thing, drifting");
    }

    @Test
    @DisplayName("a strand follows a walking mob smoothly")
    void follows() {
        final CoilGaze g = new CoilGaze();
        double worst = 0;
        for (long t = 0; t < 200; t++) {
            final double a = t * 0.03;
            final Look l = new Look(5, Verdict.TARGET, Math.cos(a) * 8, -1.5, Math.sin(a) * 8);
            g.tick(t, List.of(l), NONE, false);
            if (t > 10) {
                worst = Math.max(worst, angle(of(g, 5), l.x(), l.y(), l.z()));
            }
        }
        assertTrue(worst < 0.15, "the strand lags its mob by " + worst + " rad");
    }

    @Test
    @DisplayName("one strand per watched thing up to six; back to three, faded, when they go")
    void countFollowsTheWatch() {
        final CoilGaze g = new CoilGaze();
        final List<Look> six = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            six.add(new Look(100 + i, i % 2 == 0 ? Verdict.TARGET : Verdict.IGNORED, Math.cos(i), 0.2, Math.sin(i)));
        }
        for (long t = 0; t < 20; t++) {
            g.tick(t, six, NONE, false);
        }
        assertEquals(CoilGaze.MAX, g.strands(1f).size(), "never more than six");
        for (long t = 20; t < 40; t++) {
            g.tick(t, List.of(six.get(0)), NONE, false);
        }
        assertEquals(CoilGaze.MIN, g.strands(1f).size());
        assertEquals(1, g.strands(1f).stream().filter(s -> s.id() == 100).count(), "the one left keeps its strand");
    }

    @Test
    @DisplayName("up: the strands not on a target point at the sky; a wind-up sends the free ones to what it aimed at")
    void upAndWindUp() {
        final CoilGaze g = new CoilGaze();
        for (long t = 0; t < 60; t++) {
            g.tick(t, List.of(new Look(1, Verdict.IGNORED, 5, 0, 0)), NONE, true);
        }
        for (Strand s : g.strands(1f)) {
            assertTrue(s.y() > 0.75, "a strand looks away from the sky in a storm: " + s);
        }
        final CoilGaze w = new CoilGaze();
        final Look target = new Look(7, Verdict.TARGET, 0, -1, 9);
        for (long t = 0; t < 40; t++) {
            w.tick(t, List.of(target), new int[] {7}, false);
        }
        for (Strand s : w.strands(1f)) {
            assertTrue(angle(s, 0, -1, 9) < 0.5, "a strand looks away from the aimed target: " + s);
        }
        assertTrue(of(w, 7).aimed(), "the target's own strand knows it is aimed at");
    }
}
