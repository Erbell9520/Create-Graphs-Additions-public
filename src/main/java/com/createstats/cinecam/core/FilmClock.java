package com.createstats.cinecam.core;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class FilmClock {
    public static final int MAX_HOLD_SECONDS = 2;

    private static final double EPS = 1e-6;

    private FilmClock() {
    }

    public static double ticksPerFrame(int fps, double speed) {
        return 20.0 * speed / Math.max(1, fps);
    }

    public static double timeOf(double start, long n, double ticksPerFrame) {
        return start + n * ticksPerFrame;
    }

    public static long frameAt(double start, double now, double ticksPerFrame) {
        double d = now - start;
        return d < -EPS ? -1 : (long) Math.floor(d / ticksPerFrame + EPS);
    }

    static final double STEP_MARGIN = 1.5;

    @Nullable
    public static Plan plan(double start, double tpf, long last, long tick, float partial, double nextNow) {
        List<Plan> d = draws(start, tpf, last, tick, partial, nextNow, 1);
        return d.isEmpty() ? null : d.get(0);
    }

    public static List<Plan> draws(double start, double tpf, long last, long tick, float partial, double nextNow, int max) {
        double lo = tick - 1;
        double now = lo + partial;
        long first = last + 1;
        long inWindow = (long) Math.ceil((lo - start) / tpf - EPS);
        long lastInWindow = frameAt(start, tick, tpf);
        double step = (nextNow - now) * STEP_MARGIN;
        long coming = step <= EPS ? 0 : Math.max(0, (long) Math.floor((tick - now) / step - EPS));
        long late = -1;
        if (first < inWindow) {
            if (lo - timeOf(start, inWindow - 1, tpf) <= tpf / 2 + EPS) late = inWindow - 1;
            first = inWindow;
        }
        long inTick = Math.max(0, lastInWindow - first + 1);

        if (late >= 0 && inTick + 1 - coming > max) late = -1;
        long n = inTick + (late >= 0 ? 1 : 0);
        if (n == 0) return List.of();
        boolean due = late >= 0 || timeOf(start, first, tpf) <= now + EPS;
        long need = n - coming;
        int count = (int) (need >= 1 ? Math.min(max, need) : due ? 1 : 0);
        List<Plan> out = new ArrayList<>(count);
        long prev = last;
        for (int i = 0; i < count; i++) {
            long f;
            float p;
            boolean isLate = late >= 0 && i == 0;
            if (isLate) {
                f = late;
                p = 0f;
            } else {
                f = first + i - (late >= 0 ? 1 : 0);
                p = (float) Math.max(0.0, Math.min(1.0, timeOf(start, f, tpf) - lo));
            }
            out.add(new Plan(f, p, f - prev - 1, isLate));
            prev = f;
        }
        return out;
    }

    public static List<Plan> catchUp(double start, double tpf, long last, long tick, int max) {
        return draws(start, tpf, last, tick, 1f, tick, max);
    }

    static final double SMOOTH_MARGIN = 1.25;

    public static int smoothFps(int fps, double speed, boolean worldSlowed) {
        double perTick = Math.max(1.0, Math.ceil(fps / (20.0 * speed) - EPS));
        double ticksPerSecond = 20.0 * (worldSlowed && speed < 1.0 ? speed : 1.0);
        return (int) Math.ceil(perTick * ticksPerSecond * SMOOTH_MARGIN - EPS);
    }

    public record Plan(long frame, float partial, long missed, boolean late) {
        public Plan(long frame, float partial, long missed) {
            this(frame, partial, missed, false);
        }
    }

    public static long repeatsBefore(long previous, long frame, int fps) {
        return previous < 0 ? 0 : Math.min((long) MAX_HOLD_SECONDS * fps, Math.max(0, frame - previous - 1));
    }

    public static double seconds(long frames, int fps) {
        return frames / (double) fps;
    }
}
