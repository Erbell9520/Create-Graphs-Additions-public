package com.createstats.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.renderer.SpecializedPanelRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** [1] */
@OnlyIn(Dist.CLIENT)
public class StatsDisplayMk2Screen extends Screen {

    private enum Step {
        SOURCES("Sources"),
        CONTENT("Content"),
        STYLE("Style"),
        ALERTS("Alerts"),
        LIVE("Live");

        final String label;

        Step(String label) {
            this.label = label;
        }
    }

    // Studio palette (ARGB).
    private static final int DIM = 0xC0070A0E;
    private static final int ROOT_BG = 0xFF12161C;
    private static final int PANEL = 0xFF1A212B;
    private static final int PANEL_HI = 0xFF243040;
    private static final int BORDER = 0xFF2C3746;
    private static final int BORDER_HI = 0xFF3E4C5E;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int TEXT_DIM = 0xFF98A2AE;
    private static final int ACCENT = 0xFFE0A44A;
    private static final int ACCENT2 = 0xFF48C9D6;
    private static final int OK = 0xFF5FD08A;

    private final StatsDisplayPanelBE panel;
    private Step step = Step.SOURCES;
    private String focusedSourceId;
    private int sourceScroll;

    // Layout (computed in init()).
    private int rootX;
    private int rootY;
    private int rootW;
    private int rootH;
    private int headerX;
    private int headerY;
    private int headerW;
    private int railX;
    private int railY;
    private int stepBtnW;
    private int stepBtnH;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    private static final int HEADER_H = 22;
    private static final int RAIL_H = 22;
    private static final int STEP_GAP = 6;

    // Source list geometry captured during render for hit testing.
    private int listX;
    private int listY;
    private int listW;
    private int rowH;
    private List<SourceEntry> lastSources = new ArrayList<>();

    public StatsDisplayMk2Screen(StatsDisplayPanelBE panel) {
        super(Component.literal("Stats Display Mk II"));
        this.panel = panel;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        final int m = Mth.clamp(Math.min(this.width, this.height) / 14, 16, 44);
        this.rootX = m;
        this.rootY = m;
        this.rootW = this.width - m * 2;
        this.rootH = this.height - m * 2;

        final int pad = 12;
        this.headerX = this.rootX + pad;
        this.headerY = this.rootY + pad;
        this.headerW = this.rootW - pad * 2;

        this.railX = this.headerX;
        this.railY = this.headerY + HEADER_H + 8;
        this.stepBtnW = (this.headerW - STEP_GAP * (Step.values().length - 1)) / Step.values().length;
        this.stepBtnH = RAIL_H;

        final int bodyY = this.railY + RAIL_H + 12;
        final int bodyBottom = this.rootY + this.rootH - pad;
        this.previewW = Mth.clamp(Math.round((this.rootW - pad * 2) * 0.42f), 150, 460);
        final int colGap = 12;
        this.contentX = this.headerX;
        this.contentY = bodyY;
        this.contentW = this.headerW - this.previewW - colGap;
        this.contentH = bodyBottom - bodyY;
        this.previewX = this.contentX + this.contentW + colGap;
        this.previewY = bodyY;
        this.previewH = this.contentH;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: in 1.21 the vanilla background path runs the blur shader over the world.
        // We dim the world ourselves in render() instead. See ui-preferences memory note.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim the world behind the studio (no blur).
        g.fill(0, 0, this.width, this.height, DIM);
        // FE Dependent Mode panel in the free screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.panel.feConsumer());

        // Root panel.
        fillPanel(g, this.rootX, this.rootY, this.rootW, this.rootH, ROOT_BG, BORDER_HI);

        drawHeader(g);
        drawRail(g, mouseX, mouseY);
        drawContent(g, mouseX, mouseY);
        drawPreview(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics g) {
        g.drawString(this.font, "STATS DISPLAY", this.headerX, this.headerY + 2, ACCENT, false);
        final int titleW = this.font.width("STATS DISPLAY ");
        g.drawString(this.font, "Mk II", this.headerX + titleW, this.headerY + 2, TEXT, false);

        // Status chip on the right.
        final boolean live = !this.panel.getActiveChannels().isEmpty();
        final String status = live ? "LIVE" : "NO DATA";
        final int chipColor = live ? OK : TEXT_DIM;
        final int chipW = this.font.width(status) + 16;
        final int chipX = this.headerX + this.headerW - chipW;
        final int chipY = this.headerY - 1;
        fillPanel(g, chipX, chipY, chipW, 14, PANEL, BORDER);
        g.fill(chipX + 5, chipY + 5, chipX + 9, chipY + 9, chipColor);
        g.drawString(this.font, status, chipX + 12, chipY + 3, chipColor, false);

        g.fill(this.headerX, this.headerY + HEADER_H + 2, this.headerX + this.headerW, this.headerY + HEADER_H + 3, BORDER);
    }

    private void drawRail(GuiGraphics g, int mouseX, int mouseY) {
        final Step[] steps = Step.values();
        for (int i = 0; i < steps.length; i++) {
            final int x = this.railX + i * (this.stepBtnW + STEP_GAP);
            final boolean active = steps[i] == this.step;
            final boolean hover = inRect(mouseX, mouseY, x, this.railY, this.stepBtnW, this.stepBtnH);
            final int bg = active ? PANEL_HI : (hover ? PANEL_HI : PANEL);
            final int edge = active ? ACCENT : BORDER;
            fillPanel(g, x, this.railY, this.stepBtnW, this.stepBtnH, bg, edge);
            if (active) {
                g.fill(x, this.railY + this.stepBtnH - 2, x + this.stepBtnW, this.railY + this.stepBtnH, ACCENT);
            }
            final int labelColor = active ? TEXT : TEXT_DIM;
            g.drawCenteredString(this.font, (i + 1) + "  " + steps[i].label, x + this.stepBtnW / 2, this.railY + 7, labelColor);
        }
    }

    private void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        fillPanel(g, this.contentX, this.contentY, this.contentW, this.contentH, PANEL, BORDER);
        switch (this.step) {
            case SOURCES -> drawSourcesStep(g, mouseX, mouseY);
            case CONTENT -> drawPlaceholder(g, "Content", "Choose what this panel shows: a tailored template for the machine, or a custom layout.");
            case STYLE -> drawPlaceholder(g, "Style", "Theme, instrument frame, gauge ranges and animation, with the preview updating live.");
            case ALERTS -> drawPlaceholder(g, "Alerts & Redstone", "Per-channel thresholds, alarms and redstone output for any source.");
            case LIVE -> drawPlaceholder(g, "Live", "A large live view with running stats, data log and prediction.");
        }
    }

