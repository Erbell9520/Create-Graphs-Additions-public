package com.createstats.gui;

import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.display.DisplayTheme;
import com.createstats.display.HologramSettings;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.gui.holo.HoloComponent;
import com.createstats.gui.holo.HoloUi;
import com.createstats.gui.holo.HoloWidgets;
import com.createstats.network.HologramConfigPacket;
import com.createstats.renderer.HologramDisplayMode;
import com.createstats.renderer.ProjectorTextureManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// tabs, the accordion kept everything useful folded away
@OnlyIn(Dist.CLIENT)
public class HolographicProjectorSettingsScreen extends Screen {
    private static final String[] TABS = { "Display", "Layout", "Signal", "Data", "Channels" };

    private static final HologramDisplayMode[] MODES = {
            HologramDisplayMode.LINE_3D, HologramDisplayMode.BAR_3D, HologramDisplayMode.GAUGE_3D };
    private static final int FE_CARD_H = 46;
    private static final int CHANNEL_ROW_H = 22;

    private final HolographicProjectorBE projector;
    private final HologramSettings settings;
    private final List<HoloComponent> components = new ArrayList<>();

    private int tab;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private int contentY;
    private int contentH;
    private int channelScroll;
    private int channelContentH;

    private final HoloUi.Anim intro = new HoloUi.Anim(0.0f, 240);
    private final HoloUi.Anim indicatorX = new HoloUi.Anim(0.0f, 260);
    private final HoloUi.Anim indicatorW = new HoloUi.Anim(0.0f, 260);
    private final HoloUi.Anim contentIn = new HoloUi.Anim(1.0f, 200);

    public HolographicProjectorSettingsScreen(HolographicProjectorBE projector) {
        super(Component.translatable("gui.create_stats.holographic_projector.title"));
        this.projector = projector;
        this.settings = projector.getHologramSettings();
    }

    @Override
    protected void init() {
        super.init();
        this.cardW = Mth.clamp(this.width - 40, 288, 340);
        this.cardH = Mth.clamp(this.height - 40, 176, 248);
        final boolean showFe = com.createstats.power.client.FeStatusWidget
                .height(this.projector.feConsumer()) > 0;
        final int stackH = this.cardH + (showFe ? FE_CARD_H + 8 : 0);
        this.cardX = (this.width - this.cardW) / 2;
        this.cardY = Math.max(8, (this.height - stackH) / 2);
        this.contentY = this.cardY + HoloUi.HEADER_H + HoloUi.TAB_H;
        this.contentH = this.cardH - HoloUi.HEADER_H - HoloUi.TAB_H - HoloUi.FOOTER_H;
        this.intro.snap(0.0f);
        this.intro.target(1.0f);
        this.rebuild();
        this.snapIndicator();
    }

    private int tabGap() {
        int total = 0;
        for (String t : TABS) {
            total += this.font.width(t);
        }
        final int avail = this.cardW - 2 * HoloUi.PAD;
        for (int gap = 22; gap > 6; gap -= 2) {
            if (total + gap * (TABS.length - 1) <= avail) {
                return gap;
            }
        }
        return 6;
    }

    private int tabX(int index) {
        final int gap = this.tabGap();
        int x = this.cardX + HoloUi.PAD;
        for (int i = 0; i < index; i++) {
            x += this.font.width(TABS[i]) + gap;
        }
        return x;
    }

    private void snapIndicator() {
        this.indicatorX.snap(this.tabX(this.tab) - 2);
        this.indicatorW.snap(this.font.width(TABS[this.tab]) + 4);
    }

    private void selectTab(int index) {
        if (index == this.tab) {
            return;
        }
        this.tab = index;
        this.channelScroll = 0;
        this.contentIn.snap(0.0f);
        this.contentIn.target(1.0f);
        this.rebuild();
    }

