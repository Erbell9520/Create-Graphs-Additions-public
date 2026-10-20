package com.createstats.statsmk3.paint;

import java.util.Locale;

import com.createstats.efficiency.EfficiencyDimension;
import com.createstats.efficiency.EfficiencyGrader;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.EfficiencyTrace;
import com.createstats.efficiency.GradeScale;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// far away it's just the letter
@OnlyIn(Dist.CLIENT)
public final class EfficiencyFacePainter {
    private EfficiencyFacePainter() {
    }

    public static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();

        final EfficiencyScore score = model.efficiency;
        if (score == null || GradeScale.UNKNOWN_LETTER.equals(score.grade())) {
            paintNoData(s, area);
            return;
        }

        final float scale = faceScale(area);
        final float pad = 7.0f * scale;
        final int grade = score.gradeColor() == 0 ? Tokens.INK : score.gradeColor() | 0xFF000000;

        if (lod.isGlyph()) {
            paintGlyph(s, area, score, grade);
            return;
        }

        final boolean roomy = area.w - pad * 2.0f >= 96.0f && area.h - pad * 2.0f >= 64.0f;
        if (!roomy || !lod.showsLabels()) {
            paintSolo(s, area, score, grade, scale);
            return;
        }

        paintBoard(s, area, model, score, grade, scale, pad, lod);
    }

    private static void paintBoard(PanelSurface s, Rect area, PanelModel model,
                                   EfficiencyScore score, int grade, float scale, float pad,
                                   Lod lod) {
        float y = area.y + pad;
        if (lod.showsHeader()) {
            final float titleScale = Text.fitScale(s, "EFFICIENCY", area.w * 0.45f, area.h * 0.14f,
                    Text.SUB * scale, Text.MICRO);
            s.text("EFFICIENCY", area.x + pad, y, Tokens.fade(Tokens.INK, lod.headerAlpha()),
                    titleScale);
            final String right = statusWord(score);
            s.textRight(Text.truncate(s, right, area.w * 0.5f, Text.META * scale),
                    area.right() - pad, y, Tokens.fade(grade, lod.headerAlpha()), Text.META * scale);
            y += Text.lineHeight(titleScale) + 3.0f * scale;
            Shapes.rect(s, area.x + pad, y, area.w - pad * 2.0f, 1.0f,
                    Tokens.fade(Tokens.LINE_HI, lod.headerAlpha()));
            y += 5.0f * scale;
        }

        final Rect body = new Rect(area.x + pad, y, area.w - pad * 2.0f, area.bottom() - pad - y);
        if (body.h < 24.0f * scale || body.w < 40.0f * scale) {
            return;
        }

        final Rect rest;
        if (body.w >= 150.0f) {
            final float heroW = Math.min(body.w * 0.34f, body.h * 0.92f);
            paintHero(s, body.left(heroW), score, grade, scale);
            rest = body.afterLeft(heroW + 6.0f * scale);
        } else {
            final float lineH = Text.lineHeight(Text.META * scale);
            s.text(score.grade(), body.x, body.y, grade, Text.META * scale);
            s.textRight(String.format(Locale.ROOT, "%.0f", score.compositeScore()), body.right(),
                    body.y, Tokens.INK_2, Text.META * scale);
            rest = body.below(lineH + 2.0f * scale);
        }
        if (rest.w < 40.0f || rest.h < 20.0f) {
            return;
        }

        final EfficiencyTrace trace = model.efficiencyTrace;
        final boolean showWorking = trace != null && rest.w >= 250.0f * scale;
        if (showWorking) {
            paintDimensions(s, rest.left(rest.w * 0.52f), score, scale);
            paintWorking(s, rest.afterLeft(rest.w * 0.52f + 6.0f * scale), trace, score, scale);
            return;
        }

        final boolean showTrend = model.efficiencyTrend != null
                && model.efficiencyTrend.length >= 2 && rest.h >= 78.0f * scale;
        final float trendH = showTrend ? 26.0f * scale : 0.0f;
        paintDimensions(s, new Rect(rest.x, rest.y, rest.w, rest.h - trendH), score, scale);
        if (showTrend) {
            paintTrend(s, new Rect(rest.x, rest.bottom() - trendH, rest.w, trendH),
                    model.efficiencyTrend, grade, scale);
        }
    }

    private static void paintHero(PanelSurface s, Rect a, EfficiencyScore score, int grade,
                                  float scale) {
        if (a.w < 20.0f * scale || a.h < 20.0f * scale) {
            return;
        }
        final float radius = Math.min(a.w, a.h) * 0.36f;
        final float cx = a.centerX();
        final float cy = a.y + radius + 2.0f * scale;
        final float thickness = Math.max(2.0f, radius * 0.17f);

        Shapes.arc(s, cx, cy, radius, thickness, (float) (-Math.PI * 0.5),
                (float) (Math.PI * 2.0), Tokens.LINE);
        final float frac = Math.max(0.0f, Math.min(1.0f, score.compositeScore() / 100.0f));
        if (frac > 0.005f) {
            Shapes.arc(s, cx, cy, radius, thickness, (float) (-Math.PI * 0.5),
                    (float) (Math.PI * 2.0) * frac, grade);
        }
        final float letterScale = Text.fitScale(s, score.grade(), radius * 1.3f, radius * 1.2f,
                Text.TITLE * scale, Text.META);
        s.textCenterV(score.grade(), cx, cy, grade, letterScale);

        float y = cy + radius + 3.0f * scale;
        if (y + Text.lineHeight(Text.META * scale) <= a.bottom()) {
            s.textCenter(String.format(Locale.ROOT, "%.1f / 100", score.compositeScore()),
                    cx, y, Tokens.INK_2, Text.META * scale);
            y += Text.lineHeight(Text.META * scale) + 1.0f * scale;
        }
        final String phrase = GradeScale.phraseFor(score.grade());
        if (!phrase.isEmpty() && y + Text.lineHeight(Text.MICRO * scale) <= a.bottom()) {
            s.textCenter(Text.truncate(s, phrase, a.w, Text.MICRO * scale), cx, y, Tokens.INK_3,
                    Text.MICRO * scale);
        }
    }

    private static void paintDimensions(PanelSurface s, Rect a, EfficiencyScore score, float scale) {
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        final float rowH = Math.min(a.h / dims.length, 22.0f * scale);
        if (rowH < 7.0f * scale) {
            return;
        }

        final float labelScale = Math.max(Text.MICRO * 0.8f,
                Math.min(Text.META * scale, (rowH - 3.0f) / Text.LINE_HEIGHT));
        float y = a.y;
        for (final EfficiencyDimension d : dims) {
            final boolean known = score.isKnown(d);
            final float pct = Math.max(0.0f, Math.min(100.0f, score.getDimensionPercent(d)));
            final int base = d.getBarColor() | 0xFF000000;
            final int color = !known ? Tokens.INK_3
                    : (pct < 30.0f ? Tokens.mix(base, Tokens.INK_3, 0.5f) : base);

            final String right = known ? Math.round(pct) + "%" : "no data";
            final float rightW = s.textWidth(right, labelScale) + 4.0f * scale;
            s.text(Text.truncate(s, d.getDisplayName(), a.w - rightW, labelScale), a.x, y,
                    known ? Tokens.INK_2 : Tokens.INK_3, labelScale);
            s.textRight(right, a.right(), y, color, labelScale);

            final float barY = y + Text.lineHeight(labelScale) + 1.0f * scale;
            final float barH = Math.max(1.5f, rowH * 0.16f);
            if (barY + barH <= a.bottom()) {
                final Rect bar = new Rect(a.x, barY, a.w, barH);
                Shapes.roundedRect(s, bar, barH * 0.5f, Tokens.LINE);
                if (known && pct > 0.0f) {
                    Shapes.roundedRect(s, bar.withWidth(Math.max(barH, a.w * pct / 100.0f)),
                            barH * 0.5f, color);
                }
            }
            y += rowH;
            if (y + rowH * 0.5f > a.bottom()) {
                return;
            }
        }
    }

    private static void paintWorking(PanelSurface s, Rect a, EfficiencyTrace t,
                                     EfficiencyScore score, float scale) {
        final float textScale = Math.min(Text.MICRO * scale, a.h * 0.055f);
        final float lh = Text.lineHeight(textScale) + 1.5f * scale;
        if (lh <= 0.0f || a.h < lh * 6.0f) {
            return;
        }
        float y = a.y;
        y = line(s, a, y, lh, "THIS SAMPLE", Tokens.INK_3, textScale);
        y = line(s, a, y, lh, String.format(Locale.ROOT, "u = %.0f / %.0f su = %.3f",
                t.stressSU, t.capacitySU, t.u), Tokens.INK_2, textScale);
        y = line(s, a, y, lh, String.format(Locale.ROOT, "peak u %.3f   rpm cv %s",
                t.peakU, t.rpmKnown ? String.format(Locale.ROOT, "%.3f", t.rpmCv) : "n/a"),
                Tokens.INK_2, textScale);
        y = line(s, a, y, lh, String.format(Locale.ROOT, "E_eq = 100·%.4f^(1/%.1f) = %.1f",
                t.meanSum, EfficiencyGrader.KAPPA, t.equilibrium), Tokens.INK_2, textScale);
        y = line(s, a, y, lh, String.format(Locale.ROOT, "Ψ = %.3f pts/s   Δt = %.1f s",
                t.psi, t.dtSeconds), t.psi > 0.001f ? Tokens.CAUTION : Tokens.INK_2, textScale);
        y = line(s, a, y, lh, String.format(Locale.ROOT, "α = %.4f/s %s   blend %.4f",
                t.alpha, t.rising ? "up" : "down", t.relaxBlend), Tokens.INK_2, textScale);
        y = line(s, a, y, lh, String.format(Locale.ROOT, "%.2f → %.2f  (%+.3f)",
                t.ePrev, t.eNew, t.eNew - t.ePrev),
                score.gradeColor() == 0 ? Tokens.INK : score.gradeColor() | 0xFF000000, textScale);
        if (score.isPartial()) {
            line(s, a, y, lh, String.format(Locale.ROOT, "measured across %.0f%% of the model",
                    score.observability() * 100.0f), Tokens.CAUTION, textScale);
        }
    }

    private static float line(PanelSurface s, Rect a, float y, float lh, String text, int color,
                              float textScale) {
        if (y + Text.lineHeight(textScale) > a.bottom()) {
            return y;
        }
        s.text(Text.truncate(s, text, a.w, textScale), a.x, y, color, textScale);
        return y + lh;
    }

    private static void paintTrend(PanelSurface s, Rect a, float[] data, int grade, float scale) {
        if (a.h < 8.0f || data.length < 2) {
            return;
        }
        final int n = data.length;
        final float[] xy = new float[n * 2];
        for (int i = 0; i < n; i++) {
            final float v = Math.max(0.0f, Math.min(100.0f, data[i]));
            xy[i * 2] = a.x + a.w * (i / (float) (n - 1));
            xy[i * 2 + 1] = a.bottom() - v / 100.0f * a.h;
        }
        Shapes.areaFill(s, xy, n, a.bottom(), Tokens.alpha(grade, 0.18f), Tokens.alpha(grade, 0.0f));
        Shapes.polyline(s, xy, n, Math.max(1.0f, 1.2f * scale), grade);
    }

    private static void paintSolo(PanelSurface s, Rect area, EfficiencyScore score, int grade,
                                  float scale) {
        final float cx = area.centerX();
        final float letterScale = Text.fitScale(s, score.grade(), area.w * 0.6f, area.h * 0.42f,
                Text.HERO * scale, Text.SUB);
        float y = area.y + area.h * 0.5f - Text.lineHeight(letterScale) * 0.6f;
        s.textCenter(score.grade(), cx, y, grade, letterScale);
        y += Text.lineHeight(letterScale) + 1.0f * scale;

        if (y + Text.lineHeight(Text.META * scale) <= area.bottom()) {
            s.textCenter(String.format(Locale.ROOT, "%.0f", score.compositeScore()), cx, y,
                    Tokens.INK_2, Text.META * scale);
            y += Text.lineHeight(Text.META * scale) + 1.0f * scale;
        }
        final EfficiencyDimension worst = worstMeasured(score);
        if (worst != null && y + Text.lineHeight(Text.MICRO * scale) <= area.bottom()) {
            final String note = worst.getDisplayName() + " " + Math.round(score.getDimensionPercent(worst)) + "%";
            s.textCenter(Text.truncate(s, note, area.w * 0.92f, Text.MICRO * scale), cx, y,
                    worst.getBarColor() | 0xFF000000, Text.MICRO * scale);
        }
    }

    private static void paintGlyph(PanelSurface s, Rect area, EfficiencyScore score, int grade) {
        Shapes.rect(s, area, Tokens.alpha(grade, 0.12f));
        final float letterScale = Text.fitScale(s, score.grade(), area.w * 0.7f, area.h * 0.7f,
                Text.HERO, Text.SUB);
        s.textCenterV(score.grade(), area.centerX(), area.centerY(), grade, letterScale);
    }

    private static void paintNoData(PanelSurface s, Rect area) {
        final float titleScale = Text.fitScale(s, "NO GRADE YET", area.w * 0.8f, area.h * 0.3f,
                Text.SUB, Text.MICRO);
        final boolean roomForHint = area.w >= 90.0f && area.h >= 40.0f;
        final float titleY = roomForHint
                ? area.centerY() - Text.lineHeight(titleScale)
                : area.centerY() - Text.lineHeight(titleScale) * 0.5f;
        s.textCenter(Text.truncate(s, "NO GRADE YET", area.w - 4.0f, titleScale), area.centerX(),
                titleY, Tokens.INK_3, titleScale);
        if (roomForHint) {
            s.textCenter(Text.truncate(s, "Link a Stressometer", area.w - 4.0f, Text.MICRO),
                    area.centerX(), area.centerY() + 2.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    private static String statusWord(EfficiencyScore score) {
        if (score.isWarmingUp()) {
            return String.format(Locale.ROOT, "SETTLING %.0f%%", score.warmup() * 100.0f);
        }
        if (score.isPartial()) {
            return String.format(Locale.ROOT, "%.0f%% MEASURED", score.observability() * 100.0f);
        }
        return String.format(Locale.ROOT, "%.1f", score.compositeScore());
    }

    private static EfficiencyDimension worstMeasured(EfficiencyScore score) {
        EfficiencyDimension worst = null;
        float lowest = Float.MAX_VALUE;
        for (final EfficiencyDimension d : EfficiencyDimension.values()) {
            if (!score.isKnown(d)) {
                continue;
            }
            final float pct = score.getDimensionPercent(d);
            if (pct < lowest) {
                lowest = pct;
                worst = d;
            }
        }
        return worst;
    }

    private static float faceScale(Rect area) {
        final float shortSide = Math.min(area.w, area.h);
        return Math.max(0.6f, Math.min(2.4f, (float) Math.sqrt(shortSide / 92.0f)));
    }
}
