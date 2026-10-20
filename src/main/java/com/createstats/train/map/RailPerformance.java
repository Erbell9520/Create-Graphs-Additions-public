package com.createstats.train.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// headways not lateness, most player railways have no timetable
@OnlyIn(Dist.CLIENT)
public final class RailPerformance {
    private RailPerformance() {
    }

    public static final int MIN_CROSSINGS = 3;

    public static final float REGULAR_CV = 0.30f;

    public static final float BUNCHED_CV = 0.55f;

    public enum Grade {
        REGULAR,

        UNEVEN,

        BUNCHED,

        UNKNOWN
    }

    public record Report(int lineIndex, String label, Grade grade, int trains, int crossings,
                         float[] headways, float meanHeadway, float worstHeadway,
                         float bestHeadway, float cv, float scheduledWait, float actualWait,
                         float excessWait, float cycleTime, float movingShare, float dwellShare,
                         float heldShare, float faultShare, String cutPoint) {
        public boolean measured() {
            return this.grade != Grade.UNKNOWN;
        }

        public float stoppedShare() {
            return this.dwellShare + this.heldShare + this.faultShare;
        }
    }

    public static List<Report> report(RailMap map) {
        final List<Report> out = new ArrayList<>();
        for (int i = 0; i < map.lines().size(); i++) {
            final Report r = of(map, i);
            if (r != null) {
                out.add(r);
            }
        }
        out.sort((a, b) -> {
            if (a.measured() != b.measured()) {
                return a.measured() ? -1 : 1;
            }
            return Float.compare(b.excessWait(), a.excessWait());
        });
        return out;
    }

    public static Report of(RailMap map, int lineIndex) {
        if (map == null || lineIndex < 0 || lineIndex >= map.lines().size()) {
            return null;
        }
        final RailMap.Line line = map.lines().get(lineIndex);
        if (line.trains.isEmpty() || line.nodes.size() < 2) {
            return null;
        }
        final LineChainage chainage = RailHistory.chainage(map, lineIndex);
        if (chainage.isEmpty() || chainage.total <= 1.0f) {
            return null;
        }

        final int cutStop = Math.max(1, Math.min(chainage.stopCount() - 2, chainage.stopCount() / 2));
        final float cut = chainage.chain[cutStop];
        final RailMap.Node cutNode = chainage.node(cutStop);
        final String cutName = cutNode == null ? "the midpoint" : cutNode.name;

        final List<float[]> crossings = new ArrayList<>();
        final float[] laps = {0.0f, 0.0f};
        final int[] states = new int[4];
        int contributing = 0;

        for (final UUID id : line.trains) {
            final RailHistory.Series series = RailHistory.of(id);
            if (series == null || series.size() < 2) {
                continue;
            }
            contributing++;
            collect(series, chainage.total, cut, crossings, laps, states);
        }

        final float[] headways = headways(crossings);
        final int total = states[0] + states[1] + states[2] + states[3];
        final float cycle = laps[1] > 0.0f ? laps[0] / laps[1] : 0.0f;

        if (headways.length < MIN_CROSSINGS - 1) {
            return new Report(lineIndex, line.label(map), Grade.UNKNOWN, contributing,
                    crossings.size(), headways, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, cycle,
                    share(states[0], total), share(states[1], total), share(states[2], total),
                    share(states[3], total), cutName);
        }

        final Waits w = waits(headways);
        float worst = 0.0f;
        float best = Float.MAX_VALUE;
        for (final float h : headways) {
            worst = Math.max(worst, h);
            best = Math.min(best, h);
        }

        return new Report(lineIndex, line.label(map), grade(w.cv()), contributing,
                crossings.size(), headways, w.mean(), worst, best, w.cv(), w.scheduled(),
                w.actual(), w.excess(), cycle,
                share(states[0], total), share(states[1], total), share(states[2], total),
                share(states[3], total), cutName);
    }

    public record Waits(float mean, float cv, float scheduled, float actual, float excess) {
    }

