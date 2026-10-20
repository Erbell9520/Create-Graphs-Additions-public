package com.createstats.stormcoil;

import com.createstats.stormcoil.CoilTargets.Kind;
import com.createstats.stormcoil.CoilTargets.Mode;
import com.createstats.stormcoil.CoilTargets.Target;
import com.createstats.stormcoil.CoilWatch.Entry;
import com.createstats.stormcoil.CoilWatch.Seen;
import com.createstats.stormcoil.CoilWatch.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilWatchTest {
    private static Target plain(Kind kind) {
        return new Target(kind, true, false, false, false, false, false, false);
    }

    private static Target named(Kind kind) {
        return new Target(kind, true, false, true, false, false, false, false);
    }

    private static Seen at(int id, double dist, Target t) {
        return new Seen(id, dist * dist, t);
    }

    @Test
    @DisplayName("the verdict is the arcs' own rule: allowed and seen is a target, never one they spare")
    void verdictIsTheArcsRule() {
        for (Mode mode : Mode.values()) {
            for (Kind kind : Kind.values()) {
                for (boolean seen : new boolean[] {true, false}) {
                    final Target t = plain(kind);
                    final Verdict v = CoilWatch.judge(mode, t, seen);
                    assertEquals(CoilTargets.allows(mode, t) && seen, v == Verdict.TARGET, mode + " " + kind + " " + seen);
                }
            }
        }
        assertEquals(Verdict.SPARED, CoilWatch.judge(Mode.HOSTILE, plain(Kind.PLAYER), true));
        assertEquals(Verdict.SPARED, CoilWatch.judge(Mode.HOSTILE, plain(Kind.GOLEM), true));
        assertEquals(Verdict.SPARED, CoilWatch.judge(Mode.HOSTILE, named(Kind.HOSTILE), true), "a named zombie");
        assertEquals(Verdict.SPARED, CoilWatch.judge(Mode.HOSTILE, plain(Kind.VILLAGER), true), "villagers in Hostile");
        assertEquals(Verdict.TARGET, CoilWatch.judge(Mode.TRANSFORM, plain(Kind.VILLAGER), true), "but not in Transform");
        assertEquals(Verdict.IGNORED, CoilWatch.judge(Mode.HOSTILE, plain(Kind.PIG), true), "a pig is no business of Hostile");
        assertEquals(Verdict.IGNORED, CoilWatch.judge(Mode.HOSTILE, plain(Kind.HOSTILE), false), "a zombie behind a wall");
        assertEquals(Verdict.IGNORED, CoilWatch.judge(Mode.STORM_ONLY, plain(Kind.HOSTILE), true), "Storm only arcs at nothing");
        assertEquals(Verdict.SPARED, CoilWatch.judge(Mode.STORM_ONLY, plain(Kind.PLAYER), true));
    }

    @Test
    @DisplayName("at most six; targets first, then the rest, each nearest first")
    void sixTargetsFirst() {
        final List<Seen> near = new ArrayList<>();
        near.add(at(1, 2, plain(Kind.PLAYER)));
        near.add(at(2, 3, plain(Kind.PIG)));
        near.add(at(3, 14, plain(Kind.HOSTILE)));
        near.add(at(4, 9, plain(Kind.HOSTILE)));
        near.add(at(5, 1, plain(Kind.OTHER)));
        near.add(at(6, 5, plain(Kind.GOLEM)));
        near.add(at(7, 20, plain(Kind.CREEPER)));
        near.add(at(8, 4, named(Kind.HOSTILE)));
        final List<Entry> w = CoilWatch.pick(Mode.HOSTILE, near, s -> true);
        assertEquals(CoilWatch.MAX, w.size());
        assertEquals(List.of(new Entry(4, Verdict.TARGET), new Entry(3, Verdict.TARGET), new Entry(7, Verdict.TARGET),
                new Entry(5, Verdict.IGNORED), new Entry(1, Verdict.SPARED), new Entry(2, Verdict.IGNORED)), w);
    }

    @Test
    @DisplayName("lines of sight are traced only for what the arcs would take, nearest first, and capped")
    void tracesCapped() {
        final List<Seen> near = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            near.add(at(100 + i, 1 + i * 0.5, plain(Kind.HOSTILE)));
        }
        near.add(at(1, 0.5, plain(Kind.PLAYER)));
        final AtomicInteger traced = new AtomicInteger();
        final List<Entry> hidden = CoilWatch.pick(Mode.HOSTILE, near, s -> {
            assertTrue(CoilTargets.allows(Mode.HOSTILE, s.target()), "traced a thing the arcs would not take");
            traced.incrementAndGet();
            return false;
        });
        assertEquals(CoilWatch.MAX_TRACES, traced.get(), "a crowd behind a wall costs a capped number of ray casts");
        assertTrue(hidden.stream().noneMatch(e -> e.verdict() == Verdict.TARGET), "nothing hidden is a target");
        assertEquals(new Entry(1, Verdict.SPARED), hidden.get(0), "the nearest thing, the player, leads the rest");
        traced.set(0);
        final List<Entry> open = CoilWatch.pick(Mode.HOSTILE, near, s -> {
            traced.incrementAndGet();
            return true;
        });
        assertEquals(CoilWatch.MAX, traced.get(), "tracing stops once six targets are found");
        assertTrue(open.stream().allMatch(e -> e.verdict() == Verdict.TARGET));
    }

    @Test
    @DisplayName("synced as pairs; a change of order is not a change, a new thing or a new verdict is")
    void packAndSame() {
        final List<Entry> a = List.of(new Entry(5, Verdict.TARGET), new Entry(9, Verdict.SPARED));
        final int[] p = CoilWatch.pack(a);
        assertEquals(a, CoilWatch.unpack(p));
        final int[] swapped = CoilWatch.pack(List.of(new Entry(9, Verdict.SPARED), new Entry(5, Verdict.TARGET)));
        assertTrue(CoilWatch.same(p, swapped), "order is not a change");
        assertFalse(CoilWatch.same(p, CoilWatch.pack(List.of(new Entry(5, Verdict.TARGET), new Entry(9, Verdict.IGNORED)))));
        assertFalse(CoilWatch.same(p, CoilWatch.pack(List.of(new Entry(5, Verdict.TARGET)))));
        assertTrue(CoilWatch.same(new int[0], new int[0]));
        assertEquals(1, CoilWatch.unpack(new int[] {3, 0, 4}).size(), "a trailing odd int is ignored");
        assertEquals(Verdict.IGNORED, CoilWatch.unpack(new int[] {3, 99}).get(0).verdict(), "an unknown verdict clamps");
    }

    @Test
    @DisplayName("ungoverned, the policy form is the Mode form exactly, for every kind and flag")
    void policyFormIsTheModeForm() {
        for (Mode mode : Mode.values()) {
            for (Kind kind : Kind.values()) {
                for (int bits = 0; bits < 128; bits++) {
                    final Target t = new Target(kind, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                            (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0, (bits & 64) != 0);
                    for (boolean seen : new boolean[] {true, false}) {
                        assertEquals(CoilWatch.judge(mode, t, seen),
                                CoilWatch.judge(CoilRules.Policy.own(mode), t, CoilRules.Person.STRANGER, seen),
                                mode + " " + t + " " + seen);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("governed, the orb's verdict is the house's rule: players by who they are, spared when it holds off")
    void governedVerdicts() {
        final Target player = plain(Kind.PLAYER);
        final CoilRules.Policy strangers = CoilRules.Policy.governed(new CoilRules.Rule(true, false, true, 3), true);
        assertEquals(Verdict.TARGET, CoilWatch.judge(strangers, player, CoilRules.Person.STRANGER, true));
        assertEquals(Verdict.TARGET, CoilWatch.judge(strangers, player, CoilRules.Person.member(2), true),
                "a member below 3");
        assertEquals(Verdict.SPARED, CoilWatch.judge(strangers, player, CoilRules.Person.member(3), true),
                "a member at 3 is held off, and the orb shows it noticing them");
        assertEquals(Verdict.SPARED, CoilWatch.judge(strangers, player, CoilRules.Person.KEYHOLDER, true));
        assertEquals(Verdict.SPARED, CoilWatch.judge(strangers, player, CoilRules.Person.UNKNOWN, true));
        assertEquals(Verdict.IGNORED, CoilWatch.judge(strangers, player, CoilRules.Person.STRANGER, false),
                "a stranger behind a wall is not a target");
        final CoilRules.Policy noPvp = CoilRules.Policy.governed(new CoilRules.Rule(true, false, true, 5), false);
        assertEquals(Verdict.SPARED, CoilWatch.judge(noPvp, player, CoilRules.Person.STRANGER, true), "PvP off");
        assertEquals(Verdict.TARGET, CoilWatch.judge(noPvp, plain(Kind.HOSTILE), CoilRules.Person.NOT_A_PLAYER, true));
        final CoilRules.Policy convert = CoilRules.Policy.governed(new CoilRules.Rule(false, true, false, 0), true);
        assertEquals(Verdict.TARGET, CoilWatch.judge(convert, plain(Kind.VILLAGER), CoilRules.Person.NOT_A_PLAYER, true));
        assertEquals(Verdict.SPARED, CoilWatch.judge(strangers, plain(Kind.VILLAGER), CoilRules.Person.NOT_A_PLAYER, true),
                "a villager is spared while the rule converts nothing");
        assertEquals(Verdict.IGNORED, CoilWatch.judge(convert, plain(Kind.HOSTILE), CoilRules.Person.NOT_A_PLAYER, true));
    }

    @Test
    @DisplayName("governed, the watch list puts a targeted stranger first and traces only what the rule allows")
    void governedPick() {
        final CoilRules.Policy strangers = CoilRules.Policy.governed(new CoilRules.Rule(false, false, true, 0), true);
        final List<Seen> near = List.of(
                new Seen(1, 4, plain(Kind.PLAYER), CoilRules.Person.member(1)),
                new Seen(2, 9, plain(Kind.PLAYER), CoilRules.Person.STRANGER),
                new Seen(3, 1, plain(Kind.HOSTILE), CoilRules.Person.NOT_A_PLAYER));
        final AtomicInteger traced = new AtomicInteger();
        final List<Entry> w = CoilWatch.pick(strangers, near, s -> {
            assertEquals(2, s.id(), "only the stranger is traced: nothing else is allowed");
            traced.incrementAndGet();
            return true;
        });
        assertEquals(1, traced.get());
        assertEquals(List.of(new Entry(2, Verdict.TARGET), new Entry(3, Verdict.IGNORED), new Entry(1, Verdict.SPARED)), w);
        assertEquals(CoilRules.Person.NOT_A_PLAYER, new Seen(9, 1, plain(Kind.PIG)).who(), "the three-part form");
    }

    @Test
    @DisplayName("ties go by id, so a look is the same every time")
    void deterministic() {
        final List<Seen> near = List.of(at(8, 3, plain(Kind.HOSTILE)), at(2, 3, plain(Kind.HOSTILE)));
        final List<Entry> w = CoilWatch.pick(Mode.HOSTILE, near, s -> true);
        assertEquals(2, w.get(0).id());
        assertEquals(8, w.get(1).id());
    }
}
