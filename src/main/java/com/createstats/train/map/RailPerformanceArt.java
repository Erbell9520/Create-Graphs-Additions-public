package com.createstats.train.map;

import java.util.Locale;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RailPerformanceArt {
    private RailPerformanceArt() {
    }

    // drawn to time so a convoy and a hole look like a convoy and a hole
    public static void headways(PanelSurface c, Rect r, RailPerformance.Report report, int color,
                                float scale) {
        if (r.isEmpty() || report == null) {
            return;
        }
        c.card(r, 3.0f, Tokens.CANVAS, Tokens.LINE);
        final float[] gaps = report.headways();
        if (gaps.length == 0) {
            c.textCenterV("Waiting for a train to come round", r.centerX(), r.centerY(),
                    Tokens.INK_3, scale);
            return;
        }

        float total = 0.0f;
        for (final float g : gaps) {
            total += g;
        }
        if (total <= 0.0f) {
            return;
        }
        final float mean = report.meanHeadway();
        final Rect band = r.inset(6.0f, 5.0f, 6.0f, 14.0f);
        if (band.isEmpty()) {
            return;
        }

        float x = band.x;
        for (int i = 0; i < gaps.length; i++) {
            final float w = band.w * (gaps[i] / total);

            final float ratio = mean <= 0.0f ? 1.0f : gaps[i] / mean;
            final int fill = ratio >= 1.6f ? Tokens.CRITICAL
                    : ratio >= 1.25f ? Tokens.CAUTION
                    : ratio <= 0.5f ? Tokens.alpha(Tokens.INK_3, 0.85f)
                    : Tokens.GOOD;
            Shapes.rect(c, x + 0.5f, band.y, Math.max(0.5f, w - 1.0f), band.h,
                    Tokens.alpha(fill, ratio >= 1.25f || ratio <= 0.5f ? 0.34f : 0.20f));
            x += w;
        }

        if (mean > 0.0f) {
            final float meanW = Math.min(band.w, band.w * (mean / total));
            final float sy = r.bottom() - 7.0f;
            final int ink = Tokens.alpha(Tokens.INK_2, 0.85f);
            Shapes.rect(c, band.x, sy, meanW, 1.0f, ink);
            Shapes.rect(c, band.x, sy - 2.5f, 1.0f, 6.0f, ink);
            Shapes.rect(c, band.x + meanW - 1.0f, sy - 2.5f, 1.0f, 6.0f, ink);
            c.text("mean " + RailPerformance.clock(mean), band.x + meanW + 6.0f, r.bottom() - 11.0f,
                    Tokens.alpha(Tokens.INK_3, 0.9f), scale);
        }

        x = band.x;
        tick(c, band, x, color);
        for (final float g : gaps) {
            x += band.w * (g / total);
            tick(c, band, x, color);
        }

        final String worst = "worst gap " + RailPerformance.clock(report.worstHeadway());
        c.textRight(worst, r.right() - 4.0f, r.bottom() - 11.0f,
                report.worstHeadway() > mean * 1.6f ? Tokens.CRITICAL
                        : Tokens.alpha(Tokens.INK_3, 0.9f), scale);
    }

    private static void tick(PanelSurface c, Rect band, float x, int color) {
        final float clamped = Math.min(band.right() - 1.0f, Math.max(band.x, x));
        Shapes.rect(c, clamped - 0.6f, band.y, 1.2f, band.h, Tokens.alpha(color, 0.95f));
        Shapes.circle(c, clamped, band.y + 2.0f, 1.6f, color);
    }

    public static void headwayStrip(PanelSurface c, Rect r, RailPerformance.Report report,
                                    int color) {
        if (r.isEmpty() || report == null || report.headways().length == 0) {
            return;
        }
        final float[] gaps = report.headways();
        float total = 0.0f;
        for (final float g : gaps) {
            total += g;
        }
        if (total <= 0.0f) {
            return;
        }
        final float mean = report.meanHeadway();
        float x = r.x;
        for (final float gap : gaps) {
            final float w = r.w * (gap / total);
            final float ratio = mean <= 0.0f ? 1.0f : gap / mean;
            Shapes.rect(c, x, r.centerY() - 0.6f, Math.max(0.5f, w - 1.2f), 1.2f,
                    Tokens.alpha(ratio >= 1.6f ? Tokens.CRITICAL
                            : ratio >= 1.25f ? Tokens.CAUTION : Tokens.INK_3, 0.8f));
            Shapes.rect(c, x, r.y + 1.0f, 1.0f, r.h - 2.0f, Tokens.alpha(color, 0.9f));
            x += w;
        }
        Shapes.rect(c, Math.min(r.right() - 1.0f, x), r.y + 1.0f, 1.0f, r.h - 2.0f,
                Tokens.alpha(color, 0.9f));
    }

    public static void timeSplit(PanelSurface c, Rect r, RailPerformance.Report report,
                                 float scale) {
        if (r.isEmpty() || report == null) {
            return;
        }
        final Rect bar = r.top(Math.min(10.0f, r.h));
        final float[] shares = {report.movingShare(), report.dwellShare(), report.heldShare(),
                report.faultShare()};
        final int[] colors = {Tokens.GOOD, Tokens.INK_2, Tokens.CAUTION, Tokens.CRITICAL};
        final String[] names = {"moving", "at a platform", "held", "faulted"};

        float x = bar.x;
        for (int i = 0; i < shares.length; i++) {
            final float w = bar.w * shares[i];
            if (w <= 0.4f) {
                continue;
            }
            Shapes.rect(c, x, bar.y, w, bar.h, Tokens.alpha(colors[i], 0.75f));
            x += w;
        }
        Shapes.roundedRectOutline(c, bar, 2.0f, 1.0f, Tokens.LINE);

        if (r.h < 24.0f) {
            return;
        }

        float lx = r.x;
        final float y = bar.bottom() + 5.0f;
        for (int i = 0; i < shares.length; i++) {
            if (shares[i] < 0.02f) {
                continue;
            }
            final String label = Math.round(shares[i] * 100.0f) + "% " + names[i];
            final float w = c.textWidth(label, scale) + 12.0f;
            if (lx + w > r.right()) {
                break;
            }
            Shapes.circle(c, lx + 3.0f, y + 4.0f, 2.4f, colors[i]);
            c.text(label, lx + 9.0f, y, Tokens.INK_3, scale);
            lx += w;
        }
    }

    public static float gradeChip(PanelSurface c, float x, float cy, RailPerformance.Report report,
                                  float scale) {
        final String label = switch (report.grade()) {
            case REGULAR -> "even";
            case UNEVEN -> "uneven";
            case BUNCHED -> "convoy";
            default -> "no data";
        };
        final int color = gradeColor(report.grade());
        final float w = c.textWidth(label, scale) + 12.0f;
        final Rect chip = new Rect(x, cy - 7.0f, w, 14.0f);
        c.card(chip, 7.0f, Tokens.alpha(color, 0.14f), Tokens.alpha(color, 0.45f));
        c.textCenterV(label, chip.centerX(), chip.centerY(), color, scale);
        return w;
    }

    public static int gradeColor(RailPerformance.Grade grade) {
        return switch (grade) {
            case REGULAR -> Tokens.GOOD;
            case UNEVEN -> Tokens.CAUTION;
            case BUNCHED -> Tokens.CRITICAL;
            default -> Tokens.INK_3;
        };
    }

    public static String spread(RailPerformance.Report r) {
        if (!r.measured()) {
            return "";
        }
        return String.format(Locale.ROOT, "gaps vary by %d%%", Math.round(r.cv() * 100.0f));
    }

    public static String excessLine(RailPerformance.Report r) {
        if (!r.measured() || r.excessWait() < 1.0f) {
            return "no time lost to uneven gaps";
        }
        return RailPerformance.clock(r.excessWait()) + " added to the average wait";
    }
}
