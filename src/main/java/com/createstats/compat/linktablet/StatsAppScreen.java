package com.createstats.compat.linktablet;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.client.StressometerHudReadout;
import com.createstats.client.StressometerHudWidgetRenderer;
import com.createstats.display.StressometerHudState;
import com.modpack.linktablet.api.client.ProgramHost;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class StatsAppScreen extends Screen {
    private static final int TAB_PAIR = 0;
    private static final int TAB_STRESS = 1;

    private static final int LIVE_GREEN = 0xFF4ADE80;
    private static final int OUT_AMBER = 0xFFE0AA46;

    private final ProgramHost host;
    private int tab = TAB_PAIR;

    private List<StatsTabletPairing.Candidate> candidates = List.of();
    private final StressometerHudState hudState = new StressometerHudState();

    private int tabsY0;
    private int tabsY1;
    private int pairX0;
    private int pairX1;
    private int stressX0;
    private int stressX1;
    private int refreshX0;
    private int refreshX1;
    private int refreshY0;
    private int refreshY1;
    private int listX0;
    private int listX1;
    private int listTop;
    private int rowH = 24;
    private int pinX0;
    private int pinX1;
    private int pinY0;
    private int pinY1;

    private final long openTimeMs = System.currentTimeMillis();
    private long lastFrameMs = System.currentTimeMillis();
    private float underlineX = -1f;
    private float underlineW;

    public StatsAppScreen(ProgramHost host) {
        super(Component.literal("Stats"));
        this.host = host;
    }

    @Override
    protected void init() {
        super.init();
        refreshCandidates();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        final long now = System.currentTimeMillis();
        final float dt = Math.min(0.05f, Math.max(0f, (now - this.lastFrameMs) / 1000f));
        this.lastFrameMs = now;

        final ScreenTheme theme = this.host.theme();
        final boolean shadow = theme.textShadow;

        final int panelW = clamp((int) (this.width * 0.5), 330, Math.min(430, this.width - 24));
        final int panelH = clamp((int) (this.height * 0.62), 208, Math.min(300, this.height - 24));
        final int left = (this.width - panelW) / 2;
        final int top = (this.height - panelH) / 2;
        final int right = left + panelW;
        final int bottom = top + panelH;
        final int pad = 12;

        final float pop = easeOutCubic(clamp01f((now - this.openTimeMs) / 150f));
        final float centerX = (left + right) / 2f;
        final float centerY = (top + bottom) / 2f;
        g.pose().pushPose();
        g.pose().translate(centerX, centerY, 0f);
        final float scale = 0.97f + 0.03f * pop;
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-centerX, -centerY, 0f);
        g.pose().translate(0f, (1f - pop) * 5f, 0f);

        g.fill(left - 2, top - 2, right + 2, bottom + 2, theme.bodyOuter);
        g.fill(left, top, right, bottom, theme.bodyInner);
        g.fill(left, top, right, top + 2, theme.accent);

        final int headerY = top + 8;
        g.drawString(this.font, "Stats", left + pad, headerY, theme.textPrimary, shadow);
        final String pinLabel = this.host.isPinned() ? "unpin" : "pin";
        final int pinW = this.font.width(pinLabel) + 12;
        this.pinX0 = right - pad - pinW;
        this.pinX1 = right - pad;
        this.pinY0 = headerY - 3;
        this.pinY1 = headerY + this.font.lineHeight + 1;
        final boolean pinHover = mouseX >= this.pinX0 && mouseX < this.pinX1 && mouseY >= this.pinY0 && mouseY < this.pinY1;
        g.fill(this.pinX0, this.pinY0, this.pinX1, this.pinY1, pinHover ? theme.rowBgHover : theme.rowBg);
        g.drawCenteredString(this.font, pinLabel, (this.pinX0 + this.pinX1) / 2, headerY, this.host.isPinned() ? theme.accent : (pinHover ? theme.textPrimary : theme.textMuted));
        final Component name = this.host.tabletName();
        if (name != null) {
            final String n = this.font.plainSubstrByWidth(name.getString(), panelW / 3);
            g.drawString(this.font, n, this.pinX0 - 6 - this.font.width(n), headerY, theme.textFaint, shadow);
        }
        final int dividerY = headerY + this.font.lineHeight + 5;
        g.fill(left + pad, dividerY, right - pad, dividerY + 1, theme.surfaceLo);

        final int tabTop = dividerY + 7;
        final int tabH = 18;
        final int tabW = 72;
        final int gap = 8;
        this.tabsY0 = tabTop;
        this.tabsY1 = tabTop + tabH;
        this.pairX0 = left + pad;
        this.pairX1 = this.pairX0 + tabW;
        this.stressX0 = this.pairX1 + gap;
        this.stressX1 = this.stressX0 + tabW;
        drawTab(g, theme, this.pairX0, tabTop, this.pairX1, this.tabsY1, "Pair", this.tab == TAB_PAIR, mouseX, mouseY);
        drawTab(g, theme, this.stressX0, tabTop, this.stressX1, this.tabsY1, "Stress", this.tab == TAB_STRESS, mouseX, mouseY);
        final int activeX = this.tab == TAB_PAIR ? this.pairX0 : this.stressX0;
        if (this.underlineX < 0f) {
            this.underlineX = activeX;
            this.underlineW = tabW;
        }
        final float slide = Math.min(1f, dt * 16f);
        this.underlineX += (activeX - this.underlineX) * slide;
        this.underlineW += (tabW - this.underlineW) * slide;
        g.fill(Math.round(this.underlineX), this.tabsY1 - 3, Math.round(this.underlineX + this.underlineW), this.tabsY1, theme.accent);

        final int statusY = tabTop + tabH + 7;
        renderStatus(g, theme, shadow, left + pad, right - pad, statusY, now);

        final int bodyTop = statusY + this.font.lineHeight + 6;
        final int bodyBottom = bottom - 13;
        if (this.tab == TAB_PAIR) {
            renderPairTab(g, theme, shadow, left + pad, right - pad, bodyTop, bodyBottom, mouseX, mouseY);
        } else {
            renderStressTab(g, theme, left + pad, right - pad, bodyTop, bodyBottom);
        }

        g.drawCenteredString(this.font, "ESC to return home", (left + right) / 2, bottom - 11, theme.textFaint);

        g.pose().popPose();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void dot(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 3, y + 4, color);
        g.fill(x, y + 1, x + 4, y + 3, color);
    }

    private void dotPulse(GuiGraphics g, int x, int y, int color, long now, float periodMs) {
        final float phase = (now % (long) periodMs) / periodMs;
        final float s = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);
        final int halo = (Math.round(70f * s) << 24) | (color & 0x00FFFFFF);
        g.fill(x - 1, y, x + 5, y + 4, halo);
        g.fill(x, y - 1, x + 4, y + 5, halo);
        dot(g, x, y, color);
    }

    private static float easeOutCubic(float t) {
        final float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float clamp01f(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static void playClick(float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }

    private void renderStatus(GuiGraphics g, ScreenTheme theme, boolean shadow, int x0, int x1, int y, long now) {
        final int dotY = y + (this.font.lineHeight - 4) / 2;
        final StatsTabletPairing.Target target = StatsTabletPairing.getPaired();
        if (target == null) {
            dot(g, x0, dotY, theme.textFaint);
            g.drawString(this.font, "Not paired", x0 + 8, y, theme.textFaint, shadow);
            return;
        }
        if (StatsTabletPairing.resolvePairedPanel() == null) {
            dot(g, x0, dotY, OUT_AMBER);
            g.drawString(this.font, "Out of range", x0 + 8, y, theme.textMuted, shadow);
            return;
        }
        final StressometerHudReadout r = StatsStressReadout.paired();
        final boolean over = r != null && r.overstressed();
        dotPulse(g, x0, dotY, over ? 0xFFFF4D4D : LIVE_GREEN, now, over ? 280f : 560f);
        final String label = r != null && !r.sourceLabel().isEmpty() ? r.sourceLabel() : StatsTabletPairing.pairedLabel();
        final String text = "Connected  " + (label == null ? "Stats Display" : label);
        g.drawString(this.font, this.font.plainSubstrByWidth(text, x1 - x0 - 8), x0 + 8, y, theme.textPrimary, shadow);
    }

    private void renderPairTab(GuiGraphics g, ScreenTheme theme, boolean shadow,
                               int x0, int x1, int top, int bottom, int mouseX, int mouseY) {
        g.drawString(this.font, "NEARBY STATS DISPLAYS", x0, top, theme.textFaint, shadow);

        final String refresh = "Refresh";
        final int rw = this.font.width(refresh) + 14;
        this.refreshX0 = x1 - rw;
        this.refreshX1 = x1;
        this.refreshY0 = top - 4;
        this.refreshY1 = top + this.font.lineHeight + 2;
        final boolean rHover = mouseX >= this.refreshX0 && mouseX < this.refreshX1
                && mouseY >= this.refreshY0 && mouseY < this.refreshY1;
        g.fill(this.refreshX0, this.refreshY0, this.refreshX1, this.refreshY1, rHover ? theme.rowBgHover : theme.rowBg);
        g.drawCenteredString(this.font, refresh, (this.refreshX0 + this.refreshX1) / 2, top, rHover ? theme.textPrimary : theme.textMuted);

        this.listX0 = x0;
        this.listX1 = x1;
        this.listTop = top + this.font.lineHeight + 7;
        this.rowH = 26;

        if (this.candidates.isEmpty()) {
            final int cy = (this.listTop + bottom) / 2 - this.font.lineHeight;
            g.drawCenteredString(this.font, "No Stats Displays nearby.", (x0 + x1) / 2, cy, theme.textMuted);
            g.drawCenteredString(this.font, "Move closer, then Refresh.", (x0 + x1) / 2, cy + 12, theme.textFaint);
            return;
        }

        for (int i = 0; i < this.candidates.size(); i++) {
            final int ry = this.listTop + i * this.rowH;
            if (ry + this.rowH - 3 > bottom) {
                break;
            }
            final StatsTabletPairing.Candidate c = this.candidates.get(i);
            final boolean paired = StatsTabletPairing.isPaired(c.pos(), c.dimension());
            final boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= ry && mouseY < ry + this.rowH - 3;
            g.fill(x0, ry, x1, ry + this.rowH - 3, paired ? theme.surfaceHi : (hover ? theme.rowBgHover : theme.rowBg));
            if (paired) {
                g.fill(x0, ry, x0 + 3, ry + this.rowH - 3, theme.accent);
            }
            final int textX = x0 + 9;
            final String hint = paired ? "LINKED" : "connect";
            final int hintW = this.font.width(hint);
            final BlockPos p = c.pos();
            final int dist = (int) Math.round(Math.sqrt(c.distanceSq()));
            final String distText = dist + " m";
            final int distW = this.font.width(distText);
            final int leftMax = x1 - textX - Math.max(hintW, distW) - 17;
            g.drawString(this.font, this.font.plainSubstrByWidth(c.label(), leftMax), textX, ry + 4, theme.textPrimary, shadow);
            g.drawString(this.font, this.font.plainSubstrByWidth(p.getX() + ", " + p.getY() + ", " + p.getZ(), leftMax), textX, ry + 14, theme.textFaint, shadow);
            g.drawString(this.font, distText, x1 - 9 - distW, ry + 4, theme.textMuted, shadow);
            g.drawString(this.font, hint, x1 - 9 - hintW, ry + 14, paired ? theme.accent : theme.textMuted, shadow);
        }
    }

    private void renderStressTab(GuiGraphics g, ScreenTheme theme, int x0, int x1, int top, int bottom) {
        final int cx = (x0 + x1) / 2;
        final int cy = (top + bottom) / 2 - 4;
        if (StatsTabletPairing.getPaired() == null) {
            g.drawCenteredString(this.font, "Connect a display on the Pair tab.", cx, cy, theme.textMuted);
            return;
        }
        final StatsDisplayPanelBE panel = StatsTabletPairing.resolvePairedPanel();
        if (panel == null) {
            g.drawCenteredString(this.font, "Display out of range.", cx, cy - 5, theme.textMuted);
            g.drawCenteredString(this.font, "Get closer to see live stress.", cx, cy + 6, theme.textFaint);
            return;
        }
        final long tick = panel.getLevel() != null ? panel.getLevel().getGameTime() : 0L;
        final boolean drawn = StressometerHudWidgetRenderer.renderLive(g, panel, this.hudState, x0, top, x1 - x0, bottom - top, tick);
        if (!drawn) {
            g.drawCenteredString(this.font, "No stress source on this display.", cx, cy - 5, theme.textMuted);
            g.drawCenteredString(this.font, "Link a Stressometer to it, then reopen.", cx, cy + 6, theme.textFaint);
        }
    }

    private void drawTab(GuiGraphics g, ScreenTheme theme, int x0, int y0, int x1, int y1,
                         String label, boolean active, int mouseX, int mouseY) {
        final boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
        final int bg = active ? theme.surfaceHi : (hover ? theme.rowBgHover : theme.rowBg);
        g.fill(x0, y0, x1, y1, bg);
        final int textColor = active || hover ? theme.textPrimary : theme.textMuted;
        g.drawCenteredString(this.font, label, (x0 + x1) / 2, y0 + (y1 - y0 - this.font.lineHeight) / 2 + 1, textColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= this.pinX0 && mouseX < this.pinX1
                && mouseY >= this.pinY0 && mouseY < this.pinY1) {
            this.host.togglePin();
            playClick(this.host.isPinned() ? 1.2f : 0.85f, 0.4f);
            return true;
        }
        if (button == 0 && mouseY >= this.tabsY0 && mouseY < this.tabsY1) {
            if (mouseX >= this.pairX0 && mouseX < this.pairX1) {
                setTab(TAB_PAIR);
                return true;
            }
            if (mouseX >= this.stressX0 && mouseX < this.stressX1) {
                setTab(TAB_STRESS);
                return true;
            }
        }
        if (button == 0 && this.tab == TAB_PAIR) {
            if (mouseX >= this.refreshX0 && mouseX < this.refreshX1
                    && mouseY >= this.refreshY0 && mouseY < this.refreshY1) {
                refreshCandidates();
                playClick(1.0f, 0.3f);
                return true;
            }
            if (mouseX >= this.listX0 && mouseX < this.listX1 && mouseY >= this.listTop) {
                final int idx = (int) ((mouseY - this.listTop) / this.rowH);
                final double within = (mouseY - this.listTop) - (double) idx * this.rowH;
                if (idx >= 0 && idx < this.candidates.size() && within < this.rowH - 3) {
                    final StatsTabletPairing.Candidate c = this.candidates.get(idx);
                    if (StatsTabletPairing.isPaired(c.pos(), c.dimension())) {
                        StatsTabletPairing.clear();
                        playClick(0.8f, 0.35f);
                    } else {
                        StatsTabletPairing.setPaired(c);
                        playClick(1.25f, 0.4f);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setTab(int newTab) {
        if (this.tab != newTab) {
            this.tab = newTab;
            playClick(1.1f, 0.35f);
            if (newTab == TAB_PAIR) {
                refreshCandidates();
            }
        }
    }

    private void refreshCandidates() {
        this.candidates = StatsTabletPairing.scanNearby(5);
    }

    @Override
    public void onClose() {
        this.host.goHome();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
