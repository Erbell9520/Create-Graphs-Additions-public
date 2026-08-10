package com.createstats.client;

import com.createstats.efficiency.GradeHistory;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.EfficiencyGrader;
import com.createstats.efficiency.EfficiencyDimension;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;

@OnlyIn(Dist.CLIENT)
public class EfficiencyFormulaScreen extends Screen
{
    private final StatsDisplayPanelBE panel;
    private final Screen parentScreen;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private static final int BG = -301331942;
    private static final int CARD_BG = -15724504;
    private static final int BORDER = -14013884;
    private static final int TITLE_COL = -12276993;
    private static final int FORMULA = -3351041;
    private static final int EXPLAIN = -7824965;
    private static final int PARAM = -7820596;
    private static final int VALUE_COL = -12255352;
    private static final int LABEL = -10061927;
    private static final int DIM_COL = -6706484;
    private static final int WARN_COL = -30652;
    private int scrollOffset;
    private int contentHeight;
    
    public EfficiencyFormulaScreen(StatsDisplayPanelBE panel, Screen parentScreen) {
        super(Component.literal("Bolat Efficiency Model"));
        this.scrollOffset = 0;
        this.contentHeight = 0;
        this.panel = panel;
        this.parentScreen = parentScreen;
    }
    
    protected void init() {
        this.panelW = Math.min(360, this.width - 30);
        this.panelH = this.height - 20;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = 10;
    }
    
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        g.fill(0, 0, this.width, this.height, BG);
        g.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, CARD_BG);
        this.drawBorder(g, this.panelX, this.panelY, this.panelW, this.panelH, BORDER);
        final int contentX = this.panelX + 8;
        final int contentW = this.panelW - 16;
        final int contentTop = this.panelY + 4;
        final int contentBot = this.panelY + this.panelH - 14;
        g.enableScissor(this.panelX, contentTop, this.panelX + this.panelW, contentBot);
        int cy = contentTop - this.scrollOffset;
        g.drawCenteredString(this.font, "§l§nBolat Efficiency Model", this.panelX + this.panelW / 2, cy, TITLE_COL);
        cy += 14;
        g.drawCenteredString(this.font, "§7ODE-based mechanical efficiency grading", this.panelX + this.panelW / 2, cy, LABEL);
        cy += 14;
        cy = this.drawSection(g, "Governing ODE", contentX, cy, contentW);
        cy = this.drawFormula(g, "dE/dt = \u03b1 \u00b7 (E_eq \u2212 E) \u2212 \u03a8(t)", contentX, cy, contentW);
        cy += 2;
        cy = this.drawExplain(g, "E(t) \u2208 [0, 100] = efficiency score", contentX, cy, contentW);
        cy = this.drawExplain(g, "\u03b1 = 0.025  (relaxation rate per step)", contentX, cy, contentW);
        cy = this.drawExplain(g, "E_eq = equilibrium (where score wants to be)", contentX, cy, contentW);
        cy = this.drawExplain(g, "\u03a8 = penalty for instability + overstress", contentX, cy, contentW);
        cy = this.drawExplain(g, "Score has inertia, doesn't jump instantly", contentX, cy, contentW);
        cy += 6;
        final String kStr = String.format("%.1f", EfficiencyGrader.KAPPA);
        cy = this.drawSection(g, "Equilibrium, Power Mean (\u03ba=" + kStr + ")", contentX, cy, contentW);
        cy = this.drawFormula(g, "E_eq = 100 \u00b7 [\u03a3 w\u1d62\u00b7\u03a6\u1d62^\u03ba]^(1/\u03ba)", contentX, cy, contentW);
        cy += 2;
        cy = this.drawExplain(g, "\u03ba = Bolat coefficient (controls mean type)", contentX, cy, contentW);
        cy = this.drawExplain(g, "  \u03ba=1 \u2192 arithmetic (linear, lenient)", contentX, cy, contentW);
        cy = this.drawExplain(g, "  \u03ba\u21920 \u2192 geometric (multiplicative, harsh)", contentX, cy, contentW);
        cy = this.drawExplain(g, "  \u03ba=0.6 \u2192 between: weak dims hurt more", contentX, cy, contentW);
        cy += 6;
        cy = this.drawSection(g, "4 Dimensions", contentX, cy, contentW);
        cy = this.drawDimHeader(g, "\u03a6_U  Utilization  (w=0.30)", EfficiencyDimension.UTILIZATION.getBarColor(), contentX, cy, contentW);
        cy = this.drawFormula(g, "\u03a6_U(u) = B \u00b7 u^2.5 \u00b7 (1\u2212u)^0.8", contentX, cy, contentW);
        cy = this.drawExplain(g, "Beta kernel, peak at u*\u224875.8%", contentX, cy, contentW);
        cy = this.drawExplain(g, "u = stress / capacity (utilization ratio)", contentX, cy, contentW);
        cy += 4;
        cy = this.drawDimHeader(g, "\u03a6_H  Headroom  (w=0.20)", EfficiencyDimension.HEADROOM.getBarColor(), contentX, cy, contentW);
        cy = this.drawFormula(g, "\u03a6_H(h) = 1 \u2212 e^(\u2212h / 0.08)", contentX, cy, contentW);
        cy = this.drawExplain(g, "h = 1\u2212u = remaining capacity fraction", contentX, cy, contentW);
        cy = this.drawExplain(g, "Exponential: rapidly penalizes near-zero margin", contentX, cy, contentW);
        cy += 4;
        cy = this.drawDimHeader(g, "\u03a6_S  Stability  (w=0.25)", EfficiencyDimension.STABILITY.getBarColor(), contentX, cy, contentW);
        cy = this.drawFormula(g, "\u03a6_S(cv) = e^(\u2212cv\u00b2 / 0.02)", contentX, cy, contentW);
        cy = this.drawExplain(g, "cv = \u03c3_\u03c9 / \u03bc_\u03c9  (RPM coefficient of variation)", contentX, cy, contentW);
        cy = this.drawExplain(g, "Gaussian: cv=0 \u21921.0, cv=10% \u21920.61, cv=20% \u21920.14", contentX, cy, contentW);
        cy += 4;
        cy = this.drawDimHeader(g, "U_m  Uptime  (w=0.25)", EfficiencyDimension.UPTIME.getBarColor(), contentX, cy, contentW);
        cy = this.drawFormula(g, "dU/dt = ((1\u2212O(t)) \u2212 U) / \u03c4", contentX, cy, contentW);
        cy = this.drawExplain(g, "\u03c4 = 6000 ticks (5 min memory)", contentX, cy, contentW);
        cy = this.drawExplain(g, "O(t) = 1 if overstressed, 0 otherwise", contentX, cy, contentW);
        cy = this.drawExplain(g, "Has memory: one overstress \u2192 minutes to recover", contentX, cy, contentW);
        cy += 6;
        cy = this.drawSection(g, "Transient Penalty \u03a8", contentX, cy, contentW);
        cy = this.drawFormula(g, "\u03a8 = 200\u00b7(du/dt)\u00b2 + 2\u00b7O(t)", contentX, cy, contentW);
        cy = this.drawExplain(g, "Penalizes rapid load changes and active overstress", contentX, cy, contentW);
        cy = this.drawExplain(g, "du/dt = rate of utilization change between steps", contentX, cy, contentW);
        cy += 6;
        cy = this.drawSection(g, "Grade Scale", contentX, cy, contentW);
        cy = this.drawGradeRow(g, "A+\u226595  A\u226590  A-\u226585", -16711800, contentX, cy, contentW);
        cy = this.drawGradeRow(g, "B+\u226580  B\u226575  B-\u226570", -3355648, contentX, cy, contentW);
        cy = this.drawGradeRow(g, "C+\u226565  C\u226560  C-\u226555", -30720, contentX, cy, contentW);
        cy = this.drawGradeRow(g, "D+\u226550  D\u226545  D-\u226540  F<40", -48128, contentX, cy, contentW);
        cy += 6;
        final EfficiencyScore score = this.panel.getLatestEfficiencyScore();
        final GradeHistory history = this.panel.getGradeHistory();
        if (score != null && !"?".equals(score.grade())) {
            cy = this.drawSection(g, "Live Values", contentX, cy, contentW);
            final EfficiencyGrader.ODEState state = history.getODEState();
            final String eStr = String.format("E = %.1f  (grade: %s)", score.compositeScore(), score.grade());
            g.drawString(this.font, eStr, contentX + 4, cy, score.gradeColor());
            cy += 11;
            for (EfficiencyDimension dim : EfficiencyDimension.values()) {
                final float pct = score.getDimensionPercent(dim);
                final String line = String.format("  %s: %.1f%%  (w=%.0f%%)", dim.getDisplayName(), pct, dim.getWeight() * 100.0f);
                final int col = (pct >= 80.0f) ? VALUE_COL : ((pct >= 50.0f) ? -13312 : WARN_COL);
                g.drawString(this.font, line, contentX + 4, cy, col);
                cy += 10;
            }
            cy += 2;
            final String uStr = (state.previousU >= 0.0f) ? String.format("u = %.3f  (utilization)", state.previousU) : "u =, (warming up)";
            g.drawString(this.font, uStr, contentX + 4, cy, PARAM);
            cy += 10;
            g.drawString(this.font, String.format("U_mem = %.3f  (uptime memory)", state.uptimeMemory), contentX + 4, cy, PARAM);
            cy += 10;
            final float phiU = EfficiencyGrader.computePhiU((state.previousU >= 0.0f) ? state.previousU : 0.0f);
            final float phiH = EfficiencyGrader.computePhiH((state.previousU >= 0.0f) ? state.previousU : 0.0f);
            final float eEq = EfficiencyGrader.computeEquilibrium(phiU, phiH, score.getDimensionPercent(EfficiencyDimension.STABILITY) / 100.0f, state.uptimeMemory);
            g.drawString(this.font, String.format("E_eq = %.1f  (equilibrium target)", eEq), contentX + 4, cy, FORMULA);
            cy += 14;
        }
        this.contentHeight = cy - (contentTop - this.scrollOffset) + 10;
        g.disableScissor();
        if (this.contentHeight > this.panelH - 18) {
            final int scrollBarH = Math.max(10, (this.panelH - 18) * (this.panelH - 18) / this.contentHeight);
            final int maxScroll = this.contentHeight - (this.panelH - 18);
            final int scrollBarY = this.panelY + 4 + (int)(this.scrollOffset / (float)maxScroll * (this.panelH - 18 - scrollBarH));
            g.fill(this.panelX + this.panelW - 4, scrollBarY, this.panelX + this.panelW - 1, scrollBarY + scrollBarH, 1157627903);
        }
        g.drawCenteredString(this.font, "§7Scroll \u2195  |  ESC to close", this.panelX + this.panelW / 2, this.panelY + this.panelH - 10, -12303258);
    }
    
    private int drawSection(GuiGraphics g, String title, int x, int y, int w) {
        g.fill(x, y + 4, x + w, y + 5, -13421739);
        g.drawString(this.font, "§l" + title, x + 2, y + 7, TITLE_COL);
        return y + 19;
    }
    
    private int drawFormula(GuiGraphics g, String formula, int x, int y, int w) {
        final int fw = this.font.width(formula);
        final int fx = x + (w - fw) / 2;
        g.fill(fx - 4, y - 1, fx + fw + 4, y + 10, -15395536);
        this.drawBorder(g, fx - 4, y - 1, fw + 8, 11, -14342843);
        g.drawString(this.font, formula, fx, y, FORMULA);
        return y + 13;
    }
    
    private int drawExplain(GuiGraphics g, String text, int x, int y, int w) {
        g.drawString(this.font, text, x + 8, y, EXPLAIN);
        return y + 10;
    }
    
    private int drawDimHeader(GuiGraphics g, String text, int color, int x, int y, int w) {
        g.fill(x + 2, y + 1, x + 5, y + 8, color);
        g.drawString(this.font, text, x + 8, y, DIM_COL);
        return y + 12;
    }
    
    private int drawGradeRow(GuiGraphics g, String text, int color, int x, int y, int w) {
        g.drawCenteredString(this.font, text, x + w / 2, y, color);
        return y + 11;
    }
    
    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        final int maxScroll = Math.max(0, this.contentHeight - (this.panelH - 18));
        this.scrollOffset -= (int)(scrollY * 14.0);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
        return true;
    }
    
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
    
    public boolean isPauseScreen() {
        return false;
    }
}
