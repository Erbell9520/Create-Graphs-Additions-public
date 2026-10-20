package com.createstats.inventory.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public final class ChartPaperLayout {
    public record Column(int sample, boolean gap, boolean night, int stamp) {
        public boolean blank() {
            return sample < 0;
        }
    }

    public record Rule(int x, int hour, boolean major, boolean dayRule) {
    }

    public record Label(int x, String text) {
    }

    private final int width;
    private final int span;
    private final int days;
    private final List<Column> columns;
    private final List<Rule> rules;
    private final List<Integer> skips;
    private final List<Label> labels;

    private ChartPaperLayout(int width, int span, int days, List<Column> columns, List<Rule> rules,
                             List<Integer> skips, List<Label> labels) {
        this.width = width;
        this.span = span;
        this.days = days;
        this.columns = columns;
        this.rules = rules;
        this.skips = skips;
        this.labels = labels;
    }

    public int width() {
        return this.width;
    }

    public int span() {
        return this.span;
    }

    public int days() {
        return this.days;
    }

    public List<Column> columns() {
        return this.columns;
    }

    public List<Rule> rules() {
        return this.rules;
    }

    public List<Integer> skips() {
        return this.skips;
    }

    public List<Label> labels() {
        return this.labels;
    }

    public static int spanIndex(int x, int width, int span) {
        return (int) (((long) (x + 1) * span + width - 1) / width) - 1;
    }

    public static ChartPaperLayout of(ChartHistory history, int width, int days) {
        return of(history, width, days, d -> "DAY " + d);
    }

    public static ChartPaperLayout of(ChartHistory history, int width, int days, IntFunction<String> dayLabel) {
        final int span = Math.max(1, days) * ChartHistory.STEPS_A_DAY;
        final int n = history.size();
        final List<Column> columns = new ArrayList<>(width);
        final int[] index = new int[width];
        for (int x = 0; x < width; x++) {
            final int k = spanIndex(x, width, span);

            final int fromNewest = span - 1 - k;
            final int sample = n - 1 - fromNewest;
            index[x] = sample;
            if (sample < 0) {
                columns.add(new Column(-1, false, false, 0));
            } else {
                final int stamp = history.stamp(sample);
                columns.add(new Column(sample, history.gap(sample), ChartHistory.night(stamp), stamp));
            }
        }
        final List<Rule> rules = new ArrayList<>();
        final List<Integer> skips = new ArrayList<>();
        for (int x = 1; x < width; x++) {
            final int a = index[x - 1];
            final int b = index[x];
            if (a < 0 || b < 0 || a == b) {
                continue;
            }
            boolean jumped = false;
            for (int k = a + 1; k <= b; k++) {
                if (ChartHistory.jump(history.stamp(k - 1), history.stamp(k))) {
                    jumped = true;
                    break;
                }
            }
            if (jumped) {
                skips.add(x);
                continue;
            }
            final int sa = history.stamp(a);
            final int sb = history.stamp(b);
            if (Math.floorDiv(sa, 5) != Math.floorDiv(sb, 5)) {
                final int hour = ChartHistory.hour(sb - Math.floorMod(sb, 5));
                final boolean major = hour % 6 == 0;
                if (days > 1 && !major) {
                    continue;
                }
                rules.add(new Rule(x, hour, major, days > 1 && hour == 6));
            }
        }
        final List<Label> labels = new ArrayList<>();
        final List<int[]> taken = new ArrayList<>();
        if (days <= 1) {
            for (Rule r : rules) {
                if (r.major()) {
                    put(labels, taken, Integer.toString(r.hour()), r.x(), 1, width - 1);
                }
            }
        } else {
            final Map<Integer, int[]> spans = new LinkedHashMap<>();
            for (int x = 0; x < width; x++) {
                final Column c = columns.get(x);
                if (c.blank()) {
                    continue;
                }
                final int d = ChartHistory.day(c.stamp());
                final int at = x;
                final int[] s = spans.computeIfAbsent(d, key -> new int[]{at, at, -1});
                s[1] = x;
                if (s[2] < 0 && ChartHistory.step(c.stamp()) >= 30) {
                    s[2] = x;
                }
            }
            for (Map.Entry<Integer, int[]> e : spans.entrySet()) {
                final int[] s = e.getValue();
                final String text = dayLabel.apply(e.getKey());
                if (s[1] - s[0] < FaceFormat.glassWidth(text, 1) + 4) {
                    continue;
                }
                put(labels, taken, text, s[2] >= 0 ? s[2] : (s[0] + s[1]) / 2, s[0] + 1, s[1]);
            }
        }
        return new ChartPaperLayout(width, span, days, columns, rules, skips, labels);
    }

    private static void put(List<Label> labels, List<int[]> taken, String text, int cx, int lo, int hi) {
        final int wd = FaceFormat.glassWidth(text, 1);
        final int x0 = Math.max(lo, Math.min(hi - wd, cx - wd / 2));
        if (x0 < lo || x0 + wd > hi) {
            return;
        }
        for (int[] t : taken) {
            if (x0 < t[1] + 3 && x0 + wd + 3 > t[0]) {
                return;
            }
        }
        taken.add(new int[]{x0, x0 + wd});
        labels.add(new Label(x0, text));
    }
}
