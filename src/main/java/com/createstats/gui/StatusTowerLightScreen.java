package com.createstats.gui;

import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.blockentity.StatusTowerLightSettings;
import com.createstats.network.StatusTowerLightConfigPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StatusTowerLightScreen extends Screen {

    private static final int PANEL_BG = 0xF0181818;
    private static final int BORDER = 0xFF444444;
    private static final int ACCENT = 0xFFFFCC55;
    private static final int TEXT = 0xFFDDCCAA;
    private static final int DIM = 0xFF888079;
    private static final int STATUS_BG = 0xAA11161C;
    private static final int ROW_HEIGHT = 22;

    private final StatusTowerLightBE towerLight;
    private final StatusTowerLightSettings settings;

    private EditBox warningInput;
    private EditBox criticalInput;
    private boolean syncingInputs;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int warningRowY;
    private int criticalRowY;

    public StatusTowerLightScreen(StatusTowerLightBE towerLight) {
        super(Component.translatable("block.create_stats.status_tower_light"));
        this.towerLight = towerLight;
        this.settings = StatusTowerLightSettings.load(towerLight.getSettings().save());
        this.warningRowY = -1;
        this.criticalRowY = -1;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.warningInput = null;
        this.criticalInput = null;
        this.warningRowY = -1;
        this.criticalRowY = -1;

        final StatusTowerLightSettings.SourceKind sourceKind = this.getSourceKind();
        this.settings.ensureMetricSupported(sourceKind);

        this.panelWidth = 284;
        this.panelHeight = this.settings.usesThresholdLogic() ? 286 : 258;
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        int y = this.panelTop + 88;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.create_stats.status_tower.source_mode", this.settings.getSourceMode().getDisplayName()),
                b -> {
                    this.settings.cycleSourceMode();
                    this.rebuildWidgets();
                }).bounds(this.panelLeft + 10, y, this.panelWidth - 20, 20).build());
        y += ROW_HEIGHT;

        final Button metricButton = Button.builder(
                this.getMetricButtonLabel(sourceKind),
                b -> {
                    this.settings.cycleMetric(this.getSourceKind());
                    this.rebuildWidgets();
                }).bounds(this.panelLeft + 10, y, this.panelWidth - 20, 20).build();
        metricButton.active = this.settings.getMetricOptions(sourceKind).length > 1;
        this.addRenderableWidget(metricButton);
        y += ROW_HEIGHT;

        if (this.settings.usesAutoLogicSelection()) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.create_stats.status_tower.auto_behavior", this.settings.getLogicMode().getDisplayName()),
                    b -> {
                        this.settings.cycleLogicMode();
                        this.rebuildWidgets();
                    }).bounds(this.panelLeft + 10, y, this.panelWidth - 20, 20).build());
            y += ROW_HEIGHT;
        }

        if (this.settings.usesThresholdLogic()) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.create_stats.status_tower.direction", this.settings.getThresholdDirection().getDisplayName()),
                    b -> {
                        this.settings.cycleThresholdDirection();
                        this.rebuildWidgets();
                    }).bounds(this.panelLeft + 10, y, this.panelWidth - 20, 20).build());
            y += ROW_HEIGHT + 2;
            this.warningRowY = y;
            y = this.addThresholdRow(y, true);
            this.criticalRowY = y;
            y = this.addThresholdRow(y, false);
        } else {
            this.addRenderableWidget(Button.builder(
                    Component.translatable(this.settings.isBinaryInvert() ? "gui.create_stats.status_tower.binary_invert.on" : "gui.create_stats.status_tower.binary_invert.off"),
                    b -> {
                        this.settings.setBinaryInvert(!this.settings.isBinaryInvert());
                        this.rebuildWidgets();
                    }).bounds(this.panelLeft + 10, y, this.panelWidth - 20, 20).build());
            y += ROW_HEIGHT;
        }

        final int buttonY = this.panelTop + this.panelHeight - 30;
        final int buttonWidth = 86;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.create_stats.common.apply"), b -> this.applyAndClose())
                .bounds(this.panelLeft + this.panelWidth / 2 - buttonWidth - 4, buttonY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.create_stats.common.cancel"), b -> this.onClose())
                .bounds(this.panelLeft + this.panelWidth / 2 + 4, buttonY, buttonWidth, 20).build());
    }

    private int addThresholdRow(int y, boolean warning) {
        final int minusX = this.panelLeft + this.panelWidth - 122;
        final int inputX = this.panelLeft + this.panelWidth - 96;
        final int plusX = this.panelLeft + this.panelWidth - 26;

        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> this.adjustThreshold(warning, -1.0f))
                .bounds(minusX, y, 20, 16).build());

        final EditBox input = new EditBox(this.font, inputX, y, 68, 16,
                Component.translatable(warning ? "gui.create_stats.status_tower.warning_threshold" : "gui.create_stats.status_tower.critical_threshold"));
        input.setMaxLength(12);
        input.setFilter(text -> text.isEmpty() || text.matches("[0-9]*\\.?[0-9]*"));
        input.setValue(formatNumber(warning ? this.settings.getWarningThreshold() : this.settings.getCriticalThreshold()));
        input.setResponder(text -> this.onThresholdEdited(warning, text));
        this.addRenderableWidget(input);

        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> this.adjustThreshold(warning, 1.0f))
                .bounds(plusX, y, 20, 16).build());

        if (warning) {
            this.warningInput = input;
        } else {
            this.criticalInput = input;
        }
        return y + 22;
    }

    private void adjustThreshold(boolean warning, float delta) {
        if (warning) {
            this.settings.setWarningThreshold(this.settings.getWarningThreshold() + delta);
        } else {
            this.settings.setCriticalThreshold(this.settings.getCriticalThreshold() + delta);
        }
        this.syncThresholdInputs();
    }

    private void onThresholdEdited(boolean warning, String text) {
        if (this.syncingInputs || text.isEmpty()) {
            return;
        }
        try {
            final float value = Float.parseFloat(text);
            if (warning) {
                this.settings.setWarningThreshold(value);
            } else {
                this.settings.setCriticalThreshold(value);
            }
            this.syncThresholdInputs();
        } catch (NumberFormatException ignored) {
        }
    }

    private void syncThresholdInputs() {
        this.syncingInputs = true;
        if (this.warningInput != null) {
            this.warningInput.setValue(formatNumber(this.settings.getWarningThreshold()));
        }
        if (this.criticalInput != null) {
            this.criticalInput.setValue(formatNumber(this.settings.getCriticalThreshold()));
        }
        this.syncingInputs = false;
    }

    @Override
    protected void rebuildWidgets() {
        this.init();
    }

    private void applyAndClose() {
        PacketDistributor.sendToServer(new StatusTowerLightConfigPacket(this.towerLight.getBlockPos(), this.settings));
        this.towerLight.setSettings(this.settings);
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        // FE Dependent Mode panel in the free screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120,
                this.towerLight.feConsumer());

        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, PANEL_BG);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + 1, BORDER);
        g.fill(this.panelLeft, this.panelTop + this.panelHeight - 1, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + 1, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft + this.panelWidth - 1, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BORDER);

        g.drawCenteredString(this.font, Component.translatable("block.create_stats.status_tower_light"), this.width / 2, this.panelTop + 8, ACCENT);
        g.drawCenteredString(this.font, this.towerLight.getSignalState().getDisplayName(), this.width / 2, this.panelTop + 20, TEXT);

        final String sourceSummary = this.towerLight.getPreviewSourceSummary(this.settings.getSourceMode());
        final int sourceBoxTop = this.panelTop + 30;
        g.fill(this.panelLeft + 10, sourceBoxTop, this.panelLeft + this.panelWidth - 10, sourceBoxTop + 46, STATUS_BG);
        g.drawString(this.font, this.trimToWidth(this.getModeSummary(), this.panelWidth - 32), this.panelLeft + 16, sourceBoxTop + 7, TEXT, false);
        g.drawString(this.font, this.trimToWidth(this.getConnectionSummary(), this.panelWidth - 32), this.panelLeft + 16, sourceBoxTop + 18, DIM, false);
        g.drawString(this.font, this.trimToWidth(Component.translatable("gui.create_stats.status_tower.source_prefix", sourceSummary).getString(), this.panelWidth - 32), this.panelLeft + 16, sourceBoxTop + 29, DIM, false);

        if (this.warningRowY >= 0) {
            g.drawString(this.font, Component.translatable("gui.create_stats.status_tower.warning"), this.panelLeft + 16, this.warningRowY + 4, DIM, false);
        }
        if (this.criticalRowY >= 0) {
            g.drawString(this.font, Component.translatable("gui.create_stats.status_tower.critical"), this.panelLeft + 16, this.criticalRowY + 4, DIM, false);
        }

        final int statusTop = this.panelTop + this.panelHeight - 86;
        g.fill(this.panelLeft + 10, statusTop, this.panelLeft + this.panelWidth - 10, statusTop + 50, STATUS_BG);
        g.drawString(this.font, this.trimToWidth(Component.translatable("gui.create_stats.status_tower.live", this.towerLight.getMetricSummary()).getString(), this.panelWidth - 32),
                this.panelLeft + 16, statusTop + 7, DIM, false);
        g.drawString(this.font, this.trimToWidth(Component.translatable("gui.create_stats.status_tower.selected_rule", this.getPreviewRuleSummary()).getString(), this.panelWidth - 32),
                this.panelLeft + 16, statusTop + 19, DIM, false);
        g.drawString(this.font, this.trimToWidth(Component.translatable("gui.create_stats.status_tower.redstone_note").getString(), this.panelWidth - 32),
                this.panelLeft + 16, statusTop + 31, DIM, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private StatusTowerLightSettings.SourceKind getSourceKind() {
        return this.towerLight.getPreviewSourceKind(this.settings.getSourceMode());
    }

    private Component getMetricButtonLabel(StatusTowerLightSettings.SourceKind sourceKind) {
        if (sourceKind == StatusTowerLightSettings.SourceKind.NO_SOURCE) {
            return Component.translatable("gui.create_stats.status_tower.focus.waiting");
        }
        if (sourceKind == StatusTowerLightSettings.SourceKind.AMBIGUOUS) {
            return Component.translatable("gui.create_stats.status_tower.focus.multiple");
        }
        return Component.translatable("gui.create_stats.status_tower.focus.metric", sourceKind.getDisplayName(), this.settings.getMetric().getDisplayName());
    }

    private String getModeSummary() {
        return this.settings.getSourceMode() == StatusTowerLightSettings.SourceMode.LOCAL
                ? Component.translatable("gui.create_stats.status_tower.mode.local").getString()
                : Component.translatable("gui.create_stats.status_tower.mode.linked").getString();
    }

    private String getConnectionSummary() {
        return switch (this.towerLight.getSourceState()) {
            case BOUND -> Component.translatable("gui.create_stats.status_tower.conn.bound").getString();
            case AMBIGUOUS -> Component.translatable("gui.create_stats.status_tower.conn.multiple").getString();
            case MISSING -> this.settings.getSourceMode() == StatusTowerLightSettings.SourceMode.LOCAL
                    ? Component.translatable("gui.create_stats.status_tower.conn.no_nearby").getString()
                    : Component.translatable("gui.create_stats.status_tower.conn.no_linked").getString();
        };
    }

    private String getPreviewRuleSummary() {
        if (this.settings.usesBinaryLogic()) {
            return this.settings.isBinaryInvert()
                    ? Component.translatable("gui.create_stats.status_tower.binary.off").getString()
                    : Component.translatable("gui.create_stats.status_tower.binary.green").getString();
        }
        return Component.translatable("gui.create_stats.status_tower.threshold_summary",
                this.settings.getThresholdDirection().getDisplayName(),
                formatNumber(this.settings.getWarningThreshold()),
                formatNumber(this.settings.getCriticalThreshold())).getString();
    }

    private String getMetricOptionsPreview(StatusTowerLightSettings.SourceKind sourceKind) {
        final StatusTowerLightSettings.Metric[] options = this.settings.getMetricOptions(sourceKind);
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < options.length; i++) {
            if (i > 0) {
                builder.append(" / ");
            }
            builder.append(options[i].getDisplayName());
        }
        return builder.toString();
    }

    private String trimToWidth(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        final String ellipsis = "...";
        final int maxWidth = Math.max(0, width - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(text, maxWidth) + ellipsis;
    }

    private static String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format("%.1f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
