package com.createstats.statsmk3.page;

import java.util.Locale;

import com.createstats.efficiency.EfficiencyDimension;
import com.createstats.efficiency.EfficiencyGrader;
import com.createstats.efficiency.EfficiencyKernels;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.EfficiencyTrace;
import com.createstats.efficiency.GradeScale;
import com.createstats.statsmk3.instrument.Plot;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// nothing recomputed here, a second copy of the maths is how the old screen disagreed with its own grader
@OnlyIn(Dist.CLIENT)
public final class EfficiencyMathArt {
    private EfficiencyMathArt() {
    }

    private static final float ROW = 15.0f;

    private static final float KERNEL_ROW = 34.0f;

    private static final float HEADER_H = 34.0f;

    private static final float CARD_TOP = 24.0f;

    public static void paint(PanelSurface c, Rect area, EfficiencyTrace t, EfficiencyScore score,
                             float ageSeconds, float[] trend) {
        if (area.isEmpty()) {
            return;
        }
        if (t == null) {
            c.card(area.top(HEADER_H), Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
            c.textCenterV("Waiting for the first graded sample", area.centerX(),
                    area.y + HEADER_H * 0.5f, Tokens.INK_3, Text.META);
            return;
        }

        header(c, area.top(HEADER_H), t, score, ageSeconds);

        final Rect body = area.below(HEADER_H + Tokens.S2);
        final float leftW = body.w * 0.44f;
        final Rect left = body.left(leftW);
        final Rect right = body.afterLeft(leftW + Tokens.S3);

        float y = left.y;
        final float measuredH = CARD_TOP + (t.resilienceKnown ? 10.0f : 9.0f) * ROW + 8.0f;
        measured(c, new Rect(left.x, y, left.w, measuredH), t);
        y += measuredH + Tokens.S3;
        final float penaltyH = CARD_TOP + 5.0f * ROW + 8.0f;
        penalty(c, new Rect(left.x, y, left.w, penaltyH), t);
        y += penaltyH + Tokens.S3;
        final float integrationH = CARD_TOP + 7.0f * ROW + 8.0f;
        integration(c, new Rect(left.x, y, left.w, integrationH), t);
        y += integrationH + Tokens.S3;

        if (left.bottom() - y >= 70.0f) {
            trend(c, new Rect(left.x, y, left.w, Math.min(220.0f, left.bottom() - y)), trend, score);
        }

        float ry = right.y;
        final float kernelsH = Math.min(right.h * 0.62f,
                CARD_TOP + EfficiencyDimension.values().length * KERNEL_ROW + 8.0f);
        kernels(c, new Rect(right.x, ry, right.w, kernelsH), t);
        ry += kernelsH + Tokens.S3;
        final float meanH = CARD_TOP + 3.0f * ROW + 8.0f;
        mean(c, new Rect(right.x, ry, right.w, meanH), t);
        ry += meanH + Tokens.S3;

        if (right.bottom() - ry >= 120.0f) {
            answer(c, new Rect(right.x, ry, right.w, Math.min(200.0f, right.bottom() - ry)), t, score);
        }
    }

    private static void header(PanelSurface c, Rect a, EfficiencyTrace t, EfficiencyScore score,
                              float ageSeconds) {
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect in = a.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        final float interval = EfficiencyGrader.DEFAULT_INTERVAL_TICKS / 20.0f;

        c.textV(ageSeconds < 0.0f
                        ? String.format(Locale.ROOT, "One sample every %.0f s", interval)
                        : String.format(Locale.ROOT, "This sample %.1f s ago, one every %.0f s",
                                ageSeconds, interval),
                in.x, in.y + 7.0f, Tokens.INK_3, Text.META);
        c.textRightV(String.format(Locale.ROOT, "E %.2f   heading for %.2f   grade %s",
                t.eNew, t.equilibrium, score == null ? "?" : score.grade()),
                in.right(), in.y + 7.0f, gradeColor(score), Text.META);

        final Rect bar = new Rect(in.x, in.bottom() - 4.0f, in.w, 3.0f);
        Shapes.roundedRect(c, bar, 1.5f, Tokens.LINE);
        final float progress = ageSeconds < 0.0f
                ? 0.0f : Math.max(0.0f, Math.min(1.0f, ageSeconds / Math.max(0.1f, interval)));
        if (progress > 0.01f) {
            Shapes.roundedRect(c, bar.withWidth(bar.w * progress), 1.5f, Tokens.ACCENT);
        }
    }

    private static void measured(PanelSurface c, Rect box, EfficiencyTrace t) {
        final Rect in = card(c, box, "MEASURED THIS SAMPLE");
        float y = in.y;
        y = row(c, in, y, "capacity", String.format(Locale.ROOT, "%.0f su", t.capacitySU), Tokens.INK_2);
        y = row(c, in, y, "stress", String.format(Locale.ROOT, "%.0f su", t.stressSU), Tokens.INK_2);
        y = row(c, in, y, "u = stress / capacity", String.format(Locale.ROOT, "%.4f", t.u), Tokens.INK);
        y = row(c, in, y, "peak stress in window",
                String.format(Locale.ROOT, "%.0f su", t.peakStressSU), Tokens.INK_2);
        y = row(c, in, y, "peak u", String.format(Locale.ROOT, "%.4f", t.peakU), Tokens.INK);
        y = row(c, in, y, "rpm cv",
                t.rpmKnown ? String.format(Locale.ROOT, "%.4f", t.rpmCv) : "no channel bound",
                t.rpmKnown ? Tokens.INK : Tokens.INK_3);
        y = row(c, in, y, "stress cv",
                t.stressCvKnown ? String.format(Locale.ROOT, "%.4f", t.stressCv) : "too few samples",
                t.stressCvKnown ? Tokens.INK : Tokens.INK_3);
        y = row(c, in, y, "overstressed now", t.overstressed ? "yes" : "no",
                t.overstressed ? Tokens.CRITICAL : Tokens.GOOD);
        y = row(c, in, y, "longest episode",
                t.resilienceKnown
                        ? String.format(Locale.ROOT, "%.1f s", t.longestOverstressRunSeconds)
                        : "no channel bound",
                t.resilienceKnown ? Tokens.INK : Tokens.INK_3);
        if (t.resilienceKnown) {
            row(c, in, y, "overstressed fraction",
                    String.format(Locale.ROOT, "%.1f%%", t.overstressRate * 100.0f), Tokens.INK_2);
        }
    }

    private static void penalty(PanelSurface c, Rect box, EfficiencyTrace t) {
        final Rect in = card(c, box, "PENALTY, IN POINTS PER SECOND");
        float y = in.y;
        y = note(c, in, y, t.hasPreviousU()
                ? String.format(Locale.ROOT, "du/dt = (%.4f − %.4f) / %.2f s = %+.5f /s",
                        t.u, t.previousU, t.safeDt(), t.duDt)
                : "du/dt: no previous sample, so no transient term", Tokens.INK_2);
        y = note(c, in, y, t.swingApplied < t.swingRaw
                ? String.format(Locale.ROOT, "β₁·(du/dt)² = %.4f, capped from %.2f",
                        t.swingApplied, t.swingRaw)
                : String.format(Locale.ROOT, "β₁·(du/dt)² = %.4f", t.swingApplied),
                t.swingApplied > 0.001f ? Tokens.CAUTION : Tokens.INK_2);
        y = note(c, in, y, t.overstressed
                ? String.format(Locale.ROOT, "severity(%.3f) = %.3f, β₂·severity = %.3f",
                        t.u, t.severity, t.overstressPenalty)
                : "not overstressed, so that term is 0",
                t.overstressed ? Tokens.CRITICAL : Tokens.INK_2);
        y = note(c, in, y, String.format(Locale.ROOT, "Ψ = %.4f pts/s", t.psi), Tokens.INK);
        note(c, in, y, String.format(Locale.ROOT, "Ψ · Δt = %.4f points off",
                t.penaltyPoints), Tokens.INK);
    }

    private static void integration(PanelSurface c, Rect box, EfficiencyTrace t) {
        final Rect in = card(c, box, "INTEGRATION OF THIS STEP");
        float y = in.y;
        y = note(c, in, y, String.format(Locale.ROOT, "Δt = %.2f s over %d ticks%s",
                t.dtSeconds, t.elapsedTicks, t.stepClamped ? ", clamped" : ""), Tokens.INK_2);
        y = note(c, in, y, String.format(Locale.ROOT, "α = %.4f /s, %s",
                t.alpha, t.rising ? "rising: the slow rate" : "falling: the fast rate"), Tokens.INK_2);
        y = note(c, in, y, t.warmStart
                ? String.format(Locale.ROOT, "blend = 1/(n+1) = %.5f, warm start %d of %d",
                        t.relaxBlend, t.observations, EfficiencyGrader.WARMUP_OBSERVATIONS)
                : String.format(Locale.ROOT, "blend = 1 − e^(−α·Δt) = %.5f",
                        t.relaxBlend), Tokens.INK_2);
        y = note(c, in, y, String.format(Locale.ROOT, "%.3f + %.5f · (%.3f − %.3f) = %.3f",
                t.ePrev, t.relaxBlend, t.equilibrium, t.ePrev, t.eRelaxed), Tokens.INK_2);
        y = note(c, in, y, String.format(Locale.ROOT, "%.3f − %.4f penalty = %.3f",
                t.eRelaxed, t.penaltyPoints, t.eBeforeClamp), Tokens.INK_2);
        y = note(c, in, y, String.format(Locale.ROOT, "uptime memory %.4f → %.4f",
                t.uptimeMemoryBefore, t.uptimeMemoryAfter), Tokens.INK_3);
        note(c, in, y, String.format(Locale.ROOT, "E = %.2f, moved %+.3f this step",
                t.eNew, t.eNew - t.ePrev), Tokens.INK);
    }

    private static void kernels(PanelSurface c, Rect box, EfficiencyTrace t) {
        final Rect in = card(c, box, "SIX KERNELS, WITH THESE NUMBERS IN");
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        final float rowH = Math.min(in.h / dims.length, KERNEL_ROW);
        float y = in.y;
        for (final EfficiencyDimension d : dims) {
            final int i = d.ordinal();
            final boolean known = t.isKnown(d);
            final int base = d.getBarColor() | 0xFF000000;

            final String result = known ? String.format(Locale.ROOT, "= %.4f", t.phi[i]) : "unmeasured";
            c.text(Text.truncate(c, d.getSymbol() + "  " + call(d, t),
                            in.w - c.textWidth(result, Text.META) - 8.0f, Text.META),
                    in.x, y, known ? Tokens.INK_2 : Tokens.INK_3, Text.META);
            c.textRight(result, in.right(), y, known ? base : Tokens.INK_3, Text.META);

            final float barY = y + 15.0f;
            final Rect bar = new Rect(in.x, barY, in.w, 3.0f);
            Shapes.roundedRect(c, bar, 1.5f, Tokens.LINE);
            if (known && t.phi[i] > 0.0f) {
                Shapes.roundedRect(c, bar.withWidth(Math.max(3.0f, in.w * Math.min(1.0f, t.phi[i]))),
                        1.5f, base);
            }

            c.text(known
                            ? String.format(Locale.ROOT,
                                    "w' %.3f   term = w'·Φ^κ = %.4f",
                                    t.weightApplied[i], t.term[i])
                            : "dropped from the mean, weight redistributed",
                    in.x, barY + 6.0f, Tokens.INK_3, Text.MICRO);
            y += rowH;
            if (y + rowH * 0.4f > in.bottom()) {
                return;
            }
        }
    }

    private static void mean(PanelSurface c, Rect box, EfficiencyTrace t) {
        final Rect in = card(c, box, "WEIGHTED POWER MEAN");
        float y = in.y;
        y = note(c, in, y, String.format(Locale.ROOT,
                "W = %.3f of %.3f nominal, observability %.0f%%",
                t.totalWeight, EfficiencyDimension.weightSum(), t.observability * 100.0f),
                t.observability < 0.999f ? Tokens.CAUTION : Tokens.INK_2);
        y = note(c, in, y, String.format(Locale.ROOT, "Σ w'·Φ^κ = %.5f",
                t.meanSum), Tokens.INK_2);
        note(c, in, y, String.format(Locale.ROOT, "E_eq = 100 · %.5f^(1/%.1f) = %.2f",
                t.meanSum, EfficiencyGrader.KAPPA, t.equilibrium), Tokens.INK);
    }

    private static void answer(PanelSurface c, Rect box, EfficiencyTrace t, EfficiencyScore score) {
        c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final int color = gradeColor(score);
        final float radius = Math.min(box.w, box.h) * 0.26f;
        final float cx = box.centerX();
        final float cy = box.y + radius + 16.0f;

        Shapes.arc(c, cx, cy, radius, Math.max(4.0f, radius * 0.16f), (float) (-Math.PI * 0.5),
                (float) (Math.PI * 2.0), Tokens.LINE);
        final float frac = Math.max(0.0f, Math.min(1.0f, t.eNew / 100.0f));
        if (frac > 0.005f) {
            Shapes.arc(c, cx, cy, radius, Math.max(4.0f, radius * 0.16f), (float) (-Math.PI * 0.5),
                    (float) (Math.PI * 2.0) * frac, color);
        }
        final String letter = score == null ? "?" : score.grade();
        c.textCenterV(letter, cx, cy - 2.0f, color, 3.0f);
        c.textCenterV(String.format(Locale.ROOT, "%.1f / 100", t.eNew), cx, cy + radius * 0.62f,
                Tokens.INK_2, Text.META);

        final String phrase = score == null ? "" : GradeScale.phraseFor(score.grade());
        if (!phrase.isEmpty()) {
            c.textCenterV(Text.truncate(c, phrase, box.w - Tokens.S4, Text.META), cx,
                    cy + radius + 18.0f, Tokens.INK_3, Text.META);
        }
    }

    private static void trend(PanelSurface c, Rect box, float[] data, EfficiencyScore score) {
        c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("SCORE OVER THIS SESSION"), box.x + Tokens.S3, box.y + 7.0f,
                Tokens.INK_3, Text.MICRO);
        final Rect plot = box.inset(Tokens.S3, CARD_TOP, Tokens.S3, Tokens.S3);
        if (plot.isEmpty()) {
            return;
        }
        if (data == null || data.length < 2) {
            c.textCenterV("Collecting samples", plot.centerX(), plot.centerY(), Tokens.INK_3,
                    Text.META);
            return;
        }
        Plot.grid(c, plot, 3);
        final int n = data.length;
        final float[] xy = new float[n * 2];
        for (int i = 0; i < n; i++) {
            final float v = Math.max(0.0f, Math.min(100.0f, data[i]));
            xy[i * 2] = plot.x + plot.w * (i / (float) (n - 1));
            xy[i * 2 + 1] = plot.bottom() - v / 100.0f * plot.h;
        }
        final int color = gradeColor(score);
        Shapes.areaFill(c, xy, n, plot.bottom(), Tokens.alpha(color, 0.20f), Tokens.alpha(color, 0.0f));
        Shapes.polyline(c, xy, n, Tokens.DATA_STROKE, color);
        Plot.endpoint(c, xy[(n - 1) * 2], xy[(n - 1) * 2 + 1], Tokens.DATA_STROKE, color);
    }

