package com.createstats.gui;

import com.createstats.blockentity.SignalPattern;
import com.createstats.client.AnimationClock;
import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.blockentity.StatusTowerLightSettings;
import com.createstats.gui.pdu.Ease;
import com.createstats.gui.pdu.PduCard;
import com.createstats.gui.pdu.PduChip;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduSegmented;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.network.StatusTowerLightConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatusTowerLightScreen extends Screen {
    private static final int IDEAL_W = 468;
    private static final int IDEAL_H = 336;
    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 26;

    private static final int PREVIEW_W = 112;

    private static final int TOP_H = 78;
    private static final int TOP_H_MIN = 56;
    private static final int APPEARANCE_H = 78;
    private static final int APPEARANCE_H_MIN = 62;
    private static final int RULE_H_MIN = 62;

    private static final int ACT_SOURCE_MODE = 1;
    private static final int ACT_METRIC = 2;
    private static final int ACT_DIRECTION = 3;
    private static final int ACT_INVERT = 4;
    private static final int ACT_LENS = 5;
    private static final int ACT_STYLE = 6;
    private static final int ACT_SOUNDER = 7;
    private static final int ACT_WARN_STEP = 8;
    private static final int ACT_CRIT_STEP = 9;

    private final StatusTowerLightBE tower;
    private final StatusTowerLightSettings settings;

    private PduRect panel = new PduRect(0, 0, 0, 0);
    private PduRect previewCard = new PduRect(0, 0, 0, 0);
    private PduRect sourceCard = new PduRect(0, 0, 0, 0);
    private PduRect metricCard = new PduRect(0, 0, 0, 0);
    private PduRect ruleCard = new PduRect(0, 0, 0, 0);
    private PduRect appearanceCard = new PduRect(0, 0, 0, 0);

    private PduRect track = new PduRect(0, 0, 0, 0);

    private PduSegmented sourceMode;
    private PduSegmented direction;
    private PduSegmented style;
    private PduSegmented sounder;

    private final List<Hit> hits = new ArrayList<>();

    private int dragging;

    private float shownLevel = -1.0f;

    private float shownValue = Float.NaN;

    private record Hit(PduRect bounds, int action, int index) {
    }

    public StatusTowerLightScreen(StatusTowerLightBE tower) {
        super(Component.translatable("block.create_stats.status_tower_light"));
        this.tower = tower;

        this.settings = StatusTowerLightSettings.load(tower.getSettings().save());
    }

    @Override
    protected void init() {
        final int w = Math.min(IDEAL_W, width - 16);
        final int h = Math.min(IDEAL_H, height - 16);
        panel = new PduRect((width - w) / 2, (height - h) / 2, w, h);

        settings.ensureMetricSupported(sourceKind());

        sourceMode = new PduSegmented("Local", "Linked");
        sourceMode.setSelected(settings.getSourceMode().ordinal());
        direction = new PduSegmented("Higher is worse", "Lower is worse");
        direction.setSelected(settings.getThresholdDirection().ordinal());
        style = new PduSegmented("Classic", "Calm", "Urgent", "Beacon", "Solid");
        style.setSelected(settings.getAlertStyle().ordinal());
        sounder = new PduSegmented("Off", "Critical", "Warning");
        sounder.setSelected(settings.getSounder().ordinal());

        layout();
    }

    private void layout() {
        final int bodyY = panel.y() + HEADER_H;
        final int bodyH = panel.h() - HEADER_H - FOOTER_H;
        final int gap = PduTheme.GAP;
        final int previewW = Math.min(PREVIEW_W, panel.w() / 4);

        previewCard = new PduRect(panel.x() + gap, bodyY + gap, previewW, bodyH - gap * 2);

        final int rightX = previewCard.right() + gap;
        final int rightW = panel.right() - gap - rightX;

        int topH = TOP_H;
        int appearanceH = APPEARANCE_H;
        int ruleH = bodyH - gap * 4 - topH - appearanceH;

        if (ruleH < RULE_H_MIN) {
            final int shortfall = RULE_H_MIN - ruleH;
            final int fromTop = Math.min(shortfall, topH - TOP_H_MIN);
            topH -= fromTop;
            appearanceH -= Math.min(shortfall - fromTop, appearanceH - APPEARANCE_H_MIN);
            ruleH = Math.max(0, bodyH - gap * 4 - topH - appearanceH);
        }

        final int colW = (rightW - gap) / 2;
        sourceCard = new PduRect(rightX, bodyY + gap, colW, topH);
        metricCard = new PduRect(rightX + colW + gap, bodyY + gap, rightW - colW - gap, topH);
        ruleCard = new PduRect(rightX, sourceCard.bottom() + gap, rightW, ruleH);
        appearanceCard = new PduRect(rightX, ruleCard.bottom() + gap, rightW, appearanceH);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        g.fill(0, 0, width, height, 0xB8000000);
        g.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, panel.x(), panel.y(), panel.w(), panel.h(), PduTheme.STROKE);

        drawHeader(g);
        drawPreview(g, partialTick);
        drawSource(g, mouseX, mouseY);
        drawMetric(g, mouseX, mouseY);
        drawRule(g, mouseX, mouseY);
        drawAppearance(g, mouseX, mouseY);
        drawFooter(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics g) {
        final int y = panel.y();
        g.fill(panel.x() + 1, y + 1, panel.right() - 1, y + HEADER_H, PduTheme.BG_PANEL);
        g.fill(panel.x() + 1, y + HEADER_H - 1, panel.right() - 1, y + HEADER_H, PduTheme.STROKE);
        g.drawString(font, "STATUS TOWER LIGHT", panel.x() + PduTheme.PAD, y + 6, PduTheme.TEXT, false);
        g.drawString(font, tower.getSourceSummary(), panel.x() + PduTheme.PAD, y + 16,
                PduTheme.TEXT_DIM, false);

        final StatusTowerLightBE.SignalState live = tower.getSignalState();
        final String label = live.getDisplayName().toUpperCase(Locale.ROOT);
        final int tint = severityTint(live);
        final int textX = panel.right() - PduTheme.PAD - font.width(label);
        g.drawString(font, label, textX, y + 6, tint, false);
        g.fill(textX - 10, y + 7, textX - 4, y + 13, tint);
    }

    private void drawPreview(GuiGraphics g, float partialTick) {
        final PduRect body = PduCard.draw(g, font, previewCard, "Preview");

        final StatusTowerLightBE.SignalState severity = previewSeverity();
        final SignalPattern pattern = settings.getAlertStyle().patternFor(severity);
        final int colour = previewColour(severity);
        final double seconds = AnimationClock.time(gameTime(), partialTick) / 20.0D;

        final float level = (float) pattern.brightness(seconds, 0.0D, 0.35D);
        shownLevel = shownLevel < 0.0f ? level : Ease.ease(shownLevel, level, 24.0f, partialTick);

        final int scale = Math.max(2, Math.min(5, (body.h() - 34) / 16));
        final int towerH = 16 * scale;
        final int cx = body.centerX();

        final int readoutH = 32;
        final int groupTop = body.y() + Math.max(0, (body.h() - towerH - readoutH) / 2);
        final int baseY = groupTop + towerH;

        if (shownLevel > 0.02f) {
            for (int shell = 3; shell >= 1; shell--) {
                final int a = (int) (34.0f * shownLevel * shownLevel / shell);
                if (a <= 2) {
                    continue;
                }
                final int grow = shell * 2;
                g.fill(cx - 4 * scale / 2 - grow, baseY - (int) (12.0 * scale) - grow,
                        cx + 4 * scale / 2 + grow, baseY - (int) (5.1 * scale) + grow,
                        PduTheme.withAlpha(colour, a));
            }
        }

        final int halfW = Math.max(3, (int) (3.4 * scale));

        g.fill(cx - halfW - scale, baseY - scale, cx + halfW + scale, baseY, 0xFF30353C);
        g.fill(cx - halfW, baseY - (int) (2.8 * scale), cx + halfW, baseY - scale, 0xFF4C535C);
        g.fill(cx - halfW, baseY - (int) (4.2 * scale), cx + halfW, baseY - (int) (2.8 * scale),
                0xFFD8A824);

        final int lensTop = baseY - (int) (12.0 * scale);
        final int lensBottom = baseY - (int) (5.1 * scale);

        final int strips = 6;
        for (int i = 0; i < strips; i++) {
            final double bearing = -Math.PI / 2.0D + Math.PI * (i + 0.5D) / strips;
            final float faceLevel = (float) pattern.brightness(seconds, bearing, 0.35D);
            final int x0 = cx - halfW + (2 * halfW) * i / strips;
            final int x1 = cx - halfW + (2 * halfW) * (i + 1) / strips;
            g.fill(x0, lensTop, x1, lensBottom, lensTint(colour, faceLevel));
        }

        for (int yy = lensTop + 2; yy < lensBottom; yy += 3) {
            g.fill(cx - halfW, yy, cx + halfW, yy + 1, 0x22000000);
        }

        g.fill(cx - halfW - 1, lensBottom, cx + halfW + 1, lensBottom + scale, 0xFF30353C);
        g.fill(cx - halfW - 1, lensTop - scale, cx + halfW + 1, lensTop, 0xFF30353C);

        final int capTop = baseY - 16 * scale;

        final int vaneY = lensTop - scale - (int) (1.6 * scale);
        final double spin = pattern.spinsVane()
                ? seconds * SignalPattern.ROTATE_HZ * Math.PI * 2.0D : 0.4D;
        final int vaneHalf = Math.max(1, (int) (Math.abs(Math.cos(spin)) * 2.4 * scale));
        g.fill(cx - vaneHalf, vaneY, cx + vaneHalf, vaneY + (int) (1.4 * scale),
                shownLevel > 0.02f ? mirrorTint(colour) : 0xFF6A7078);
        g.fill(cx - halfW - 1, capTop + scale, cx + halfW + 1, capTop + 2 * scale, 0xFF22262B);
        g.fill(cx - 2, capTop, cx + 2, capTop + scale, 0xFFC28E3A);

        for (int side = -1; side <= 1; side += 2) {
            final int rx = cx + side * halfW - (side < 0 ? 0 : scale);
            g.fill(rx, capTop + scale, rx + scale, baseY - (int) (4.2 * scale), 0xFF8A919A);
        }

        int ty = baseY + 6;
        final String metric = tower.getActiveMetricLabel().isEmpty()
                ? "No reading" : tower.getActiveMetricLabel();
        g.drawString(font, trim(metric, body.w()), body.x(), ty, PduTheme.TEXT_DIM, false);
        ty += 10;
        final String value = tower.getActiveMetricLabel().isEmpty() ? "—"
                : tower.getActiveMetricType().formatValue(tower.getActiveMetricValue());
        g.drawString(font, trim(value, body.w()), body.x(), ty, PduTheme.TEXT, false);
        ty += 10;
        g.drawString(font, pattern.getDisplayName(), body.x(), ty, severityTint(severity), false);
    }

    private long gameTime() {
        return tower.getLevel() == null ? 0L : tower.getLevel().getGameTime();
    }

    private static int lensTint(int colour, float level) {
        if (level <= 0.02f) {
            return 0xFF2A2E34;
        }
        final float scale = 0.22f + 0.78f * Math.min(1.0f, level);
        return 0xFF000000
                | (Math.round(((colour >> 16) & 0xFF) * scale) << 16)
                | (Math.round(((colour >> 8) & 0xFF) * scale) << 8)
                | Math.round((colour & 0xFF) * scale);
    }

    private static int mirrorTint(int colour) {
        return 0xFF000000
                | (Math.min(255, Math.round(60 + ((colour >> 16) & 0xFF) * 0.62f)) << 16)
                | (Math.min(255, Math.round(60 + ((colour >> 8) & 0xFF) * 0.62f)) << 8)
                | Math.min(255, Math.round(60 + (colour & 0xFF) * 0.62f));
    }

    private StatusTowerLightBE.SignalState previewSeverity() {
        final StatusTowerLightBE.SignalState live = tower.getSignalState();
        if (live == StatusTowerLightBE.SignalState.OFF || live == StatusTowerLightBE.SignalState.NO_DATA) {
            return live;
        }
        if (!settings.usesThresholdLogic()) {
            return live;
        }
        return settings.thresholdSeverity(tower.getActiveMetricValue());
    }

    private int previewColour(StatusTowerLightBE.SignalState severity) {
        final StatusTowerLightSettings.Lens lens = settings.getLens();
        if (!lens.isAuto()) {
            return 0xFF000000 | lens.rgb();
        }
        return switch (severity) {
            case CRITICAL -> 0xFF000000 | StatusTowerLightSettings.Lens.AUTO_CRITICAL;
            case WARNING, NO_DATA -> 0xFF000000 | StatusTowerLightSettings.Lens.AUTO_WARNING;
            case NORMAL, OFF -> 0xFF000000 | StatusTowerLightSettings.Lens.AUTO_NORMAL;
        };
    }

    private static int severityTint(StatusTowerLightBE.SignalState state) {
        return switch (state) {
            case CRITICAL -> PduTheme.CRIT;
            case WARNING -> PduTheme.WARN;
            case NORMAL -> PduTheme.OK;
            case NO_DATA -> PduTheme.INFO;
            case OFF -> PduTheme.TEXT_FAINT;
        };
    }

    private void drawSource(GuiGraphics g, int mouseX, int mouseY) {
        final PduRect body = PduCard.draw(g, font, sourceCard, "Source");
        sourceMode.draw(g, font, body.x(), body.y(), 13, mouseX, mouseY);
        hits.add(new Hit(new PduRect(body.x(), body.y(), sourceMode.width(font), 13),
                ACT_SOURCE_MODE, 0));

        int y = body.y() + 17;
        if (settings.getSourceMode() == StatusTowerLightSettings.SourceMode.LOCAL) {
            final List<String> sources = tower.localSourceSummaries();
            if (sources.isEmpty()) {
                g.drawString(font, "Nothing adjacent to watch", body.x(), y, PduTheme.TEXT_FAINT, false);
            } else {
                for (int i = 0; i < sources.size() && i < 2; i++) {
                    g.fill(body.x(), y + 2, body.x() + 2, y + 6,
                            i == 0 ? PduTheme.ACCENT : PduTheme.TEXT_FAINT);
                    g.drawString(font, trim(sources.get(i), body.w() - 6), body.x() + 6, y,
                            i == 0 ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
                    y += 10;
                }
                if (sources.size() > 1 && y + 8 <= body.bottom()) {
                    g.drawString(font, "worst of the two decides", body.x() + 6, y,
                            PduTheme.TEXT_FAINT, false);
                }
            }
        } else {
            g.drawString(font, trim(tower.getSourceSummary(), body.w()), body.x(), y,
                    tower.getSourceState() == StatusTowerLightBE.SourceState.BOUND
                            ? PduTheme.TEXT : PduTheme.TEXT_FAINT, false);
            g.drawString(font, "fed by a Display Link", body.x(), y + 10, PduTheme.TEXT_FAINT, false);
        }
    }

    private void drawMetric(GuiGraphics g, int mouseX, int mouseY) {
        final PduRect body = PduCard.draw(g, font, metricCard, "Metric");

        final StatusTowerLightSettings.Metric[] options = settings.getMetricOptions(sourceKind());
        int x = body.x();
        int y = body.y();
        for (int i = 0; i < options.length; i++) {
            final String label = options[i].getDisplayName();
            final int w = PduChip.width(font, label);
            if (x + w > body.right() && x > body.x()) {
                x = body.x();
                y += PduChip.HEIGHT + 3;
            }
            if (y + PduChip.HEIGHT > body.bottom()) {
                break;
            }
            final boolean active = options[i] == settings.getMetric();
            final PduRect chip = new PduRect(x, y, w, PduChip.HEIGHT);
            PduChip.draw(g, font, x, y, label, active ? PduTheme.ACCENT : PduTheme.TEXT_DIM,
                    chip.contains(mouseX, mouseY), false);
            hits.add(new Hit(chip, ACT_METRIC, i));
            x += w + 3;
        }
    }

    private void drawRule(GuiGraphics g, int mouseX, int mouseY) {
        final PduRect body = PduCard.draw(g, font, ruleCard, "Rule");
        if (!settings.usesThresholdLogic()) {
            drawBinaryRule(g, body, mouseX, mouseY);
            return;
        }

        direction.draw(g, font, body.x(), body.y(), 13, mouseX, mouseY);
        hits.add(new Hit(new PduRect(body.x(), body.y(), direction.width(font), 13), ACT_DIRECTION, 0));

        final float warn = settings.getWarningThreshold();
        final float crit = settings.getCriticalThreshold();
        final float value = tower.getActiveMetricValue();
        final float max = gaugeMax(warn, crit, value);

        final int trackY = body.y() + 24;
        track = new PduRect(body.x(), trackY, body.w(), 12);

        final int wx = valueToX(warn, max);
        final int cx = valueToX(crit, max);
        final boolean higher = settings.getThresholdDirection()
                == StatusTowerLightSettings.ThresholdDirection.HIGHER_IS_WORSE;

        if (higher) {
            band(g, track.x(), wx, PduTheme.OK);
            band(g, wx, cx, PduTheme.WARN);
            band(g, cx, track.right(), PduTheme.CRIT);
        } else {
            band(g, track.x(), cx, PduTheme.CRIT);
            band(g, cx, wx, PduTheme.WARN);
            band(g, wx, track.right(), PduTheme.OK);
        }
        PduTheme.outline(g, track.x(), track.y(), track.w(), track.h(), PduTheme.STROKE);

        if (!Float.isNaN(value)) {
            shownValue = Float.isNaN(shownValue) ? value : Ease.ease(shownValue, value, 10.0f, 1.0f);
            final int vx = valueToX(shownValue, max);
            g.fill(vx - 1, track.y() - 3, vx + 1, track.bottom() + 3, PduTheme.TEXT);
        }

        handle(g, wx, "W", mouseX, mouseY);
        handle(g, cx, "C", mouseX, mouseY);

        int labelY = track.bottom() + 3;
        if (labelY + 8 <= body.bottom()) {
            g.drawString(font, "0", track.x(), labelY, PduTheme.TEXT_FAINT, false);
            final String maxLabel = fmt(max);
            g.drawString(font, maxLabel, track.right() - font.width(maxLabel), labelY,
                    PduTheme.TEXT_FAINT, false);
        }

        // steppers, dragging can't hit an exact number

        labelY += 10;
        if (labelY + PduChip.HEIGHT <= body.bottom()) {
            int sx = body.x();
            sx += stepper(g, sx, labelY, "W " + fmt(warn), ACT_WARN_STEP, mouseX, mouseY);
            sx += 6;
            sx += stepper(g, sx, labelY, "C " + fmt(crit), ACT_CRIT_STEP, mouseX, mouseY);
            final String hint = "drag the handles";
            if (body.right() - font.width(hint) > sx + 8) {
                g.drawString(font, hint, body.right() - font.width(hint), labelY + 2,
                        PduTheme.TEXT_FAINT, false);
            }
        }
    }

    private int stepper(GuiGraphics g, int x, int y, String label, int action, int mouseX, int mouseY) {
        final int labelW = font.width(label) + 8;
        final PduRect minus = new PduRect(x, y, 9, PduChip.HEIGHT);
        final PduRect plus = new PduRect(x + 9 + labelW, y, 9, PduChip.HEIGHT);
        PduChip.draw(g, font, minus.x(), y, "-", PduTheme.TEXT_DIM, minus.contains(mouseX, mouseY), false);
        g.fill(x + 9, y, x + 9 + labelW, y + PduChip.HEIGHT, PduTheme.BG_DEEP);
        g.drawString(font, label, x + 13, y + 2, PduTheme.TEXT, false);
        PduChip.draw(g, font, plus.x(), y, "+", PduTheme.TEXT_DIM, plus.contains(mouseX, mouseY), false);
        hits.add(new Hit(minus, action, -1));
        hits.add(new Hit(plus, action, 1));
        return 9 + labelW + 9 + 4;
    }

    private void band(GuiGraphics g, int x0, int x1, int tint) {
        if (x1 <= x0) {
            return;
        }
        g.fill(x0, track.y(), x1, track.bottom(), PduTheme.withAlpha(tint, 0x66));
    }

    private void handle(GuiGraphics g, int x, String label, int mouseX, int mouseY) {
        final boolean hot = Math.abs(mouseX - x) <= 4 && mouseY >= track.y() - 4
                && mouseY <= track.bottom() + 4;
        g.fill(x - 2, track.y() - 4, x + 2, track.bottom() + 4,
                hot ? PduTheme.TEXT : PduTheme.TEXT_DIM);
        g.drawString(font, label, x - font.width(label) / 2, track.y() - 14,
                hot ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
    }

    private void drawBinaryRule(GuiGraphics g, PduRect body, int mouseX, int mouseY) {
        final boolean invert = settings.isBinaryInvert();
        g.drawString(font, "Signal true", body.x(), body.y() + 2, PduTheme.TEXT_DIM, false);
        g.drawString(font, "→", body.x() + 58, body.y() + 2, PduTheme.TEXT_FAINT, false);
        g.drawString(font, invert ? "dark" : "lit", body.x() + 72, body.y() + 2,
                invert ? PduTheme.TEXT_FAINT : PduTheme.OK, false);

        g.drawString(font, "Signal false", body.x(), body.y() + 14, PduTheme.TEXT_DIM, false);
        g.drawString(font, "→", body.x() + 58, body.y() + 14, PduTheme.TEXT_FAINT, false);
        g.drawString(font, invert ? "lit" : "dark", body.x() + 72, body.y() + 14,
                invert ? PduTheme.OK : PduTheme.TEXT_FAINT, false);

        final String label = invert ? "Inverted" : "Invert";
        final PduRect chip = new PduRect(body.right() - PduChip.width(font, label), body.y() + 6,
                PduChip.width(font, label), PduChip.HEIGHT);
        PduChip.draw(g, font, chip.x(), chip.y(), label,
                invert ? PduTheme.ACCENT : PduTheme.TEXT_DIM, chip.contains(mouseX, mouseY), false);
        hits.add(new Hit(chip, ACT_INVERT, 0));

        if (body.y() + 40 <= body.bottom()) {
            g.drawString(font, "This metric is on or off, so the", body.x(), body.y() + 32,
                    PduTheme.TEXT_FAINT, false);
            g.drawString(font, "warning and critical thresholds do not apply.", body.x(),
                    body.y() + 42, PduTheme.TEXT_FAINT, false);
        }
    }

    private void drawAppearance(GuiGraphics g, int mouseX, int mouseY) {
        final PduRect body = PduCard.draw(g, font, appearanceCard, "Appearance");

        g.drawString(font, "Lens", body.x(), body.y() + 2, PduTheme.TEXT_DIM, false);
        int sx = body.x() + 30;
        final StatusTowerLightSettings.Lens[] lenses = StatusTowerLightSettings.Lens.values();
        for (int i = 0; i < lenses.length; i++) {
            final PduRect sw = new PduRect(sx, body.y(), 12, 12);
            final boolean active = lenses[i] == settings.getLens();
            if (lenses[i].isAuto()) {
                final int third = (sw.w() - 2) / 3;
                g.fill(sw.x() + 1, sw.y() + 1, sw.x() + 1 + third, sw.bottom() - 1,
                        0xFF000000 | StatusTowerLightSettings.Lens.AUTO_NORMAL);
                g.fill(sw.x() + 1 + third, sw.y() + 1, sw.x() + 1 + third * 2, sw.bottom() - 1,
                        0xFF000000 | StatusTowerLightSettings.Lens.AUTO_WARNING);
                g.fill(sw.x() + 1 + third * 2, sw.y() + 1, sw.right() - 1, sw.bottom() - 1,
                        0xFF000000 | StatusTowerLightSettings.Lens.AUTO_CRITICAL);
            } else {
                g.fill(sw.x() + 1, sw.y() + 1, sw.right() - 1, sw.bottom() - 1,
                        0xFF000000 | lenses[i].rgb());
            }
            PduTheme.outline(g, sw.x(), sw.y(), sw.w(), sw.h(),
                    active ? PduTheme.TEXT : sw.contains(mouseX, mouseY)
                            ? PduTheme.STROKE_HOT : PduTheme.STROKE);
            if (active) {
                g.fill(sw.x(), sw.bottom() + 1, sw.right(), sw.bottom() + 2, PduTheme.ACCENT);
            }
            hits.add(new Hit(sw, ACT_LENS, i));
            sx += 14;
        }
        final String lensName = settings.getLens().getDisplayName();
        if (sx + 6 + font.width(lensName) <= body.right()) {
            g.drawString(font, lensName, sx + 6, body.y() + 2, PduTheme.TEXT_DIM, false);
        }

        g.drawString(font, "Style", body.x(), body.y() + 19, PduTheme.TEXT_DIM, false);
        style.draw(g, font, body.x() + 30, body.y() + 16, 13, mouseX, mouseY);
        hits.add(new Hit(new PduRect(body.x() + 30, body.y() + 16, style.width(font), 13),
                ACT_STYLE, 0));

        g.drawString(font, "Horn", body.x(), body.y() + 36, PduTheme.TEXT_DIM, false);
        sounder.draw(g, font, body.x() + 30, body.y() + 33, 13, mouseX, mouseY);
        hits.add(new Hit(new PduRect(body.x() + 30, body.y() + 33, sounder.width(font), 13),
                ACT_SOUNDER, 0));
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        final int y = panel.bottom() - FOOTER_H;
        g.fill(panel.x() + 1, y, panel.right() - 1, panel.bottom() - 1, PduTheme.BG_PANEL);
        g.fill(panel.x() + 1, y, panel.right() - 1, y + 1, PduTheme.STROKE);

        final int textRoom = cancelRect().x() - 8 - (panel.x() + PduTheme.PAD);
        g.drawString(font, trim(explain(), textRoom), panel.x() + PduTheme.PAD, y + 9,
                PduTheme.TEXT_DIM, false);

        drawButton(g, applyRect(), "Apply", PduTheme.ACCENT, mouseX, mouseY);
        drawButton(g, cancelRect(), "Cancel", PduTheme.TEXT_DIM, mouseX, mouseY);
    }

    private PduRect applyRect() {
        return new PduRect(panel.right() - PduTheme.PAD - 54, panel.bottom() - FOOTER_H + 5, 54, 15);
    }

    private PduRect cancelRect() {
        return new PduRect(panel.right() - PduTheme.PAD - 54 - 58, panel.bottom() - FOOTER_H + 5,
                54, 15);
    }

    private void drawButton(GuiGraphics g, PduRect r, String label, int tint, int mouseX, int mouseY) {
        final boolean hot = r.contains(mouseX, mouseY);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), hot ? PduTheme.BG_RAISED : PduTheme.BG_DEEP);
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), hot ? tint : PduTheme.STROKE);
        g.drawString(font, label, r.centerX() - font.width(label) / 2, r.y() + 4, tint, false);
    }

    private String explain() {
        final StringBuilder sb = new StringBuilder();
        sb.append(settings.getLens().isAuto()
                ? "Green to amber to red" : settings.getLens().getDisplayName() + " lens");
        sb.append(", ");
        sb.append(switch (settings.getAlertStyle()) {
            case CLASSIC -> "blink on warning, double flash on critical";
            case CALM -> "pulse on warning, blink on critical";
            case URGENT -> "double flash on warning, strobe on critical";
            case BEACON -> "pulse on warning, rotating mirror on critical";
            case SOLID -> "no flashing at any level";
        });
        if (settings.getSounder() != StatusTowerLightSettings.Sounder.OFF) {
            sb.append(", horn ").append(settings.getSounder().getDisplayName().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static float gaugeMax(float warn, float crit, float value) {
        final float peak = Math.max(1.0f, Math.max(Math.max(warn, crit), value) * 1.25f);
        final double magnitude = Math.pow(10, Math.floor(Math.log10(peak)));
        // finer rungs, 1-2-5 rounded 112.5 up to 200 and both handles huddled on the left

        for (double step : new double[] {1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 7.5, 10.0}) {
            if (peak <= magnitude * step) {
                return (float) (magnitude * step);
            }
        }
        return (float) (magnitude * 10.0);
    }

    private int valueToX(float value, float max) {
        return track.x() + Math.round(track.w() * Mth.clamp(value / max, 0.0f, 1.0f));
    }

    private float xToValue(double mx, float max) {
        return Mth.clamp((float) ((mx - track.x()) / Math.max(1, track.w())), 0.0f, 1.0f) * max;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        if (applyRect().contains(mx, my)) {
            applyAndClose();
            return true;
        }
        if (cancelRect().contains(mx, my)) {
            onClose();
            return true;
        }

        if (settings.usesThresholdLogic() && my >= track.y() - 5 && my <= track.bottom() + 5) {
            final float max = gaugeMax(settings.getWarningThreshold(),
                    settings.getCriticalThreshold(), tower.getActiveMetricValue());
            final int wx = valueToX(settings.getWarningThreshold(), max);
            final int cx = valueToX(settings.getCriticalThreshold(), max);
            final double dw = Math.abs(mx - wx);
            final double dc = Math.abs(mx - cx);
            if (Math.min(dw, dc) <= 6) {
                dragging = dw <= dc ? 1 : 2;
                return true;
            }
        }

        for (Hit hit : hits) {
            if (!hit.bounds().contains(mx, my)) {
                continue;
            }
            if (activate(hit, mx, my)) {
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean activate(Hit hit, double mx, double my) {
        switch (hit.action()) {
            case ACT_SOURCE_MODE -> {
                final int seg = sourceMode.segmentAt(mx, my);
                if (seg < 0) {
                    return false;
                }
                settings.setSourceMode(StatusTowerLightSettings.SourceMode.values()[seg]);
                sourceMode.setSelected(seg);
                settings.ensureMetricSupported(sourceKind());
                layout();
            }
            case ACT_METRIC -> {
                final StatusTowerLightSettings.Metric[] options = settings.getMetricOptions(sourceKind());
                if (hit.index() >= options.length) {
                    return false;
                }
                settings.setMetric(options[hit.index()]);
                layout();
            }
            case ACT_DIRECTION -> {
                final int seg = direction.segmentAt(mx, my);
                if (seg < 0) {
                    return false;
                }
                settings.setThresholdDirection(
                        StatusTowerLightSettings.ThresholdDirection.values()[seg]);
                direction.setSelected(seg);
            }
            case ACT_INVERT -> settings.setBinaryInvert(!settings.isBinaryInvert());
            case ACT_LENS -> settings.setLens(StatusTowerLightSettings.Lens.values()[hit.index()]);
            case ACT_STYLE -> {
                final int seg = style.segmentAt(mx, my);
                if (seg < 0) {
                    return false;
                }
                settings.setAlertStyle(StatusTowerLightSettings.AlertStyle.values()[seg]);
                style.setSelected(seg);
            }
            case ACT_SOUNDER -> {
                final int seg = sounder.segmentAt(mx, my);
                if (seg < 0) {
                    return false;
                }
                settings.setSounder(StatusTowerLightSettings.Sounder.values()[seg]);
                sounder.setSelected(seg);
            }
            case ACT_WARN_STEP -> nudge(true, hit.index());
            case ACT_CRIT_STEP -> nudge(false, hit.index());
            default -> {
                return false;
            }
        }
        playClick();
        return true;
    }

    private void nudge(boolean warning, int sign) {
        final float max = gaugeMax(settings.getWarningThreshold(), settings.getCriticalThreshold(),
                tower.getActiveMetricValue());
        final float step = (hasShiftDown() ? max / 200.0f : max / 20.0f) * sign;
        if (warning) {
            settings.setWarningThreshold(settings.getWarningThreshold() + step);
        } else {
            settings.setCriticalThreshold(settings.getCriticalThreshold() + step);
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging == 0) {
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        final float max = gaugeMax(settings.getWarningThreshold(), settings.getCriticalThreshold(),
                tower.getActiveMetricValue());
        final float value = xToValue(mx, max);

        if (dragging == 1) {
            settings.setThresholds(value, settings.getCriticalThreshold());
        } else {
            settings.setThresholds(settings.getWarningThreshold(), value);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != 0) {
            dragging = 0;
            playClick();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.35f));
        }
    }

    private StatusTowerLightSettings.SourceKind sourceKind() {
        return tower.getPreviewSourceKind(settings.getSourceMode());
    }

    private void applyAndClose() {
        PacketDistributor.sendToServer(
                new StatusTowerLightConfigPacket(tower.getBlockPos(), settings));
        onClose();
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static String fmt(float value) {
        if (Math.abs(value) >= 100_000.0f) {
            return String.format(Locale.ROOT, "%.0fk", value / 1000.0f);
        }
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
