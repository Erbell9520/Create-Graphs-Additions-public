package com.createstats.gui;

import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.blockentity.SafetyClutchSettings;
import com.createstats.network.SafetyClutchConfigPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SafetyClutchScreen extends Screen {
    private static final int PANEL_BG = 0xF0181818;
    private static final int ACCENT = 0xFFCC7700;
    private static final int TEXT = 0xFFDDCCAA;
    private static final int DIM = 0xFF888079;
    private static final int BORDER = 0xFF444444;
    private static final int GREEN = 0xFF44BB44;
    private static final int RED = 0xFFDD4444;
    private static final int YELLOW = 0xFFDDBB33;

    private final SafetyClutchBE clutch;
    private final Screen returnScreen;
    private final SafetyClutchSettings settings;

    private int scrollOffset;
    private int contentHeight;
    private int visibleHeight;

    private boolean thresholdOpen;
    private boolean modeOpen;

    private int selfMonitorLabelY = -1;

    public SafetyClutchScreen(SafetyClutchBE clutch) {
        this(clutch, null);
    }

    public SafetyClutchScreen(SafetyClutchBE clutch, Screen returnScreen) {
        super(Component.translatable("gui.create_stats.safety_clutch.title"));
        this.clutch = clutch;
        this.returnScreen = returnScreen;
        this.settings = SafetyClutchSettings.load(clutch.getSettings().save());
        this.scrollOffset = 0;
        this.thresholdOpen = true;
        this.modeOpen = true;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.visibleHeight = (int) (this.height * 0.88);

        final int cx = this.width / 2;
        final int panelW = 260;
        final int left = cx - panelW / 2;
        int y = 4;

        y += 14;

        addSectionHeader(Component.translatable("gui.create_stats.safety_clutch.section.threshold"), left, y, panelW, this.thresholdOpen, () -> {
            this.thresholdOpen = !this.thresholdOpen;
            this.rebuildWidgets();
        });
        y += 16;
        if (this.thresholdOpen) {
            y = addSliderRow(left, y, panelW, "Trip Threshold", String.format("%.0f%%", this.settings.getThreshold()), () -> {
                this.settings.setThreshold(this.settings.getThreshold() - 5.0f);
                this.rebuildWidgets();
            }, () -> {
                this.settings.setThreshold(this.settings.getThreshold() + 5.0f);
                this.rebuildWidgets();
            });
        }
        y += 4;

        addSectionHeader(Component.translatable("gui.create_stats.safety_clutch.section.mode"), left, y, panelW, this.modeOpen, () -> {
            this.modeOpen = !this.modeOpen;
            this.rebuildWidgets();
        });
        y += 16;
        if (this.modeOpen) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.create_stats.safety_clutch.mode", this.settings.getMode().getDisplayName()),
                    b -> { this.settings.cycleMode(); this.rebuildWidgets(); }
            ).bounds(left + 4, screenY(y), panelW - 8, 16).build());
            y += 20;

            this.addRenderableWidget(Button.builder(
                    Component.translatable(this.settings.isEnabled()
                            ? "gui.create_stats.safety_clutch.enabled"
                            : "gui.create_stats.safety_clutch.disabled"),
                    b -> { this.settings.setEnabled(!this.settings.isEnabled()); this.rebuildWidgets(); }
            ).bounds(left + 4, screenY(y), panelW - 8, 16).build());
            y += 20;

            this.selfMonitorLabelY = y;
            y += 18;

            final int halfW = (panelW - 12) / 2;
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.create_stats.safety_clutch.reset"),
                    b -> {
                        PacketDistributor.sendToServer(new SafetyClutchConfigPacket(
                                this.clutch.getBlockPos(), SafetyClutchConfigPacket.Action.MANUAL_RESET));
                    }
            ).bounds(left + 4, screenY(y), halfW, 16).build());
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.create_stats.safety_clutch.trip_button"),
                    b -> {
                        PacketDistributor.sendToServer(new SafetyClutchConfigPacket(
                                this.clutch.getBlockPos(), SafetyClutchConfigPacket.Action.MANUAL_TRIP));
                    }
            ).bounds(left + 8 + halfW, screenY(y), halfW, 16).build());
            y += 20;
        }
        y += 12;

        final int btnW = 80;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.create_stats.safety_clutch.apply"), b -> applyAndClose())
                .bounds(cx - btnW - 4, screenY(y), btnW, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.create_stats.safety_clutch.cancel"), b -> onClose())
                .bounds(cx + 4, screenY(y), btnW, 20).build());
        y += 30;

        this.contentHeight = y;
        final int maxS = Math.max(0, this.contentHeight - this.visibleHeight + 20);
        if (this.scrollOffset > maxS) this.scrollOffset = maxS;
    }

    private void addSectionHeader(Component label, int x, int y, int w, boolean open, Runnable toggle) {
        final String arrow = open ? "▼ " : "▶ ";
        this.addRenderableWidget(Button.builder(Component.literal(arrow).append(label), b -> toggle.run())
                .bounds(x, screenY(y), w, 14).build());
    }

    private int addSliderRow(int left, int y, int panelW, String label, String valueText,
                             Runnable onMinus, Runnable onPlus) {
        final int btnW = 20;
        final int innerW = panelW - 8;
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> onMinus.run())
                .bounds(left + 4, screenY(y), btnW, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> onPlus.run())
                .bounds(left + 4 + innerW - btnW, screenY(y), btnW, 16).build());
        return y + 20;
    }

    private void applyAndClose() {
        PacketDistributor.sendToServer(new SafetyClutchConfigPacket(this.clutch.getBlockPos(), this.settings));
        this.clutch.setSettings(this.settings);
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderTransparentBackground(g);
        final int cx = this.width / 2;
        final int panelW = 260;
        final int left = cx - panelW / 2;
        final int top = (this.height - this.visibleHeight) / 2;

        g.fill(left, top, left + panelW, top + this.visibleHeight, PANEL_BG);
        g.fill(left, top, left + panelW, top + 1, BORDER);
        g.fill(left, top + this.visibleHeight - 1, left + panelW, top + this.visibleHeight, BORDER);
        g.fill(left, top, left + 1, top + this.visibleHeight, BORDER);
        g.fill(left + panelW - 1, top, left + panelW, top + this.visibleHeight, BORDER);

        g.drawCenteredString(this.font, Component.translatable("gui.create_stats.safety_clutch.header"), cx, top + 5, ACCENT);

        Component status;
        int statusColor;
        if (!this.clutch.getSettings().isEnabled()) {
            status = Component.translatable("gui.create_stats.safety_clutch.status.disabled");
            statusColor = DIM;
        } else if (this.clutch.isTripped()) {
            status = Component.translatable("gui.create_stats.safety_clutch.status.tripped");
            statusColor = RED;
        } else if (!this.clutch.isNetworkRunning()) {
            status = Component.translatable("gui.create_stats.safety_clutch.status.idle");
            statusColor = DIM;
        } else {
            status = Component.translatable("gui.create_stats.safety_clutch.status.armed");
            statusColor = GREEN;
        }
        final float stressPercent = this.clutch.getLastStressPercent();
        final float threshold = this.clutch.getSettings().getThreshold();
        final Component liveLine = Component.translatable("gui.create_stats.safety_clutch.live",
                String.format("%.1f", stressPercent), String.format("%.0f", threshold));
        g.drawCenteredString(this.font, Component.translatable("gui.create_stats.safety_clutch.status_line", status, liveLine),
                cx, top + this.visibleHeight - 10, statusColor);
        final Component reasonLine;
        if (!this.clutch.getSettings().isEnabled()) {
            reasonLine = Component.translatable("gui.create_stats.safety_clutch.reason.disabled");
        } else if (this.clutch.isTripped()) {
            reasonLine = this.clutch.getSettings().getMode() == SafetyClutchSettings.ClutchMode.AUTO_RESET
                    ? Component.translatable("gui.create_stats.safety_clutch.reason.retrying",
                            String.format("%.1f", this.clutch.getAutoResetRetrySeconds()))
                    : Component.translatable("gui.create_stats.safety_clutch.reason.manual_reset");
        } else if (this.clutch.isNetworkRunning()) {
            reasonLine = Component.translatable("gui.create_stats.safety_clutch.reason.armed");
        } else {
            reasonLine = Component.translatable("gui.create_stats.safety_clutch.reason.waiting");
        }
        g.drawCenteredString(this.font, reasonLine, cx, top + this.visibleHeight - 22, DIM);

        int y = 4 + 14 + 16;
        if (this.thresholdOpen) {
            drawSliderLabel(g, left, screenY(y), panelW,
                    Component.translatable("gui.create_stats.safety_clutch.trip_at", String.format("%.0f%%", this.settings.getThreshold())));
        }

        if (this.modeOpen && this.selfMonitorLabelY >= 0) {
            g.drawCenteredString(this.font, Component.translatable("gui.create_stats.safety_clutch.self_monitor"),
                    cx, screenY(this.selfMonitorLabelY) + 4, DIM);
        }

        if (this.contentHeight > this.visibleHeight) {
            final int maxS = Math.max(1, this.contentHeight - this.visibleHeight + 20);
            final int barH = Math.max(12, this.visibleHeight * this.visibleHeight / this.contentHeight);
            final int barY = top + (int) (this.scrollOffset / (float) maxS * (this.visibleHeight - barH));
            g.fill(left + panelW - 5, barY, left + panelW - 2, barY + barH, 0x67FFFFFF);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        final int maxS = Math.max(0, this.contentHeight - this.visibleHeight + 20);
        if (maxS > 0) {
            this.scrollOffset = Math.max(0, Math.min(maxS, this.scrollOffset - (int) (scrollY * 14.0)));
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private int screenY(int contentY) {
        final int panelTop = (this.height - this.visibleHeight) / 2;
        return panelTop + 16 + contentY - this.scrollOffset;
    }

    private void drawSliderLabel(GuiGraphics g, int left, int y, int panelW, Component text) {
        final int innerLeft = left + 28;
        final int innerRight = left + panelW - 28;
        final int centerX = (innerLeft + innerRight) / 2;
        g.drawCenteredString(this.font, text, centerX, y + 4, TEXT);
    }

    @Override
    public void onClose() {
        if (this.returnScreen != null) {
            Minecraft.getInstance().setScreen(this.returnScreen);
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
