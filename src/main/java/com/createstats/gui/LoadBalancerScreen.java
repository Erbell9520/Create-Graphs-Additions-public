package com.createstats.gui;

import com.createstats.blockentity.LoadBalancerBE;
import com.createstats.blockentity.LoadBalancerSettings;
import com.createstats.network.LoadBalancerConfigPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class LoadBalancerScreen extends Screen {

    // palette (warm brass, Create-ish)
    private static final int PANEL_BG_DARK   = 0xF0161008;
    private static final int PANEL_BG_MID    = 0xF02A1E0C;
    private static final int STATUS_BG       = 0xCC0F0A05;
    private static final int NODE_BG         = 0xEE1C1306;
    private static final int BORDER_OUT      = 0xFF3A260E;
    private static final int BORDER_IN       = 0xFF5C3E17;
    private static final int BRASS_LIGHT     = 0xFFF0C858;
    private static final int BRASS_MID       = 0xFFC89838;
    private static final int BRASS_DARK      = 0xFF7A5A20;
    private static final int TEXT            = 0xFFF0DAA8;
    private static final int TEXT_DIM        = 0xFFA08760;
    private static final int TEXT_MUTED      = 0xFF6D5A3C;
    private static final int ACCENT          = 0xFFFFD45A;
    private static final int GREEN           = 0xFF7AE67A;
    private static final int YELLOW          = 0xFFF5D04A;
    private static final int ORANGE          = 0xFFF08A3A;
    private static final int RED             = 0xFFE05050;
    private static final int CYAN            = 0xFF6DD6F0;

    // layout constants
    private static final int TARGET_WIDTH    = 576;
    private static final int TARGET_HEIGHT   = 380;
    private static final int TITLE_H         = 18;
    private static final int FOOTER_H        = 30;
    private static final int LEFT_COL_W      = 278;
    private static final int GUTTER          = 8;
    private static final int PAD             = 8;

    private final LoadBalancerBE balancer;
    private final Screen returnScreen;
    private final LoadBalancerSettings settings;

    // Computed per init
    private int panelW;
    private int panelH;
    private int panelLeft;
    private int panelTop;
    private int leftColLeft;
    private int leftColTop;
    private int leftColW;
    private int leftColH;
    private int rightColLeft;
    private int rightColTop;
    private int rightColW;
    private int rightColH;

    // Scroll state (right column only)
    private int scrollOffset;
    private int rightContentHeight;

    private boolean limitsOpen = true;
    private boolean splitOpen  = true;
    private boolean controlOpen = true;

    private EditBox lowerBoundInput;
    private EditBox upperBoundInput;
    private EditBox minimumRpmInput;
    private EditBox maximumRpmInput;
    private PercentageBoundSlider lowerBoundSlider;
    private PercentageBoundSlider upperBoundSlider;
    private ShareSlider shareSlider;
    private boolean syncingBoundInputs;
    private boolean syncingRpmInputs;

    // Widgets that live in the scrollable right column, we move them on scroll.
    private final List<ScrollTrackedWidget> scrollTracked = new ArrayList<>();
    // Footer buttons rendered outside scissor.
    private final List<AbstractWidget> footerWidgets = new ArrayList<>();

    public LoadBalancerScreen(LoadBalancerBE balancer) {
        this(balancer, null);
    }

    public LoadBalancerScreen(LoadBalancerBE balancer, Screen returnScreen) {
        super(Component.literal("Load Balancer"));
        this.balancer = balancer;
        this.returnScreen = returnScreen;
        this.settings = LoadBalancerSettings.load(balancer.getSettings().save());
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.scrollTracked.clear();
        this.footerWidgets.clear();
        this.lowerBoundInput = null;
        this.upperBoundInput = null;
        this.minimumRpmInput = null;
        this.maximumRpmInput = null;
        this.lowerBoundSlider = null;
        this.upperBoundSlider = null;
        this.shareSlider = null;

        // Compute panel size (clamped to screen).
        this.panelW = Math.min(TARGET_WIDTH, this.width - 20);
        this.panelH = Math.min(TARGET_HEIGHT, this.height - 20);
        this.panelLeft = (this.width - panelW) / 2;
        this.panelTop  = (this.height - panelH) / 2;

        this.leftColLeft = panelLeft + PAD;
        this.leftColTop  = panelTop + TITLE_H + PAD;
        this.leftColW    = Math.min(LEFT_COL_W, panelW - 180);
        this.leftColH    = panelH - TITLE_H - FOOTER_H - PAD * 2;

        this.rightColLeft = leftColLeft + leftColW + GUTTER;
        this.rightColTop  = leftColTop;
        this.rightColW    = panelW - PAD * 2 - leftColW - GUTTER;
        this.rightColH    = leftColH;

        // Footer buttons (absolute, never scrolled, rendered manually outside scissor).
        int footerY = panelTop + panelH - FOOTER_H + 5;
        int footerCx = panelLeft + panelW / 2;
        Button applyBtn = Button.builder(Component.literal("✔ Apply"), b -> applyAndClose())
                .bounds(footerCx - 88, footerY, 84, 20).build();
        Button cancelBtn = Button.builder(Component.literal("✘ Cancel"), b -> onClose())
                .bounds(footerCx + 4, footerY, 84, 20).build();
        this.addWidget(applyBtn);
        this.addWidget(cancelBtn);
        footerWidgets.add(applyBtn);
        footerWidgets.add(cancelBtn);

        buildRightColumn();
    }

    private void buildRightColumn() {
        // Build right-column widgets anchored to their "content Y" then shifted by scrollOffset.
        int x = rightColLeft + 4;
        int w = rightColW - 8;
        int y = 0; // content-space Y (0 = top of right col)

        y = addSectionHeaderBtn(x, y, w, "⚡ Limits", limitsOpen, () -> { limitsOpen = !limitsOpen; rebuildWidgets(); });
        if (limitsOpen) {
            // Mode
            y = addTrackedButton(x, y, w, 16,
                    Component.literal("Mode: " + settings.getLimitMode().getDisplayName()),
                    b -> { settings.cycleLimitMode(); rebuildWidgets(); });
            y += 4;

            if (settings.getLimitMode() == LoadBalancerSettings.LimitMode.SU) {
                y = addNumericInputRow(x, y, w, true);
                y = addNumericInputRow(x, y, w, false);
            } else {
                lowerBoundSlider = addTrackedSlider(new PercentageBoundSlider(x, absY(y), w, 16, true), y);
                y += 20;
                upperBoundSlider = addTrackedSlider(new PercentageBoundSlider(x, absY(y), w, 16, false), y);
                y += 20;
            }
            y = addRpmInputRow(x, y, w, true);
            y = addRpmInputRow(x, y, w, false);
        }
        y += 6;

        y = addSectionHeaderBtn(x, y, w, "⇄ Split", splitOpen, () -> { splitOpen = !splitOpen; rebuildWidgets(); });
        if (splitOpen) {
            shareSlider = addTrackedSlider(new ShareSlider(x, absY(y), w, 16), y);
            y += 20;
            y = addSplitPresetRow(x, y, w);
            y = addTrackedButton(x, y, w, 16,
                    Component.literal("Sharing: " + settings.getSharingMode().getDisplayName()),
                    b -> { settings.cycleSharingMode(); rebuildWidgets(); });
            y += getWrappedHeight(getSharingHintText(), w);
            y += getWrappedHeight(getBranchStatusText(), w);
        }
        y += 6;

        y = addSectionHeaderBtn(x, y, w, "⚙ Control", controlOpen, () -> { controlOpen = !controlOpen; rebuildWidgets(); });
        if (controlOpen) {
            y = addTrackedButton(x, y, w, 16,
                    Component.literal(settings.isEnabled() ? "✔ Enabled" : "✘ Disabled"),
                    b -> { settings.setEnabled(!settings.isEnabled()); rebuildWidgets(); });
            y += getWrappedHeight(getControlHintText(), w);
            y += getWrappedHeight(getTopologyCounterText(), w);
            y += getWrappedHeight(getTopologyCapNoteText(), w);
        }
        y += 12;

        rightContentHeight = y;
        int maxScroll = Math.max(0, rightContentHeight - rightColH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    // Convert content-Y → absolute Y (accounting for scroll).
    private int absY(int contentY) {
        return rightColTop + 4 + contentY - scrollOffset;
    }

    private int addSectionHeaderBtn(int x, int y, int w, String label, boolean open, Runnable toggle) {
        String arrow = open ? "▼ " : "▶ ";
        addTrackedButton(x, y, w, 14, Component.literal(arrow + label), b -> toggle.run());
        return y + 16;
    }

    private int addTrackedButton(int x, int y, int w, int h, Component msg, Button.OnPress onPress) {
        Button b = Button.builder(msg, onPress).bounds(x, absY(y), w, h).build();
        addRenderableWidget(b);
        scrollTracked.add(new ScrollTrackedWidget(b, y));
        return y + h + 4;
    }

    private <T extends AbstractWidget> T addTrackedSlider(T slider, int contentY) {
        addRenderableWidget(slider);
        scrollTracked.add(new ScrollTrackedWidget(slider, contentY));
        return slider;
    }

    private int addNumericInputRow(int x, int y, int w, boolean lower) {
        int inputW = 74;
        int inputX = x + w - inputW;
        EditBox input = new EditBox(this.font, inputX, absY(y), inputW, 16,
                Component.literal(lower ? "Lower SU" : "Upper SU"));
        input.setMaxLength(5);
        input.setFilter(t -> t.isEmpty() || t.matches("[0-9]+"));
        input.setValue(String.format("%.0f", lower ? settings.getLowerBound() : settings.getUpperBound()));
        input.setResponder(t -> onBoundInputChanged(lower, t));
        addRenderableWidget(input);
        scrollTracked.add(new ScrollTrackedWidget(input, y));
        if (lower) lowerBoundInput = input; else upperBoundInput = input;
        return y + 20;
    }

    private int addRpmInputRow(int x, int y, int w, boolean minimum) {
        int btnW = 20, inputW = 48;
        int plusX = x + w - btnW;
        int inputX = plusX - inputW - 4;
        int minusX = inputX - btnW - 4;
        Button minus = Button.builder(Component.literal("-"), b -> adjustRpm(minimum, -1.0f))
                .bounds(minusX, absY(y), btnW, 16).build();
        addRenderableWidget(minus); scrollTracked.add(new ScrollTrackedWidget(minus, y));
        EditBox input = new EditBox(this.font, inputX, absY(y), inputW, 16,
                Component.literal(minimum ? "Min RPM" : "Max RPM"));
        input.setMaxLength(3);
        input.setFilter(t -> t.isEmpty() || t.matches("[0-9]+"));
        input.setValue(String.format("%.0f", minimum ? settings.getMinimumRPM() : settings.getMaximumRPM()));
        input.setResponder(t -> onRpmInputChanged(minimum, t));
        addRenderableWidget(input); scrollTracked.add(new ScrollTrackedWidget(input, y));
        Button plus = Button.builder(Component.literal("+"), b -> adjustRpm(minimum, 1.0f))
                .bounds(plusX, absY(y), btnW, 16).build();
        addRenderableWidget(plus); scrollTracked.add(new ScrollTrackedWidget(plus, y));
        if (minimum) minimumRpmInput = input; else maximumRpmInput = input;
        return y + 20;
    }

    private int addSplitPresetRow(int x, int y, int w) {
        int bw = (w - 8) / 3;
        Button b25 = Button.builder(Component.literal("25 / 75"), b -> setSharePreset(25.0f))
                .bounds(x, absY(y), bw, 16).build();
        Button b50 = Button.builder(Component.literal("50 / 50"), b -> setSharePreset(50.0f))
                .bounds(x + bw + 4, absY(y), bw, 16).build();
        Button b75 = Button.builder(Component.literal("75 / 25"), b -> setSharePreset(75.0f))
                .bounds(x + (bw + 4) * 2, absY(y), bw, 16).build();
        addRenderableWidget(b25); addRenderableWidget(b50); addRenderableWidget(b75);
        scrollTracked.add(new ScrollTrackedWidget(b25, y));
        scrollTracked.add(new ScrollTrackedWidget(b50, y));
        scrollTracked.add(new ScrollTrackedWidget(b75, y));
        return y + 20;
    }

    // render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        long now = System.currentTimeMillis();
        float anim = (now % 100000L) / 1000.0f; // seconds, wrapping

        drawFrame(graphics);
        drawTitleBar(graphics);
        drawLeftColumn(graphics, anim);

        // Scissor clip the right column so scrolled widgets don't bleed.
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int sx = (int) (rightColLeft * scale);
        int sy = (int) ((this.height - (rightColTop + rightColH)) * scale);
        int sw = (int) (rightColW * scale);
        int sh = (int) (rightColH * scale);
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(sx, sy, sw, sh);
        drawRightColumnBackground(graphics);
        drawRightColumnText(graphics);
        // Render scroll-tracked widgets inside scissor so they clip to the right column.
        for (ScrollTrackedWidget t : scrollTracked) {
            t.widget.render(graphics, mouseX, mouseY, partialTick);
        }
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();

        drawScrollbar(graphics);
        drawFooterSeparator(graphics);

        // Render footer widgets outside scissor.
        for (AbstractWidget fw : footerWidgets) {
            fw.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawFrame(GuiGraphics g) {
        // Outer frame: dark rounded look
        g.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, PANEL_BG_DARK);
        // Inner panel fill
        g.fill(panelLeft + 2, panelTop + 2, panelLeft + panelW - 2, panelTop + panelH - 2, PANEL_BG_MID);
        // Double border
        drawBorder(g, panelLeft, panelTop, panelW, panelH, BORDER_OUT);
        drawBorder(g, panelLeft + 1, panelTop + 1, panelW - 2, panelH - 2, BORDER_IN);
        // Brass highlights on corners
        g.fill(panelLeft + 2, panelTop + 2, panelLeft + 4, panelTop + 4, BRASS_LIGHT);
        g.fill(panelLeft + panelW - 4, panelTop + 2, panelLeft + panelW - 2, panelTop + 4, BRASS_LIGHT);
        g.fill(panelLeft + 2, panelTop + panelH - 4, panelLeft + 4, panelTop + panelH - 2, BRASS_LIGHT);
        g.fill(panelLeft + panelW - 4, panelTop + panelH - 4, panelLeft + panelW - 2, panelTop + panelH - 2, BRASS_LIGHT);
    }

    private void drawTitleBar(GuiGraphics g) {
        int y0 = panelTop + 2;
        int y1 = panelTop + TITLE_H - 2;
        // Brass gradient strip
        for (int y = y0; y < y1; y++) {
            float t = (float) (y - y0) / (y1 - y0);
            int c = lerpColor(BRASS_MID, BRASS_DARK, t);
            g.fill(panelLeft + 3, y, panelLeft + panelW - 3, y + 1, c);
        }
        // Top highlight line
        g.fill(panelLeft + 3, y0, panelLeft + panelW - 3, y0 + 1, BRASS_LIGHT);
        g.drawCenteredString(font, Component.literal("§l⇄ Load Balancer"),
                panelLeft + panelW / 2, y0 + 4, 0xFF241A05);
    }

    private void drawFooterSeparator(GuiGraphics g) {
        int y = panelTop + panelH - FOOTER_H + 1;
        g.fill(panelLeft + 4, y, panelLeft + panelW - 4, y + 1, BORDER_IN);
        g.fill(panelLeft + 4, y + 1, panelLeft + panelW - 4, y + 2, BRASS_DARK);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    // left column (status + animated pipeline)

    private void drawLeftColumn(GuiGraphics g, float anim) {
        // Outer status card
        g.fill(leftColLeft, leftColTop, leftColLeft + leftColW, leftColTop + leftColH, STATUS_BG);
        drawBorder(g, leftColLeft, leftColTop, leftColW, leftColH, BORDER_IN);
        g.fill(leftColLeft + 1, leftColTop + 1, leftColLeft + leftColW - 1, leftColTop + 2, BRASS_DARK);

        int innerX = leftColLeft + 6;
        int innerY = leftColTop + 6;
        int innerW = leftColW - 12;

        // Status banner
        int statusColor = getStatusColor();
        String statusText = getStatusText() + " · " + balancer.getTopologyStatus().getDisplayName();
        g.drawString(font, truncate(statusText, innerW), innerX, innerY, statusColor, false);
        g.drawString(font, truncate(getFlowSummaryText(), innerW), innerX, innerY + 10, TEXT_DIM, false);

        // Pipeline area
        int pipeTop = innerY + 26;
        int pipeBot = leftColTop + leftColH - 8;
        drawPipeline(g, innerX, pipeTop, innerW, pipeBot - pipeTop, anim);
    }

    private void drawPipeline(GuiGraphics g, int x, int y, int w, int h, float anim) {
        // Node geometry: SOURCE at top center, SPLIT in middle, LEFT/RIGHT on bottom row.
        int cx = x + w / 2;
        int nodeW = Math.min(90, w - 10);
        int srcX = cx - nodeW / 2;
        int srcY = y;
        int srcH = 30;

        int splitY = srcY + srcH + 22;
        int splitH = 30;

        int branchW = Math.min((w - 12) / 2, 92);
        int branchH = 32;
        int leftX = x + 2;
        int rightX = x + w - 2 - branchW;
        int branchY = splitY + splitH + 26;

        int statusColor = getStatusColor();
        boolean leftAtt = balancer.isLeftBranchAttached();
        boolean rightAtt = balancer.isRightBranchAttached();
        int leftColor = getBranchLoadColor(leftAtt, balancer.getLastLeftStressSU(), balancer.getLastLeftBudgetSU());
        int rightColor = getBranchLoadColor(rightAtt, balancer.getLastRightStressSU(), balancer.getLastRightBudgetSU());
        boolean balancing = balancer.isBalancing() && balancer.getSettings().isEnabled();

        // Connector: SOURCE → SPLIT (vertical)
        drawConnectorV(g, cx, srcY + srcH, splitY, statusColor, balancing, anim, 1.3f);
        // SPLIT → LEFT branch
        drawConnectorL(g, cx, splitY + splitH / 2, leftX + branchW, branchY + branchH / 2,
                leftColor, leftAtt, anim, 1.0f, true);
        // SPLIT → RIGHT branch
        drawConnectorL(g, cx, splitY + splitH / 2, rightX, branchY + branchH / 2,
                rightColor, rightAtt, anim, 1.0f, false);

        // Nodes with pulsing glow
        drawNode(g, srcX, srcY, nodeW, srcH, ACCENT, "SOURCE",
                String.format("%.0f RPM", balancer.getSourceSpeed()),
                String.format("Base %.0f SU", balancer.getLastBaseStressSU()),
                anim, true);

        int splitX = cx - nodeW / 2;
        drawNode(g, splitX, splitY, nodeW, splitH, statusColor, "SPLIT",
                String.format("%.0f / %.0f", settings.getLeftSharePct(), settings.getRightSharePct()),
                getSharingRuntimeText(), anim, balancing);

        drawNode(g, leftX, branchY, branchW, branchH, leftColor, "LEFT",
                String.format("%.0f RPM  x%.2f", balancer.getTransmittedSpeedLeft(), balancer.getCurrentModifierLeft()),
                leftAtt ? String.format("%.0f/%.0f SU", balancer.getLastLeftStressSU(), balancer.getLastLeftBudgetSU()) : "No branch",
                anim, leftAtt && balancing);

        drawNode(g, rightX, branchY, branchW, branchH, rightColor, "RIGHT",
                String.format("%.0f RPM  x%.2f", balancer.getTransmittedSpeedRight(), balancer.getCurrentModifierRight()),
                rightAtt ? String.format("%.0f/%.0f SU", balancer.getLastRightStressSU(), balancer.getLastRightBudgetSU()) : "No branch",
                anim, rightAtt && balancing);
    }

    private void drawNode(GuiGraphics g, int x, int y, int w, int h, int accent,
                          String title, String line1, String line2, float anim, boolean active) {
        // Pulsing glow ring behind node
        float pulse = active ? (0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(anim * 2.2))) : 0.18f;
        int glowA = (int) (pulse * 110);
        int glowColor = (accent & 0x00FFFFFF) | (glowA << 24);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, glowColor);

        // Body
        g.fill(x, y, x + w, y + h, NODE_BG);
        // Brass inlay
        g.fill(x, y, x + w, y + 1, BRASS_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, BRASS_DARK);
        drawBorder(g, x, y, w, h, accent);

        // Title with tiny rotating gear on left side
        int gearCx = x + 8;
        int gearCy = y + 4;
        if (active) drawRotatingGear(g, gearCx, gearCy, 4, anim * 60f, accent);

        g.drawCenteredString(font, truncate(title, w - 8), x + w / 2, y + 2, accent);
        g.drawCenteredString(font, truncate(line1, w - 6), x + w / 2, y + 12, TEXT);
        g.drawCenteredString(font, truncate(line2, w - 6), x + w / 2, y + 22, TEXT_DIM);
    }

    private void drawRotatingGear(GuiGraphics g, int cx, int cy, int radius, float degrees, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        // Simple 4-arm cross gear
        g.fill(-radius, -1, radius + 1, 2, color);
        g.fill(-1, -radius, 2, radius + 1, color);
        int d = radius - 1;
        g.fill(-d, -d, -d + 2, -d + 2, color);
        g.fill(d - 1, -d, d + 1, -d + 2, color);
        g.fill(-d, d - 1, -d + 2, d + 1, color);
        g.fill(d - 1, d - 1, d + 1, d + 1, color);
        pose.popPose();
    }

    private void drawConnectorV(GuiGraphics g, int x, int y0, int y1, int color, boolean flowing, float anim, float speedMul) {
        if (y1 <= y0) return;
        // Pulsing edge
        int pulseA = flowing ? (int) (150 + 90 * Math.sin(anim * 3.5f)) : 100;
        pulseA = Mth.clamp(pulseA, 60, 255);
        int edgeColor = (color & 0x00FFFFFF) | (pulseA << 24);
        g.fill(x - 1, y0, x + 2, y1, edgeColor);
        g.fill(x, y0, x + 1, y1, 0xFF111111);
        // Flowing dots
        if (flowing) {
            int len = y1 - y0;
            for (int i = 0; i < 3; i++) {
                float t = ((anim * speedMul) + i / 3.0f) % 1.0f;
                int dy = y0 + (int) (t * len);
                g.fill(x - 2, dy - 1, x + 3, dy + 2, color);
            }
        }
    }

    private void drawConnectorL(GuiGraphics g, int startX, int startY, int endX, int endY,
                                int color, boolean flowing, float anim, float speedMul, boolean leftward) {
        // Path: go horizontally from startX to endX at startY, then vertical to endY.
        int pulseA = flowing ? (int) (150 + 90 * Math.sin(anim * 3.2f)) : 90;
        pulseA = Mth.clamp(pulseA, 60, 255);
        int edgeColor = (color & 0x00FFFFFF) | (pulseA << 24);

        int hx0 = Math.min(startX, endX);
        int hx1 = Math.max(startX, endX);
        g.fill(hx0, startY - 1, hx1, startY + 2, edgeColor);
        g.fill(hx0, startY, hx1, startY + 1, 0xFF111111);

        int vx = endX;
        int vy0 = Math.min(startY, endY);
        int vy1 = Math.max(startY, endY);
        g.fill(vx - 1, vy0, vx + 2, vy1, edgeColor);
        g.fill(vx, vy0, vx + 1, vy1, 0xFF111111);

        if (flowing) {
            int totalLen = (hx1 - hx0) + (vy1 - vy0);
            if (totalLen > 0) {
                for (int i = 0; i < 3; i++) {
                    float t = ((anim * speedMul) + i / 3.0f) % 1.0f;
                    int pos = (int) (t * totalLen);
                    int dotX, dotY;
                    int hLen = hx1 - hx0;
                    if (pos < hLen) {
                        dotX = leftward ? (hx1 - pos) : (hx0 + pos);
                        dotY = startY;
                    } else {
                        dotX = endX;
                        int rem = pos - hLen;
                        dotY = startY < endY ? (startY + rem) : (startY - rem);
                    }
                    g.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, color);
                }
            }
        }
    }

    // right column text (inline labels + hints)

    private void drawRightColumnBackground(GuiGraphics g) {
        g.fill(rightColLeft, rightColTop, rightColLeft + rightColW, rightColTop + rightColH, STATUS_BG);
        drawBorder(g, rightColLeft, rightColTop, rightColW, rightColH, BORDER_IN);
    }

    private void drawRightColumnText(GuiGraphics g) {
        int x = rightColLeft + 4;
        int w = rightColW - 8;
        int y = 0;

        y += 16; // limits header
        if (limitsOpen) {
            y += 20; // mode button
            y += 4;
            if (settings.getLimitMode() == LoadBalancerSettings.LimitMode.SU) {
                drawInlineLabel(g, x + 4, absY(y) + 4, "Lower SU");
                y += 20;
                drawInlineLabel(g, x + 4, absY(y) + 4, "Upper SU");
                y += 20;
            } else {
                drawInlineLabel(g, x + 4, absY(y) + 4, "Lower %");
                y += 20;
                drawInlineLabel(g, x + 4, absY(y) + 4, "Upper %");
                y += 20;
            }
            drawInlineLabel(g, x + 4, absY(y) + 4, "Min output RPM");
            y += 20;
            drawInlineLabel(g, x + 4, absY(y) + 4, "Max output cap");
            y += 20;
        }
        y += 6;

        y += 16; // split header
        if (splitOpen) {
            y += 20; // share slider
            y += 20; // presets
            y += 20; // sharing mode
            y = drawWrapped(g, getSharingHintText(), x + 2, absY(y), w - 4, TEXT_DIM, y);
            y = drawWrapped(g, getBranchStatusText(), x + 2, absY(y), w - 4, getBranchStatusColor(), y);
        }
        y += 6;

        y += 16; // control header
        if (controlOpen) {
            y += 20; // enabled button
            y = drawWrapped(g, getControlHintText(), x + 2, absY(y), w - 4, getControlHintColor(), y);
            y = drawWrapped(g, getTopologyCounterText(), x + 2, absY(y), w - 4, getTopologyCounterColor(), y);
            y = drawWrapped(g, getTopologyCapNoteText(), x + 2, absY(y), w - 4, TEXT_MUTED, y);
        }
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int yAbs, int maxW, int color, int yContent) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxW);
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, yAbs, color, false);
            yAbs += 10;
            yContent += 10;
        }
        return yContent + 4;
    }

    private int getWrappedHeight(String text, int maxW) {
        int n = Math.max(1, font.split(Component.literal(text), maxW).size());
        return n * 10 + 4;
    }

    private void drawInlineLabel(GuiGraphics g, int x, int y, String label) {
        g.drawString(font, label, x, y, TEXT, false);
    }

    private void drawScrollbar(GuiGraphics g) {
        if (rightContentHeight <= rightColH) return;
        int barX = rightColLeft + rightColW - 4;
        int trackY = rightColTop + 2;
        int trackH = rightColH - 4;
        g.fill(barX, trackY, barX + 2, trackY + trackH, 0x66000000);
        int barH = Math.max(14, trackH * rightColH / rightContentHeight);
        int maxScroll = rightContentHeight - rightColH;
        int barY = trackY + (int) (scrollOffset / (float) maxScroll * (trackH - barH));
        g.fill(barX, barY, barX + 2, barY + barH, BRASS_MID);
    }

    // input / callbacks

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= rightColLeft && mouseX < rightColLeft + rightColW
                && mouseY >= rightColTop && mouseY < rightColTop + rightColH) {
            int maxScroll = Math.max(0, rightContentHeight - rightColH);
            if (maxScroll > 0) {
                int newOffset = Mth.clamp(scrollOffset - (int) (scrollY * 14.0), 0, maxScroll);
                if (newOffset != scrollOffset) {
                    scrollOffset = newOffset;
                    updateTrackedPositions();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateTrackedPositions() {
        for (ScrollTrackedWidget t : scrollTracked) {
            t.widget.setY(absY(t.contentY));
        }
    }

    @Override
    public void onClose() {
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

    // helpers: settings binding

    private void onBoundInputChanged(boolean lower, String text) {
        if (syncingBoundInputs || text.isEmpty()) return;
        try {
            syncingBoundInputs = true;
            float v = Float.parseFloat(text);
            if (lower) settings.setLowerBound(v); else settings.setUpperBound(v);
            syncBoundInputs(lower);
        } catch (NumberFormatException ignored) {
        } finally {
            syncingBoundInputs = false;
        }
    }

    private void syncBoundInputs(boolean editingLower) {
        if (!editingLower && lowerBoundInput != null) lowerBoundInput.setValue(String.format("%.0f", settings.getLowerBound()));
        if (editingLower && upperBoundInput != null) upperBoundInput.setValue(String.format("%.0f", settings.getUpperBound()));
    }

    private void adjustRpm(boolean minimum, float delta) {
        if (minimum) settings.setMinimumRPM(settings.getMinimumRPM() + delta);
        else settings.setMaximumRPM(settings.getMaximumRPM() + delta);
        syncRpmInputs();
    }

    private void onRpmInputChanged(boolean minimum, String text) {
        if (syncingRpmInputs || text.isEmpty()) return;
        try {
            float v = Float.parseFloat(text);
            if (minimum) settings.setMinimumRPM(v); else settings.setMaximumRPM(v);
            syncRpmInputs();
        } catch (NumberFormatException ignored) {}
    }

    private void syncRpmInputs() {
        syncingRpmInputs = true;
        if (minimumRpmInput != null) minimumRpmInput.setValue(String.format("%.0f", settings.getMinimumRPM()));
        if (maximumRpmInput != null) maximumRpmInput.setValue(String.format("%.0f", settings.getMaximumRPM()));
        syncingRpmInputs = false;
    }

    private void setSharePreset(float leftSharePct) {
        settings.setLeftSharePct(leftSharePct);
        syncShareSlider();
    }

    private void syncPercentageSliders() {
        if (lowerBoundSlider != null) lowerBoundSlider.syncFromSettings();
        if (upperBoundSlider != null) upperBoundSlider.syncFromSettings();
    }

    private void syncShareSlider() {
        if (shareSlider != null) shareSlider.syncFromSettings();
    }

    private void applyAndClose() {
        if (!settings.save().equals(balancer.getSettings().save())) {
            PacketDistributor.sendToServer(new LoadBalancerConfigPacket(balancer.getBlockPos(), settings));
        }
        onClose();
    }

    // status text/colors

    private String getStatusText() {
        if (!balancer.getSettings().isEnabled()) return "DISABLED";
        return switch (balancer.getTopologyStatus()) {
            case NO_SOURCE -> "IDLE";
            case NO_BRANCHES -> "NO BRANCHES";
            case SIDE_INPUT -> "WRONG INPUT";
            case MERGED_BRANCHES -> "MERGED";
            case BRANCH_SOURCE -> "BRANCH SOURCE";
            case TRUNCATED -> "TOPOLOGY LIMIT";
            case OK -> balancer.isBalancing() ? "BALANCING" : "STANDBY";
        };
    }

    private int getStatusColor() {
        if (!balancer.getSettings().isEnabled()) return TEXT_DIM;
        return switch (balancer.getTopologyStatus()) {
            case NO_SOURCE -> TEXT_DIM;
            case NO_BRANCHES, TRUNCATED -> YELLOW;
            case SIDE_INPUT, MERGED_BRANCHES, BRANCH_SOURCE -> RED;
            case OK -> balancer.isBalancing() ? CYAN : GREEN;
        };
    }

    private String getFlowSummaryText() {
        String band = settings.getLimitMode() == LoadBalancerSettings.LimitMode.SU
                ? String.format("Band %.0f-%.0f SU", settings.getLowerBound(), settings.getUpperBound())
                : String.format("Band %.0f-%.0f%%", settings.getLowerBound(), settings.getUpperBound());
        return band + " · Total " + String.format("%.1f%%", balancer.getLastStressPercent())
                + " · Base " + String.format("%.0f SU", balancer.getLastBaseStressSU());
    }

    private int getBranchLoadColor(boolean attached, float stress, float budget) {
        if (!attached || budget <= 0.0f) return YELLOW;
        float r = stress / budget;
        if (r >= 1.0f) return RED;
        if (r >= 0.85f) return ORANGE;
        return GREEN;
    }

    private String getSharingHintText() {
        if (settings.getSharingMode() == LoadBalancerSettings.SharingMode.STRICT)
            return "Strict: each side stays inside its own budget.";
        return "Elastic: fills spare capacity and borrows unused headroom.";
    }

    private String getBranchStatusText() {
        String l = balancer.isLeftBranchAttached() ? "left branch attached" : "left branch missing";
        String r = balancer.isRightBranchAttached() ? "right branch attached" : "right branch missing";
        return l + " · " + r;
    }

    private int getBranchStatusColor() {
        return balancer.isLeftBranchAttached() && balancer.isRightBranchAttached() ? TEXT_DIM : YELLOW;
    }

    private String getControlHintText() {
        return switch (balancer.getTopologyStatus()) {
            case OK -> "Rear shaft is input; left/right shafts are controlled outputs.";
            case NO_SOURCE -> "Feed RPM into the rear shaft to wake it up.";
            case NO_BRANCHES -> "Attach a branch on the left or right output.";
            case SIDE_INPUT -> "Wrong power direction: the rear shaft must be the source.";
            case MERGED_BRANCHES -> "Keep the left and right output networks separated.";
            case BRANCH_SOURCE -> "Remove motors or controllers from the output branches.";
            case TRUNCATED -> "Branch scan hit the global topology cap; simplify the network.";
        };
    }

    private String getTopologyCounterText() {
        return String.format("Topology items: %d / %d",
                balancer.getLastTopologyNodeCount(), balancer.getTopologyNodeLimit());
    }

    private String getTopologyCapNoteText() {
        return "Topology cap is set in Panel Settings → Topology Map → Max Nodes.";
    }

    private int getTopologyCounterColor() {
        if (balancer.getTopologyStatus() == LoadBalancerBE.TopologyStatus.TRUNCATED) return RED;
        if (balancer.getLastTopologyNodeCount() >= balancer.getTopologyNodeLimit()) return RED;
        if (balancer.getLastTopologyNodeCount() >= Math.max(1, balancer.getTopologyNodeLimit() - 10)) return YELLOW;
        return TEXT_DIM;
    }

    private int getControlHintColor() {
        return switch (balancer.getTopologyStatus()) {
            case OK -> TEXT_DIM;
            case NO_SOURCE -> TEXT_DIM;
            case NO_BRANCHES, TRUNCATED -> YELLOW;
            case SIDE_INPUT, MERGED_BRANCHES, BRANCH_SOURCE -> RED;
        };
    }

    private String getSharingRuntimeText() {
        if (!settings.isEnabled()) return "Disabled";
        if (balancer.getTopologyStatus() != LoadBalancerBE.TopologyStatus.OK) {
            return switch (balancer.getTopologyStatus()) {
                case NO_SOURCE -> "Waiting for RPM";
                case NO_BRANCHES -> "Waiting for branch";
                case SIDE_INPUT -> "Wrong input";
                case MERGED_BRANCHES -> "Branches merged";
                case BRANCH_SOURCE -> "Branch source";
                case TRUNCATED -> "Topology limit";
                case OK -> "Standby";
            };
        }
        if (settings.getSharingMode() == LoadBalancerSettings.SharingMode.STRICT) return "Strict share";
        float totalHigh = getTotalHighSU();
        float totalUsed = balancer.getLastBaseStressSU() + balancer.getLastLeftStressSU() + balancer.getLastRightStressSU();
        float strictL = totalHigh * settings.getLeftSharePct() / 100.0f;
        float strictR = totalHigh - strictL;
        boolean bL = balancer.getLastLeftBudgetSU() > strictL + 0.5f && balancer.getLastLeftStressSU() > strictL + 0.5f;
        boolean bR = balancer.getLastRightBudgetSU() > strictR + 0.5f && balancer.getLastRightStressSU() > strictR + 0.5f;
        if (bL && !bR) return "Borrowing left";
        if (bR && !bL) return "Borrowing right";
        if (totalUsed < totalHigh - 0.5f) return "Filling spare";
        return "Elastic hold";
    }

    private float getTotalHighSU() {
        if (settings.getLimitMode() == LoadBalancerSettings.LimitMode.PERCENTAGE)
            return balancer.getLastCapacitySU() * settings.getUpperBound() / 100.0f;
        return settings.getUpperBound();
    }

    // utilities

    private String truncate(String s, int maxW) {
        if (s == null || s.isEmpty()) return "";
        if (font.width(s) <= maxW) return s;
        int w = Math.max(0, maxW - font.width("..."));
        return font.plainSubstrByWidth(s, w) + "...";
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        int aa = (a >> 24) & 0xFF;
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    // nested types

    private record ScrollTrackedWidget(AbstractWidget widget, int contentY) {}

    private class PercentageBoundSlider extends AbstractSliderButton {
        private final boolean lower;
        private PercentageBoundSlider(int x, int y, int width, int height, boolean lower) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.lower = lower;
            syncFromSettings();
        }
        @Override protected void updateMessage() {
            float bound = lower ? settings.getLowerBound() : settings.getUpperBound();
            setMessage(Component.literal((lower ? "Lower: " : "Upper: ") + String.format("%.0f%%", bound)));
        }
        @Override protected void applyValue() {
            float bound = 1.0f + Math.round((float) value * 99.0f);
            if (lower) settings.setLowerBound(bound); else settings.setUpperBound(bound);
            syncPercentageSliders();
        }
        private void syncFromSettings() {
            float bound = lower ? settings.getLowerBound() : settings.getUpperBound();
            value = Mth.clamp((bound - 1.0f) / 99.0f, 0.0f, 1.0f);
            updateMessage();
        }
    }

    private class ShareSlider extends AbstractSliderButton {
        private ShareSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.0);
            syncFromSettings();
        }
        @Override protected void updateMessage() {
            setMessage(Component.literal(String.format("Left %.0f%% / Right %.0f%%",
                    settings.getLeftSharePct(), settings.getRightSharePct())));
        }
        @Override protected void applyValue() {
            float leftShare = Math.round((float) value * 100.0f);
            settings.setLeftSharePct(leftShare);
            syncShareSlider();
        }
        private void syncFromSettings() {
            value = Mth.clamp(settings.getLeftSharePct() / 100.0f, 0.0f, 1.0f);
            updateMessage();
        }
    }
}