    private void drawSourcesStep(GuiGraphics g, int mouseX, int mouseY) {
        final int pad = 12;
        g.drawString(this.font, "Sources", this.contentX + pad, this.contentY + pad, TEXT, false);
        g.drawString(this.font, "Machines feeding this display. Click one to focus it in the preview.",
                this.contentX + pad, this.contentY + pad + 12, TEXT_DIM, false);

        this.lastSources = collectSources();
        this.listX = this.contentX + pad;
        this.listY = this.contentY + pad + 30;
        this.listW = this.contentW - pad * 2;
        this.rowH = 30;

        if (this.lastSources.isEmpty()) {
            g.drawString(this.font, "No sources linked yet.", this.listX, this.listY + 6, TEXT_DIM, false);
            g.drawString(this.font, "Point a Create Display Link from a machine at this panel.",
                    this.listX, this.listY + 20, TEXT_DIM, false);
            return;
        }

        final int visibleBottom = this.contentY + this.contentH - pad;
        int y = this.listY - this.sourceScroll;
        for (final SourceEntry entry : this.lastSources) {
            if (y + this.rowH >= this.listY && y <= visibleBottom) {
                final boolean focused = entry.id.equals(this.focusedSourceId);
                final boolean hover = inRect(mouseX, mouseY, this.listX, y, this.listW, this.rowH - 4);
                fillPanel(g, this.listX, y, this.listW, this.rowH - 4, focused ? PANEL_HI : (hover ? PANEL_HI : PANEL),
                        focused ? ACCENT : BORDER);
                if (focused) {
                    g.fill(this.listX, y, this.listX + 3, y + this.rowH - 4, ACCENT);
                }
                final String name = truncate(pretty(entry.id), this.listW - 60);
                g.drawString(this.font, name, this.listX + 8, y + 5, TEXT, false);
                final String meta = entry.channels.size() + (entry.channels.size() == 1 ? " reading" : " readings");
                g.drawString(this.font, meta, this.listX + 8, y + 15, TEXT_DIM, false);
                if (!entry.channels.isEmpty()) {
                    final String sample = entry.channels.get(0).getLastText();
                    if (sample != null && !sample.isEmpty()) {
                        final String s = truncate(sample, 56);
                        g.drawString(this.font, s, this.listX + this.listW - this.font.width(s) - 8, y + 10, ACCENT2, false);
                    }
                }
            }
            y += this.rowH;
        }
    }

    private void drawPlaceholder(GuiGraphics g, String title, String desc) {
        final int cx = this.contentX + this.contentW / 2;
        final int cy = this.contentY + this.contentH / 2;
        g.drawCenteredString(this.font, title, cx, cy - 16, TEXT);
        final int maxW = this.contentW - 40;
        int ty = cy;
        for (final String line : wrap(desc, maxW)) {
            g.drawCenteredString(this.font, line, cx, ty, TEXT_DIM);
            ty += 12;
        }
        g.drawCenteredString(this.font, "Coming in a later phase", cx, ty + 8, ACCENT);
    }