    private void rebuild() {
        for (HoloComponent c : this.components) {
            c.loseFocus();
        }
        this.components.clear();
        final int cx = this.cardX + HoloUi.PAD;
        final int cw = this.cardW - 2 * HoloUi.PAD;
        final int half = (cw - 8) / 2;
        switch (this.tab) {
            case 0 -> this.buildDisplay(cx, cw, half);
            case 1 -> this.buildLayout(cx, cw, half);
            case 2 -> this.buildSignal(cx, cw);
            case 3 -> this.buildData(cx, cw);
            default -> this.buildChannels(cx, cw);
        }
        this.components.add(new HoloWidgets.Button("Done", true, this::onClose)
                .bounds(this.cardX + this.cardW - HoloUi.PAD - 76, this.cardY + this.cardH - 28, 76, 22));
    }

    private void buildDisplay(int cx, int cw, int half) {
        this.components.add(new HoloWidgets.Segmented(
                () -> new String[] { "Line", "Bar", "Gauge" },
                this::currentModeIndex,
                i -> {
                    this.settings.setDisplayMode(MODES[Mth.clamp(i, 0, MODES.length - 1)]);
                    this.apply();
                }).bounds(cx, this.contentY + 24, cw, HoloUi.ROW_H));

        final DisplayTheme[] themes = DisplayTheme.values();
        final int[] backgrounds = new int[themes.length];
        final int[] accents = new int[themes.length];
        for (int i = 0; i < themes.length; i++) {
            backgrounds[i] = themes[i].graphBg;
            accents[i] = themes[i].accent;
        }
        this.components.add(new HoloWidgets.Swatches(backgrounds, accents,
                () -> this.settings.getDisplayTheme().ordinal(),
                i -> {
                    this.settings.setDisplayTheme(themes[Mth.clamp(i, 0, themes.length - 1)]);
                    this.apply();
                }).bounds(cx, this.contentY + 70, cw, HoloUi.ROW_H));

        this.components.add(new HoloWidgets.Toggle("Grid", this.settings::isShowGrid, v -> {
            this.settings.setShowGrid(v);
            this.apply();
        }).bounds(cx, this.contentY + 104, half, HoloUi.ROW_H));
        this.components.add(new HoloWidgets.Toggle("Labels", this.settings::isShowLabels, v -> {
            this.settings.setShowLabels(v);
            this.apply();
        }).bounds(cx + half + 8, this.contentY + 104, half, HoloUi.ROW_H));
    }

    private void buildLayout(int cx, int cw, int half) {
        this.components.add(new HoloWidgets.Segmented(
                () -> new String[] { "Fixed", "Face me", "Spin" },
                () -> this.settings.getAlignment().ordinal(),
                i -> {
                    this.settings.setAlignment(HologramSettings.Alignment.values()
                            [Mth.clamp(i, 0, HologramSettings.Alignment.values().length - 1)]);
                    this.apply();
                    this.rebuild();
                }).bounds(cx, this.contentY + 24, cw, HoloUi.ROW_H));

        this.components.add(new HoloWidgets.Slider("Glow", 0.0, 1.0, 0.1,
                this.settings::getGlowIntensity,
                v -> {
                    this.settings.setGlowIntensity(v.floatValue());
                    this.apply();
                },
                () -> String.format(Locale.ROOT, "%.0f%%", this.settings.getGlowIntensity() * 100.0f))
                .bounds(cx, this.contentY + 54, cw, 20));

        final HoloWidgets.Slider spin = new HoloWidgets.Slider("Spin speed", 0.3, 3.0, 0.3,
                this.settings::getRotationSpeed,
                v -> {
                    this.settings.setRotationSpeed(v.floatValue());
                    this.apply();
                },
                () -> String.format(Locale.ROOT, "%.1f deg/t", this.settings.getRotationSpeed()));
        // dim it instead of hiding it so nothing below jumps

        spin.enabled = this.settings.getAlignment() == HologramSettings.Alignment.SPIN;
        this.components.add(spin.bounds(cx, this.contentY + 79, cw, 20));

        this.components.add(new HoloWidgets.Chip(
                () -> this.settings.isSizeUnlocked() ? "Max 32" : "Max 16",
                this.settings::isSizeUnlocked,
                () -> {
                    this.settings.setSizeUnlocked(!this.settings.isSizeUnlocked());
                    this.apply();
                }).bounds(cx + cw - 58, this.contentY + 102, 58, 14));

        final int max = this.settings.isSizeUnlocked() ? 32 : 16;
        this.components.add(new HoloWidgets.Stepper("Width", this.settings::getHologramWidth, v -> {
            this.settings.setHologramWidth(v);
            this.apply();
        }, 1, max).bounds(cx, this.contentY + 116, half, HoloUi.ROW_H));
        this.components.add(new HoloWidgets.Stepper("Height", this.settings::getHologramHeight, v -> {
            this.settings.setHologramHeight(v);
            this.apply();
        }, 1, max).bounds(cx + half + 8, this.contentY + 116, half, HoloUi.ROW_H));
    }

