package com.createstats.client;

import java.util.Locale;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.efficiency.EfficiencyDimension;
import com.createstats.efficiency.EfficiencyGrader;
import com.createstats.efficiency.EfficiencyKernels;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.EfficiencyTrace;
import com.createstats.efficiency.GradeScale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EfficiencyFormulaScreen extends Screen {
    private static final int BG = 0xEE0A0A0E;
    private static final int CARD_BG = 0xFF101418;
    private static final int BORDER = 0xFF2A2F44;
    private static final int TITLE_COL = 0xFF44AAFF;
    private static final int FORMULA = 0xFFCCDDFF;
    private static final int FORMULA_BG = 0xFF161C24;
    private static final int FORMULA_BORDER = 0xFF2A3145;
    private static final int EXPLAIN = 0xFF8899BB;
    private static final int PARAM = 0xFF88A0CC;
    private static final int VALUE_COL = 0xFF44CCC8;
    private static final int LABEL = 0xFF677899;
    private static final int DIM_COL = 0xFF99AACC;
    private static final int WARN_COL = 0xFFFF8844;
    private static final int UNKNOWN_COL = 0xFF707070;
    private static final int RULE_COL = 0xFF333C55;
    private static final int FOOT_COL = 0xFF444C66;
    private static final int SCROLLBAR = 0x45FFFFFF;
    private static final int TAB_ON_BG = 0xFF1C2636;
    private static final int TAB_OFF_BG = 0xFF13171E;
    private static final int TAB_ON_COL = 0xFF66CCFF;
    private static final int TAB_OFF_COL = 0xFF6A7A96;
    private static final int PULSE_COL = 0xFF66FFCC;

    private static final int VALUE_X = 148;

    private static final int TAB_H = 14;

    private static boolean liveTab;

    private final StatsDisplayPanelBE panel;
    private final Screen parentScreen;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private final int[] scrollOffsets = new int[2];
    private int contentHeight;
    private int modelTabX;
    private int liveTabX;
    private int tabY;
    private int tabW;

    private long lastTraceTick = Long.MIN_VALUE;
    private long freshUntilMs;

    public EfficiencyFormulaScreen(StatsDisplayPanelBE panel, Screen parentScreen) {
        super(Component.literal("Bolat Efficiency Model"));
        this.panel = panel;
        this.parentScreen = parentScreen;
    }

    public EfficiencyFormulaScreen(StatsDisplayPanelBE panel, Screen parentScreen, boolean live) {
        this(panel, parentScreen);
        liveTab = live;
    }

    @Override
    protected void init() {
        this.panelW = Math.min(380, this.width - 30);
        this.panelH = this.height - 20;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = 10;
        this.tabW = Math.min(96, (this.panelW - 24) / 2);
        this.modelTabX = this.panelX + 8;
        this.liveTabX = this.modelTabX + this.tabW + 4;
        this.tabY = this.panelY + 16;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        g.fill(0, 0, this.width, this.height, BG);
        g.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, CARD_BG);
        this.drawBorder(g, this.panelX, this.panelY, this.panelW, this.panelH, BORDER);

        final int contentX = this.panelX + 8;
        final int contentW = this.panelW - 16;
        final int centreX = this.panelX + this.panelW / 2;

        g.drawCenteredString(this.font, "Bolat Efficiency Model  v2", centreX, this.panelY + 5, TITLE_COL);
        this.tab(g, "Model", this.modelTabX, !liveTab);
        this.tab(g, "Live math", this.liveTabX, liveTab);

        final int contentTop = this.contentTop();
        final int contentBot = this.contentBottom();

        g.enableScissor(this.panelX, contentTop, this.panelX + this.panelW, contentBot);
        final int startY = contentTop - this.scrollOffsets[liveTab ? 1 : 0];
        final int endY = liveTab
                ? this.renderLive(g, contentX, startY, contentW)
                : this.renderModel(g, contentX, startY, contentW, centreX);
        this.contentHeight = endY - startY + 10;
        g.disableScissor();

        final int trackH = contentBot - contentTop;
        if (this.contentHeight > trackH) {
            final int barH = Math.max(10, trackH * trackH / this.contentHeight);
            final int maxScroll = this.contentHeight - trackH;
            final int barY = contentTop
                    + (int) (this.scrollOffsets[liveTab ? 1 : 0] / (float) maxScroll * (trackH - barH));
            g.fill(this.panelX + this.panelW - 4, barY, this.panelX + this.panelW - 1, barY + barH, SCROLLBAR);
        }
        g.drawCenteredString(this.font, "Scroll  |  Tab to switch  |  ESC to close", centreX,
                this.panelY + this.panelH - 10, FOOT_COL);
    }

    private int renderModel(GuiGraphics g, int contentX, int startY, int contentW, int centreX) {
        int cy = startY;
        g.drawCenteredString(this.font, "A damped system over six independent signals",
                centreX, cy, LABEL);
        cy += 14;

        cy = this.section(g, "Governing ODE", contentX, cy, contentW);
        cy = this.formula(g, "dE/dt = α·(E_eq − E) − Ψ(t)", contentX, cy, contentW);
        cy += 2;
        cy = this.explain(g, "E(t) ∈ [0, 100] is the score, integrated not sampled", contentX, cy);
        cy = this.explain(g, String.format(Locale.ROOT,
                "α = %.4f/s rising, %.3f/s falling (asymmetric)",
                EfficiencyGrader.ALPHA_UP, EfficiencyGrader.ALPHA_DOWN), contentX, cy);
        cy = this.explain(g, "Quick to report a fault, slow to agree it is fixed", contentX, cy);
        cy = this.explain(g, "Stepped as 1 − e^(−α·Δt), exact for any interval", contentX, cy);
        cy = this.explain(g, String.format(Locale.ROOT,
                "First %d samples use a running average to converge fast",
                EfficiencyGrader.WARMUP_OBSERVATIONS), contentX, cy);
        cy += 6;

        cy = this.section(g, String.format(Locale.ROOT,
                "Equilibrium, power mean (κ=%.1f)", EfficiencyGrader.KAPPA), contentX, cy, contentW);
        cy = this.formula(g, "E_eq = 100·[ Σ (wᵢ/W)·Φᵢ^κ ]^(1/κ)", contentX, cy, contentW);
        cy += 2;
        cy = this.explain(g, "κ = Bolat coefficient, which mean this is", contentX, cy);
        cy = this.explain(g, "  κ=1 arithmetic (lenient), κ→0 geometric (harsh)", contentX, cy);
        cy = this.explain(g, "W = Σ wᵢ over the dimensions actually measured", contentX, cy);
        cy = this.explain(g, "An unmeasured dimension leaves the mean entirely.", contentX, cy);
        cy = this.explain(g, "It is not scored as zero: unknown is not failure.", contentX, cy);
        cy += 6;

        cy = this.section(g, EfficiencyDimension.values().length + " Dimensions", contentX, cy, contentW);
        final EfficiencyScore score = this.panel.getLatestEfficiencyScore();
        final boolean live = score != null && !GradeScale.UNKNOWN_LETTER.equals(score.grade());
        for (EfficiencyDimension dim : EfficiencyDimension.values()) {
            cy = this.dimension(g, dim, live ? score : null, contentX, cy, contentW);
        }
        cy += 2;

        cy = this.section(g, "Transient penalty Ψ", contentX, cy, contentW);
        cy = this.formula(g, "Ψ = β₁·(du/dt)² + β₂·severity(u)", contentX, cy, contentW);
        cy += 2;
        cy = this.explain(g, String.format(Locale.ROOT,
                "β₁ = %.0f, capped at %.0f pts/s", EfficiencyGrader.BETA_TRANSIENT,
                EfficiencyGrader.PSI_TRANSIENT_CAP), contentX, cy);
        cy = this.explain(g, String.format(Locale.ROOT,
                "β₂ = %.1f pts/s at full severity", EfficiencyGrader.BETA_OVERSTRESS), contentX, cy);
        cy = this.explain(g, "Both terms are rates, multiplied by real elapsed time", contentX, cy);
        cy = this.explain(g, "severity is 0.5 at the line, 1.0 at 150% of capacity", contentX, cy);
        cy = this.explain(g, "A quarter swing of load in one sample costs about a point", contentX, cy);
        cy += 6;

        cy = this.section(g, "Grade scale", contentX, cy, contentW);
        cy = this.gradeTable(g, contentX, cy, contentW);
        cy += 6;

        cy = this.section(g, "Where the numbers are", contentX, cy, contentW);
        cy = this.explain(g, "The Live math tab runs this sample's arithmetic with", contentX, cy);
        cy = this.explain(g, "the measured values in place, term by term.", contentX, cy);
        return cy;
    }

    private int dimension(GuiGraphics g, EfficiencyDimension dim, EfficiencyScore score, int x, int y, int w) {
        final boolean known = score != null && score.isKnown(dim);
        final String head = String.format(Locale.ROOT, "%s  %s  (w=%.0f%%)",
                dim.getSymbol(), dim.getDisplayName(), dim.getWeight() * 100.0f);
        int cy = this.dimHeader(g, head, dim.getBarColor(), x, y);

        switch (dim) {
            case UTILIZATION -> {
                cy = this.formula(g, "Φ_U(u) = ramp → 1 → e^(−((u−u_hi)/s)²)", x, cy, w);
                cy = this.explain(g, String.format(Locale.ROOT,
                        "Perfect across the band u ∈ [%.2f, %.2f]",
                        EfficiencyKernels.LOAD_BAND_LO, EfficiencyKernels.LOAD_BAND_HI), x, cy);
                cy = this.explain(g, "u = stress / capacity, right now", x, cy);
                cy = this.explain(g, "Under-use is wasteful, overrun is broken, so the", x, cy);
                cy = this.explain(g, "curve rises gently and falls off a cliff", x, cy);
            }
            case HEADROOM -> {
                cy = this.formula(g, "Φ_H(h) = 1 − e^(−h / 0.10)", x, cy, w);
                cy = this.explain(g, "h = 1 − (peak stress in window) / capacity", x, cy);
                cy = this.explain(g, "Measured against the worst demand, not the current", x, cy);
                cy = this.explain(g, "one, so a network that spikes to the ceiling", x, cy);
                cy = this.explain(g, "between samples cannot show a full margin", x, cy);
            }
            case STABILITY -> {
                cy = this.formula(g, "Φ_S(cv) = e^(−cv² / 0.020)", x, cy, w);
                cy = this.explain(g, "cv = σ_ω / μ_ω, the RPM coefficient of variation", x, cy);
                cy = this.explain(g, "cv=0 → 1.00, cv=10% → 0.61, cv=20% → 0.14", x, cy);
                cy = this.explain(g, "Unknown, not zero, with no RPM channel bound", x, cy);
            }
            case UPTIME -> {
                cy = this.formula(g, "dU/dt = ((1 − O(t)) − U) / τ", x, cy, w);
                cy = this.explain(g, String.format(Locale.ROOT,
                        "τ = %.0f s of memory", EfficiencyGrader.UPTIME_TAU), x, cy);
                cy = this.explain(g, "O(t) = 1 while overstressed, 0 otherwise", x, cy);
                cy = this.explain(g, "Seeded from observed history, so a panel placed on", x, cy);
                cy = this.explain(g, "a thrashing factory does not open by claiming 100%", x, cy);
            }
            case SMOOTHNESS -> {
                cy = this.formula(g, "Φ_V(cv) = e^(−cv² / 0.060)", x, cy, w);
                cy = this.explain(g, "cv of the stress draw itself, over the window", x, cy);
                cy = this.explain(g, "Three times looser than Rhythm: load is meant to", x, cy);
                cy = this.explain(g, "breathe, RPM is not", x, cy);
                cy = this.explain(g, "Separates a flat draw from a sawtooth at equal mean", x, cy);
            }
            case RESILIENCE -> {
                cy = this.formula(g, "Φ_R(t) = e^(−t_longest / 30 s)", x, cy, w);
                cy = this.explain(g, "t_longest = longest unbroken overstress episode", x, cy);
                cy = this.explain(g, "Twenty one-second stalls and one twenty-second stall", x, cy);
                cy = this.explain(g, "are the same to Reliability and different faults.", x, cy);
                cy = this.explain(g, "This is the dimension that can tell them apart", x, cy);
            }
        }

        if (score != null) {
            final String readout = known
                    ? String.format(Locale.ROOT, "  now: %.1f%%", score.getDimensionPercent(dim))
                    : "  now: no data, dropped from the mean";
            g.drawString(this.font, readout, x + 8, cy, known ? VALUE_COL : UNKNOWN_COL);
            cy += 10;
        }
        return cy + 4;
    }

    private int gradeTable(GuiGraphics g, int x, int y, int w) {
        int cy = y;
        final StringBuilder row = new StringBuilder();
        int rowColor = 0;
        for (int i = 0; i < GradeScale.count(); i++) {
            final int color = GradeScale.colorAt(i);
            if (rowColor != 0 && color != rowColor) {
                g.drawCenteredString(this.font, row.toString(), x + w / 2, cy, rowColor);
                cy += 11;
                row.setLength(0);
            }
            rowColor = color;
            if (row.length() > 0) {
                row.append("  ");
            }
            final float min = GradeScale.minScoreAt(i);
            row.append(GradeScale.letterAt(i));
            row.append(min > 0.0f ? "≥" + (int) min : "<" + (int) GradeScale.minScoreAt(i - 1));
        }
        if (row.length() > 0) {
            g.drawCenteredString(this.font, row.toString(), x + w / 2, cy, rowColor);
            cy += 11;
        }
        g.drawString(this.font, String.format(Locale.ROOT,
                "Letter holds until the score clears a boundary by %.1f", GradeScale.HYSTERESIS),
                x + 8, cy, EXPLAIN);
        return cy + 10;
    }

    private int renderLive(GuiGraphics g, int x, int y, int w) {
        final EfficiencyTrace t = this.panel.getLatestEfficiencyTrace();
        final EfficiencyScore score = this.panel.getLatestEfficiencyScore();
        int cy = y;

        if (t == null) {
            cy = this.section(g, "Nothing to work through yet", x, cy, w);
            cy = this.explain(g, "The model grades once a stress and a capacity channel", x, cy);
            cy = this.explain(g, "are both bound and have some history behind them.", x, cy);
            cy = this.explain(g, "Link a Stressometer through a Stats Link and give it", x, cy);
            cy = this.explain(g, "about a minute of running before the first sample.", x, cy);
            return cy + 6;
        }

        cy = this.liveHeader(g, t, x, cy, w);

        cy = this.section(g, "Measured this sample", x, cy, w);
        cy = this.kv(g, "capacity", fmt(t.capacitySU, 0) + " su", x, cy, VALUE_COL);
        cy = this.kv(g, "stress", fmt(t.stressSU, 0) + " su", x, cy, VALUE_COL);
        cy = this.kv(g, "u = stress / capacity", fmt(t.u, 4), x, cy, VALUE_COL);
        cy = this.kv(g, "peak stress in window", fmt(t.peakStressSU, 0) + " su", x, cy, PARAM);
        cy = this.kv(g, "peak u", fmt(t.peakU, 4), x, cy, VALUE_COL);
        cy = this.kv(g, "rpm cv", t.rpmKnown ? fmt(t.rpmCv, 4) : "no channel bound",
                x, cy, t.rpmKnown ? VALUE_COL : UNKNOWN_COL);
        cy = this.kv(g, "stress cv", t.stressCvKnown ? fmt(t.stressCv, 4) : "too few samples",
                x, cy, t.stressCvKnown ? VALUE_COL : UNKNOWN_COL);
        cy = this.kv(g, "overstressed now", t.overstressed ? "yes" : "no",
                x, cy, t.overstressed ? WARN_COL : VALUE_COL);
        cy = this.kv(g, "longest episode",
                t.resilienceKnown ? fmt(t.longestOverstressRunSeconds, 1) + " s" : "no channel bound",
                x, cy, t.resilienceKnown ? VALUE_COL : UNKNOWN_COL);
        if (t.resilienceKnown) {
            cy = this.kv(g, "overstressed fraction", fmt(t.overstressRate * 100.0f, 1) + "%",
                    x, cy, PARAM);
        }
        cy += 6;

        cy = this.section(g, "Six kernels, with these numbers in", x, cy, w);
        for (EfficiencyDimension dim : EfficiencyDimension.values()) {
            cy = this.liveDimension(g, dim, t, x, cy, w);
        }
        cy += 4;

        cy = this.section(g, "Weighted power mean", x, cy, w);
        cy = this.explain(g, String.format(Locale.ROOT,
                "W = %.3f of %.3f nominal  (observability %.0f%%)",
                t.totalWeight, EfficiencyDimension.weightSum(), t.observability * 100.0f), x, cy);
        cy = this.explain(g, "Σ w'·Φ^κ = " + fmt(t.meanSum, 5), x, cy);
        cy = this.formula(g, String.format(Locale.ROOT, "E_eq = 100 · %.5f^(1/%.1f) = %.2f",
                t.meanSum, EfficiencyGrader.KAPPA, t.equilibrium), x, cy, w);
        cy += 6;

        cy = this.section(g, "Penalty Ψ", x, cy, w);
        if (t.hasPreviousU()) {
            cy = this.explain(g, String.format(Locale.ROOT,
                    "du/dt = (%.4f − %.4f) / %.2f s = %+.5f /s",
                    t.u, t.previousU, t.safeDt(), t.duDt), x, cy);
        } else {
            cy = this.explain(g, "du/dt: no previous sample, so no transient term", x, cy);
        }
        final String swing = String.format(Locale.ROOT, "β₁·(du/dt)² = %s pts/s",
                fmt(t.swingApplied, 4));
        cy = this.explain(g, t.swingApplied < t.swingRaw
                ? swing + "  (capped from " + fmt(t.swingRaw, 2) + ")"
                : swing, x, cy);
        cy = this.explain(g, t.overstressed
                ? String.format(Locale.ROOT, "severity(%.3f) = %.3f → β₂·severity = %s pts/s",
                        t.u, t.severity, fmt(t.overstressPenalty, 3))
                : "not overstressed, so the overstress term is 0", x, cy);
        cy = this.explain(g, String.format(Locale.ROOT, "Ψ = %s pts/s,  Ψ·Δt = %s pts",
                fmt(t.psi, 4), fmt(t.penaltyPoints, 4)), x, cy);
        cy += 6;

        cy = this.section(g, "Integration of this step", x, cy, w);
        cy = this.explain(g, String.format(Locale.ROOT, "Δt = %.2f s  (%d ticks)%s",
                t.dtSeconds, t.elapsedTicks,
                t.stepClamped ? ", clamped from a longer gap" : ""), x, cy);
        cy = this.explain(g, String.format(Locale.ROOT, "α = %.4f /s  (%s)",
                t.alpha, t.rising ? "rising, the slow rate" : "falling, the fast rate"), x, cy);
        cy = this.explain(g, t.warmStart
                ? String.format(Locale.ROOT, "blend = 1/(n+1) = %.5f  (warm start, sample %d of %d)",
                        t.relaxBlend, t.observations, EfficiencyGrader.WARMUP_OBSERVATIONS)
                : String.format(Locale.ROOT, "blend = 1 − e^(−α·Δt) = %.5f", t.relaxBlend), x, cy);
        cy = this.explain(g, String.format(Locale.ROOT, "relax: %.3f + %.5f·(%.3f − %.3f) = %.3f",
                t.ePrev, t.relaxBlend, t.equilibrium, t.ePrev, t.eRelaxed), x, cy);
        cy = this.explain(g, String.format(Locale.ROOT, "penalty: %.3f − %s = %.3f",
                t.eRelaxed, fmt(t.penaltyPoints, 4), t.eBeforeClamp), x, cy);
        if (Math.abs(t.eBeforeClamp - t.eNew) > 0.0005f) {
            cy = this.explain(g, String.format(Locale.ROOT, "clamped into [0, 100] → %.3f", t.eNew), x, cy);
        }
        cy = this.explain(g, String.format(Locale.ROOT,
                "uptime memory: %.4f → %.4f", t.uptimeMemoryBefore, t.uptimeMemoryAfter), x, cy);
        cy += 2;

        final int gradeColor = score != null ? score.gradeColor() : VALUE_COL;
        final String letter = score != null ? score.grade() : GradeScale.UNKNOWN_LETTER;
        cy = this.formula(g, String.format(Locale.ROOT, "E = %.2f   grade %s", t.eNew, letter), x, cy, w);
        g.drawString(this.font, String.format(Locale.ROOT,
                "moved %+.3f this step, heading for %.2f", t.eNew - t.ePrev, t.equilibrium),
                x + 8, cy, gradeColor);
        cy += 12;
        g.drawString(this.font, String.format(Locale.ROOT, "samples taken: %d", t.observations),
                x + 8, cy, LABEL);
        return cy + 12;
    }

    // show the sample age, "live" with no age looks stale
    private int liveHeader(GuiGraphics g, EfficiencyTrace t, int x, int y, int w) {
        final long now = this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getGameTime()
                : t.computedAtTick;
        final int interval = EfficiencyGrader.DEFAULT_INTERVAL_TICKS;
        final long ageTicks = Math.max(0L, now - t.computedAtTick);

        if (t.computedAtTick != this.lastTraceTick) {
            this.lastTraceTick = t.computedAtTick;
            this.freshUntilMs = System.currentTimeMillis() + 400L;
        }
        final boolean fresh = System.currentTimeMillis() < this.freshUntilMs;

        int cy = y;
        g.drawString(this.font, String.format(Locale.ROOT,
                "This sample, %.1f s ago", ageTicks / 20.0f), x + 2, cy,
                fresh ? PULSE_COL : LABEL);
        final String cadence = String.format(Locale.ROOT, "every %.1f s", interval / 20.0f);
        g.drawString(this.font, cadence, x + w - this.font.width(cadence) - 2, cy, LABEL);
        cy += 11;

        final float progress = Math.max(0.0f, Math.min(1.0f, ageTicks / (float) interval));
        g.fill(x + 2, cy, x + w - 2, cy + 2, FORMULA_BG);
        g.fill(x + 2, cy, x + 2 + (int) ((w - 4) * progress), cy + 2, fresh ? PULSE_COL : TAB_ON_COL);
        return cy + 8;
    }

    private int liveDimension(GuiGraphics g, EfficiencyDimension dim, EfficiencyTrace t,
                              int x, int y, int w) {
        final int i = dim.ordinal();
        final boolean known = t.isKnown(dim);
        final float phi = t.phi[i];

        g.fill(x + 2, y + 1, x + 5, y + 8, dim.getBarColor() | 0xFF000000);
        g.drawString(this.font, dim.getSymbol() + " " + kernelCall(dim, t), x + 8, y,
                known ? DIM_COL : UNKNOWN_COL);
        final String result = known ? "= " + fmt(phi, 4) : "unmeasured";
        g.drawString(this.font, result, x + w - this.font.width(result) - 2, y,
                known ? VALUE_COL : UNKNOWN_COL);
        int cy = y + 10;

        if (known) {
            g.drawString(this.font, String.format(Locale.ROOT,
                    "w' = %.3f   term = w'·Φ^κ = %.4f",
                    t.weightApplied[i], t.term[i]), x + 12, cy, EXPLAIN);
            cy += 10;

            final int barW = w - 16;
            g.fill(x + 12, cy, x + 12 + barW, cy + 2, FORMULA_BG);
            g.fill(x + 12, cy, x + 12 + (int) (barW * Math.max(0.0f, Math.min(1.0f, phi))), cy + 2,
                    dim.getBarColor() | 0xFF000000);
            cy += 6;
        } else {
            g.drawString(this.font, "dropped from the mean, weight redistributed",
                    x + 12, cy, UNKNOWN_COL);
            cy += 10;
        }
        return cy + 2;
    }

    private static String kernelCall(EfficiencyDimension dim, EfficiencyTrace t) {
        return switch (dim) {
            case UTILIZATION -> "load(u=" + fmt(t.u, 3) + ")";
            case HEADROOM -> "reserve(peak u=" + fmt(t.peakU, 3) + ")";
            case STABILITY -> t.rpmKnown ? "rhythm(cv=" + fmt(t.rpmCv, 4) + ")" : "rhythm(no rpm)";
            case UPTIME -> "uptime memory after " + fmt(t.dtSeconds, 1) + " s";
            case SMOOTHNESS -> t.stressCvKnown
                    ? "smoothness(cv=" + fmt(t.stressCv, 4) + ")"
                    : "smoothness(no cv)";
            case RESILIENCE -> t.resilienceKnown
                    ? "resilience(" + fmt(t.longestOverstressRunSeconds, 1) + " s)"
                    : "resilience(no episodes)";
        };
    }

    private static String fmt(float v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    private int contentTop() {
        return this.tabY + TAB_H + 4;
    }

    private int contentBottom() {
        return this.panelY + this.panelH - 14;
    }

    private int trackHeight() {
        return this.contentBottom() - this.contentTop();
    }

    private void tab(GuiGraphics g, String label, int x, boolean on) {
        g.fill(x, this.tabY, x + this.tabW, this.tabY + TAB_H, on ? TAB_ON_BG : TAB_OFF_BG);
        this.drawBorder(g, x, this.tabY, this.tabW, TAB_H, on ? TAB_ON_COL : BORDER);
        g.drawCenteredString(this.font, label, x + this.tabW / 2, this.tabY + 3,
                on ? TAB_ON_COL : TAB_OFF_COL);
    }

    private int section(GuiGraphics g, String title, int x, int y, int w) {
        g.fill(x, y + 4, x + w, y + 5, RULE_COL);
        g.drawString(this.font, title, x + 2, y + 7, TITLE_COL);
        return y + 19;
    }

    private int formula(GuiGraphics g, String text, int x, int y, int w) {
        final int fw = this.font.width(text);
        final int fx = x + (w - fw) / 2;
        g.fill(fx - 4, y - 1, fx + fw + 4, y + 10, FORMULA_BG);
        this.drawBorder(g, fx - 4, y - 1, fw + 8, 11, FORMULA_BORDER);
        g.drawString(this.font, text, fx, y, FORMULA);
        return y + 13;
    }

    private int explain(GuiGraphics g, String text, int x, int y) {
        g.drawString(this.font, text, x + 8, y, EXPLAIN);
        return y + 10;
    }

    private int kv(GuiGraphics g, String key, String value, int x, int y, int color) {
        g.drawString(this.font, key, x + 8, y, PARAM);
        g.drawString(this.font, value, x + VALUE_X, y, color);
        return y + 10;
    }

    private int dimHeader(GuiGraphics g, String text, int color, int x, int y) {
        g.fill(x + 2, y + 1, x + 5, y + 8, color | 0xFF000000);
        g.drawString(this.font, text, x + 8, y, DIM_COL);
        return y + 12;
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && my >= this.tabY && my <= this.tabY + TAB_H) {
            if (mx >= this.modelTabX && mx <= this.modelTabX + this.tabW) {
                liveTab = false;
                return true;
            }
            if (mx >= this.liveTabX && mx <= this.liveTabX + this.tabW) {
                liveTab = true;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            liveTab = !liveTab;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        final int slot = liveTab ? 1 : 0;
        final int maxScroll = Math.max(0, this.contentHeight - this.trackHeight());
        this.scrollOffsets[slot] -= (int) (scrollY * 14.0);
        this.scrollOffsets[slot] = Math.max(0, Math.min(maxScroll, this.scrollOffsets[slot]));
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
