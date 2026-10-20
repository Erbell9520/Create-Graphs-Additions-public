package com.createstats.stormcoil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

// server decides, same rule as the arcs, so the eye never lies about what it'll hit
public final class CoilWatch {
    public static final int MAX = 6;

    public static final int EVERY = 10;

    public static final int MAX_TRACES = 12;

    public enum Verdict {
        TARGET, SPARED, IGNORED;

        public static Verdict of(int ordinal) {
            final Verdict[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    public record Seen(int id, double distSq, CoilTargets.Target target, CoilRules.Person who) {
        public Seen {
            who = who == null ? CoilRules.Person.NOT_A_PLAYER : who;
        }

        public Seen(int id, double distSq, CoilTargets.Target target) {
            this(id, distSq, target, CoilRules.Person.NOT_A_PLAYER);
        }
    }

    public record Entry(int id, Verdict verdict) {
    }

    private CoilWatch() {
    }

    public static boolean spared(CoilRules.Policy policy, CoilTargets.Target t) {
        final boolean converts = policy.rule() == null ? policy.mode() == CoilTargets.Mode.TRANSFORM
                : policy.rule().transform();
        return t.kind() == CoilTargets.Kind.PLAYER || t.kind() == CoilTargets.Kind.GOLEM || t.tamedOrOwned()
                || t.named() || t.leashed() || t.creativeOrSpectator()
                || (t.kind() == CoilTargets.Kind.VILLAGER && !converts);
    }

    public static boolean spared(CoilTargets.Mode mode, CoilTargets.Target t) {
        return spared(CoilRules.Policy.own(mode), t);
    }

    public static Verdict judge(CoilRules.Policy policy, CoilTargets.Target t, CoilRules.Person who, boolean inSight) {
        if (policy.allows(t, who)) {
            return inSight ? Verdict.TARGET : Verdict.IGNORED;
        }
        return spared(policy, t) ? Verdict.SPARED : Verdict.IGNORED;
    }

    public static Verdict judge(CoilTargets.Mode mode, CoilTargets.Target t, boolean inSight) {
        return judge(CoilRules.Policy.own(mode), t, CoilRules.Person.NOT_A_PLAYER, inSight);
    }

    public static List<Entry> pick(CoilTargets.Mode mode, List<Seen> near, Predicate<Seen> sight) {
        return pick(CoilRules.Policy.own(mode), near, sight);
    }

    public static List<Entry> pick(CoilRules.Policy policy, List<Seen> near, Predicate<Seen> sight) {
        final List<Seen> sorted = new ArrayList<>(near);
        sorted.sort(Comparator.comparingDouble(Seen::distSq).thenComparingInt(Seen::id));
        final List<Entry> targets = new ArrayList<>();
        final List<Entry> others = new ArrayList<>();
        int traces = 0;
        for (Seen s : sorted) {
            final Verdict v;
            if (policy.allows(s.target(), s.who())) {
                final boolean traced = targets.size() < MAX && traces < MAX_TRACES;
                if (traced) {
                    traces++;
                }
                v = traced && sight.test(s) ? Verdict.TARGET : Verdict.IGNORED;
            } else {
                v = spared(policy, s.target()) ? Verdict.SPARED : Verdict.IGNORED;
            }
            (v == Verdict.TARGET ? targets : others).add(new Entry(s.id(), v));
        }
        final List<Entry> out = new ArrayList<>(MAX);
        for (Entry e : targets) {
            if (out.size() < MAX) {
                out.add(e);
            }
        }
        for (Entry e : others) {
            if (out.size() < MAX) {
                out.add(e);
            }
        }
        return out;
    }

    public static int[] pack(List<Entry> list) {
        final int[] out = new int[2 * list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[2 * i] = list.get(i).id();
            out[2 * i + 1] = list.get(i).verdict().ordinal();
        }
        return out;
    }

    public static List<Entry> unpack(int[] packed) {
        final List<Entry> out = new ArrayList<>();
        for (int i = 0; i + 1 < packed.length && out.size() < MAX; i += 2) {
            out.add(new Entry(packed[i], Verdict.of(packed[i + 1])));
        }
        return out;
    }

    public static boolean same(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        return Arrays.equals(sortedPairs(a), sortedPairs(b));
    }

    private static long[] sortedPairs(int[] packed) {
        final long[] out = new long[packed.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((long) packed[2 * i] << 32) | (packed[2 * i + 1] & 0xFFFFFFFFL);
        }
        Arrays.sort(out);
        return out;
    }
}