    private void buildSignal(int cx, int cw) {
        this.components.add(new HoloWidgets.Toggle("Redstone output",
                this.projector::isRedstoneEnabled, v -> {
            this.projector.setRedstoneEnabled(v);
            this.apply();
            this.rebuild();
        }).bounds(cx, this.contentY + 12, cw, HoloUi.ROW_H));

        final boolean on = this.projector.isRedstoneEnabled();
        final HoloWidgets.Picker channel = new HoloWidgets.Picker("Channel",
                this::redstoneChannelLabel, this::cycleRedstoneChannel);
        channel.enabled = on;
        this.components.add(channel.bounds(cx, this.contentY + 42, cw, HoloUi.ROW_H));

        final HoloWidgets.Segmented mode = new HoloWidgets.Segmented(
                () -> new String[] { "Binary", "Proportional" },
                this.projector::getRedstoneMode,
                i -> {
                    this.projector.setRedstoneMode(i);
                    this.apply();
                });
        mode.enabled = on;
        this.components.add(mode.bounds(cx, this.contentY + 72, cw, HoloUi.ROW_H));

        final int fieldW = cw - 116;
        final HoloWidgets.NumberField threshold = new HoloWidgets.NumberField("Threshold",
                () -> formatThreshold(this.projector.getRedstoneThreshold()),
                v -> {
                    this.projector.setRedstoneThreshold(v);
                    this.apply();
                });
        threshold.enabled = on;
        this.components.add(threshold.bounds(cx, this.contentY + 102, fieldW, HoloUi.ROW_H));

        final HoloWidgets.Button compare = new HoloWidgets.Button(
                () -> this.projector.isRedstoneAbove() ? "Above" : "Below", false,
                () -> {
                    this.projector.setRedstoneAbove(!this.projector.isRedstoneAbove());
                    this.apply();
                });
        compare.enabled = on;
        this.components.add(compare.bounds(cx + fieldW + 8, this.contentY + 102,
                cw - fieldW - 8, HoloUi.ROW_H));
    }

    private void buildData(int cx, int cw) {
        final StatsDisplaySettings.TimeRange[] ranges = StatsDisplaySettings.TimeRange.values();
        final String[] names = new String[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            names[i] = ranges[i].getDisplayName();
        }
        this.components.add(new HoloWidgets.Segmented(() -> names,
                () -> this.settings.getTimeRange().ordinal(),
                i -> {
                    this.settings.setTimeRange(ranges[Mth.clamp(i, 0, ranges.length - 1)]);
                    this.apply();
                }).bounds(cx, this.contentY + 24, cw, HoloUi.ROW_H));

        this.components.add(new HoloWidgets.Slider("Page dwell", 20.0, 200.0, 20.0,
                this.settings::getCycleInterval,
                v -> {
                    this.settings.setCycleInterval((int) Math.round(v));
                    this.apply();
                },
                () -> String.format(Locale.ROOT, "%.1fs", this.settings.getCycleInterval() / 20.0f))
                .bounds(cx, this.contentY + 56, cw, 20));

        this.components.add(new HoloWidgets.Toggle("Scan nearby blocks",
                this.projector::isScanEnabled, v -> {
            this.projector.setScanEnabled(v);
            this.apply();
        }).bounds(cx, this.contentY + 84, cw, HoloUi.ROW_H));
    }