    public static Waits waits(float[] headways) {
        if (headways == null || headways.length == 0) {
            return new Waits(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        float sum = 0.0f;
        float sumSq = 0.0f;
        for (final float h : headways) {
            sum += h;
            sumSq += h * h;
        }
        final float mean = sum / headways.length;
        if (mean <= 0.0f) {
            return new Waits(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        final float meanSq = sumSq / headways.length;
        final float cv = (float) Math.sqrt(Math.max(0.0f, meanSq - mean * mean)) / mean;
        final float actual = meanSq / (2.0f * mean);
        final float scheduled = mean * 0.5f;
        return new Waits(mean, cv, scheduled, actual, Math.max(0.0f, actual - scheduled));
    }

    public static Grade grade(float cv) {
        return cv <= REGULAR_CV ? Grade.REGULAR : cv <= BUNCHED_CV ? Grade.UNEVEN : Grade.BUNCHED;
    }

    private static void collect(RailHistory.Series series, float total, float cut,
                                List<float[]> crossings, float[] laps, int[] states) {
        float lapStart = Float.NaN;
        for (int i = 0; i < series.size(); i++) {
            final byte state = series.state(i);
            if (state >= 0 && state < states.length) {
                states[state]++;
            }
            if (i == 0) {
                continue;
            }
            final float ta = series.time(i - 1);
            final float tb = series.time(i);
            final float ca = series.chain(i - 1);
            final float cb = series.chain(i);
            if (tb <= ta) {
                continue;
            }
            if (Math.abs(cb - ca) > total * 0.5f) {
                if (!Float.isNaN(lapStart)) {
                    laps[0] += tb - lapStart;
                    laps[1] += 1.0f;
                }
                lapStart = tb;
                continue;
            }
            if (Float.isNaN(lapStart)) {
                lapStart = tb;
            }
            final boolean up = ca < cut && cb >= cut;
            final boolean down = ca >= cut && cb < cut;
            if (!up && !down) {
                continue;
            }
            final float t = ta + (tb - ta) * ((cut - ca) / (cb - ca));
            crossings.add(new float[]{t, up ? 1.0f : 0.0f});
        }
    }

    private static float[] headways(List<float[]> crossings) {
        int up = 0;
        for (final float[] c : crossings) {
            if (c[1] > 0.5f) {
                up++;
            }
        }
        final boolean wantUp = up * 2 >= crossings.size();
        final List<Float> times = new ArrayList<>();
        for (final float[] c : crossings) {
            if ((c[1] > 0.5f) == wantUp) {
                times.add(c[0]);
            }
        }
        times.sort(Float::compare);
        if (times.size() < 2) {
            return new float[0];
        }
        final float[] out = new float[times.size() - 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = times.get(i + 1) - times.get(i);
        }
        return out;
    }

    private static float share(int part, int total) {
        return total <= 0 ? 0.0f : part / (float) total;
    }

    public static String describe(Report r) {
        if (r == null || !r.measured()) {
            return "not enough passings yet";
        }
        return switch (r.grade()) {
            case REGULAR -> "running to an even gap";
            case UNEVEN -> "gaps are uneven";
            case BUNCHED -> "trains are running in convoy";
            default -> "";
        };
    }

    public static String clock(float seconds) {
        if (seconds <= 0.0f || Float.isNaN(seconds)) {
            return "-";
        }
        final int whole = Math.round(seconds);
        if (whole < 60) {
            return whole + "s";
        }
        final int minutes = whole / 60;
        final int rest = whole % 60;
        if (minutes < 60) {
            return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    public static float idealHeadway(Report r) {
        return r == null || r.trains() <= 0 || r.cycleTime() <= 0.0f
                ? 0.0f : r.cycleTime() / r.trains();
    }

    public static float headwayWithOneMore(Report r) {
        return r == null || r.cycleTime() <= 0.0f ? 0.0f : r.cycleTime() / (r.trains() + 1);
    }

    public static Report worst(List<Report> reports) {
        for (final Report r : reports) {
            if (r.measured()) {
                return r;
            }
        }
        return null;
    }

    public static boolean stoppedState(byte state) {
        return state == RailHistory.DOCKED || state == RailHistory.HELD
                || state == RailHistory.FAULT;
    }
}
