package com.createstats.gui;

import com.createstats.blockentity.LoadBalancerBE;
import com.createstats.blockentity.LoadBalancerSettings;
import com.createstats.gui.pdu.Ease;
import com.createstats.gui.pdu.PduCard;
import com.createstats.gui.pdu.PduChip;
import com.createstats.gui.pdu.PduGauge;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduSegmented;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.network.LoadBalancerConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LoadBalancerScreen extends Screen {
    private static final int PANEL_W = 424;
    private static final int PANEL_H = 268;
    private static final int HEADER_H = 24;
    private static final int FOOTER_H = 18;
    private static final int DIAGRAM_H = 112;
    private static final int NODE_H = 36;

    private static final int LEG = 30;
    private static final int SRC_W = 104;
    private static final int SPLIT_W = 84;
    private static final int BRANCH_W = 116;
    private static final int SPLIT_BAR_H = 12;
    private static final int BAND_BAR_H = 8;

    private static final int ACT_PRESET = 1;
    private static final int ACT_SHARING = 2;
    private static final int ACT_LIMIT_MODE = 3;
    private static final int ACT_ENABLED = 4;
    private static final int ACT_MIN_RPM = 5;
    private static final int ACT_MAX_RPM = 6;
    private static final int ACT_SPLIT_BAR = 7;
    private static final int ACT_BAND_BAR = 8;

    private final LoadBalancerBE balancer;
    private final Screen returnScreen;

    private final LoadBalancerSettings settings;

    private final PduSegmented sharingSeg = new PduSegmented("Strict", "Elastic");
    private final PduSegmented bandSeg = new PduSegmented("%", "SU");

    private PduRect panel = new PduRect(0, 0, 0, 0);
    private PduRect diagram = new PduRect(0, 0, 0, 0);
    private PduRect splitCard = new PduRect(0, 0, 0, 0);
    private PduRect limitsCard = new PduRect(0, 0, 0, 0);
    private PduRect splitBar = new PduRect(0, 0, 0, 0);
    private PduRect bandBar = new PduRect(0, 0, 0, 0);

    private final List<Hit> hits = new ArrayList<>();

    private int dragging;

    private boolean dragDirty;

    private float dragSpan;

    private float shownLeftPct = -1.0f;
    private float shownLeftLoad;
    private float shownRightLoad;
    private float shownStress;

    private record Hit(PduRect bounds, int action, int value) {
    }

    public LoadBalancerScreen(LoadBalancerBE balancer) {
        this(balancer, null);
    }

    public LoadBalancerScreen(LoadBalancerBE balancer, Screen returnScreen) {
        super(Component.translatable("block.create_stats.load_balancer"));
        this.balancer = balancer;
        this.returnScreen = returnScreen;
        this.settings = LoadBalancerSettings.load(balancer.getSettings().save());
    }

    @Override
    protected void init() {
        final int w = Math.min(PANEL_W, width - 16);
        final int h = Math.min(PANEL_H, height - 16);
        panel = new PduRect((width - w) / 2, (height - h) / 2, w, h);

        diagram = new PduRect(panel.x() + PduTheme.GAP, panel.y() + HEADER_H + PduTheme.GAP,
                w - PduTheme.GAP * 2, DIAGRAM_H);

        final int ctlY = diagram.bottom() + PduTheme.GAP;
        final int ctlH = panel.bottom() - FOOTER_H - PduTheme.GAP - ctlY;
        final int ctlW = (diagram.w() - PduTheme.GAP) / 2;
        splitCard = new PduRect(diagram.x(), ctlY, ctlW, ctlH);
        limitsCard = new PduRect(diagram.x() + ctlW + PduTheme.GAP, ctlY, ctlW, ctlH);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        ease(partialTick);

        g.fill(0, 0, width, height, 0xB8000000);
        g.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, panel.x(), panel.y(), panel.w(), panel.h(), PduTheme.STROKE);

        drawHeader(g);
        drawDiagram(g);
        drawSplitCard(g, mouseX, mouseY);
        drawLimitsCard(g, mouseX, mouseY);
        drawFooter(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void ease(float partialTick) {
        final float targetSplit = settings.getLeftSharePct();
        if (shownLeftPct < 0.0f) {
            shownLeftPct = targetSplit;
            shownLeftLoad = branchLoad(true);
            shownRightLoad = branchLoad(false);
            shownStress = balancer.getLastStressPercent();
            return;
        }

        shownLeftPct = dragging == 1 ? targetSplit
                : Ease.ease(shownLeftPct, targetSplit, 0.35f, partialTick);
        shownLeftLoad = Ease.ease(shownLeftLoad, branchLoad(true), 0.22f, partialTick);
        shownRightLoad = Ease.ease(shownRightLoad, branchLoad(false), 0.22f, partialTick);
        shownStress = Ease.ease(shownStress, balancer.getLastStressPercent(), 0.22f, partialTick);
    }

    private void drawHeader(GuiGraphics g) {
        final int y = panel.y();
        g.fill(panel.x() + 1, y + 1, panel.right() - 1, y + HEADER_H, PduTheme.BG_PANEL);
        g.fill(panel.x() + 1, y + HEADER_H - 1, panel.right() - 1, y + HEADER_H, PduTheme.STROKE);
        g.drawString(font, "LOAD BALANCER", panel.x() + PduTheme.PAD, y + 8, PduTheme.TEXT, false);

        final LoadBalancerBE.ReportedState state = balancer.getReportedState();
        final String label = stateLabel(state);
        final int chipW = PduChip.width(font, label);
        PduChip.draw(g, font, panel.right() - PduTheme.PAD - chipW, y + 6, label,
                severityColour(state.severity()), false, false);

        final String load = String.format(Locale.ROOT, "%.0f%% load", shownStress);
        g.drawString(font, load,
                panel.right() - PduTheme.PAD - chipW - PduTheme.GAP - font.width(load), y + 8,
                PduTheme.TEXT_DIM, false);
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        final int y = panel.bottom() - FOOTER_H;
        g.fill(panel.x() + 1, y, panel.right() - 1, panel.bottom() - 1, PduTheme.BG_PANEL);
        g.fill(panel.x() + 1, y, panel.right() - 1, y + 1, PduTheme.STROKE);

        final LoadBalancerBE.ReportedState state = balancer.getReportedState();

        final String hint = Component.translatable(state.hintKey()).getString();
        final String label = settings.isEnabled() ? "Enabled" : "Disabled";
        final int chipW = PduChip.width(font, label);
        final int chipX = panel.right() - PduTheme.PAD - chipW;

        g.drawString(font, trim(hint, chipX - panel.x() - PduTheme.PAD * 2),
                panel.x() + PduTheme.PAD, y + 5,
                state.severity() == LoadBalancerBE.Severity.OK
                        ? PduTheme.TEXT_DIM : severityColour(state.severity()), false);

        final PduRect chip = new PduRect(chipX, y + 3, chipW, PduChip.HEIGHT);
        PduChip.draw(g, font, chip.x(), chip.y(), label,
                settings.isEnabled() ? PduTheme.OK : PduTheme.TEXT_FAINT,
                chip.contains(mouseX, mouseY), false);
        hits.add(new Hit(chip, ACT_ENABLED, settings.isEnabled() ? 0 : 1));
    }

    private void drawDiagram(GuiGraphics g) {
        PduTheme.panel(g, diagram.x(), diagram.y(), diagram.w(), diagram.h());
        final PduRect c = diagram.inset(PduTheme.PAD);
        final int mid = c.centerY();

        final int srcX = c.x();
        final int splitX = c.x() + SRC_W + LEG;
        final int branchX = c.right() - BRANCH_W;
        final int nodeY = mid - NODE_H / 2;
        final int leftY = c.y();
        final int rightY = c.bottom() - NODE_H;

        final LoadBalancerBE.ReportedState state = balancer.getReportedState();
        final int tint = severityColour(state.severity());
        final boolean leftAtt = balancer.isLeftBranchAttached();
        final boolean rightAtt = balancer.isRightBranchAttached();
        final float srcRpm = balancer.getSourceSpeed();
        final float leftRpm = leftAtt ? balancer.getTransmittedSpeedLeft() : 0.0f;
        final float rightRpm = rightAtt ? balancer.getTransmittedSpeedRight() : 0.0f;

        // liveness per leg

        final int leftLegTint = leftAtt ? PduTheme.OK : PduTheme.WARN;
        final int rightLegTint = rightAtt ? PduTheme.OK : PduTheme.WARN;
        final int elbow = splitX + SPLIT_W + LEG / 2;

        legH(g, srcX + SRC_W, splitX, mid, tint, srcRpm);
        legH(g, splitX + SPLIT_W, elbow, mid, tint, srcRpm);
        legV(g, elbow, leftY + NODE_H / 2, mid, leftLegTint, leftRpm);
        legV(g, elbow, mid, rightY + NODE_H / 2, rightLegTint, rightRpm);
        legH(g, elbow, branchX, leftY + NODE_H / 2, leftLegTint, leftRpm);
        legH(g, elbow, branchX, rightY + NODE_H / 2, rightLegTint, rightRpm);

        node(g, new PduRect(srcX, nodeY, SRC_W, NODE_H), "Source",
                String.format(Locale.ROOT, "%.0f RPM", srcRpm),
                String.format(Locale.ROOT, "%.0f SU base", balancer.getLastBaseStressSU()),
                PduTheme.INFO, -1.0f);
        node(g, new PduRect(splitX, nodeY, SPLIT_W, NODE_H), "Split",
                String.format(Locale.ROOT, "%.0f / %.0f",
                        settings.getLeftSharePct(), settings.getRightSharePct()),
                splitRuntimeText(), tint, -1.0f);

        node(g, new PduRect(branchX, leftY, BRANCH_W, NODE_H), "Left",
                String.format(Locale.ROOT, "%.0f RPM  x%.2f", leftRpm, balancer.getCurrentModifierLeft()),
                leftAtt ? String.format(Locale.ROOT, "%.0f/%.0f SU",
                        balancer.getLastLeftStressSU(), balancer.getLastLeftBudgetSU()) : "no branch",
                leftLegTint, shownLeftLoad);
        node(g, new PduRect(branchX, rightY, BRANCH_W, NODE_H), "Right",
                String.format(Locale.ROOT, "%.0f RPM  x%.2f", rightRpm, balancer.getCurrentModifierRight()),
                rightAtt ? String.format(Locale.ROOT, "%.0f/%.0f SU",
                        balancer.getLastRightStressSU(), balancer.getLastRightBudgetSU()) : "no branch",
                rightLegTint, shownRightLoad);
    }

    private void node(GuiGraphics g, PduRect r, String heading, String line1, String line2,
                      int tint, float load) {
        PduTheme.panel(g, r.x(), r.y(), r.w(), r.h());

        g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), tint);
        PduCard.drawHeading(g, font, heading, r.x() + 7, r.y() + 4);
        g.drawString(font, trim(line1, r.w() - 14), r.x() + 7, r.y() + 15, PduTheme.TEXT, false);
        if (load >= 0.0f) {
            g.drawString(font, line2, r.right() - 7 - font.width(line2), r.y() + 4,
                    PduTheme.TEXT_DIM, false);
            PduGauge.drawHorizontal(g, r.x() + 7, r.bottom() - 10, r.w() - 14, 5,
                    load, -1.0f, loadColour(load, tint));
        } else {
            g.drawString(font, trim(line2, r.w() - 14), r.x() + 7, r.y() + 25,
                    PduTheme.TEXT_DIM, false);
        }
    }

    private void legH(GuiGraphics g, int x0, int x1, int y, int colour, float rpm) {
        if (x1 <= x0) {
            return;
        }
        g.fill(x0, y, x1, y + 1, PduTheme.STROKE);
        if (rpm <= 0.0f) {
            return;
        }
        final int phase = dashPhase(rpm);
        for (int x = x0; x < x1; x++) {
            if (Math.floorMod(x - x0 - phase, 8) < 3) {
                g.fill(x, y, x + 1, y + 1, colour);
            }
        }
    }

    private void legV(GuiGraphics g, int x, int y0, int y1, int colour, float rpm) {
        final int lo = Math.min(y0, y1);
        final int hi = Math.max(y0, y1);
        if (hi <= lo) {
            return;
        }
        g.fill(x, lo, x + 1, hi, PduTheme.STROKE);
        if (rpm <= 0.0f) {
            return;
        }
        final int phase = dashPhase(rpm);
        for (int y = lo; y < hi; y++) {
            if (Math.floorMod(y - lo - phase, 8) < 3) {
                g.fill(x, y, x + 1, y + 1, colour);
            }
        }
    }

    private int dashPhase(float rpm) {
        if (Ease.reduceMotion()) {
            return 0;
        }
        final float speed = 6.0f + Math.min(40.0f, Math.abs(rpm) / 4.0f);
        return (int) ((System.currentTimeMillis() * speed / 1000.0f) % 8);
    }

    private void drawSplitCard(GuiGraphics g, int mouseX, int mouseY) {
        final PduRect c = PduCard.draw(g, font, splitCard, "Split");
        int y = c.y() + 2;

        splitBar = new PduRect(c.x(), y, c.w(), SPLIT_BAR_H);
        final int boundary = c.x() + Math.round(c.w() * shownLeftPct / 100.0f);
        g.fill(splitBar.x(), splitBar.y(), splitBar.right(), splitBar.bottom(), PduTheme.BG_DEEP);
        g.fill(splitBar.x() + 1, splitBar.y() + 1, boundary, splitBar.bottom() - 1,
                PduTheme.withAlpha(PduTheme.OK, 0xCC));
        g.fill(boundary, splitBar.y() + 1, splitBar.right() - 1, splitBar.bottom() - 1,
                PduTheme.withAlpha(PduTheme.INFO, 0xCC));
        PduTheme.outline(g, splitBar.x(), splitBar.y(), splitBar.w(), splitBar.h(),
                splitBar.contains(mouseX, mouseY) ? PduTheme.STROKE_HOT : PduTheme.STROKE);
        g.fill(boundary - 1, splitBar.y() - 2, boundary + 2, splitBar.bottom() + 2, PduTheme.ACCENT);
        g.drawString(font, String.format(Locale.ROOT, "L %.0f%%", settings.getLeftSharePct()),
                splitBar.x() + 3, splitBar.y() + 2, PduTheme.TEXT, false);
        final String right = String.format(Locale.ROOT, "R %.0f%%", settings.getRightSharePct());
        g.drawString(font, right, splitBar.right() - 3 - font.width(right), splitBar.y() + 2,
                PduTheme.TEXT, false);
        hits.add(new Hit(splitBar, ACT_SPLIT_BAR, 0));
        y += SPLIT_BAR_H + PduTheme.GAP;

        int px = c.x();
        for (int preset : new int[] {25, 50, 75}) {
            final String label = preset + "/" + (100 - preset);
            final PduRect r = new PduRect(px, y, PduChip.width(font, label), PduChip.HEIGHT);
            PduChip.draw(g, font, r.x(), r.y(), label, PduTheme.ACCENT,
                    r.contains(mouseX, mouseY), false);
            hits.add(new Hit(r, ACT_PRESET, preset));
            px += r.w() + PduTheme.BASE;
        }
        y += PduChip.HEIGHT + PduTheme.GAP;

        g.drawString(font, "Sharing", c.x(), y + 3, PduTheme.TEXT_DIM, false);
        sharingSeg.setSelected(
                settings.getSharingMode() == LoadBalancerSettings.SharingMode.ELASTIC ? 1 : 0);
        sharingSeg.draw(g, font, c.x() + 46, y, 14, mouseX, mouseY);
        hits.add(new Hit(new PduRect(c.x() + 46, y, sharingSeg.width(font), 14), ACT_SHARING, 0));
    }

    private void drawLimitsCard(GuiGraphics g, int mouseX, int mouseY) {
        final PduRect c = PduCard.draw(g, font, limitsCard, "Limits");
        int y = c.y() + 2;
        final boolean pct = settings.getLimitMode() == LoadBalancerSettings.LimitMode.PERCENTAGE;

        g.drawString(font, "Band", c.x(), y + 3, PduTheme.TEXT_DIM, false);
        bandSeg.setSelected(pct ? 0 : 1);
        bandSeg.draw(g, font, c.x() + 34, y, 14, mouseX, mouseY);
        hits.add(new Hit(new PduRect(c.x() + 34, y, bandSeg.width(font), 14), ACT_LIMIT_MODE, 0));

        final String range = pct
                ? String.format(Locale.ROOT, "%.0f - %.0f%%", settings.getLowerBound(), settings.getUpperBound())
                : String.format(Locale.ROOT, "%.0f - %.0f SU", settings.getLowerBound(), settings.getUpperBound());
        g.drawString(font, range, c.right() - font.width(range), y + 3, PduTheme.TEXT, false);
        y += 20;

        bandBar = new PduRect(c.x(), y, c.w(), BAND_BAR_H);
        final float span = activeBandSpan();
        g.fill(bandBar.x(), bandBar.y(), bandBar.right(), bandBar.bottom(), PduTheme.BG_DEEP);
        final int lo = bandBar.x() + Math.round(bandBar.w() * settings.getLowerBound() / span);
        final int hi = bandBar.x() + Math.round(bandBar.w() * settings.getUpperBound() / span);
        g.fill(lo, bandBar.y() + 1, hi, bandBar.bottom() - 1,
                PduTheme.withAlpha(PduTheme.ACCENT, 0x66));
        final int cur = bandBar.x()
                + Math.round(bandBar.w() * Mth.clamp(currentAgainstBand() / span, 0.0f, 1.0f));
        g.fill(cur, bandBar.y() - 2, cur + 1, bandBar.bottom() + 2,
                severityColour(balancer.getReportedState().severity()));
        PduTheme.outline(g, bandBar.x(), bandBar.y(), bandBar.w(), bandBar.h(),
                bandBar.contains(mouseX, mouseY) ? PduTheme.STROKE_HOT : PduTheme.STROKE);
        hits.add(new Hit(bandBar, ACT_BAND_BAR, 0));
        y += BAND_BAR_H + PduTheme.GAP;

        drawStepper(g, c.x(), y, 74, "Min RPM", settings.getMinimumRPM(), ACT_MIN_RPM, mouseX, mouseY);
        drawStepper(g, c.x() + 84, y, c.w() - 84, "Max RPM", settings.getMaximumRPM(), ACT_MAX_RPM,
                mouseX, mouseY);
    }

    private void drawStepper(GuiGraphics g, int x, int y, int w, String label, float value,
                             int action, int mouseX, int mouseY) {
        final PduRect down = new PduRect(x, y, w / 2, 10);
        final PduRect up = new PduRect(x + w / 2, y, w - w / 2, 10);
        final boolean hoverDown = down.contains(mouseX, mouseY);
        final boolean hoverUp = up.contains(mouseX, mouseY);

        g.drawString(font, label, x, y + 2,
                hoverDown || hoverUp ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
        final String shown = String.format(Locale.ROOT, "%.0f", value);
        g.drawString(font, shown, x + w - font.width(shown), y + 2, PduTheme.TEXT, false);
        if (hoverDown) {
            g.drawString(font, "-", x + font.width(label) + 4, y + 2, PduTheme.ACCENT, false);
        }
        if (hoverUp) {
            g.drawString(font, "+", x + w - font.width(shown) - 8, y + 2, PduTheme.ACCENT, false);
        }
        hits.add(new Hit(down, action, -8));
        hits.add(new Hit(up, action, 8));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Hit hit : hits) {
            if (!hit.bounds().contains(mouseX, mouseY)) {
                continue;
            }
            switch (hit.action()) {
                case ACT_SPLIT_BAR -> {
                    dragging = 1;
                    applySplitFromMouse(mouseX);
                }
                case ACT_BAND_BAR -> {
                    dragSpan = bandSpan();
                    final float at = valueAtBand(mouseX);
                    dragging = Math.abs(at - settings.getLowerBound())
                            <= Math.abs(at - settings.getUpperBound()) ? 2 : 3;
                    applyBandFromMouse(mouseX);
                }
                case ACT_PRESET -> {
                    settings.setLeftSharePct(hit.value());
                    push();
                }
                case ACT_SHARING -> {
                    final int seg = sharingSeg.segmentAt(mouseX, mouseY);
                    if (seg >= 0) {
                        settings.setSharingMode(seg == 1
                                ? LoadBalancerSettings.SharingMode.ELASTIC
                                : LoadBalancerSettings.SharingMode.STRICT);
                        push();
                    }
                }
                case ACT_LIMIT_MODE -> {
                    final int seg = bandSeg.segmentAt(mouseX, mouseY);
                    final boolean wantPct = seg == 0;
                    if (seg >= 0 && wantPct
                            != (settings.getLimitMode() == LoadBalancerSettings.LimitMode.PERCENTAGE)) {
                        settings.cycleLimitMode();
                        push();
                    }
                }
                case ACT_ENABLED -> {
                    settings.setEnabled(hit.value() != 0);
                    push();
                }
                case ACT_MIN_RPM -> {
                    settings.setMinimumRPM(settings.getMinimumRPM() + hit.value());
                    push();
                }
                case ACT_MAX_RPM -> {
                    settings.setMaximumRPM(settings.getMaximumRPM() + hit.value());
                    push();
                }
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging == 1) {
            applySplitFromMouse(mouseX);
            return true;
        }
        if (dragging == 2 || dragging == 3) {
            applyBandFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != 0) {
            dragging = 0;
            dragSpan = 0.0f;
            if (dragDirty) {
                dragDirty = false;
                push();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (splitBar.contains(mouseX, mouseY)) {
            settings.setLeftSharePct(settings.getLeftSharePct() + (float) Math.signum(scrollY));
            push();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void applySplitFromMouse(double mouseX) {
        final float pct = (float) (mouseX - splitBar.x()) / Math.max(1, splitBar.w()) * 100.0f;
        settings.setLeftSharePct(Math.round(Mth.clamp(pct, 0.0f, 100.0f)));
        dragDirty = true;
    }

    private void applyBandFromMouse(double mouseX) {
        final float v = valueAtBand(mouseX);
        if (dragging == 2) {
            settings.setLowerBound(v);
        } else {
            settings.setUpperBound(v);
        }
        dragDirty = true;
    }

    private float valueAtBand(double mouseX) {
        final float t = (float) (mouseX - bandBar.x()) / Math.max(1, bandBar.w());
        return Math.round(Mth.clamp(t, 0.0f, 1.0f) * activeBandSpan());
    }

    private float activeBandSpan() {
        return (dragging == 2 || dragging == 3) && dragSpan > 0.0f ? dragSpan : bandSpan();
    }

    private float bandSpan() {
        if (settings.getLimitMode() == LoadBalancerSettings.LimitMode.PERCENTAGE) {
            return 100.0f;
        }
        return Math.max(64.0f, Math.max(balancer.getLastCapacitySU(), settings.getUpperBound()));
    }

    private float currentAgainstBand() {
        if (settings.getLimitMode() == LoadBalancerSettings.LimitMode.PERCENTAGE) {
            return shownStress;
        }
        return balancer.getLastStressSU();
    }

    private void push() {
        PacketDistributor.sendToServer(new LoadBalancerConfigPacket(balancer.getBlockPos(), settings));
    }

    @Override
    public void onClose() {
        if (dragDirty) {
            dragDirty = false;
            push();
        }
        if (returnScreen != null) {
            Minecraft.getInstance().setScreen(returnScreen);
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String splitRuntimeText() {
        if (!settings.isEnabled()) {
            return "disabled";
        }
        if (balancer.getReportedState().severity() != LoadBalancerBE.Severity.OK) {
            return settings.getSharingMode().getDisplayName();
        }
        if (settings.getSharingMode() == LoadBalancerSettings.SharingMode.STRICT) {
            return "strict share";
        }
        final float totalHigh = settings.getLimitMode() == LoadBalancerSettings.LimitMode.PERCENTAGE
                ? balancer.getLastCapacitySU() * settings.getUpperBound() / 100.0f
                : settings.getUpperBound();
        final float strictLeft = totalHigh * settings.getLeftSharePct() / 100.0f;
        final float strictRight = totalHigh - strictLeft;
        final boolean borrowLeft = balancer.getLastLeftBudgetSU() > strictLeft + 0.5f
                && balancer.getLastLeftStressSU() > strictLeft + 0.5f;
        final boolean borrowRight = balancer.getLastRightBudgetSU() > strictRight + 0.5f
                && balancer.getLastRightStressSU() > strictRight + 0.5f;
        if (borrowLeft && !borrowRight) {
            return "borrowing left";
        }
        if (borrowRight && !borrowLeft) {
            return "borrowing right";
        }
        final float used = balancer.getLastBaseStressSU()
                + balancer.getLastLeftStressSU() + balancer.getLastRightStressSU();
        return used < totalHigh - 0.5f ? "filling spare" : "elastic hold";
    }

    private float branchLoad(boolean left) {
        final boolean attached = left ? balancer.isLeftBranchAttached() : balancer.isRightBranchAttached();
        final float budget = left ? balancer.getLastLeftBudgetSU() : balancer.getLastRightBudgetSU();
        if (!attached || budget <= 0.0f) {
            return 0.0f;
        }
        final float stress = left ? balancer.getLastLeftStressSU() : balancer.getLastRightStressSU();
        return Mth.clamp(stress / budget, 0.0f, 1.0f);
    }

    private static int loadColour(float load, int fallback) {
        if (load >= 0.98f) {
            return PduTheme.CRIT;
        }
        if (load >= 0.85f) {
            return PduTheme.WARN;
        }
        return fallback;
    }

    private static int severityColour(LoadBalancerBE.Severity severity) {
        return switch (severity) {
            case OK -> PduTheme.OK;
            case NOTICE -> PduTheme.TEXT_DIM;
            case INCOMPLETE -> PduTheme.WARN;
            case FAULT, LIMIT -> PduTheme.CRIT;
        };
    }

    private static String stateLabel(LoadBalancerBE.ReportedState state) {
        return Component.translatable(state.stateKey()).getString()
                .replaceAll("§.", "").replaceAll("[^A-Za-z0-9 /-]", "").trim();
    }

    private String trim(String s, int maxWidth) {
        if (s == null || s.isEmpty() || font.width(s) <= maxWidth) {
            return s == null ? "" : s;
        }
        return font.plainSubstrByWidth(s, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
}