    private void buildChannels(int cx, int cw) {
        final List<HolographicProjectorBE.DataChannel> channels = this.projector.getActiveChannels();
        this.channelContentH = channels.size() * CHANNEL_ROW_H;
        final int viewH = this.contentH - 8;
        this.channelScroll = Mth.clamp(this.channelScroll, 0, Math.max(0, this.channelContentH - viewH));
        final int listW = this.channelContentH > viewH ? cw - 6 : cw;
        int rowY = this.contentY + 4 - this.channelScroll;
        for (HolographicProjectorBE.DataChannel ch : channels) {
            final String id = ch.getId();
            this.components.add(new HoloWidgets.ChannelRow(ch.getLabel(),
                    () -> ch.getStore().getMetricType().formatValue(ch.getStore().getLatestValue()),
                    () -> !this.settings.getHiddenChannelIds().contains(id),
                    () -> {
                        if (!this.settings.getHiddenChannelIds().remove(id)) {
                            this.settings.getHiddenChannelIds().add(id);
                        }
                        this.apply();
                    }).bounds(cx, rowY, listW, CHANNEL_ROW_H));
            rowY += CHANNEL_ROW_H;
        }
    }

    private int currentModeIndex() {
        final HologramDisplayMode mode = this.settings.getDisplayMode();
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i] == mode) {
                return i;
            }
        }
        return 0;
    }

    private static String formatThreshold(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private String redstoneChannelLabel() {
        final String id = this.projector.getRedstoneChannelId();
        if (id == null || id.isEmpty()) {
            return "None";
        }
        for (HolographicProjectorBE.DataChannel ch : this.projector.getActiveChannels()) {
            if (ch.getId().equals(id)) {
                return ch.getLabel();
            }
        }
        return id;
    }

    private void cycleRedstoneChannel(int direction) {
        final List<HolographicProjectorBE.DataChannel> channels = this.projector.getActiveChannels();
        if (channels.isEmpty()) {
            return;
        }

        final String current = this.projector.getRedstoneChannelId();
        int index = -1;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).getId().equals(current)) {
                index = i;
                break;
            }
        }
        final int size = channels.size() + 1;
        final int next = Math.floorMod(index + 1 + direction, size) - 1;
        this.projector.setRedstoneChannelId(next < 0 ? "" : channels.get(next).getId());
        this.apply();
    }

    private void apply() {
        this.projector.setHologramSettings(this.settings);
        PacketDistributor.sendToServer(new HologramConfigPacket(this.projector.getBlockPos(),
                this.settings.save(), this.projector.isRedstoneEnabled(),
                this.projector.getRedstoneChannelId(), this.projector.getRedstoneThreshold(),
                this.projector.isRedstoneAbove(), this.projector.getRedstoneMode(),
                this.projector.isScanEnabled()));
        ProjectorTextureManager.markDirty(this.projector.getBlockPos());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, HoloUi.fade(HoloUi.SCRIM, this.intro.value()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        final float in = this.intro.value();

        final int lift = Math.round((1.0f - in) * 8.0f);
        g.pose().pushPose();
        g.pose().translate(0.0f, lift, 0.0f);

        HoloUi.roundFrame(g, this.cardX, this.cardY, this.cardW, this.cardH, 6,
                HoloUi.fade(HoloUi.CARD_BORDER, in), HoloUi.fade(HoloUi.CARD_BG, in));
        this.renderHeader(g, mouseX, mouseY, in);
        this.renderTabs(g, mouseX, mouseY, in);

        final float contentAlpha = in * this.contentIn.value();
        final int slide = Math.round((1.0f - this.contentIn.value()) * 6.0f);
        g.pose().pushPose();
        g.pose().translate(0.0f, slide, 0.0f);
        this.renderContent(g, mouseX, mouseY - slide - lift, contentAlpha);
        g.pose().popPose();

        this.renderFooter(g, mouseX, mouseY, in);
        g.pose().popPose();
        this.renderFeCard(g, in);
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY, float in) {
        final int left = this.cardX + HoloUi.PAD;
        HoloUi.text(g, this.font, "Holographic Projector", left, this.cardY + 14,
                HoloUi.fade(HoloUi.TEXT_HI, in), true);
        final int channels = this.projector.getActiveChannels().size();
        final String meta = this.settings.getHologramWidth() + "×" + this.settings.getHologramHeight()
                + "  ·  " + channels + (channels == 1 ? " channel" : " channels")
                + "  ·  " + (this.projector.hasData() ? "live" : "no data");
        HoloUi.text(g, this.font, meta, left, this.cardY + 26, HoloUi.fade(HoloUi.TEXT_DIM, in), false);

        final int cx = this.cardX + this.cardW - HoloUi.PAD - 7;
        final int cy = this.cardY + 15;
        final boolean hot = mouseX >= cx - 3 && mouseX < cx + 10 && mouseY >= cy - 3 && mouseY < cy + 10;
        final int color = HoloUi.fade(hot ? HoloUi.TEXT_HI : HoloUi.TEXT_DIM, in);
        for (int i = 0; i < 7; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            g.fill(cx + 6 - i, cy + i, cx + 7 - i, cy + i + 1, color);
        }
        HoloUi.hLine(g, left, this.cardY + HoloUi.HEADER_H - 1, this.cardW - 2 * HoloUi.PAD,
                HoloUi.fade(HoloUi.DIVIDER, in));
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY, float in) {
        final int gap = this.tabGap();
        final int rowY = this.cardY + HoloUi.HEADER_H;
        int x = this.cardX + HoloUi.PAD;
        for (int i = 0; i < TABS.length; i++) {
            final int tw = this.font.width(TABS[i]);
            final boolean active = i == this.tab;
            final boolean hot = mouseX >= x - 2 && mouseX < x + tw + 2
                    && mouseY >= rowY && mouseY < rowY + HoloUi.TAB_H;
            final int color = active ? HoloUi.TEXT_HI : (hot ? HoloUi.TEXT_MID : HoloUi.TEXT_DIM);
            HoloUi.text(g, this.font, TABS[i], x, rowY + 8, HoloUi.fade(color, in), active);
            x += tw + gap;
        }
        this.indicatorX.target(this.tabX(this.tab) - 2);
        this.indicatorW.target(this.font.width(TABS[this.tab]) + 4);
        HoloUi.roundRect(g, Math.round(this.indicatorX.value()), this.contentY - 3,
                Math.round(this.indicatorW.value()), 2, 1, HoloUi.fade(HoloUi.ACCENT, in));
        HoloUi.hLine(g, this.cardX + HoloUi.PAD, this.contentY - 1, this.cardW - 2 * HoloUi.PAD,
                HoloUi.fade(HoloUi.DIVIDER, in));
    }

    private void renderContent(GuiGraphics g, int mouseX, int mouseY, float alpha) {
        final int left = this.cardX + HoloUi.PAD;
        final int cw = this.cardW - 2 * HoloUi.PAD;
        switch (this.tab) {
            case 0 -> {
                this.caption(g, "Style", left, this.contentY + 12, alpha);
                this.caption(g, "Theme", left, this.contentY + 58, alpha);
                HoloUi.textRight(g, this.font, this.settings.getDisplayTheme().getDisplayName(),
                        left + cw, this.contentY + 58, HoloUi.fade(HoloUi.TEXT_MID, alpha), false);
            }
            case 1 -> {
                this.caption(g, "Projection", left, this.contentY + 12, alpha);
                this.caption(g, "Size", left, this.contentY + 104, alpha);
            }
            case 3 -> {
                this.caption(g, "Window", left, this.contentY + 12, alpha);
                HoloUi.text(g, this.font, "Reads gauges and tanks within 5 blocks.", left,
                        this.contentY + 114, HoloUi.fade(HoloUi.TEXT_DIM, alpha), false);
            }
            case 4 -> {
                if (this.components.size() <= 1) {
                    HoloUi.textCenter(g, this.font, "No channels yet", this.cardX + this.cardW / 2,
                            this.contentY + this.contentH / 2 - 4, HoloUi.fade(HoloUi.TEXT_DIM, alpha), false);
                    HoloUi.textCenter(g, this.font, "Connect a Display Link or enable scanning",
                            this.cardX + this.cardW / 2, this.contentY + this.contentH / 2 + 8,
                            HoloUi.fade(HoloUi.TEXT_DIM, alpha * 0.7f), false);
                }
            }
            default -> {
            }
        }

        final boolean clip = this.tab == 4;
        if (clip) {
            g.enableScissor(this.cardX, this.contentY, this.cardX + this.cardW, this.contentY + this.contentH);
        }
        for (HoloComponent c : this.components) {
            c.update(mouseX, mouseY);
            c.render(g, this.font, mouseX, mouseY, c.enabled ? alpha : alpha * 0.45f);
        }
        if (clip) {
            g.disableScissor();
            final int viewH = this.contentH - 8;
            if (this.channelContentH > viewH) {
                final int trackX = this.cardX + this.cardW - HoloUi.PAD - 2;
                HoloUi.roundRect(g, trackX, this.contentY + 4, 2, viewH, 1, HoloUi.fade(HoloUi.DIVIDER, alpha));
                final int barH = Math.max(12, viewH * viewH / this.channelContentH);
                final int barY = this.contentY + 4 + (viewH - barH)
                        * this.channelScroll / Math.max(1, this.channelContentH - viewH);
                HoloUi.roundRect(g, trackX, barY, 2, barH, 1, HoloUi.fade(HoloUi.CARD_BORDER, alpha));
            }
        }
    }

    private void caption(GuiGraphics g, String s, int x, int y, float alpha) {
        HoloUi.text(g, this.font, s, x, y, HoloUi.fade(HoloUi.TEXT_DIM, alpha), false);
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY, float in) {
        HoloUi.hLine(g, this.cardX + HoloUi.PAD, this.cardY + this.cardH - HoloUi.FOOTER_H,
                this.cardW - 2 * HoloUi.PAD, HoloUi.fade(HoloUi.DIVIDER, in));
        HoloUi.text(g, this.font, "Esc to close", this.cardX + HoloUi.PAD,
                this.cardY + this.cardH - 21, HoloUi.fade(HoloUi.TEXT_DIM, in), false);
    }

    private void renderFeCard(GuiGraphics g, float in) {
        final com.createstats.power.FeConsumer fe = this.projector.feConsumer();
        if (com.createstats.power.client.FeStatusWidget.height(fe) <= 0) {
            return;
        }
        final int y = this.cardY + this.cardH + 8;
        HoloUi.roundFrame(g, this.cardX, y, this.cardW, FE_CARD_H, 6,
                HoloUi.fade(HoloUi.CARD_BORDER, in), HoloUi.fade(HoloUi.CARD_BG, in));
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.cardX + HoloUi.PAD,
                y + 8, this.cardW - 2 * HoloUi.PAD, fe);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        final int rowY = this.cardY + HoloUi.HEADER_H;
        if (my >= rowY && my < rowY + HoloUi.TAB_H) {
            final int gap = this.tabGap();
            int x = this.cardX + HoloUi.PAD;
            for (int i = 0; i < TABS.length; i++) {
                final int tw = this.font.width(TABS[i]);
                if (mx >= x - 2 && mx < x + tw + 2) {
                    this.selectTab(i);
                    return true;
                }
                x += tw + gap;
            }
        }
        final int cx = this.cardX + this.cardW - HoloUi.PAD - 7;
        final int cy = this.cardY + 15;
        if (mx >= cx - 3 && mx < cx + 10 && my >= cy - 3 && my < cy + 10) {
            this.onClose();
            return true;
        }

        final boolean inContent = my >= this.contentY && my < this.contentY + this.contentH;
        for (HoloComponent c : this.components) {
            if (this.tab == 4 && c instanceof HoloWidgets.ChannelRow && !inContent) {
                continue;
            }
            if (c.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (HoloComponent c : this.components) {
            if (c.mouseDragged(mx, my, button)) {
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        for (HoloComponent c : this.components) {
            c.mouseReleased(mx, my, button);
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        final int viewH = this.contentH - 8;
        if (this.tab == 4 && this.channelContentH > viewH) {
            final int max = this.channelContentH - viewH;
            final int next = Mth.clamp(this.channelScroll - (int) (scrollY * 16.0), 0, max);
            if (next != this.channelScroll) {
                this.channelScroll = next;
                this.rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        for (HoloComponent c : this.components) {
            if (c.keyPressed(key, scancode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        for (HoloComponent comp : this.components) {
            if (comp.charTyped(c, modifiers)) {
                return true;
            }
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public void onClose() {
        for (HoloComponent c : this.components) {
            c.loseFocus();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
