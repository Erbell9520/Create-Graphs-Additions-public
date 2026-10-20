package com.createstats.gui;

import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.blockentity.AutoTransmissionClutchSettings;
import com.createstats.network.AutoTransmissionClutchConfigPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class AutoTransmissionClutchScreen extends Screen {
    private static final int PANEL_BG = 0xF0181818;
    private static final int ACCENT = 0xFF5599DD;
    private static final int TEXT = 0xFFDDCCAA;
    private static final int DIM = 0xFF888079;
    private static final int BORDER = 0xFF444444;
    private static final int GREEN = 0xFF44BB44;
    private static final int RED = 0xFFDD4444;
    private static final int YELLOW = 0xFFDDBB33;
    private static final int ORANGE = 0xFFCC7700;
    private static final int STATUS_BG = 0xAA11161C;
    private static final int CONTENT_START_Y = 48;

    private final AutoTransmissionClutchBE transmission;
    private final Screen returnScreen;
    private final AutoTransmissionClutchSettings settings;

    private int scrollOffset;
    private int contentHeight;
    private int visibleHeight;

    private boolean limitsOpen;
    private boolean controlOpen;
    private EditBox lowerBoundInput;
    private EditBox upperBoundInput;
    private EditBox minimumRpmInput;
    private EditBox maximumRpmInput;
    private PercentageBoundSlider lowerBoundSlider;
    private PercentageBoundSlider upperBoundSlider;
    private boolean syncingBoundInputs;
    private boolean syncingRpmInputs;

    public AutoTransmissionClutchScreen(AutoTransmissionClutchBE transmission) {
        this(transmission, null);
    }

    public AutoTransmissionClutchScreen(AutoTransmissionClutchBE transmission, Screen returnScreen) {
        super(Component.literal("Auto Stress Governor"));
        this.transmission = transmission;
        this.returnScreen = returnScreen;
        this.settings = AutoTransmissionClutchSettings.load(transmission.getSettings().save());
        this.scrollOffset = 0;
        this.limitsOpen = true;
        this.controlOpen = true;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.lowerBoundInput = null;
        this.upperBoundInput = null;
        this.minimumRpmInput = null;
        this.maximumRpmInput = null;
        this.lowerBoundSlider = null;
        this.upperBoundSlider = null;
        this.visibleHeight = (int) (this.height * 0.88);

        final int cx = this.width / 2;
        final int panelW = 260;
        final int left = cx - panelW / 2;
        int y = CONTENT_START_Y;

        addSectionHeader("⚡ Limits", left, y, panelW, this.limitsOpen, () -> {
            this.limitsOpen = !this.limitsOpen;
            this.rebuildWidgets();
        });
        y += 16;
        if (this.limitsOpen) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("Mode: " + this.settings.getLimitMode().getDisplayName()),
                    b -> { this.settings.cycleLimitMode(); this.rebuildWidgets(); }
            ).bounds(left + 4, screenY(y), panelW - 8, 16).build());
            y += 20;

            if (this.settings.getLimitMode() == AutoTransmissionClutchSettings.LimitMode.SU) {
                y = addNumericInputRow(left, y, panelW, true);
                y = addNumericInputRow(left, y, panelW, false);
            } else {
                this.lowerBoundSlider = this.addRenderableWidget(new PercentageBoundSlider(left + 4, screenY(y), panelW - 8, 16, true));
                y += 20;
                this.upperBoundSlider = this.addRenderableWidget(new PercentageBoundSlider(left + 4, screenY(y), panelW - 8, 16, false));
                y += 20;
            }

            y = addRpmInputRow(left, y, panelW, true);
            y = addRpmInputRow(left, y, panelW, false);
        }
        y += 4;

        addSectionHeader("⚙ Control", left, y, panelW, this.controlOpen, () -> {
            this.controlOpen = !this.controlOpen;
            this.rebuildWidgets();
        });
        y += 16;
        if (this.controlOpen) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(this.settings.isEnabled() ? "✔ Enabled" : "✘ Disabled"),
                    b -> { this.settings.setEnabled(!this.settings.isEnabled()); this.rebuildWidgets(); }
            ).bounds(left + 4, screenY(y), panelW - 8, 16).build());
            y += 20;
        }
        y += 12;

        final int btnW = 80;
        this.addRenderableWidget(Button.builder(Component.literal("✔ Apply"), b -> applyAndClose())
                .bounds(cx - btnW - 4, screenY(y), btnW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("✘ Cancel"), b -> onClose())
                .bounds(cx + 4, screenY(y), btnW, 20).build());
        y += 30;

        this.contentHeight = y;
        final int maxS = Math.max(0, this.contentHeight - this.visibleHeight + 20);
        if (this.scrollOffset > maxS) this.scrollOffset = maxS;
    }

    private void addSectionHeader(String label, int x, int y, int w, boolean open, Runnable toggle) {
        final String arrow = open ? "▼ " : "▶ ";
        this.addRenderableWidget(Button.builder(Component.literal(arrow + label), b -> toggle.run())
                .bounds(x, screenY(y), w, 14).build());
    }

    private int addNumericInputRow(int left, int y, int panelW, boolean lower) {
        final int inputW = 72;
        final int inputX = left + panelW - inputW - 8;
        final EditBox input = new EditBox(this.font, inputX, screenY(y), inputW, 16, Component.literal(lower ? "Lower SU" : "Upper SU"));
        input.setMaxLength(5);
        input.setFilter(text -> text.isEmpty() || text.matches("[0-9]+"));
        input.setValue(String.format("%.0f", lower ? this.settings.getLowerBound() : this.settings.getUpperBound()));
        input.setResponder(text -> this.onBoundInputChanged(lower, text));
        this.addRenderableWidget(input);
        if (lower) {
            this.lowerBoundInput = input;
        } else {
            this.upperBoundInput = input;
        }
        return y + 20;
    }

    private int addRpmInputRow(int left, int y, int panelW, boolean minimum) {
        final int btnW = 20;
        final int inputW = 48;
        final int plusX = left + panelW - btnW - 8;
        final int inputX = plusX - inputW - 4;
        final int minusX = inputX - btnW - 4;

        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustRpm(minimum, -1.0f))
                .bounds(minusX, screenY(y), btnW, 16).build());

        final EditBox input = new EditBox(this.font, inputX, screenY(y), inputW, 16, Component.literal(minimum ? "Min RPM" : "Max RPM"));
        input.setMaxLength(3);
        input.setFilter(text -> text.isEmpty() || text.matches("[0-9]+"));
        input.setValue(String.format("%.0f", minimum ? this.settings.getMinimumRPM() : this.settings.getMaximumRPM()));
        input.setResponder(text -> this.onRpmInputChanged(minimum, text));
        this.addRenderableWidget(input);

        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustRpm(minimum, 1.0f))
                .bounds(plusX, screenY(y), btnW, 16).build());

        if (minimum) {
            this.minimumRpmInput = input;
        } else {
            this.maximumRpmInput = input;
        }

        return y + 20;
    }

    private void onBoundInputChanged(boolean lower, String text) {
        if (this.syncingBoundInputs || text.isEmpty()) {
            return;
        }
        try {
            this.syncingBoundInputs = true;
            float value = Float.parseFloat(text);
            if (lower) {
                this.settings.setLowerBound(value);
            } else {
                this.settings.setUpperBound(value);
            }
            // only sync the other field
            this.syncBoundInputs(lower);
        } catch (NumberFormatException ignored) {
        } finally {
            this.syncingBoundInputs = false;
        }
    }

    private void syncBoundInputs(boolean editingLower) {
        if (this.lowerBoundInput != null) {
            this.lowerBoundInput.setValue(String.format("%.0f", this.settings.getLowerBound()));
        }
        if (this.upperBoundInput != null) {
            this.upperBoundInput.setValue(String.format("%.0f", this.settings.getUpperBound()));
        }
    }

    private void adjustRpm(boolean minimum, float delta) {
        if (minimum) {
            this.settings.setMinimumRPM(this.settings.getMinimumRPM() + delta);
        } else {
            this.settings.setMaximumRPM(this.settings.getMaximumRPM() + delta);
        }
        syncRpmInputs();
    }

    private void onRpmInputChanged(boolean minimum, String text) {
        if (this.syncingRpmInputs || text.isEmpty()) {
            return;
        }
        try {
            float value = Float.parseFloat(text);
            if (minimum) {
                this.settings.setMinimumRPM(value);
            } else {
                this.settings.setMaximumRPM(value);
            }
            syncRpmInputs();
        } catch (NumberFormatException ignored) {
        }
    }

    private void syncRpmInputs() {
        this.syncingRpmInputs = true;
        if (this.minimumRpmInput != null) {
            this.minimumRpmInput.setValue(String.format("%.0f", this.settings.getMinimumRPM()));
        }
        if (this.maximumRpmInput != null) {
            this.maximumRpmInput.setValue(String.format("%.0f", this.settings.getMaximumRPM()));
        }
        this.syncingRpmInputs = false;
    }

    private void applyAndClose() {
        PacketDistributor.sendToServer(new AutoTransmissionClutchConfigPacket(this.transmission.getBlockPos(), this.settings));
        this.transmission.setSettings(this.settings);
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

        g.drawCenteredString(this.font, "§b§l⚡ Auto Stress Governor", cx, top + 5, ACCENT);
        drawStatusCard(g, left, top, panelW);

        int y = CONTENT_START_Y + 16;
        if (this.limitsOpen) {
            y += 20;
            if (this.settings.getLimitMode() == AutoTransmissionClutchSettings.LimitMode.SU) {
                drawInlineLabel(g, left + 8, screenY(y) + 4, "Lower bound");
                y += 20;
                drawInlineLabel(g, left + 8, screenY(y) + 4, "Upper bound");
                y += 20;
            } else {
                y += 40;
            }
            drawInlineLabel(g, left + 8, screenY(y) + 4, "Min output RPM");
            y += 20;
            drawInlineLabel(g, left + 8, screenY(y) + 4, "Max output cap");
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

    private void drawStatusCard(GuiGraphics g, int left, int top, int panelW) {
        final int cardLeft = left + 4;
        final int cardTop = top + 18;
        final int cardRight = left + panelW - 4;
        final int cardBottom = cardTop + 44;

        g.fill(cardLeft, cardTop, cardRight, cardBottom, STATUS_BG);
        g.fill(cardLeft, cardTop, cardRight, cardTop + 1, BORDER);
        g.fill(cardLeft, cardBottom - 1, cardRight, cardBottom, BORDER);
        g.fill(cardLeft, cardTop, cardLeft + 1, cardBottom, BORDER);
        g.fill(cardRight - 1, cardTop, cardRight, cardBottom, BORDER);

        final String loadSummary = this.transmission.hasStressNetwork()
                ? String.format("%.1f%% load", this.transmission.getLastStressPercent())
                : "no stress data";
        final String rpmSummary = String.format("Input %.0f RPM -> Output %.0f RPM (x%.3f)",
                this.transmission.getSourceSpeed(),
                this.transmission.getTransmittedSpeed(),
                this.transmission.getCurrentModifier());
        final String bandSummary = this.settings.getLimitMode() == AutoTransmissionClutchSettings.LimitMode.SU
                ? String.format("Band %.0f-%.0f SU | Floor %.0f | Cap %.0f",
                        this.settings.getLowerBound(), this.settings.getUpperBound(),
                        this.settings.getMinimumRPM(), this.settings.getMaximumRPM())
                : String.format("Band %.0f-%.0f%% | Floor %.0f | Cap %.0f",
                        this.settings.getLowerBound(), this.settings.getUpperBound(),
                        this.settings.getMinimumRPM(), this.settings.getMaximumRPM());

        g.drawString(this.font, getStatusText() + " | " + loadSummary, cardLeft + 6, cardTop + 4, getStatusColor());
        g.drawString(this.font, rpmSummary, cardLeft + 6, cardTop + 14, TEXT);
        g.drawString(this.font, bandSummary, cardLeft + 6, cardTop + 24, DIM);
        g.drawString(this.font, "Below = faster • above = slower", cardLeft + 6, cardTop + 34, DIM);
    }

    private String getStatusText() {
        if (!this.settings.isEnabled()) {
            return "DISABLED";
        }
        switch (this.transmission.getAlarmState()) {
            case CONCERNING:
                return "MIN RPM";
            case VERY_CONCERNING:
                return "EMERGENCY";
            default:
                if (!this.transmission.isNetworkRunning()) {
                    return "IDLE";
                }
                if (this.transmission.isBoosting()) {
                    return "BOOSTING";
                }
                if (this.transmission.isThrottling()) {
                    return "THROTTLING";
                }
                return "PASS-THROUGH";
        }
    }

    private int getStatusColor() {
        if (!this.settings.isEnabled()) {
            return DIM;
        }
        switch (this.transmission.getAlarmState()) {
            case CONCERNING:
                return YELLOW;
            case VERY_CONCERNING:
                return RED;
            default:
                if (!this.transmission.isNetworkRunning()) {
                    return DIM;
                }
                if (this.transmission.isBoosting()) {
                    return ACCENT;
                }
                if (this.transmission.isThrottling()) {
                    return ORANGE;
                }
                return GREEN;
        }
    }

    private void drawInlineLabel(GuiGraphics g, int x, int y, String label) {
        g.drawString(this.font, label, x, y, TEXT);
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

    private class PercentageBoundSlider extends AbstractSliderButton {
        private final boolean lower;

        private PercentageBoundSlider(int x, int y, int width, int height, boolean lower) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.lower = lower;
            this.syncFromSettings();
        }

        @Override
        protected void updateMessage() {
            float bound = this.lower ? settings.getLowerBound() : settings.getUpperBound();
            this.setMessage(Component.literal((this.lower ? "Lower: " : "Upper: ") + String.format("%.0f%%", bound)));
        }

        @Override
        protected void applyValue() {
            float bound = 1.0f + Math.round((float) this.value * 99.0f);
            if (this.lower) {
                settings.setLowerBound(bound);
            } else {
                settings.setUpperBound(bound);
            }
            syncPercentageSliders();
        }

        private void syncFromSettings() {
            float bound = this.lower ? settings.getLowerBound() : settings.getUpperBound();
            this.value = Mth.clamp((bound - 1.0f) / 99.0f, 0.0f, 1.0f);
            this.updateMessage();
        }
    }

    private void syncPercentageSliders() {
        if (this.lowerBoundSlider != null) {
            this.lowerBoundSlider.syncFromSettings();
        }
        if (this.upperBoundSlider != null) {
            this.upperBoundSlider.syncFromSettings();
        }
    }
}
