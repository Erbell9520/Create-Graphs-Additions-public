package com.createstats.client;

import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import com.createstats.network.ScanControlPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import com.createstats.display.DisplayTheme;
import java.util.HashSet;
import net.minecraft.network.chat.Component;
import com.createstats.blockentity.ScanSettings;
import java.util.Set;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;

@OnlyIn(Dist.CLIENT)
public class ScanPopupScreen extends Screen
{
    private final StatsDisplayPanelBE panel;
    private final Screen parentScreen;
    private int popX;
    private int popY;
    private int popW;
    private int popH;
    private static final int POP_WIDTH = 280;
    private static final int MIN_POP_HEIGHT = 240;
    private int BG;
    private int TEXT;
    private int DIM;
    private int ACCENT;
    private int BORDER;
    private int WARN;
    private int scrollOffset;
    private int maxScroll;
    private static final int ROW_HEIGHT = 24;
    private static final int EXPANDED_CHANNEL_HEIGHT = 12;
    private final Set<String> expandedSources;
    private ScanSettings.ScanMode localMode;
    private int localRadius;
    private boolean localUnlocked;
    private int localIntervalTicks;
    private boolean draggingRadius;
    private boolean draggingInterval;
    
    public ScanPopupScreen(StatsDisplayPanelBE panel, Screen parentScreen) {
        super(Component.literal("Block Scanner"));
        this.scrollOffset = 0;
        this.maxScroll = 0;
        this.expandedSources = new HashSet<String>();
        this.draggingRadius = false;
        this.draggingInterval = false;
        this.panel = panel;
        this.parentScreen = parentScreen;
        final ScanSettings ss = panel.getScanSettings();
        this.localMode = ss.getScanMode();
        this.localRadius = ss.getScanRadius();
        this.localUnlocked = ss.isRadiusUnlocked();
        this.localIntervalTicks = ss.getAutoIntervalTicks();
    }
    
