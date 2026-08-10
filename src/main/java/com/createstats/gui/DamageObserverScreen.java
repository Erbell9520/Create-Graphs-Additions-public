package com.createstats.gui;

import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.DamageObserverSettings;
import com.createstats.network.DamageObserverActionPacket;
import com.createstats.network.DamageObserverConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class DamageObserverScreen extends Screen {

    private static final int BACKDROP_TOP = 0xFF071119;
    private static final int BACKDROP_BOTTOM = 0xFF17110D;
    private static final int SHELL = 0xFF101821;
    private static final int PANEL = 0xFF0C151D;
    private static final int PANEL_DEEP = 0xFF081018;
    private static final int STROKE = 0xFF315064;
    private static final int STROKE_DIM = 0xFF223241;
    private static final int TEXT = 0xFFE7F3F5;
    private static final int TEXT_DIM = 0xFF8FA9B4;
    private static final int TEXT_DARK = 0xFF566D78;
    private static final int CYAN = 0xFF4AD9FF;
    private static final int GREEN = 0xFF62E8A3;
    private static final int AMBER = 0xFFFFC15E;
    private static final int RED = 0xFFFF5D69;
    private static final int VIOLET = 0xFFA985FF;
    private static final int SHADOW = 0x8A000000;

    private final DamageObserverBE observer;
    private final DamageObserverSettings settings;

    private EditBox nameBox;
    private Button modeButton;
    private Button categoryButton;
    private Button radiusDownButton;
    private Button radiusUpButton;
    private Button governorMappingButton;
    private Button governorYawInvertButton;
    private Button governorPitchInvertButton;
    private Button recalibrateButton;
    private Button savePresetButton;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public DamageObserverScreen(DamageObserverBE observer) {
        super(Component.literal("Battle Observer"));
        this.observer = observer;
        this.settings = observer.getSettings().copy();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        this.panelWidth = Math.min(686, Math.max(560, this.width - 30));
        this.panelHeight = Math.min(414, Math.max(352, this.height - 24));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        int inset = 20;
        int bodyTop = this.panelTop + 86;
        int footerTop = this.panelTop + this.panelHeight - 48;
        int leftWidth = Math.min(270, Math.max(236, this.panelWidth / 2 - 44));
        int leftX = this.panelLeft + inset;
        int rightX = leftX + leftWidth + 14;
        int rightWidth = this.panelLeft + this.panelWidth - inset - rightX;
        int rowY = bodyTop + 45;

        this.nameBox = new EditBox(this.font, leftX + 14, rowY, leftWidth - 28, 22, Component.literal("Observer Name"));
        this.nameBox.setValue(this.settings.getObserverName());
        this.nameBox.setMaxLength(48);
        this.nameBox.setResponder(this.settings::setObserverName);
        this.addRenderableWidget(this.nameBox);
        rowY += 46;

        this.modeButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.settings.cycleWatchMode();
            this.refreshControls();
        }).bounds(leftX + 14, rowY, leftWidth - 28, 22).build());
        rowY += 32;

        this.categoryButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.settings.cycleCategory();
            this.refreshControls();
        }).bounds(leftX + 14, rowY, leftWidth - 28, 22).build());
        rowY += 34;

        int radiusWidth = (leftWidth - 36) / 2;
        this.radiusDownButton = this.addRenderableWidget(Button.builder(Component.literal("Radius -"), button -> {
            this.settings.setAreaRadius(this.settings.getAreaRadius() - 1);
            this.refreshControls();
        }).bounds(leftX + 14, rowY, radiusWidth, 22).build());
        this.radiusUpButton = this.addRenderableWidget(Button.builder(Component.literal("Radius +"), button -> {
            this.settings.setAreaRadius(this.settings.getAreaRadius() + 1);
            this.refreshControls();
        }).bounds(leftX + 22 + radiusWidth, rowY, radiusWidth, 22).build());

        int bodyHeight = footerTop - bodyTop - 10;
        int memoryTop = bodyTop + bodyHeight - 70;
        int actionWidth = (leftWidth - 36) / 2;
        this.recalibrateButton = this.addRenderableWidget(Button.builder(Component.literal("Set Current As Healthy"), button -> this.sendObserverAction(DamageObserverActionPacket.Action.RECALIBRATE_BASELINE))
            .bounds(leftX + 14, memoryTop + 44, actionWidth, 20)
            .build());
        this.savePresetButton = this.addRenderableWidget(Button.builder(Component.literal("Save Reference"), button -> this.sendObserverAction(DamageObserverActionPacket.Action.SAVE_STRUCTURE_PRESET))
            .bounds(leftX + 22 + actionWidth, memoryTop + 44, actionWidth, 20)
            .build());

        int governorControlsWidth = Math.min(236, rightWidth - 24);
        int governorControlsY = bodyTop + bodyHeight - 24;
        int governorToggleWidth = 60;
        int governorMappingWidth = governorControlsWidth - governorToggleWidth * 2 - 8;
        this.governorMappingButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.settings.cycleWeaponGovernorMapping();
            this.refreshControls();
        }).bounds(rightX + 12, governorControlsY, governorMappingWidth, 20).build());
        this.governorYawInvertButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.settings.toggleWeaponYawInverted();
            this.refreshControls();
        }).bounds(rightX + 16 + governorMappingWidth, governorControlsY, governorToggleWidth, 20).build());
        this.governorPitchInvertButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.settings.toggleWeaponPitchInverted();
            this.refreshControls();
        }).bounds(rightX + 20 + governorMappingWidth + governorToggleWidth, governorControlsY, governorToggleWidth, 20).build());

        int footerButtonWidth = 190;
        int buttonY = footerTop + 12;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.saveAndClose())
            .bounds(this.panelLeft + this.panelWidth - inset - footerButtonWidth, buttonY, (footerButtonWidth - 8) / 2, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
            .bounds(this.panelLeft + this.panelWidth - inset - (footerButtonWidth - 8) / 2, buttonY, (footerButtonWidth - 8) / 2, 20)
                .build());

        this.refreshControls();
        this.setInitialFocus(this.nameBox);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float time = this.animationTime(partialTick);
        this.renderBackdrop(g, time);
        // FE Dependent Mode panel in the free screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.observer.feConsumer());

        int inset = 20;
        int bodyTop = this.panelTop + 86;
        int footerTop = this.panelTop + this.panelHeight - 48;
        int leftWidth = Math.min(270, Math.max(236, this.panelWidth / 2 - 44));
        int leftX = this.panelLeft + inset;
        int bodyHeight = footerTop - bodyTop - 10;
        int rightX = leftX + leftWidth + 14;
        int rightWidth = this.panelLeft + this.panelWidth - inset - rightX;

        g.fill(this.panelLeft + 5, this.panelTop + 7, this.panelLeft + this.panelWidth + 5, this.panelTop + this.panelHeight + 7, SHADOW);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, SHELL);
        border(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, STROKE);
        g.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1, this.panelTop + 3, this.categoryColor());

        this.renderHeader(g, this.panelLeft + inset, this.panelTop + 10, this.panelWidth - inset * 2, time);
        this.renderIdentityPanel(g, leftX, bodyTop, leftWidth, bodyHeight, time);
        this.renderGeometryPanel(g, rightX, bodyTop, rightWidth, bodyHeight, time);
        this.renderFooter(g, this.panelLeft + inset, footerTop, this.panelWidth - inset * 2 - 202, 32, time);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private void renderHeader(GuiGraphics g, int x, int y, int width, float time) {
        int color = this.categoryColor();
        g.fill(x, y, x + width, y + 58, PANEL);
        border(g, x, y, width, 58, STROKE_DIM);
        this.drawScaledString(g, "BATTLE OBSERVER", x + 14, y + 9, TEXT, 2);
        g.drawString(this.font, "ROLE SENSOR / DISPLAY LINK SOURCE", x + 16, y + 36, color, false);
        g.drawString(this.font, "BLOCK " + this.observer.getBlockPos().toShortString(), x + 16, y + 48, TEXT_DIM, false);

        int chipX = x + width - 350;
        this.drawMetricChip(g, chipX, y + 10, 108, "WATCH", this.settings.getWatchMode().getDisplayName(), CYAN);
        this.drawMetricChip(g, chipX + 118, y + 10, 92, "ROLE", this.settings.getCategory().getDisplayName(), color);
        this.drawMetricChip(g, chipX + 220, y + 10, 120, "ANCHOR", this.previewAnchorPos().toShortString(), AMBER);

        int pulse = 5 + (int) ((Math.sin(time * 0.22f) + 1.0f) * 2.0f);
        this.ring(g, x + width - 18, y + 46, pulse + 4, pulse + 4, color);
        g.fill(x + width - 20, y + 44, x + width - 15, y + 49, color);
    }

    private void renderIdentityPanel(GuiGraphics g, int x, int y, int width, int height, float time) {
        int color = this.categoryColor();
        g.fill(x, y, x + width, y + height, PANEL);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, "REGION IDENTITY", x + 12, y + 12, AMBER, false);
        g.drawString(this.font, "NAME", x + 14, y + 33, TEXT_DIM, false);
        g.drawString(this.font, "WATCH MODE", x + 14, y + 80, TEXT_DIM, false);
        g.drawString(this.font, "ROLE", x + 14, y + 112, TEXT_DIM, false);
        g.drawString(this.font, "BOX RADIUS", x + 14, y + 146, TEXT_DIM, false);

        int radiusY = y + 174;
        g.fill(x + 14, radiusY, x + width - 14, radiusY + 24, 0xFF0A121A);
        border(g, x + 14, radiusY, width - 28, 24, this.usesAreaRadius() ? color : STROKE_DIM);
        String radius = this.usesAreaRadius()
                ? this.settings.getAreaRadius() + " radius / " + this.observedDiameter() + "w"
                : "single block / radius locked";
        g.drawString(this.font, radius, x + 22, radiusY + 8, this.usesAreaRadius() ? TEXT : TEXT_DARK, false);

        int memoryTop = y + height - 70;
        g.drawString(this.font, "REFERENCE STATE", x + 14, memoryTop, TEXT_DIM, false);
        g.fill(x + 14, memoryTop + 14, x + width - 14, memoryTop + 66, PANEL_DEEP);
        border(g, x + 14, memoryTop + 14, width - 28, 52, STROKE_DIM);
        g.fill(x + 22, memoryTop + 22, x + 26, memoryTop + 50, color);
        g.drawString(this.font, this.trimToWidth(this.baselineSummary(), width - 48), x + 34, memoryTop + 21, this.observer.getMissingBlocks() > 0 ? RED : GREEN, false);
        g.drawString(this.font, this.trimToWidth(this.presetSummary(), width - 48), x + 34, memoryTop + 34, this.observer.hasStructurePreset() ? AMBER : TEXT_DARK, false);

        int scanX = x + 18 + (int) ((width - 36) * ((time * 0.018f) % 1.0f));
        g.fill(scanX, y + height - 4, scanX + 28, y + height - 2, 0x664AD9FF);
    }

    private void renderGeometryPanel(GuiGraphics g, int x, int y, int width, int height, float time) {
        int color = this.categoryColor();
        g.fill(x, y, x + width, y + height, PANEL);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, "WATCH GEOMETRY", x + 12, y + 12, CYAN, false);
        g.drawString(this.font, this.observer.getWatchDirection().getName().toUpperCase() + " FACE", x + width - 74, y + 12, TEXT_DIM, false);

        int previewX = x + 12;
        int previewY = y + 32;
        int previewW = width - 24;
        int previewH = Math.min(160, height - 116);
        this.renderPreview(g, previewX, previewY, previewW, previewH, time);

        int categoryTop = previewY + previewH + 10;
        this.renderCategoryMatrix(g, x + 12, categoryTop, width - 24, 62);

        int feedTop = categoryTop + 72;
        if (feedTop + 44 > y + height - 8) {
            feedTop = y + height - 52;
        }
        g.fill(x + 12, feedTop, x + width - 12, feedTop + 42, PANEL_DEEP);
        border(g, x + 12, feedTop, width - 24, 42, STROKE_DIM);
        if (this.settings.getCategory() == DamageObserverSettings.ObserverCategory.WEAPONS) {
            g.drawString(this.font, "RPM GOVERNORS", x + 20, feedTop + 6, TEXT_DIM, false);
            g.drawString(this.font, this.trimToWidth(this.governorSummary(), width - 40), x + 20, feedTop + 18, this.governorSummaryColor(), false);
        } else {
            g.drawString(this.font, "DISPLAY LINK SOURCE TOKEN", x + 20, feedTop + 8, TEXT_DIM, false);
            g.drawString(this.font, this.trimToWidth(this.anchorSummary() + " / " + this.areaSummary(), width - 40), x + 20, feedTop + 23, color, false);
        }
    }

    private void renderPreview(GuiGraphics g, int x, int y, int width, int height, float time) {
        int color = this.categoryColor();
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE);
        this.drawGrid(g, x + 1, y + 1, width - 2, height - 2, 16, 0x172D5965);

        int centerY = y + height / 2 + 2;
        int observerX = x + 34;
        int targetX = x + width - 80;
        int targetY = centerY - 20;
        int targetSize = this.usesAreaRadius()
                ? Math.min(58, 26 + this.settings.getAreaRadius() * 7)
                : 28;

        this.drawBlockGlyph(g, observerX, centerY - 16, 24, 0xFF263747, AMBER);
        g.drawString(this.font, "SENSOR", observerX - 8, centerY + 19, TEXT_DIM, false);

        int anchorLeft = targetX - targetSize / 2;
        int anchorTop = targetY;
        this.drawLine(g, observerX + 24, centerY - 4, anchorLeft, anchorTop + targetSize / 2, 0x774AD9FF);
        this.drawLine(g, observerX + 24, centerY - 14, anchorLeft, anchorTop, 0x334AD9FF);
        this.drawLine(g, observerX + 24, centerY + 6, anchorLeft, anchorTop + targetSize, 0x334AD9FF);

        for (int i = 0; i < 4; i++) {
            float phase = (time * 0.025f + i * 0.25f) % 1.0f;
            int pulseX = observerX + 28 + Math.round((anchorLeft - observerX - 28) * phase);
            int pulseY = centerY - 4 + Math.round((anchorTop + targetSize / 2 - centerY + 4) * phase);
            g.fill(pulseX - 1, pulseY - 5, pulseX + 2, pulseY + 6, i == 0 ? TEXT : color);
        }

        if (this.usesAreaRadius()) {
            int layers = Math.min(4, this.settings.getAreaRadius());
            for (int layer = layers; layer >= 0; layer--) {
                int inset = layer * 4;
                int alphaColor = layer == 0 ? color : 0x66315064;
                g.fill(anchorLeft - inset, anchorTop - inset, anchorLeft + targetSize + inset, anchorTop + targetSize + inset, layer == 0 ? 0xFF132C38 : 0x44132C38);
                border(g, anchorLeft - inset, anchorTop - inset, targetSize + inset * 2, targetSize + inset * 2, alphaColor);
            }
            if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX) {
                g.fill(anchorLeft + targetSize / 2 - 4, anchorTop + targetSize - 3, anchorLeft + targetSize / 2 + 5, anchorTop + targetSize + 3, AMBER);
                g.drawCenteredString(this.font, "BASE", anchorLeft + targetSize / 2, anchorTop + targetSize + 8, AMBER);
            }
        } else {
            this.drawBlockGlyph(g, anchorLeft, anchorTop, targetSize, 0xFF132C38, color);
        }

        g.drawCenteredString(this.font, this.usesAreaRadius() ? this.observedDiameter() + "x" + this.observedDiameter() : "1x1", anchorLeft + targetSize / 2, anchorTop + targetSize / 2 - 4, TEXT);
        g.drawString(this.font, "ANCHOR " + this.previewAnchorPos().toShortString(), x + 10, y + height - 19, TEXT_DIM, false);
        g.drawString(this.font, this.settings.getCategory().getDisplayName(), x + width - this.font.width(this.settings.getCategory().getDisplayName()) - 10, y + height - 19, color, false);

        int ringRadius = 11 + (int) ((Math.sin(time * 0.32f) + 1.0f) * 3.0f);
        this.ring(g, anchorLeft + targetSize / 2, anchorTop + targetSize / 2, ringRadius + targetSize / 2, ringRadius + targetSize / 2, color);
    }

    private void renderCategoryMatrix(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, "ROLE MATRIX", x + 8, y + 7, TEXT_DIM, false);
        int chipY = y + 22;
        int chipW = Math.max(52, (width - 18) / 3);
        int i = 0;
        for (DamageObserverSettings.ObserverCategory category : DamageObserverSettings.ObserverCategory.values()) {
            int col = i % 3;
            int row = i / 3;
            int chipX = x + 8 + col * (chipW + 4);
            int yOffset = chipY + row * 18;
            boolean selected = category == this.settings.getCategory();
            int color = this.categoryColor(category);
            g.fill(chipX, yOffset, chipX + chipW, yOffset + 14, selected ? 0xFF17313C : 0xFF0A121A);
            border(g, chipX, yOffset, chipW, 14, selected ? color : STROKE_DIM);
            g.drawString(this.font, this.categoryShort(category), chipX + 5, yOffset + 3, selected ? color : TEXT_DARK, false);
            i++;
        }
    }

    private void renderFooter(GuiGraphics g, int x, int y, int width, int height, float time) {
        int color = this.categoryColor();
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        int ledX = x + 12;
        int pulse = 3 + (int) ((Math.sin(time * 0.33f) + 1.0f) * 2.0f);
        this.ring(g, ledX + 4, y + 16, pulse + 3, pulse + 3, color);
        g.fill(ledX + 2, y + 14, ledX + 7, y + 19, color);
        g.drawString(this.font, this.trimToWidth(this.geometryRuleLine(), width - 34), x + 26, y + 12, TEXT_DIM, false);
    }

    private void refreshControls() {
        this.modeButton.setMessage(Component.literal("Mode: " + this.settings.getWatchMode().getDisplayName()));
        this.categoryButton.setMessage(Component.literal("Role: " + this.settings.getCategory().getDisplayName()));
        if (this.governorMappingButton != null) {
            boolean weapons = this.settings.getCategory() == DamageObserverSettings.ObserverCategory.WEAPONS;
            this.governorMappingButton.visible = weapons;
            this.governorMappingButton.active = weapons;
            this.governorMappingButton.setMessage(Component.literal(this.settings.getWeaponGovernorMapping() == DamageObserverSettings.WeaponGovernorMapping.A_YAW_B_PITCH
                    ? "A:Y B:P"
                    : "A:P B:Y"));
        }
        if (this.governorYawInvertButton != null) {
            boolean weapons = this.settings.getCategory() == DamageObserverSettings.ObserverCategory.WEAPONS;
            this.governorYawInvertButton.visible = weapons;
            this.governorYawInvertButton.active = weapons;
            this.governorYawInvertButton.setMessage(Component.literal(this.settings.isWeaponYawInverted() ? "Yaw:Rev" : "Yaw:Nrm"));
        }
        if (this.governorPitchInvertButton != null) {
            boolean weapons = this.settings.getCategory() == DamageObserverSettings.ObserverCategory.WEAPONS;
            this.governorPitchInvertButton.visible = weapons;
            this.governorPitchInvertButton.active = weapons;
            this.governorPitchInvertButton.setMessage(Component.literal(this.settings.isWeaponPitchInverted() ? "Pit:Rev" : "Pit:Nrm"));
        }
        boolean areaMode = this.usesAreaRadius();
        this.radiusDownButton.active = areaMode && this.settings.getAreaRadius() > 1;
        this.radiusUpButton.active = areaMode && this.settings.getAreaRadius() < 6;
        if (this.recalibrateButton != null) {
            this.recalibrateButton.setMessage(Component.literal("Set Current As Healthy"));
        }
        if (this.savePresetButton != null) {
            this.savePresetButton.setMessage(Component.literal("Save Reference"));
        }
    }

    private void saveAndClose() {
        this.settings.setObserverName(this.nameBox.getValue());
        PacketDistributor.sendToServer(new DamageObserverConfigPacket(this.observer.getBlockPos(), this.settings));
        this.onClose();
    }

    private void sendObserverAction(DamageObserverActionPacket.Action action) {
        this.settings.setObserverName(this.nameBox.getValue());
        PacketDistributor.sendToServer(new DamageObserverActionPacket(this.observer.getBlockPos(), this.settings, action));
    }

    private String baselineSummary() {
        if (this.observer.getBaselineBlocks() <= 0) {
            return "Reference empty / ready to capture";
        }
        return "Reference " + this.observer.getPresentBaselineBlocks() + "/" + this.observer.getBaselineBlocks() + " present / lost " + this.observer.getMissingBlocks();
    }

    private String presetSummary() {
        if (!this.observer.hasStructurePreset()) {
            return "Reference not saved / active state only";
        }
        return "Reference saved / " + this.observer.getStructurePresetBlockCount() + " original blocks";
    }

    private int observedDiameter() {
        return this.usesAreaRadius()
                ? this.settings.getAreaRadius() * 2 + 1
                : 1;
    }

    private String anchorSummary() {
        BlockPos anchor = this.previewAnchorPos();
        return anchor.toShortString() + " / " + this.observedDiameter() + "w";
    }

    private String areaSummary() {
        if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.SINGLE_BLOCK) {
            return "single block";
        }
        int diameter = this.observedDiameter();
        if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX) {
            return diameter + "x" + diameter + "x" + diameter + " behind box";
        }
        return diameter + "x" + diameter + "x" + diameter + " local volume";
    }

    private String governorSummary() {
        List<BlockPos> governors = this.observer.getWeaponGovernorPositions();
        String a = governors.size() > 0 ? governors.get(0).toShortString() : "--";
        String b = governors.size() > 1 ? governors.get(1).toShortString() : "--";
        String mapping = this.settings.getWeaponGovernorMapping() == DamageObserverSettings.WeaponGovernorMapping.A_YAW_B_PITCH
                ? "A yaw / B pitch"
                : "A pitch / B yaw";
        if (governors.size() == 2) {
            return "A " + a + "  B " + b + "  / " + mapping;
        }
        return governors.size() + " detected / need exactly 2 / A " + a + " / B " + b;
    }

    private int governorSummaryColor() {
        int count = this.observer.getWeaponGovernorPositions().size();
        return count == 2 ? GREEN : count == 0 ? RED : AMBER;
    }

    private String geometryRuleLine() {
        if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.SINGLE_BLOCK) {
            return "Single Block locks the block directly in front of the observer face.";
        }
        int diameter = this.observedDiameter();
        if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX) {
            return "Behind Box grows a " + diameter + "x" + diameter + "x" + diameter + " box behind the mount; observer marks bottom-front center.";
        }
        return "Local Area locks a " + diameter + "x" + diameter + "x" + diameter + " cube centered one block forward.";
    }

    private boolean usesAreaRadius() {
        return this.settings.getWatchMode() == DamageObserverSettings.WatchMode.LOCAL_AREA
                || this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX;
    }

    private BlockPos previewAnchorPos() {
        if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX) {
            int radius = this.settings.getAreaRadius();
            return this.observer.getBlockPos().relative(this.observer.getBehindDirection(), radius + 1).above(radius);
        }
        return this.observer.getBlockPos().relative(this.observer.getWatchDirection());
    }

    private void drawMetricChip(GuiGraphics g, int x, int y, int width, String label, String value, int color) {
        g.fill(x, y, x + width, y + 32, 0xFF0A121A);
        border(g, x, y, width, 32, color);
        g.drawString(this.font, label, x + 7, y + 6, TEXT_DIM, false);
        g.drawString(this.font, this.trimToWidth(value, width - 14), x + 7, y + 18, color, false);
    }

    private void drawBlockGlyph(GuiGraphics g, int x, int y, int size, int fill, int borderColor) {
        int offset = Math.max(4, size / 5);
        g.fill(x + offset, y - offset, x + size + offset, y + size - offset, 0xFF0B141C);
        border(g, x + offset, y - offset, size, size, STROKE_DIM);
        g.fill(x, y, x + size, y + size, fill);
        border(g, x, y, size, size, borderColor);
        this.drawLine(g, x, y, x + offset, y - offset, borderColor);
        this.drawLine(g, x + size, y, x + size + offset, y - offset, borderColor);
        this.drawLine(g, x + size, y + size, x + size + offset, y + size - offset, borderColor);
    }

    private void drawGrid(GuiGraphics g, int x, int y, int width, int height, int spacing, int color) {
        for (int gridX = x; gridX <= x + width; gridX += spacing) {
            g.fill(gridX, y, gridX + 1, y + height, color);
        }
        for (int gridY = y; gridY <= y + height; gridY += spacing) {
            g.fill(x, gridY, x + width, gridY + 1, color);
        }
    }

    private void renderBackdrop(GuiGraphics g, float time) {
        g.fillGradient(0, 0, this.width, this.height, BACKDROP_TOP, BACKDROP_BOTTOM);
        int offset = (int) (time * 0.35f) % 28;
        for (int x = -offset; x < this.width; x += 28) {
            g.fill(x, 0, x + 1, this.height, 0x1530525E);
        }
        for (int y = offset - 28; y < this.height; y += 28) {
            g.fill(0, y, this.width, y + 1, 0x1030525E);
        }
        for (int y = 0; y < this.height; y += 4) {
            g.fill(0, y, this.width, y + 1, 0x08000000);
        }
    }

    private int categoryColor() {
        return this.categoryColor(this.settings.getCategory());
    }

    private int categoryColor(DamageObserverSettings.ObserverCategory category) {
        return switch (category) {
            case HULL -> RED;
            case LIFT -> CYAN;
            case POWER -> AMBER;
            case WEAPONS -> 0xFFFF7F50;
            case CONTROL -> GREEN;
            case CARGO -> VIOLET;
        };
    }

    private String categoryShort(DamageObserverSettings.ObserverCategory category) {
        return switch (category) {
            case HULL -> "HUL";
            case LIFT -> "LFT";
            case POWER -> "PWR";
            case WEAPONS -> "WPN";
            case CONTROL -> "CTL";
            case CARGO -> "CRG";
        };
    }

    private float animationTime(float partialTick) {
        if (this.observer.getLevel() != null) {
            return this.observer.getLevel().getGameTime() + partialTick;
        }
        return (System.currentTimeMillis() % 100000L) / 50.0f;
    }

    private void drawScaledString(GuiGraphics g, String text, int x, int y, int color, int scale) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(this.font, text, x / scale, y / scale, color, false);
        g.pose().popPose();
    }

    private String trimToWidth(String text, int maxWidth) {
        String trimmed = this.font.plainSubstrByWidth(text, maxWidth);
        if (trimmed.length() == text.length()) {
            return text;
        }
        return trimmed.substring(0, Math.max(0, trimmed.length() - 1)) + "...";
    }

    private void ring(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        if (rx <= 0 || ry <= 0) {
            return;
        }
        for (int degree = 0; degree < 360; degree += 5) {
            double radians = Math.toRadians(degree);
            int x = cx + (int) Math.round(Math.cos(radians) * rx);
            int y = cy + (int) Math.round(Math.sin(radians) * ry);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}