    private void drawPreview(GuiGraphics g) {
        fillPanel(g, this.previewX, this.previewY, this.previewW, this.previewH, ROOT_BG, BORDER);
        final String caption = switch (this.step) {
            case SOURCES -> "Live preview - focused source";
            case CONTENT -> "Live preview - layout";
            case STYLE -> "Live preview - style";
            case ALERTS -> "Live preview - alerts";
            case LIVE -> "Live view";
        };
        g.drawString(this.font, caption, this.previewX + 8, this.previewY + 6, TEXT_DIM, false);

        final int inx = this.previewX + 8;
        final int iny = this.previewY + 20;
        final int inw = this.previewW - 16;
        final int inh = this.previewH - 28;

        final List<StatsDisplayPanelBE.DataChannel> channels = this.panel.getActiveChannels();
        final long tick = this.panel.getLevel() != null ? this.panel.getLevel().getGameTime() : 0L;
        boolean drew = false;
        if (!channels.isEmpty()) {
            drew = SpecializedPanelRenderer.renderStandardSpecializedPanelGUI(
                    g, this.font, inx, iny, inw, inh, this.panel, channels, this.focusedSourceId, tick);
        }
        if (!drew) {
            g.fill(inx, iny, inx + inw, iny + inh, 0xFF0E1319);
            drawBorder(g, inx, iny, inw, inh, BORDER);
            final int cx = inx + inw / 2;
            g.drawCenteredString(this.font, "No live data yet", cx, iny + inh / 2 - 10, TEXT_DIM);
            for (final String line : wrap("Link a Create Display Link from a machine to this panel.", inw - 20)) {
                g.drawCenteredString(this.font, line, cx, iny + inh / 2 + 4, TEXT_DIM);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Step rail.
            final Step[] steps = Step.values();
            for (int i = 0; i < steps.length; i++) {
                final int x = this.railX + i * (this.stepBtnW + STEP_GAP);
                if (inRect((int) mouseX, (int) mouseY, x, this.railY, this.stepBtnW, this.stepBtnH)) {
                    this.step = steps[i];
                    return true;
                }
            }
            // Source rows.
            if (this.step == Step.SOURCES && !this.lastSources.isEmpty()) {
                int y = this.listY - this.sourceScroll;
                for (final SourceEntry entry : this.lastSources) {
                    if (inRect((int) mouseX, (int) mouseY, this.listX, y, this.listW, this.rowH - 4)) {
                        this.focusedSourceId = entry.id.equals(this.focusedSourceId) ? null : entry.id;
                        return true;
                    }
                    y += this.rowH;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.step == Step.SOURCES && inRect((int) mouseX, (int) mouseY, this.contentX, this.contentY, this.contentW, this.contentH)) {
            final int total = this.lastSources.size() * this.rowH;
            final int viewport = this.contentH - (this.listY - this.contentY) - 12;
            final int maxScroll = Math.max(0, total - viewport);
            this.sourceScroll = Mth.clamp(this.sourceScroll - (int) (scrollY * 16), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= 49 && keyCode <= 53) {
            this.step = Step.values()[keyCode - 49];
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // helpers

    private List<SourceEntry> collectSources() {
        final LinkedHashMap<String, SourceEntry> map = new LinkedHashMap<>();
        for (final StatsDisplayPanelBE.DataChannel ch : this.panel.getActiveChannels()) {
            final String sid = sourceIdOf(ch.getId());
            map.computeIfAbsent(sid, SourceEntry::new).channels.add(ch);
        }
        return new ArrayList<>(map.values());
    }

    private static String sourceIdOf(String channelId) {
        if (channelId != null && channelId.startsWith("src_")) {
            final int a = channelId.indexOf("_auto_");
            if (a > 4) {
                return channelId.substring(4, a);
            }
        }
        return channelId == null ? "" : channelId;
    }

    private static String pretty(String sid) {
        if (sid == null || sid.isEmpty()) {
            return "Source";
        }
        final String s = sid.replace('_', ' ').trim();
        return s.isEmpty() ? "Source" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String truncate(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (this.font.width(sb.toString() + text.charAt(i) + "...") > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + "...";
    }

    private List<String> wrap(String text, int maxWidth) {
        final List<String> lines = new ArrayList<>();
        final String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (final String word : words) {
            final String test = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(test) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static boolean inRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static void fillPanel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        drawBorder(g, x, y, w, h, border);
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static final class SourceEntry {
        final String id;
        final List<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<>();

        SourceEntry(String id) {
            this.id = id;
        }
    }
}
