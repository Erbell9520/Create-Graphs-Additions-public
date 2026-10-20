package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StopRules {
    public interface Cargo {
        default boolean known() { return true; }

        long items(String item, boolean stacks);

        long fluidMb(String fluid);

        int idleTicks();

        boolean linkPowered(String first, String second);

        default long dayTime() {
            return -1L;
        }

        default int seated() {
            return 0;
        }

        default boolean stopPowered() {
            return false;
        }

        default boolean docked() {
            return false;
        }
    }

    public static final Cargo EMPTY = new Cargo() {
        @Override
        public long items(String item, boolean stacks) {
            return 0;
        }

        @Override
        public long fluidMb(String fluid) {
            return 0;
        }

        @Override
        public int idleTicks() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean linkPowered(String first, String second) {
            return false;
        }
    };

    public record Bar(StopCondition condition, double fraction, boolean met, String now) {
    }

    public record Verdict(boolean go, boolean timedOut, List<List<Bar>> bars) {
    }

    private StopRules() {
    }

    public static Verdict evaluate(Stop stop, int waitedTicks, Cargo cargo) {
        List<List<Bar>> bars = new ArrayList<>(stop.rules().size());
        boolean go = false;
        for (List<StopCondition> alt : stop.rules()) {
            List<Bar> row = new ArrayList<>(alt.size());
            boolean all = !alt.isEmpty();
            for (StopCondition c : alt) {
                Bar b = bar(c, waitedTicks, cargo);
                row.add(b);
                all &= b.met();
            }
            bars.add(row);
            go |= all;
        }
        if (stop.rules().isEmpty()) go = true;
        boolean timedOut = false;
        if (!go && stop.leaveAfter() > 0 && waitedTicks >= stop.leaveAfter() * 20) {
            go = true;
            timedOut = true;
        }
        return new Verdict(go, timedOut, bars);
    }

    static Bar bar(StopCondition c, int waitedTicks, Cargo cargo) {
        if (!cargo.known() && (c instanceof StopCondition.Items || c instanceof StopCondition.Fluid || c instanceof StopCondition.Idle)) {
            return new Bar(c, 0.0, false, "cargo has not been measured");
        }
        return switch (c) {
            case StopCondition.Wait w -> {
                int need = w.seconds() * 20;
                double f = need <= 0 ? 1.0 : Math.min(1.0, waitedTicks / (double) need);
                yield new Bar(c, f, waitedTicks >= need, String.format(Locale.ROOT, "%d of %d s",
                        Math.min(waitedTicks, need) / 20, w.seconds()));
            }
            case StopCondition.Idle i -> {
                int need = i.seconds() * 20;
                int idle = Math.min(cargo.idleTicks(), 1_000_000);
                double f = Math.min(1.0, idle / (double) Math.max(1, need));
                yield new Bar(c, f, idle >= need, String.format(Locale.ROOT, "idle %d of %d s", Math.min(idle, need) / 20, i.seconds()));
            }
            case StopCondition.Items i -> {
                long have = cargo.items(i.item(), i.stacks());
                yield threshold(c, i.op(), have, i.amount(), i.stacks() ? "stacks" : "items");
            }
            case StopCondition.Fluid f -> {
                long buckets = cargo.fluidMb(f.fluid()) / 1000L;
                yield threshold(c, f.op(), buckets, f.buckets(), "buckets");
            }
            case StopCondition.Link l -> {
                boolean on = cargo.linkPowered(l.first(), l.second());
                boolean met = on == l.powered();
                yield new Bar(c, met ? 1.0 : 0.0, met, on ? "powered" : "unpowered");
            }
            case StopCondition.TimeOfDay t -> {
                long day = cargo.dayTime();
                if (day < 0) yield new Bar(c, 0.0, false, "the time is not known");
                boolean met = t.doneAt(day);
                int left = t.ticksUntil(day);
                double f = met ? 1.0 : Math.max(0.0, Math.min(0.999, 1.0 - left / (double) t.rotationTicks()));
                yield new Bar(c, f, met, met ? "now" : String.format(Locale.ROOT, "%s at %d:%02d", clock(left), t.hour(), t.minute()));
            }
            case StopCondition.Players pl -> {
                int n = cargo.seated();
                boolean met = pl.orMore() ? n >= pl.count() : n == pl.count();
                double f = met ? 1.0 : (pl.count() <= 0 ? 0.0 : Math.min(0.999, n / (double) pl.count()));
                yield new Bar(c, f, met, String.format(Locale.ROOT, "%d of %d%s seated", n, pl.count(), pl.orMore() ? " or more" : ""));
            }
            case StopCondition.Powered pw -> {
                boolean on = cargo.stopPowered();
                yield new Bar(c, on ? 1.0 : 0.0, on, on ? "powered" : "unpowered");
            }
            case StopCondition.Docked d -> {
                boolean on = cargo.docked();
                yield new Bar(c, on ? 1.0 : 0.0, on, on ? "docked" : "not docked");
            }
        };
    }

    static String clock(int ticks) {
        int s = (ticks + 19) / 20;
        if (s < 60) return "in " + s + " s";
        int m = (s + 59) / 60;
        if (m < 60) return "in " + m + " min";
        return String.format(Locale.ROOT, "in %d h %d min", m / 60, m % 60);
    }

    public static final class Progress {
        private final Stop stop;
        private final long arrived;
        private final int[] current;
        private final long[] since;

        public Progress(Stop stop, long arrived) {
            this.stop = stop;
            this.arrived = arrived;
            int n = stop.rules().size();
            this.current = new int[n];
            this.since = new long[n];
            java.util.Arrays.fill(since, arrived);
        }

        public Stop stop() {
            return stop;
        }

        public long arrived() {
            return arrived;
        }

        public int current(int column) {
            return column >= 0 && column < current.length ? current[column] : 0;
        }

        public record Saved(long arrived, int[] current, long[] since) {
            public Saved { current = current.clone(); since = since.clone(); }
            @Override public int[] current() { return current.clone(); }
            @Override public long[] since() { return since.clone(); }
        }

        public Saved save() { return new Saved(arrived, current, since); }

        public static Progress restore(Stop stop, Saved saved) {
            Progress out = new Progress(stop, saved.arrived());
            int[] at = saved.current(); long[] began = saved.since();
            for (int i = 0; i < out.current.length && i < at.length && i < began.length; i++) {
                out.current[i] = Math.max(0, Math.min(stop.rules().get(i).size(), at[i]));
                out.since[i] = Math.max(saved.arrived(), began[i]);
            }
            return out;
        }

        public Verdict tick(long now, Cargo cargo) {
            List<List<StopCondition>> rules = stop.rules();
            List<List<Bar>> bars = new ArrayList<>(rules.size());
            boolean go = rules.isEmpty();
            for (int col = 0; col < rules.size(); col++) {
                List<StopCondition> alt = rules.get(col);

                if (current[col] < alt.size()) {
                    Bar b = bar(alt.get(current[col]), (int) Math.max(0L, now - since[col]), cargo);
                    if (b.met()) {
                        current[col]++;
                        since[col] = now;
                    }
                }
                List<Bar> row = new ArrayList<>(alt.size());
                for (int k = 0; k < alt.size(); k++) {
                    StopCondition cnd = alt.get(k);
                    if (k < current[col]) {
                        row.add(new Bar(cnd, 1.0, true, "done"));
                    } else if (k == current[col]) {
                        row.add(bar(cnd, (int) Math.max(0L, now - since[col]), cargo));
                    } else {
                        Bar later = bar(cnd, 0, cargo);
                        row.add(new Bar(cnd, 0.0, false, "then " + later.now()));
                    }
                }
                bars.add(row);
                go |= !alt.isEmpty() && current[col] >= alt.size();
            }
            boolean timedOut = false;
            if (!go && stop.leaveAfter() > 0 && now - arrived >= stop.leaveAfter() * 20L) {
                go = true;
                timedOut = true;
            }
            return new Verdict(go, timedOut, bars);
        }
    }

    private static Bar threshold(StopCondition c, StopCondition.Op op, long have, long target, String unit) {
        boolean met = op.test(have, target);
        double f;
        switch (op) {
            case GREATER -> f = Math.min(1.0, have / (double) (target + 1));
            case LESS -> f = have < target ? 1.0 : (have <= 0 ? 1.0 : Math.min(1.0, target / (double) have));
            default -> f = have == target ? 1.0 : Math.min(have, target) / (double) Math.max(1L, Math.max(have, target));
        }
        return new Bar(c, met ? 1.0 : Math.max(0.0, Math.min(0.999, f)), met,
                String.format(Locale.ROOT, "%d %s %d %s", have, op.symbol, target, unit));
    }
}
