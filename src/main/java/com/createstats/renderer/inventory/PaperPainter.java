package com.createstats.renderer.inventory;

import com.createstats.inventory.core.ChartHistory;
import com.createstats.inventory.core.ChartPaperLayout;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

// one layout for both papers so they can't disagree
public final class PaperPainter {
    private PaperPainter() {
    }

    private static final int PAPER = 0xFFE8E0CC;
    private static final int GRAIN = 0xFFE2DAC5;
    private static final int GRID = 0xFFCE968C;
    private static final int GRID_HALF = 0xFFBE7064;
    private static final int RULE_MAJOR = 0xFFBA6A60;
    private static final int RULE_MINOR = 0xFFCE968C;
    private static final int RULE_DAY = 0xFF96463E;
    private static final int SKIP = 0xFF3A322E;
    private static final int SPROCKET = 0xFF464038;
    private static final int LABEL = 0xFF784640;
    private static final int LOW = 0xFFE68C28;
    private static final int HIGH = 0xFF3C6EC8;
    private static final int INK = 0xFFB02834;

    public static double vnoise(long x, long y, long seed) {
        long h = (x * 374761393L + y * 668265263L + seed * 2246822519L) & 0xFFFFFFFFL;
        h = ((h ^ (h >>> 13)) * 1274126177L) & 0xFFFFFFFFL;
        return ((h ^ (h >>> 16)) & 0xFFFFL) / 65535.0;
    }

    public static GlassCanvas paint(ChartHistory history, int width, int height, int days, int scale, float low, float high) {
        final GlassCanvas c = new GlassCanvas(width, height);
        c.fill(0, 0, width, height, PAPER);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (vnoise(x / 2, y / 2, 7) > 0.8) {
                    c.put(x, y, GRAIN);
                }
            }
        }
        final int top = 6;
        final int bot = height - 17;
        final ChartPaperLayout layout = ChartPaperLayout.of(history, width, days,
                d -> Component.translatable("create_stats.inventory_display.face.paper.day", Integer.toString(d)).getString());
        for (int x = 0; x < width; x++) {
            final ChartPaperLayout.Column col = layout.columns().get(x);
            if (!col.blank() && col.night()) {
                for (int y = top; y <= bot; y++) {
                    final int p = c.get(x, y);
                    c.put(x, y, 0xFF000000 | (sub(p >> 16, 26) << 16) | (sub(p >> 8, 22) << 8) | sub(p, 8));
                }
            }
        }
        for (int i = 0; i <= 10; i++) {
            final int y = bot - pyRound((bot - top) * i / 10.0);
            final int colour = i % 5 != 0 ? GRID : GRID_HALF;
            for (int x = 0; x < width; x++) {
                if (i % 5 != 0 || x % 2 == 0) {
                    c.put(x, y, colour);
                }
            }
        }
        for (ChartPaperLayout.Rule r : layout.rules()) {
            final int colour = r.dayRule() ? RULE_DAY : r.major() ? RULE_MAJOR : RULE_MINOR;
            for (int y = top; y <= bot; y++) {
                if (r.major() || y % 3 == 0) {
                    c.put(r.x(), y, colour);
                }
            }
        }
        final Set<Integer> skips = new HashSet<>(layout.skips());
        for (int x : layout.skips()) {
            for (int xx : new int[]{x - 1, x + 1}) {
                for (int y = top - 1; y <= bot + 1; y++) {
                    c.put(xx, y, SKIP);
                }
            }
        }
        for (int x = 3; x < width; x += 8) {
            c.fill(x, 1, x + 3, 4, SPROCKET);
            c.fill(x, height - 4, x + 3, height - 1, SPROCKET);
        }
        for (ChartPaperLayout.Label l : layout.labels()) {
            c.text(FacePainter.glassSafe(l.text()), l.x(), height - 14, LABEL, 1);
        }
        dashed(c, low, top, bot, width, LOW);
        dashed(c, high, top, bot, width, HIGH);
        final double full = Math.max(1, scale);
        int prev = Integer.MIN_VALUE;
        for (int x = 0; x < width; x++) {
            final ChartPaperLayout.Column col = layout.columns().get(x);
            if (col.blank() || col.gap()) {
                prev = Integer.MIN_VALUE;
                continue;
            }
            if (skips.contains(x)) {
                prev = Integer.MIN_VALUE;
            }
            final double frac = Math.max(0.0, Math.min(1.0, history.value(col.sample()) / full));
            final int y = bot - pyRound((bot - top) * frac);
            final int from = prev == Integer.MIN_VALUE ? y : Math.min(prev, y);
            final int to = prev == Integer.MIN_VALUE ? y : Math.max(prev, y);
            for (int yy = from; yy <= to; yy++) {
                c.put(x, yy, INK);
                if (yy + 1 <= bot) {
                    final int p = c.get(x, yy + 1);
                    c.put(x, yy + 1, 0xFF000000 | (((p >> 16 & 255) + (INK >> 16 & 255)) / 2 << 16)
                            | (((p >> 8 & 255) + (INK >> 8 & 255)) / 2 << 8) | (((p & 255) + (INK & 255)) / 2));
                }
            }
            prev = y;
        }
        return c;
    }

    private static int pyRound(double v) {
        return (int) Math.rint(v);
    }

    private static int sub(int channel, int by) {
        return Math.max(0, (channel & 255) - by);
    }

    private static void dashed(GlassCanvas c, float v, int top, int bot, int width, int colour) {
        if (Float.isNaN(v)) {
            return;
        }
        final int y = bot - pyRound((bot - top) * Math.max(0.0, Math.min(1.0, v)));
        for (int x = 0; x < width; x += 4) {
            c.fill(x, y, Math.min(width, x + 2), y + 1, colour);
        }
    }

    public static float penFraction(ChartHistory history, int scale) {
        final java.util.OptionalInt v = history.newestValue();
        return v.isPresent() ? (float) Math.max(0.0, Math.min(1.0, v.getAsInt() / (double) Math.max(1, scale))) : 0.0f;
    }
}
