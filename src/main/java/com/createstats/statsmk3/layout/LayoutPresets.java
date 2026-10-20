package com.createstats.statsmk3.layout;

import java.util.ArrayList;
import java.util.List;

public final class LayoutPresets {
    private static final String AREA = "area";
    private static final String LINE = "line";
    private static final String BAR = "bar";
    private static final String ARC = "arc";
    private static final String NUMERIC = "numeric";
    private static final String RING = "ring";
    private static final String SPARK = "spark";

    public interface Generator {
        void build(Mk3Layout out, int channelCount);
    }

    public record Preset(String id, String name, String blurb, int minCols, int minRows,
                         Generator generator) {
        public boolean fits(int cols, int rows) {
            return cols >= this.minCols && rows >= this.minRows;
        }
    }

    private static final List<Preset> ALL = new ArrayList<>();

    static {
        add("single", "Single", "One instrument, the whole face", 1, 1,
                (out, n) -> full(out, AREA, true));

        add("split", "Split", "Two stacked bands", 1, 2, (out, n) -> {
            final int[] y = edges(out.rows(), 2);
            band(out, y[0], y[1], AREA, true);
            band(out, y[1], y[2], BAR, true);
        });

        add("quad", "Quad", "Four equal panes", 2, 2, (out, n) -> {
            final int[] x = edges(out.cols(), 2);
            final int[] y = edges(out.rows(), 2);
            box(out, x[0], y[0], x[1], y[1], AREA, true);
            box(out, x[1], y[0], x[2], y[1], ARC, true);
            box(out, x[0], y[1], x[1], y[2], BAR, true);
            box(out, x[1], y[1], x[2], y[2], NUMERIC, true);
        });

        add("hero", "Hero", "One large reading over three small", 3, 3, (out, n) -> {
            final int split = at(out.rows(), 0.58f);
            band(out, 0, split, AREA, true).value = true;
            final int[] x = edges(out.cols(), 3);
            for (int i = 0; i < 3; i++) {
                box(out, x[i], split, x[i + 1], out.rows(), NUMERIC, true);
            }
        });

        add("ribbon", "Ribbon", "Three full width bands", 1, 3, (out, n) -> {
            final int[] y = edges(out.rows(), 3);
            band(out, y[0], y[1], SPARK, true);
            band(out, y[1], y[2], SPARK, true);
            band(out, y[2], y[3], SPARK, true);
        });

        add("columns", "Columns", "Three tall columns", 3, 1, (out, n) -> {
            final int[] x = edges(out.cols(), 3);
            for (int i = 0; i < 3; i++) {
                box(out, x[i], 0, x[i + 1], out.rows(), LINE, true);
            }
        });

        add("dashboard", "Dashboard", "Hero, side stack and a footer strip", 6, 6, (out, n) -> {
            final int footer = at(out.rows(), 0.74f);
            final int side = at(out.cols(), 0.64f);
            box(out, 0, 0, side, footer, AREA, true).value = true;

            final int mid = at(footer, 0.5f);
            box(out, side, 0, out.cols(), mid, ARC, true);
            box(out, side, mid, out.cols(), footer, RING, true);

            final int[] x = edges(out.cols(), 4);
            for (int i = 0; i < 4; i++) {
                box(out, x[i], footer, x[i + 1], out.rows(), NUMERIC, true);
            }
        });

        add("control", "Control room", "A watched channel, and six on the wall", 6, 6, (out, n) -> {
            final int split = at(out.cols(), 0.5f);
            box(out, 0, 0, split, out.rows(), AREA, true).value = true;
            final int[] x = edges(out.cols() - split, 2);
            final int[] y = edges(out.rows(), 3);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 2; c++) {
                    box(out, split + x[c], y[r], split + x[c + 1], y[r + 1], SPARK, true);
                }
            }
        });

        add("mosaic", "Mosaic", "An even grid sized to the signals you have", 2, 2, (out, n) -> {
            final int want = Math.max(1, Math.min(n <= 0 ? 4 : n, GridSpec.MAX_TILES));
            final float aspect = out.cols() / (float) out.rows();
            final int c = divisorNear(out.cols(), Math.round((float) Math.sqrt(want * aspect)));
            final int r = divisorNear(out.rows(), (want + c - 1) / c);
            final int[] x = edges(out.cols(), c);
            final int[] y = edges(out.rows(), r);
            int placed = 0;
            for (int row = 0; row < r; row++) {
                for (int col = 0; col < c && placed < want; col++, placed++) {
                    box(out, x[col], y[row], x[col + 1], y[row + 1], AREA, true);
                }
            }
        });

        add("wall", "Signal wall", "Every signal as a number, nothing else", 4, 4, (out, n) -> {
            final int want = Math.max(4, Math.min(n <= 0 ? 8 : n, GridSpec.MAX_TILES));
            final float aspect = out.cols() / (float) out.rows();
            final int c = divisorNear(out.cols(), Math.round((float) Math.sqrt(want * aspect)));
            final int r = divisorNear(out.rows(), (want + c - 1) / c);
            final int[] x = edges(out.cols(), c);
            final int[] y = edges(out.rows(), r);
            for (int row = 0; row < r; row++) {
                for (int col = 0; col < c; col++) {
                    box(out, x[col], y[row], x[col + 1], y[row + 1], NUMERIC, true);
                }
            }
        });

        add("focus", "Focus", "One channel large, the rest as a rail", 4, 4, (out, n) -> {
            final int rail = at(out.rows(), 0.78f);
            band(out, 0, rail, LINE, true).value = true;
            final int lanes = Math.max(2, Math.min(4, Math.max(1, n - 1)));
            final int[] x = edges(out.cols(), lanes);
            for (int i = 0; i < lanes; i++) {
                box(out, x[i], rail, x[i + 1], out.rows(), SPARK, false);
            }
        });
    }

    private LayoutPresets() {
    }

    private static void add(String id, String name, String blurb, int minCols, int minRows,
                            Generator gen) {
        ALL.add(new Preset(id, name, blurb, minCols, minRows, gen));
    }

    public static List<Preset> all() {
        return java.util.Collections.unmodifiableList(ALL);
    }

    public static List<Preset> fitting(int cols, int rows) {
        final List<Preset> out = new ArrayList<>();
        for (final Preset p : ALL) {
            if (p.fits(cols, rows)) {
                out.add(p);
            }
        }
        return out;
    }

    public static Preset byId(String id) {
        for (final Preset p : ALL) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return ALL.get(0);
    }

    public static Mk3Layout build(String presetId, int cols, int rows, int channelCount) {
        final Preset preset = byId(presetId);
        final Mk3Layout out = Mk3Layout.onGrid(cols, rows);
        preset.generator().build(out, channelCount);
        out.normalise();
        out.setPreset(preset.id());
        return out;
    }

    public static Mk3Layout defaultFor(int cols, int rows, int channelCount) {
        final String id = cols >= 12 && rows >= 8 ? "dashboard"
                : cols >= 6 && rows >= 6 ? "hero"
                : cols >= 4 && rows >= 4 ? "quad"
                : "split";
        return build(id, cols, rows, channelCount);
    }

    public static Mk3Layout fromTemplate(String templateName, int cols, int rows, int channelCount) {
        final String id = templateName == null ? "split" : switch (templateName.toUpperCase(java.util.Locale.ROOT)) {
            case "SINGLE" -> "single";
            case "QUAD" -> "quad";
            case "HERO" -> "hero";
            case "RIBBON" -> "ribbon";
            default -> "split";
        };
        return build(id, cols, rows, channelCount);
    }

    private static int[] edges(int total, int n) {
        final int[] out = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            out[i] = (int) Math.round(total * (double) i / n);
        }
        for (int i = 1; i <= n; i++) {
            if (out[i] <= out[i - 1]) {
                out[i] = out[i - 1] + 1;
            }
        }
        out[n] = total;
        return out;
    }

    private static int divisorNear(int total, int want) {
        final int target = Math.max(1, Math.min(want, total));
        int best = 1;
        int bestDistance = Integer.MAX_VALUE;
        for (int d = 1; d <= total; d++) {
            if (total % d != 0) {
                continue;
            }
            final int distance = Math.abs(d - target);
            if (distance < bestDistance || (distance == bestDistance && d > best)) {
                best = d;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int at(int total, float fraction) {
        final int v = Math.round(total * fraction);
        return v < 1 ? 1 : Math.min(v, total - 1);
    }

    private static Tile full(Mk3Layout out, String instrument, boolean label) {
        return box(out, 0, 0, out.cols(), out.rows(), instrument, label);
    }

    private static Tile band(Mk3Layout out, int y0, int y1, String instrument, boolean label) {
        return box(out, 0, y0, out.cols(), y1, instrument, label);
    }

    private static Tile box(Mk3Layout out, int x0, int y0, int x1, int y1, String instrument,
                            boolean label) {
        final Tile t = new Tile(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        t.instrumentId = instrument;
        t.label = label;
        out.addRaw(t);
        return t;
    }
}
