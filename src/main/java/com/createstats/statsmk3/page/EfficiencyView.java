package com.createstats.statsmk3.page;

import java.util.Locale;

import com.createstats.efficiency.EfficiencyDimension;
import com.createstats.efficiency.EfficiencyGrader;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.EfficiencyTrace;
import com.createstats.efficiency.GradeHistory;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.instrument.Plot;
import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Layout;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class EfficiencyView extends StudioView {
    private enum Mode {
        OVERVIEW,

        LIVE_MATH
    }

    private final Spring scoreSpring = Spring.ui(0.0f);

    private Mode mode = Mode.OVERVIEW;
    private Segmented modeControl;
    private Button faceButton;

    private Rect gradeArea = Rect.EMPTY;
    private Rect dimsArea = Rect.EMPTY;
    private Rect trendArea = Rect.EMPTY;
    private Rect mathArea = Rect.EMPTY;

    public EfficiencyView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "efficiency";
    }

    @Override
    public String label() {
        return "Efficiency";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.rect(c, cx - 5.5f, cy + 1.0f, 2.5f, 5.0f, color);
        Shapes.rect(c, cx - 1.2f, cy - 2.0f, 2.5f, 8.0f, color);
        Shapes.rect(c, cx + 3.0f, cy - 5.5f, 2.5f, 11.5f, color);
    }

    @Override
    protected void build() {
        this.modeControl = new Segmented(new String[]{"Overview", "Live math"},
                () -> this.mode.ordinal(),
                i -> this.mode = i == 1 ? Mode.LIVE_MATH : Mode.OVERVIEW);
        this.modeControl.textScale(Text.META);
        final float mw = 168.0f;
        this.modeControl.setBounds(new Rect(this.area.right() - mw, this.area.y, mw, 26.0f));
        this.widgets.add(this.modeControl);

        this.faceButton = new Button(faceLabel(), this::toggleFace);
        this.faceButton.textScale(Text.MICRO);
        this.faceButton.setBounds(new Rect(this.area.right() - mw - 120.0f, this.area.y + 2.0f,
                112.0f, 22.0f));
        this.widgets.add(this.faceButton);

        final Rect body = this.area.inset(0.0f, 46.0f, 0.0f, 0.0f);
        this.mathArea = body;

        final float topH = Math.max(170.0f, Math.min(body.h * 0.52f, 300.0f));
        final Rect top = body.top(topH);
        final float gradeW = Math.min(topH * 0.92f, top.w * 0.32f);
        this.gradeArea = top.left(gradeW);
        this.dimsArea = top.afterLeft(gradeW + Tokens.S3);
        this.trendArea = body.below(topH + Tokens.S3);
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        final EfficiencyScore s = this.ctx.panel.getLatestEfficiencyScore();
        if (s != null) {
            this.scoreSpring.set(s.compositeScore()).step(dt);
        }
        if (this.faceButton != null) {
            this.faceButton.setLabel(faceLabel());
        }
    }

    private String faceLabel() {
        return showingOnFace() ? "On face ✓" : "Show on face";
    }

    private boolean showingOnFace() {
        return this.ctx.settings().faceMode == Mk3Settings.FaceMode.EFFICIENCY;
    }

    private void toggleFace() {
        final Mk3Settings settings = this.ctx.settings();
        settings.faceMode = showingOnFace()
                ? Mk3Settings.FaceMode.INSTRUMENTS : Mk3Settings.FaceMode.EFFICIENCY;
        this.ctx.commit();
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final EfficiencyScore score = this.ctx.panel.getLatestEfficiencyScore();
        final GradeHistory history = this.ctx.panel.getGradeHistory();

        c.text("Network efficiency", this.area.x, this.area.y, Tokens.INK, Text.SUB);

        if (score == null || "?".equals(score.grade())) {
            c.text("Awaiting network data", this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);

            final Rect box = this.area.inset(0.0f, 56.0f, 0.0f, 0.0f).top(118.0f);
            c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
            final float cx = box.centerX();
            c.textCenterV("Health needs a live stress reading", cx, box.y + 22.0f,
                    Tokens.INK_2, Text.BODY);
            c.textCenterV("Link a Stressometer and pick its Stats Link output",
                    cx, box.y + 48.0f, Tokens.INK_3, Text.META);
            c.textCenterV("Give it a short warmup to learn the load",
                    cx, box.y + 66.0f, Tokens.INK_3, Text.META);
            c.textCenterV("Scoring then keeps updating in the background",
                    cx, box.y + 84.0f, Tokens.INK_3, Text.META);
            return;
        }
        final String change = history == null ? "" : history.getSessionChangeText();

        final String status;
        if (score.isWarmingUp()) {
            status = String.format(java.util.Locale.ROOT, "Warming up, %.0f%% settled",
                    score.warmup() * 100.0f);
        } else if (score.isPartial()) {
            status = String.format(java.util.Locale.ROOT, "Measured across %.0f%% of the model",
                    score.observability() * 100.0f);
        } else if (change != null && !change.isEmpty()) {
            status = change;
        } else {
            status = "Scored continuously while the network runs";
        }
        c.text(status, this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);

        if (this.mode == Mode.LIVE_MATH) {
            drawLiveMath(c, score);
            return;
        }
        drawGrade(c, score, history);
        drawDimensions(c, score);
        drawTrend(c, history);
    }

    private void drawLiveMath(PanelSurface c, EfficiencyScore score) {
        final GradeHistory history = this.ctx.panel.getGradeHistory();
        EfficiencyMathArt.paint(c, this.mathArea, this.ctx.panel.getLatestEfficiencyTrace(), score,
                sampleAgeSeconds(), history == null ? null : history.getSparklineData(120));
    }

    private float sampleAgeSeconds() {
        final EfficiencyTrace t = this.ctx.panel.getLatestEfficiencyTrace();
        if (t == null || this.ctx.panel.getLevel() == null) {
            return -1.0f;
        }
        return Math.max(0L, this.ctx.panel.getLevel().getGameTime() - t.computedAtTick) / 20.0f;
    }

    private void drawGrade(PanelSurface c, EfficiencyScore score, GradeHistory history) {
        final Rect a = this.gradeArea;
        if (a.isEmpty()) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);

        final int color = score.gradeColor() == 0 ? Tokens.INK : score.gradeColor() | 0xFF000000;
        final float cx = a.centerX();
        final float cy = a.y + a.h * 0.44f;
        final float radius = Math.min(a.w, a.h) * 0.30f;

        Shapes.arc(c, cx, cy, radius, Math.max(5.0f, radius * 0.16f),
                (float) (-Math.PI * 0.5), (float) (Math.PI * 2.0), Tokens.LINE);
        final float frac = Math.max(0.0f, Math.min(1.0f, this.scoreSpring.value() / 100.0f));
        if (frac > 0.005f) {
            Shapes.arc(c, cx, cy, radius, Math.max(5.0f, radius * 0.16f),
                    (float) (-Math.PI * 0.5), (float) (Math.PI * 2.0) * frac, color);
        }

        c.textCenterV(score.grade() == null ? "-" : score.grade(), cx, cy - 4.0f, color, 4.0f);
        c.textCenterV(String.format(java.util.Locale.ROOT, "%.1f / 100", this.scoreSpring.value()),
                cx, cy + radius * 0.62f, Tokens.INK_2, Text.META);

        final String desc = gradeDescription(score.grade());
        if (!desc.isEmpty()) {
            c.textCenterV(desc, cx, cy + radius * 0.62f + 14.0f, Tokens.INK_3, Text.MICRO);
        }

        if (history != null) {
            final String change = history.getSessionChangeText();
            if (change != null && !change.isEmpty()) {
                final boolean up = history.hasImproved();
                c.textCenterV((up ? "^ " : "v ") + change, cx, a.bottom() - 30.0f,
                        up ? Tokens.GOOD : Tokens.CRITICAL, Text.META);
            }
            final String best = history.getBestGrade();
            if (best != null && !best.isEmpty()) {
                c.textCenterV("best " + best + "  .  " + Math.round(history.getBestScore()),
                        cx, a.bottom() - 14.0f, Tokens.INK_3, Text.MICRO);
            }
        }
    }

    private static String gradeDescription(String grade) {
        return grade == null ? "" : com.createstats.efficiency.GradeScale.phraseFor(grade);
    }

    private void drawDimensions(PanelSurface c, EfficiencyScore score) {
        final Rect a = this.dimsArea;
        if (a.isEmpty()) {
            return;
        }
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        final Rect[] rows = Layout.grid(a, 2, (dims.length + 1) / 2, Tokens.S2);

        for (int i = 0; i < dims.length && i < rows.length; i++) {
            final EfficiencyDimension d = dims[i];
            final Rect r = rows[i];
            c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
            final Rect in = r.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);

            final float pct = Math.max(0.0f, Math.min(100.0f, score.getDimensionPercent(d)));
            final boolean known = score.isKnown(d);

            final int base = d.getBarColor() | 0xFF000000;
            final int color = !known ? Tokens.INK_3 : (pct < 30.0f ? Tokens.mix(base, Tokens.INK_3, 0.5f) : base);

            c.textV(d.getDisplayName(), in.x, in.centerY() - 6.0f,
                    known ? Tokens.INK : Tokens.INK_3, Text.META);

            c.textV("weight " + Math.round(d.getWeight() * 100.0f) + "%", in.x,
                    in.centerY() + 6.0f, Tokens.INK_3, Text.MICRO);
            c.textRightV(known ? Math.round(pct) + "%" : "no data", in.right(),
                    in.centerY() - 6.0f, color, Text.META);

            final Rect bar = new Rect(in.x, in.bottom() - 4.0f, in.w, 4.0f);
            Shapes.roundedRect(c, bar, 2.0f, Tokens.LINE);
            if (known && pct > 0.0f) {
                Shapes.roundedRect(c, bar.withWidth(Math.max(2.0f, bar.w * pct / 100.0f)), 2.0f, color);
            }
        }
    }

    private void drawTrend(PanelSurface c, GradeHistory history) {
        final Rect a = this.trendArea;
        if (a.isEmpty() || a.h < 40.0f) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Score over this session"), a.x + Tokens.S3, a.y + 6.0f,
                Tokens.INK_3, Text.MICRO);

        final Rect plot = a.inset(Tokens.S3, Tokens.S4 + 4.0f, Tokens.S3, Tokens.S3);
        if (plot.isEmpty()) {
            return;
        }
        final float[] data = history == null ? null : history.getSparklineData(120);
        if (data == null || data.length < 2) {
            c.textCenterV("Collecting samples", plot.centerX(), plot.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }

        // fixed 0-100, autoscale makes a 2 point wobble look like a collapse

        Plot.grid(c, plot, 4);
        final int n = data.length;
        final float[] xy = new float[n * 2];
        for (int i = 0; i < n; i++) {
            final float v = Math.max(0.0f, Math.min(100.0f, data[i]));
            xy[i * 2] = plot.x + plot.w * (i / (float) (n - 1));
            xy[i * 2 + 1] = plot.bottom() - v / 100.0f * plot.h;
        }
        final float last = Math.max(0.0f, Math.min(100.0f, data[n - 1]));
        final int color = last >= 75.0f ? Tokens.GOOD : last >= 45.0f ? Tokens.CAUTION : Tokens.CRITICAL;
        Shapes.areaFill(c, xy, n, plot.bottom(),
                Tokens.alpha(color, 0.20f), Tokens.alpha(color, 0.0f));
        Shapes.polyline(c, xy, n, Tokens.DATA_STROKE, color);
        Plot.endpoint(c, xy[(n - 1) * 2], xy[(n - 1) * 2 + 1], Tokens.DATA_STROKE, color);

        c.textV("0", plot.x, plot.bottom() - 6.0f, Tokens.INK_3, Text.MICRO);
        c.textV("100", plot.x, plot.y - 2.0f, Tokens.INK_3, Text.MICRO);
    }
}
