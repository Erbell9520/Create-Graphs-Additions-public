package com.createstats.gui;

import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.menu.BattleScreenLinkedControllerMenu;
import com.createstats.network.BattleScreenWeaponActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class BattleScreenLinkedControllerScreen extends AbstractContainerScreen<BattleScreenLinkedControllerMenu> {

    private static final int CONTROL_SECTION_TOP = 78;
    private static final int CONTROL_SECTION_HEIGHT = 166;
    private static final int INVENTORY_SECTION_TOP = 248;
    private static final int INVENTORY_SECTION_HEIGHT = 120;

    private static final int SCREEN_BG = 0xFF071018;
    private static final int PANEL = 0xFF101821;
    private static final int PANEL_DEEP = 0xFF0B141C;
    private static final int CARD = 0xFF0E1821;
    private static final int CARD_ACTIVE = 0xFF132432;
    private static final int STROKE = 0xFF315064;
    private static final int STROKE_DIM = 0xFF223241;
    private static final int TEXT = 0xFFE7F3F5;
    private static final int TEXT_DIM = 0xFF8FA9B4;
    private static final int CYAN = 0xFF4AD9FF;
    private static final int GREEN = 0xFF62E8A3;
    private static final int AMBER = 0xFFFFC15E;
    private static final int SHADOW = 0x8A000000;

    private static final String[] KEY_LABELS = {"W", "S", "A", "D", "SPACE", "SHIFT"};
    private static final String[] ACTION_LABELS = {
            "Pitch Up",
            "Pitch Down",
            "Yaw Left",
            "Yaw Right",
            "Auto Fire Hold",
            "Lock Facing Target"
    };

    private LinkedControllerRpmSliderTarget rpmSliderTarget;
    private boolean rpmSliderDragging;
    private boolean rpmSliderPreviewActive;
    private int rpmSliderPreview = BattleScreenDisplayBE.LINKED_CONTROLLER_MANUAL_AIM_RPM_DEFAULT;

    public BattleScreenLinkedControllerScreen(BattleScreenLinkedControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 416;
        this.imageHeight = 384;
        this.inventoryLabelY = 290;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.reopenBattleScreen())
                .bounds(this.leftPos + this.imageWidth - 84, this.topPos + 18, 62, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.rpmSliderTarget != null && this.rpmSliderTarget.contains(mouseX, mouseY)) {
            this.rpmSliderDragging = true;
            this.rpmSliderPreviewActive = true;
            this.rpmSliderPreview = this.rpmFromMouse(mouseX, this.rpmSliderTarget);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.rpmSliderDragging && this.rpmSliderTarget != null) {
            this.rpmSliderPreview = this.rpmFromMouse(mouseX, this.rpmSliderTarget);
            this.rpmSliderPreviewActive = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.rpmSliderDragging) {
            if (this.rpmSliderTarget != null) {
                this.rpmSliderPreview = this.rpmFromMouse(mouseX, this.rpmSliderTarget);
            }
            this.rpmSliderDragging = false;
            this.commitRpmSlider();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(0, 0, this.width, this.height, SCREEN_BG);
        graphics.fill(x + 8, y + 10, x + this.imageWidth + 8, y + this.imageHeight + 10, SHADOW);
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        border(graphics, x, y, this.imageWidth, this.imageHeight, STROKE);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + 4, CYAN);
        graphics.fill(x + 16, y + 16, x + this.imageWidth - 16, y + 64, PANEL_DEEP);
        border(graphics, x + 16, y + 16, this.imageWidth - 32, 48, STROKE_DIM);
        graphics.fill(x + 16, y + CONTROL_SECTION_TOP, x + this.imageWidth - 16, y + CONTROL_SECTION_TOP + CONTROL_SECTION_HEIGHT, PANEL_DEEP);
        border(graphics, x + 16, y + CONTROL_SECTION_TOP, this.imageWidth - 32, CONTROL_SECTION_HEIGHT, STROKE_DIM);
        graphics.fill(x + 16, y + INVENTORY_SECTION_TOP, x + this.imageWidth - 16, y + INVENTORY_SECTION_TOP + INVENTORY_SECTION_HEIGHT, PANEL_DEEP);
        border(graphics, x + 16, y + INVENTORY_SECTION_TOP, this.imageWidth - 32, INVENTORY_SECTION_HEIGHT, STROKE_DIM);

        BattleScreenDisplayBE.LinkedObserver observer = this.menu.getObserver();
        String observerName = observer == null ? "Linked weapon" : trim(observer.name(), this.imageWidth - 138);
        String detail = observer == null || !observer.weapon().detected()
                ? "Match these six frequency pairs to a Create Linked Controller"
                : trim(observer.weapon().displayName().isEmpty() ? observer.weapon().kind() : observer.weapon().displayName(), this.imageWidth - 138);
        String supportLine = observer == null || !observer.weapon().detected()
                ? "W/S pitch, A/D yaw, Space hold fire, Shift lock current facing target"
                : trim(this.weaponSupportLine(observer.weapon()), this.imageWidth - 138);

        graphics.drawString(this.font, "Linked Controller", x + 28, y + 24, TEXT, false);
        graphics.drawString(this.font, observerName, x + 28, y + 39, CYAN, false);
        graphics.drawString(this.font, detail, x + 28, y + 53, TEXT_DIM, false);
        graphics.drawString(this.font, supportLine, x + 28, y + 67, TEXT_DIM, false);

        graphics.drawString(this.font, "CONTROL MAP", x + 28, y + CONTROL_SECTION_TOP + 12, TEXT_DIM, false);
        graphics.drawString(this.font, "PAIR A", x + BattleScreenLinkedControllerMenu.SLOT_A_X - 2, y + CONTROL_SECTION_TOP + 12, TEXT_DIM, false);
        graphics.drawString(this.font, "PAIR B", x + BattleScreenLinkedControllerMenu.SLOT_B_X - 2, y + CONTROL_SECTION_TOP + 12, TEXT_DIM, false);
        graphics.drawString(this.font, "INVENTORY DOCK", x + 28, y + INVENTORY_SECTION_TOP + 10, TEXT_DIM, false);
        this.renderManualAimRpmSlider(graphics, x, y, mouseX, mouseY);

        for (int button = 0; button < BattleScreenDisplayBE.LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            int rowY = y + BattleScreenLinkedControllerMenu.ROW_START_Y + button * BattleScreenLinkedControllerMenu.ROW_HEIGHT;
            boolean configured = this.menu.isConfigured(button);
            int accent = button < 4 ? CYAN : AMBER;
            int statusColor = configured ? GREEN : accent;
            graphics.fill(x + 24, rowY - 2, x + this.imageWidth - 24, rowY + 20, configured ? CARD_ACTIVE : CARD);
            border(graphics, x + 24, rowY - 2, this.imageWidth - 48, 22, configured ? GREEN : STROKE_DIM);
            graphics.fill(x + 24, rowY - 2, x + 28, rowY + 20, configured ? GREEN : accent);
            graphics.drawString(this.font, KEY_LABELS[button], x + 38, rowY + 4, statusColor, false);
            graphics.drawString(this.font, ACTION_LABELS[button], x + 94, rowY + 4, TEXT, false);
            String status = configured ? "MATCHED" : "OPEN";
            graphics.drawString(this.font, status, x + 274, rowY + 4, configured ? GREEN : accent, false);
            renderSlotBackdrop(graphics, x + BattleScreenLinkedControllerMenu.SLOT_A_X - 1, rowY - 1);
            renderSlotBackdrop(graphics, x + BattleScreenLinkedControllerMenu.SLOT_B_X - 1, rowY - 1);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void renderManualAimRpmSlider(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int infoY = y + INVENTORY_SECTION_TOP + 10;
        int sliderX = x + 28;
        int sliderY = y + INVENTORY_SECTION_TOP + 26;
        int sliderWidth = this.imageWidth - 56;
        int sliderHeight = 18;
        int min = BattleScreenDisplayBE.LINKED_CONTROLLER_MANUAL_AIM_RPM_MIN;
        int max = BattleScreenDisplayBE.LINKED_CONTROLLER_MANUAL_AIM_RPM_MAX;
        int rpm = this.currentManualAimRpm();
        float normalized = (rpm - min) / (float) Math.max(1, max - min);
        int trackX = sliderX + 4;
        int trackY = sliderY + 7;
        int trackWidth = sliderWidth - 8;
        int fillWidth = Math.round(trackWidth * normalized);
        boolean hover = mouseX >= sliderX && mouseX < sliderX + sliderWidth && mouseY >= sliderY && mouseY < sliderY + sliderHeight;
        int frameColor = this.rpmSliderDragging ? GREEN : hover ? CYAN : STROKE;

        graphics.drawString(this.font, "MANUAL AIM RPM 16-128", x + 28, infoY, TEXT_DIM, false);
        String valueText = rpm + " RPM";
        graphics.drawString(this.font, valueText, x + this.imageWidth - this.font.width(valueText) - 28, infoY, CYAN, false);
        graphics.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, PANEL);
        border(graphics, sliderX, sliderY, sliderWidth, sliderHeight, frameColor);
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 4, SCREEN_BG);
        if (fillWidth > 0) {
            graphics.fill(trackX, trackY, trackX + fillWidth, trackY + 4, CYAN);
        }
        int knobX = trackX + Math.round(trackWidth * normalized);
        graphics.fill(knobX - 4, trackY - 3, knobX + 5, trackY + 9, PANEL_DEEP);
        border(graphics, knobX - 4, trackY - 3, 9, 12, hover || this.rpmSliderDragging ? GREEN : TEXT, false);

        this.rpmSliderTarget = new LinkedControllerRpmSliderTarget(sliderX, sliderY, sliderWidth, sliderHeight, min, max);
    }

    private int currentManualAimRpm() {
        int serverValue = this.menu.getManualAimRpm();
        if (this.rpmSliderPreviewActive) {
            if (!this.rpmSliderDragging && Math.abs(serverValue - this.rpmSliderPreview) <= 1) {
                this.rpmSliderPreviewActive = false;
                return serverValue;
            }
            return this.rpmSliderPreview;
        }
        return serverValue;
    }

    private int rpmFromMouse(double mouseX, LinkedControllerRpmSliderTarget target) {
        float normalized = Math.max(0.0f, Math.min(1.0f, (float) ((mouseX - target.x()) / Math.max(1.0, target.width()))));
        return target.minRpm() + Math.round(normalized * (target.maxRpm() - target.minRpm()));
    }

    private void commitRpmSlider() {
        PacketDistributor.sendToServer(new BattleScreenWeaponActionPacket(
                this.menu.getDisplayPos(),
                this.menu.getObserverPos(),
                BattleScreenWeaponActionPacket.Action.SET_LINKED_CONTROLLER_MANUAL_AIM_RPM,
                this.rpmSliderPreview));
    }

    private String weaponSupportLine(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if ("BIG_CANNON".equals(weapon.kind())) {
            return "Big cannon lock uses the same mount tracking, but with a slower response profile for heavier motion.";
        }
        return "Autocannon lock can track faster and keeps the same fire-hold behavior from the battle console.";
    }

    private void reopenBattleScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            this.onClose();
            return;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(this.menu.getDisplayPos());
        if (blockEntity instanceof BattleScreenDisplayBE display) {
            minecraft.setScreen(new BattleScreenDisplayScreen(display));
            return;
        }
        this.onClose();
    }

    private static void renderSlotBackdrop(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, PANEL_DEEP);
        border(graphics, x, y, 18, 18, STROKE_DIM);
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        border(graphics, x, y, width, height, color, true);
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color, boolean fillBottom) {
        graphics.fill(x, y, x + width, y + 1, color);
        if (fillBottom) {
            graphics.fill(x, y + height - 1, x + width, y + height, color);
        }
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private String trim(String value, int maxWidth) {
        return this.font.plainSubstrByWidth(value, Math.max(20, maxWidth));
    }

    private record LinkedControllerRpmSliderTarget(
            int x,
            int y,
            int width,
            int height,
            int minRpm,
            int maxRpm) {

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}