    private static String call(EfficiencyDimension d, EfficiencyTrace t) {
        return switch (d) {
            case UTILIZATION -> String.format(Locale.ROOT, "load(u=%.3f)", t.u);
            case HEADROOM -> String.format(Locale.ROOT, "reserve(peak u=%.3f)", t.peakU);
            case STABILITY -> t.rpmKnown
                    ? String.format(Locale.ROOT, "rhythm(cv=%.4f)", t.rpmCv)
                    : "rhythm(no rpm channel)";
            case UPTIME -> String.format(Locale.ROOT, "uptime memory, tau = %.0f s",
                    EfficiencyGrader.UPTIME_TAU);
            case SMOOTHNESS -> t.stressCvKnown
                    ? String.format(Locale.ROOT, "smoothness(cv=%.4f)", t.stressCv)
                    : "smoothness(no cv yet)";
            case RESILIENCE -> t.resilienceKnown
                    ? String.format(Locale.ROOT, "resilience(%.1f s of %.0f s)",
                            t.longestOverstressRunSeconds, EfficiencyKernels.RESILIENCE_TAU)
                    : "resilience(no episode channel)";
        };
    }

    private static int gradeColor(EfficiencyScore score) {
        if (score == null || score.gradeColor() == 0) {
            return Tokens.INK;
        }
        return score.gradeColor() | 0xFF000000;
    }

    private static Rect card(PanelSurface c, Rect r, String title) {
        c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track(title), r.x + Tokens.S3, r.y + 7.0f, Tokens.INK_3, Text.MICRO);
        return r.inset(Tokens.S3, CARD_TOP, Tokens.S3, Tokens.S2);
    }

    private static float row(PanelSurface c, Rect in, float y, String label, String value, int color) {
        c.text(Text.truncate(c, label, in.w * 0.62f, Text.META), in.x, y, Tokens.INK_3, Text.META);
        c.textRight(value, in.right(), y, color, Text.META);
        return y + ROW;
    }

    private static float note(PanelSurface c, Rect in, float y, String text, int color) {
        c.text(Text.truncate(c, text, in.w, Text.META), in.x, y, color, Text.META);
        return y + ROW;
    }
}