    protected void init() {
        this.popW = 280;
        this.popH = Math.min(this.height - 20, Math.max(240, this.height * 3 / 4));
        this.popX = (this.width - this.popW) / 2;
        this.popY = (this.height - this.popH) / 2;
        final DisplayTheme theme = this.panel.getSettings().getDisplayTheme();
        this.BG = theme.bg;
        this.TEXT = theme.text;
        this.DIM = theme.dim;
        this.ACCENT = theme.accent;
        this.BORDER = theme.border;
        this.WARN = theme.warn;
    }
    
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, -2013265920);
        g.fill(this.popX, this.popY, this.popX + this.popW, this.popY + this.popH, this.BG);
        this.border(g, this.popX, this.popY, this.popW, this.popH, this.BORDER);
        g.fill(this.popX + 1, this.popY + 1, this.popX + this.popW - 1, this.popY + 2, 869576503);
        int y = this.popY + 6;
        final int margin = 8;
        final int innerW = this.popW - margin * 2;
        final int lx = this.popX + margin;
        g.drawString(this.font, "\ud83d\udd0d Block Scanner", lx, y, this.ACCENT);
        final ScanSettings ss = this.panel.getScanSettings();
        final int srcCount = ss.getDiscoveredSources().size();
        ScanSettings.DiscoveredSource ds = null;
        final long enabledCount = ss.getDiscoveredSources().values().stream().filter(dsx -> ss.isSourceEnabled(dsx.sourceId)).count();
        final String countStr = enabledCount + "/" + srcCount + " active";
        g.drawString(this.font, countStr, this.popX + this.popW - margin - this.font.width(countStr), y, this.DIM);
        y += 14;
        final String closeStr = "\u2715";
        final int closeX = this.popX + this.popW - margin - this.font.width(closeStr);
        final int closeY = this.popY + 6;
        g.drawString(this.font, closeStr, closeX, closeY, this.hovered(mouseX, mouseY, closeX - 2, closeY - 2, this.font.width(closeStr) + 4, 12) ? -48060 : this.DIM);
        g.fill(lx, y, lx + innerW, y + 1, this.BORDER & 0x44FFFFFF);
        y += 4;
        g.drawString(this.font, "Mode:", lx, y + 2, this.TEXT);
        final int modeX = lx + 34;
        final boolean manualHovered = this.hovered(mouseX, mouseY, modeX, y, 50, 14);
        this.drawToggle(g, modeX, y, 50, 14, "Manual", this.localMode == ScanSettings.ScanMode.MANUAL, manualHovered);
        final boolean autoHovered = this.hovered(mouseX, mouseY, modeX + 54, y, 40, 14);
        this.drawToggle(g, modeX + 54, y, 40, 14, "Auto", this.localMode == ScanSettings.ScanMode.AUTO, autoHovered);
        if (this.localMode == ScanSettings.ScanMode.AUTO) {
            final float intervalSec = this.localIntervalTicks / 20.0f;
            final String intLabel = String.format("every %.0fs", intervalSec);
            g.drawString(this.font, intLabel, modeX + 100, y + 3, this.DIM);
        }
        y += 18;
        if (this.localMode == ScanSettings.ScanMode.AUTO) {
            g.drawString(this.font, "Interval:", lx, y + 2, this.TEXT);
            final int sliderX = lx + 50;
            final int sliderW = innerW - 54;
            this.drawSlider(g, sliderX, y + 2, sliderW, 10, mouseX, mouseY, (this.localIntervalTicks - 20) / 1180.0f, String.format("%.1fs", this.localIntervalTicks / 20.0f), this.draggingInterval);
            y += 16;
        }
        g.drawString(this.font, "Radius:", lx, y + 2, this.TEXT);
        final int sliderX = lx + 42;
        final int sliderW = innerW - 90;
        final int maxRad = this.localUnlocked ? 512 : 64;
        final float radFrac = (this.localRadius - 4) / (float)(maxRad - 4);
        this.drawSlider(g, sliderX, y + 2, sliderW, 10, mouseX, mouseY, radFrac, this.localRadius + " blocks", this.draggingRadius);
        final int unlockX = sliderX + sliderW + 4;
        final boolean unlockHovered = this.hovered(mouseX, mouseY, unlockX, y, 40, 14);
        this.drawToggle(g, unlockX, y, 40, 14, this.localUnlocked ? "\ud83d\udd13" : "\ud83d\udd12", this.localUnlocked, unlockHovered);
        y += 18;
        if (this.localUnlocked) {
            g.drawString(this.font, "\u26a0 Unlock raises scan radius only.", lx, y, this.WARN);
            y += 12;
            g.drawString(this.font, "Topology cap stays in Settings > Max Nodes (" + this.panel.getSettings().getTopologyMaxNodes() + ")", lx, y, this.DIM);
            y += 12;
        }
        if (this.localUnlocked && this.localRadius > 64) {
            g.drawString(this.font, "\u26a0 Large radius may cause lag!", lx, y, this.WARN);
            y += 12;
        }
        final int scanBtnW = 80;
        final int scanBtnX = lx + (innerW - scanBtnW) / 2;
        final boolean scanHovered = this.hovered(mouseX, mouseY, scanBtnX, y, scanBtnW, 16);
        g.fill(scanBtnX, y, scanBtnX + scanBtnW, y + 16, scanHovered ? -13408717 : -14531550);
        this.border(g, scanBtnX, y, scanBtnW, 16, scanHovered ? -12264124 : this.ACCENT);
        g.drawCenteredString(this.font, "\u25b6 Scan Now", scanBtnX + scanBtnW / 2, y + 4, -12255420);
        y += 20;
        final long gameTick = (this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : 0L;
        final long elapsed = gameTick - ss.getLastScanTick();
        String lastScan;
        if (ss.getLastScanTick() == 0L) {
            lastScan = "Never scanned";
        }
        else if (elapsed < 20L) {
            lastScan = "Last scanned: just now";
        }
        else {
            lastScan = "Last scanned: " + elapsed / 20L + "s ago";
        }
        g.drawString(this.font, lastScan, lx, y, this.DIM);
        y += 12;
        g.fill(lx, y, lx + innerW, y + 1, this.BORDER & 0x44FFFFFF);
        y += 4;
        g.drawString(this.font, "Discovered Sources:", lx, y, this.TEXT);
        y += 12;
        final int listTop = y;
        final int listBottom = this.popY + this.popH - 6;
        final int listH = listBottom - listTop;
        g.enableScissor(lx, listTop, lx + innerW, listBottom);
        final List<ScanSettings.DiscoveredSource> sources = new ArrayList<ScanSettings.DiscoveredSource>(ss.getDiscoveredSources().values());
        sources.sort((a, b) -> {
            final boolean aEnabled = ss.isSourceEnabled(a.sourceId);
            final boolean bEnabled = ss.isSourceEnabled(b.sourceId);
            if (aEnabled != bEnabled) {
                return aEnabled ? -1 : 1;
            }
            else if (a.active != b.active) {
                return a.active ? -1 : 1;
            }
            else {
                return Double.compare(distSq(a.pos, this.panel.getBlockPos()), distSq(b.pos, this.panel.getBlockPos()));
            }
        });
        int totalContentHeight = 0;
        final Iterator<ScanSettings.DiscoveredSource> iterator = sources.iterator();
        while (iterator.hasNext()) {
            ds = iterator.next();
            totalContentHeight += 24;
            if (this.expandedSources.contains(ds.sourceId)) {
                totalContentHeight += ds.channels.size() * 12;
            }
        }
        this.maxScroll = Math.max(0, totalContentHeight - listH);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));
        int drawY = listTop - this.scrollOffset;
        for (ScanSettings.DiscoveredSource ds2 : sources) {
            final boolean enabled = ss.isSourceEnabled(ds2.sourceId);
            final boolean expanded = this.expandedSources.contains(ds2.sourceId);
            final int rowBottom = drawY + 24;
            if (rowBottom > listTop && drawY < listBottom) {
                this.renderSourceRow(g, lx, drawY, innerW, ds2, enabled, expanded, mouseX, mouseY, gameTick);
            }
            drawY += 24;
            if (expanded) {
                for (ScanSettings.ChannelSnapshot ch : ds2.channels) {
                    if (drawY + 12 > listTop && drawY < listBottom) {
                        this.renderChannelRow(g, lx + 16, drawY, innerW - 16, ch);
                    }
                    drawY += 12;
                }
            }
        }
        if (sources.isEmpty()) {
            g.drawCenteredString(this.font, "No sources found, click Scan Now", lx + innerW / 2, listTop + 10, this.DIM);
        }
        g.disableScissor();
        if (this.maxScroll > 0) {
            final int barH = Math.max(10, listH * listH / totalContentHeight);
            final int barY = listTop + (int)(this.scrollOffset / (float)this.maxScroll * (listH - barH));
            g.fill(this.popX + this.popW - 5, barY, this.popX + this.popW - 2, barY + barH, this.DIM);
        }
    }
    
    private void renderSourceRow(GuiGraphics g, int x, int y, int w, ScanSettings.DiscoveredSource ds, boolean enabled, boolean expanded, int mx, int my, long gameTick) {
        final int bgColor = enabled ? (ds.active ? 536905728 : 541344768) : 541327360;
        g.fill(x, y + 1, x + w, y + 24 - 1, bgColor);
        final int dotColor = enabled ? (ds.active ? -16724890 : -3355648) : -10066330;
        g.fill(x + 2, y + 8, x + 6, y + 12, dotColor);
        final boolean checkHovered = this.hovered(mx, my, x + 8, y + 4, 12, 12);
        g.fill(x + 8, y + 5, x + 19, y + 16, checkHovered ? -11184811 : -13421773);
        this.border(g, x + 8, y + 5, 11, 11, enabled ? this.ACCENT : this.DIM);
        if (enabled) {
            g.drawString(this.font, "\u2713", x + 10, y + 6, this.ACCENT);
        }
        final String relPos = this.relativePos(ds.pos, this.panel.getBlockPos());
        final String label = ds.sourceType + "  " + relPos;
        g.drawString(this.font, label, x + 22, y + 4, enabled ? this.TEXT : this.DIM);
        final long stale = gameTick - ds.lastSeenTick;
        if (ds.lastSeenTick > 0L && stale > 200L) {
            g.drawString(this.font, "stale", x + w - 30, y + 4, this.WARN);
        }
        if (!ds.channels.isEmpty()) {
            final ScanSettings.ChannelSnapshot first = ds.channels.get(0);
            final String val = this.formatValue(first.latestValue);
            g.drawString(this.font, val, x + w - this.font.width(val) - 2, y + 4, enabled ? -7811841 : this.DIM);
        }
        final String arrow = expanded ? "\u25bc" : "\u25b6";
        final int arrowX = x + 22;
        final int arrowY = y + 14;
        g.drawString(this.font, arrow + " " + ds.channels.size() + " ch", arrowX, arrowY, this.DIM);
    }
    
    private void renderChannelRow(GuiGraphics g, int x, int y, int w, ScanSettings.ChannelSnapshot ch) {
        g.drawString(this.font, "\u2022 " + ch.label, x, y + 1, this.DIM);
        final String val = this.formatValue(ch.latestValue);
        g.drawString(this.font, val, x + w - this.font.width(val), y + 1, -7811841);
    }
    
    private void drawToggle(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean hovered) {
        final int bg = active ? -14531550 : (hovered ? -13421773 : -14540254);
        g.fill(x, y, x + w, y + h, bg);
        this.border(g, x, y, w, h, active ? this.ACCENT : (hovered ? this.TEXT : this.DIM));
        g.drawCenteredString(this.font, label, x + w / 2, y + 3, active ? this.ACCENT : this.TEXT);
    }
    
    private void drawSlider(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float fraction, String label, boolean dragging) {
        g.fill(x, y + 3, x + w, y + h - 3, -13421773);
        final int fillW = (int)(w * Math.max(0.0f, Math.min(1.0f, fraction)));
        g.fill(x, y + 3, x + fillW, y + h - 3, this.ACCENT & 0x88FFFFFF);
        final int thumbX = x + fillW - 2;
        g.fill(thumbX, y, thumbX + 5, y + h, dragging ? -1 : this.ACCENT);
        g.drawString(this.font, label, x + w + 4, y, this.TEXT);
    }
    
    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        final int imx = (int)mx;
        final int imy = (int)my;
        final int margin = 8;
        final int lx = this.popX + margin;
        final int innerW = this.popW - margin * 2;
        final String closeStr = "\u2715";
        final int closeX = this.popX + this.popW - margin - this.font.width(closeStr);
        if (this.hovered(imx, imy, closeX - 2, this.popY + 4, this.font.width(closeStr) + 4, 12)) {
            this.onClose();
            return true;
        }
        if (imx < this.popX || imx > this.popX + this.popW || imy < this.popY || imy > this.popY + this.popH) {
            this.onClose();
            return true;
        }
        int y = this.popY + 6 + 14 + 4;
        final int modeX = lx + 34;
        if (this.hovered(imx, imy, modeX, y, 50, 14)) {
            this.localMode = ScanSettings.ScanMode.MANUAL;
            this.pushSettings();
            return true;
        }
        if (this.hovered(imx, imy, modeX + 54, y, 40, 14)) {
            this.localMode = ScanSettings.ScanMode.AUTO;
            this.pushSettings();
            return true;
        }
        y += 18;
        if (this.localMode == ScanSettings.ScanMode.AUTO) {
            final int sliderX = lx + 50;
            final int sliderW = innerW - 54;
            if (this.hovered(imx, imy, sliderX, y, sliderW, 14)) {
                this.draggingInterval = true;
                this.updateIntervalFromMouse(imx, sliderX, sliderW);
                return true;
            }
            y += 16;
        }
        final int rSliderX = lx + 42;
        final int rSliderW = innerW - 90;
        if (this.hovered(imx, imy, rSliderX, y, rSliderW, 14)) {
            this.draggingRadius = true;
            this.updateRadiusFromMouse(imx, rSliderX, rSliderW);
            return true;
        }
        final int unlockX = rSliderX + rSliderW + 4;
        if (this.hovered(imx, imy, unlockX, y, 40, 14)) {
            this.localUnlocked = !this.localUnlocked;
            if (!this.localUnlocked && this.localRadius > 64) {
                this.localRadius = 64;
            }
            this.pushSettings();
            return true;
        }
        y += 18;
        if (this.localUnlocked) {
            y += 24;
        }
        if (this.localUnlocked && this.localRadius > 64) {
            y += 12;
        }
        final int scanBtnW = 80;
        final int scanBtnX = lx + (innerW - scanBtnW) / 2;
        if (this.hovered(imx, imy, scanBtnX, y, scanBtnW, 16)) {
            PacketDistributor.sendToServer(ScanControlPacket.triggerScan(this.panel.getBlockPos()));
            return true;
        }
        y += 48;
        final int listTop = y;
        final int listBottom = this.popY + this.popH - 6;
        if (imy >= listTop && imy <= listBottom) {
            return this.handleSourceListClick(imx, imy, lx, listTop, innerW);
        }
        return super.mouseClicked(mx, my, button);
    }
    
    private boolean handleSourceListClick(int mx, int my, int x, int listTop, int w) {
        final ScanSettings ss = this.panel.getScanSettings();
        final List<ScanSettings.DiscoveredSource> sources = this.getSortedSources(ss);
        int drawY = listTop - this.scrollOffset;
        for (ScanSettings.DiscoveredSource ds : sources) {
            final int rowBottom = drawY + 24;
            if (my >= drawY && my < rowBottom) {
                if (mx >= x + 8 && mx <= x + 19) {
                    PacketDistributor.sendToServer(ScanControlPacket.toggleSource(this.panel.getBlockPos(), ds.sourceId));
                    return true;
                }
                if (my >= drawY + 12) {
                    if (this.expandedSources.contains(ds.sourceId)) {
                        this.expandedSources.remove(ds.sourceId);
                    }
                    else {
                        this.expandedSources.add(ds.sourceId);
                    }
                    return true;
                }
                return true;
            }
            else {
                drawY += 24;
                if (!this.expandedSources.contains(ds.sourceId)) {
                    continue;
                }
                drawY += ds.channels.size() * 12;
            }
        }
        return false;
    }
    
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        final int imx = (int)mx;
        final int margin = 8;
        final int lx = this.popX + margin;
        final int innerW = this.popW - margin * 2;
        if (this.draggingRadius) {
            final int rSliderX = lx + 42;
            final int rSliderW = innerW - 90;
            this.updateRadiusFromMouse(imx, rSliderX, rSliderW);
            return true;
        }
        if (this.draggingInterval) {
            final int sliderX = lx + 50;
            final int sliderW = innerW - 54;
            this.updateIntervalFromMouse(imx, sliderX, sliderW);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.draggingRadius || this.draggingInterval) {
            this.draggingRadius = false;
            this.draggingInterval = false;
            this.pushSettings();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }
    
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= this.popX && mx <= this.popX + this.popW && my >= this.popY && my <= this.popY + this.popH) {
            this.scrollOffset -= (int)(scrollY * 16.0);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parentScreen);
    }
    
    public boolean isPauseScreen() {
        return false;
    }
    
    private void pushSettings() {
        final ScanSettings temp = new ScanSettings();
        temp.setScanMode(this.localMode);
        temp.setRadiusUnlocked(this.localUnlocked);
        temp.setScanRadius(this.localRadius);
        temp.setAutoIntervalTicks(this.localIntervalTicks);
        PacketDistributor.sendToServer(ScanControlPacket.updateSettings(this.panel.getBlockPos(), temp));
    }
    
    private void updateRadiusFromMouse(int mx, int sliderX, int sliderW) {
        final float frac = Math.max(0.0f, Math.min(1.0f, (mx - sliderX) / (float)sliderW));
        final int maxRad = this.localUnlocked ? 512 : 64;
        this.localRadius = Math.max(4, Math.min(maxRad, 4 + (int)(frac * (maxRad - 4))));
    }
    
    private void updateIntervalFromMouse(int mx, int sliderX, int sliderW) {
        final float frac = Math.max(0.0f, Math.min(1.0f, (mx - sliderX) / (float)sliderW));
        this.localIntervalTicks = Math.max(20, Math.min(1200, 20 + (int)(frac * 1180.0f)));
    }
    
    private boolean hovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    
    private static double distSq(BlockPos a, BlockPos b) {
        final double dx = a.getX() - b.getX();
        final double dy = a.getY() - b.getY();
        final double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
    
    private String relativePos(BlockPos src, BlockPos panel) {
        final int dx = src.getX() - panel.getX();
        final int dy = src.getY() - panel.getY();
        final int dz = src.getZ() - panel.getZ();
        return "@(" + ((dx >= 0) ? "+" : "") + dx + ", " + ((dy >= 0) ? "+" : "") + dy + ", " + ((dz >= 0) ? "+" : "") + dz;
    }
    
    private String formatValue(float v) {
        if (Math.abs(v) >= 1000000.0f) {
            return String.format("%.1fM", v / 1000000.0f);
        }
        if (Math.abs(v) >= 1000.0f) {
            return String.format("%.1fk", v / 1000.0f);
        }
        if (v == (int)v) {
            return String.valueOf((int)v);
        }
        return String.format("%.1f", v);
    }
    
    private List<ScanSettings.DiscoveredSource> getSortedSources(ScanSettings ss) {
        final List<ScanSettings.DiscoveredSource> sources = new ArrayList<ScanSettings.DiscoveredSource>(ss.getDiscoveredSources().values());
        sources.sort((a, b) -> {
            final boolean aE = ss.isSourceEnabled(a.sourceId);
            final boolean bE = ss.isSourceEnabled(b.sourceId);
            if (aE != bE) {
                return aE ? -1 : 1;
            }
            else if (a.active != b.active) {
                return a.active ? -1 : 1;
            }
            else {
                return Double.compare(distSq(a.pos, this.panel.getBlockPos()), distSq(b.pos, this.panel.getBlockPos()));
            }
        });
        return sources;
    }
}
