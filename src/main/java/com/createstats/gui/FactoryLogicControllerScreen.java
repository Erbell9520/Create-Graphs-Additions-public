package com.createstats.gui;

import javax.annotation.Nullable;

import com.createstats.blockentity.FactoryLogicControllerBE;
import com.createstats.blockentity.FactoryLogicControllerBE.RuleRuntime;
import com.createstats.blockentity.FactoryLogicControllerBE.RuleStatus;
import com.createstats.menu.FactoryLogicControllerMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

public class FactoryLogicControllerScreen extends AbstractContainerScreen<FactoryLogicControllerMenu> {

    private static final int SCREEN_BG = 0xE6080A0D;
    private static final int FRAME_BG = 0xF011151A;
    private static final int FRAME_INNER = 0xF0151B22;
    private static final int HEADER_BG = 0xFF1A222B;
    private static final int PANEL_BG = 0xF0182028;
    private static final int PANEL_HEADER_BG = 0xFF242E39;
    private static final int CARD_BG = 0xF0131A21;
    private static final int CARD_EMPTY_BG = 0xC9101419;
    private static final int CARD_HOVER_BG = 0xF01A232C;
    private static final int CARD_ACTIVE_BG = 0xF0232D38;
    private static final int SLOT_BG = 0xAA202933;
    private static final int BUTTON_TRAY = 0x6613191F;
    private static final int BORDER = 0xFF65727D;
    private static final int SOFT_BORDER = 0x555B6772;
    private static final int ACCENT = 0xFFF0B35A;
    private static final int TEXT = 0xFFE8D8BB;
    private static final int DIM = 0xFF9E9583;
    private static final int MUTED = 0xFF7F8B97;
    private static final int RUNNING = 0xFF73E08C;
    private static final int COUNTDOWN = 0xFFF0C466;
    private static final int STOPPED = 0xFFF07D62;
    private static final int ERROR = 0xFFFF6262;
    private static final int IDLE = 0xFF74B7E8;
    private static final int UNUSED = 0xFF56606A;

    private static final int WINDOW_WIDTH = 650;
    private static final int MIN_WINDOW_HEIGHT = 472;
    private static final int HEADER_X = 8;
    private static final int HEADER_Y = 8;
    private static final int HEADER_HEIGHT = 50;
    private static final int LEFT_PANEL_X = 12;
    private static final int LEFT_PANEL_Y = 68;
    private static final int LEFT_PANEL_WIDTH = 384;
    private static final int RIGHT_PANEL_X = 406;
    private static final int RIGHT_PANEL_WIDTH = 232;
    private static final int DETAILS_PANEL_Y = 68;
    private static final int DETAILS_PANEL_HEIGHT = 126;
    private static final int INVENTORY_PANEL_Y = 202;
    private static final int INVENTORY_PANEL_HEIGHT = 104;
    private static final int HELP_PANEL_Y = 318;
    private static final int ROW_TEXT_X = 78;
    private static final int ROW_MACHINE_X = 96;
    private static final int BUTTON_MINUS_X = 274;
    private static final int BUTTON_PLUS_X = 294;
    private static final int BUTTON_PAUSE_X = 314;
    private static final int BUTTON_RESET_X = 348;
    private static final int BUTTON_SMALL_WIDTH = 16;
    private static final int BUTTON_PAUSE_WIDTH = 32;
    private static final int BUTTON_RESET_WIDTH = 34;
    private static final int CHIP_WIDTH = 55;
    private static final int CHIP_HEIGHT = 20;

    private int visibleRowCount;
    private int lastHoveredRow = -1;
    private int selectedRow = -1;
    private float renderScale = 1.0f;
    @Nullable
    private Button[][] rowControlButtons;
    @Nullable
    private Button strategyButton;
    @Nullable
    private Button rpmDownButton;
    @Nullable
    private Button rpmUpButton;

    public FactoryLogicControllerScreen(FactoryLogicControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.visibleRowCount = menu.getVisibleRowCount();
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = getWindowHeight(this.visibleRowCount);
        this.inventoryLabelX = FactoryLogicControllerMenu.PLAYER_INV_X;
        this.inventoryLabelY = FactoryLogicControllerMenu.PLAYER_INV_Y - 12;
    }

    @Override
    protected void init() {
        super.init();
        this.refreshLayout(true);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshLayout(false);
        this.updateRowButtonStates();
    }

    private void refreshLayout(boolean force) {
        int updatedVisibleRowCount = this.menu.getVisibleRowCount();
        if (!force && updatedVisibleRowCount == this.visibleRowCount) {
            return;
        }
        this.visibleRowCount = updatedVisibleRowCount;
        this.imageHeight = getWindowHeight(updatedVisibleRowCount);
        this.renderScale = this.computeRenderScale();
        if (this.renderScale < 1.0f) {
            this.leftPos = Math.round((this.width / this.renderScale - this.imageWidth) / 2f);
            this.topPos = Math.round((this.height / this.renderScale - this.imageHeight) / 2f);
        } else {
            this.leftPos = (this.width - this.imageWidth) / 2;
            this.topPos = (this.height - this.imageHeight) / 2;
        }
        if (this.selectedRow >= this.visibleRowCount) {
            this.selectedRow = this.visibleRowCount - 1;
        }
        this.rebuildRowButtons();
    }

    private float computeRenderScale() {
        if (this.width == 0 || this.height == 0) {
            return 1.0f;
        }
        float sx = (this.width - 4f) / WINDOW_WIDTH;
        float sy = (this.height - 4f) / getWindowHeight(this.visibleRowCount);
        return Math.min(1.0f, Math.min(sx, sy));
    }

    private void rebuildRowButtons() {
        this.clearWidgets();
        this.rowControlButtons = new Button[this.visibleRowCount][4];
        for (int row = 0; row < this.visibleRowCount; row++) {
            int y = this.topPos + FactoryLogicControllerMenu.ROW_START_Y + row * FactoryLogicControllerMenu.ROW_HEIGHT + 8;

            Button minus = this.createRowButton(Component.literal("-"),
                    this.leftPos + BUTTON_MINUS_X, y, BUTTON_SMALL_WIDTH,
                    FactoryLogicControllerMenu.getButtonId(row, 0), ButtonStyle.SMALL);
            Button plus = this.createRowButton(Component.literal("+"),
                    this.leftPos + BUTTON_PLUS_X, y, BUTTON_SMALL_WIDTH,
                    FactoryLogicControllerMenu.getButtonId(row, 1), ButtonStyle.SMALL);
            Button pause = this.createRowButton(Component.literal("Hold"),
                    this.leftPos + BUTTON_PAUSE_X, y, BUTTON_PAUSE_WIDTH,
                    FactoryLogicControllerMenu.getButtonId(row, 2), ButtonStyle.HOLD);
            Button reset = this.createRowButton(Component.literal("Run"),
                    this.leftPos + BUTTON_RESET_X, y, BUTTON_RESET_WIDTH,
                    FactoryLogicControllerMenu.getButtonId(row, 3), ButtonStyle.RUN);

            this.addRenderableWidget(minus);
            this.addRenderableWidget(plus);
            this.addRenderableWidget(pause);
            this.addRenderableWidget(reset);

            this.rowControlButtons[row][0] = minus;
            this.rowControlButtons[row][1] = plus;
            this.rowControlButtons[row][2] = pause;
            this.rowControlButtons[row][3] = reset;
        }

        this.strategyButton = new ConsoleButton(this.leftPos + 168, this.topPos + 12, 116, 16,
                Component.literal("Bank: Parallel"),
                button -> this.sendMenuButton(FactoryLogicControllerMenu.STRATEGY_TOGGLE_ID), ButtonStyle.SMALL);
        this.addRenderableWidget(this.strategyButton);

        this.rpmDownButton = new ConsoleButton(this.leftPos + 290, this.topPos + 12, 14, 16,
                Component.literal("-"),
                button -> this.sendMenuButton(FactoryLogicControllerMenu.TARGET_RPM_DOWN_ID), ButtonStyle.SMALL);
        this.rpmUpButton = new ConsoleButton(this.leftPos + 360, this.topPos + 12, 14, 16,
                Component.literal("+"),
                button -> this.sendMenuButton(FactoryLogicControllerMenu.TARGET_RPM_UP_ID), ButtonStyle.SMALL);
        this.addRenderableWidget(this.rpmDownButton);
        this.addRenderableWidget(this.rpmUpButton);

        this.strategyButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Bank coordination for linked flywheels: Parallel shares the load, Sequential runs one at a time, Smart holds the target RPM.")));
        this.rpmDownButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Lower the RPM the Smart bank holds.")));
        this.rpmUpButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Raise the RPM the Smart bank holds.")));
    }

    private Button createRowButton(Component label, int x, int y, int width, int buttonId, ButtonStyle style) {
        return new ConsoleButton(x, y, width, 16, label, button -> this.sendMenuButton(buttonId), style);
    }

    private void sendMenuButton(int buttonId) {
        Minecraft minecraft = this.minecraft;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        if (this.renderScale < 1.0f) {
            graphics.pose().pushPose();
            graphics.pose().scale(this.renderScale, this.renderScale, 1.0f);
            int scaledMouseX = Math.round(mouseX / this.renderScale);
            int scaledMouseY = Math.round(mouseY / this.renderScale);
            this.lastHoveredRow = this.getHoveredRow(scaledMouseX, scaledMouseY);
            this.renderConsole(graphics);
            super.render(graphics, scaledMouseX, scaledMouseY, partialTick);
            graphics.pose().popPose();
        } else {
            this.lastHoveredRow = this.getHoveredRow(mouseX, mouseY);
            this.renderConsole(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
        // FE Dependent Mode panel, drawn unscaled in the screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(graphics, font, 8, 8, 110,
                this.menu.getController() == null ? null : this.menu.getController().feConsumer());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        FactoryLogicControllerBE controller = this.menu.getController();
        int focusRow = this.resolveFocusedRow(controller);

        graphics.drawString(this.font, "Factory Logic Controller", 18, 16, ACCENT, false);
        graphics.drawString(this.font, "branch automation console", 18, 28, DIM, false);

        int configuredCount = 0;
        int liveCount = 0;
        int heldCount = 0;
        int faultCount = 0;
        for (int row = 0; row < FactoryLogicControllerBE.RULE_COUNT; row++) {
            if (!controller.isRowUsed(row)) {
                continue;
            }
            configuredCount++;
            RuleRuntime runtime = controller.getRuntime(row);
            RuleStatus status = runtime.getStatus();
            if (status == RuleStatus.RUNNING || status == RuleStatus.COUNTDOWN) {
                liveCount++;
            }
            if (status == RuleStatus.STOPPED || (status == RuleStatus.NO_MACHINES && runtime.isControllerHeldStopped())) {
                heldCount++;
            }
            if (status == RuleStatus.LINK_ERROR || status == RuleStatus.AREA_ERROR) {
                faultCount++;
            }
        }

        this.drawSummaryChip(graphics, RIGHT_PANEL_X + 0, 18, "RULES", configuredCount, ACCENT);
        this.drawSummaryChip(graphics, RIGHT_PANEL_X + CHIP_WIDTH + 4, 18, "LIVE", liveCount, RUNNING);
        this.drawSummaryChip(graphics, RIGHT_PANEL_X + (CHIP_WIDTH + 4) * 2, 18, "HELD", heldCount, STOPPED);
        this.drawSummaryChip(graphics, RIGHT_PANEL_X + (CHIP_WIDTH + 4) * 3, 18, "FAULT", faultCount, ERROR);

        if (controller.hasFlywheelRows()
                && controller.getBankStrategy() == FactoryLogicControllerBE.BankStrategy.SMART) {
            graphics.drawCenteredString(this.font, controller.getTargetRpm() + " RPM", 332, 17, ACCENT);
        }

        graphics.drawString(this.font, "Rule Matrix", LEFT_PANEL_X + 10, LEFT_PANEL_Y + 7, TEXT, false);
        graphics.drawString(this.font, "Active rule", RIGHT_PANEL_X + 10, DETAILS_PANEL_Y + 7, TEXT, false);
        graphics.drawString(this.font, "Operator Inventory", RIGHT_PANEL_X + 10, INVENTORY_PANEL_Y + 7, TEXT, false);
        graphics.drawString(this.font, "Setup Notes", RIGHT_PANEL_X + 10, HELP_PANEL_Y + 7, TEXT, false);

        int titleWidth = BUTTON_MINUS_X - ROW_MACHINE_X - 8;
        int bodyWidth = BUTTON_MINUS_X - ROW_TEXT_X - 8;
        for (int row = 0; row < this.visibleRowCount; row++) {
            int y = FactoryLogicControllerMenu.ROW_START_Y + row * FactoryLogicControllerMenu.ROW_HEIGHT;
            RuleRuntime runtime = controller.getRuntime(row);
            RuleStatus status = runtime.getStatus();
            int statusColor = controller.isRowUsed(row) ? this.getStatusColor(status) : UNUSED;

            graphics.drawString(this.font, String.format("%02d", row + 1), ROW_TEXT_X, y + 3, ACCENT, false);
            graphics.drawString(this.font, trim(controller.getRowMachineLabel(row), titleWidth), ROW_MACHINE_X, y + 3, TEXT, false);
            graphics.drawString(this.font, trim(controller.getRowInfoLabel(row), bodyWidth), ROW_TEXT_X, y + 13, DIM, false);
            graphics.drawString(this.font,
                    trim(controller.getRowStatusLabel(row) + " - " + controller.getRowDetailLabel(row), bodyWidth),
                    ROW_TEXT_X, y + 23, statusColor, false);
        }

        this.renderDetailsPanel(graphics, controller, focusRow);
        this.renderHelpPanel(graphics, controller, focusRow);
    }

    private void renderConsole(GuiGraphics graphics) {
        FactoryLogicControllerBE controller = this.menu.getController();
        int focusRow = this.resolveFocusedRow(controller);

        graphics.fill(this.leftPos - 4, this.topPos - 4, this.leftPos + this.imageWidth + 4, this.topPos + this.imageHeight + 4, 0x44000000);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, SCREEN_BG);
        graphics.fill(this.leftPos + 2, this.topPos + 2, this.leftPos + this.imageWidth - 2, this.topPos + this.imageHeight - 2, FRAME_BG);
        graphics.fill(this.leftPos + 6, this.topPos + 6, this.leftPos + this.imageWidth - 6, this.topPos + this.imageHeight - 6, FRAME_INNER);
        this.border(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, BORDER);
        graphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + 2, 0x22FFFFFF);

        this.renderPanel(graphics, this.leftPos + HEADER_X, this.topPos + HEADER_Y, this.imageWidth - 16, HEADER_HEIGHT);

        int rulesPanelHeight = this.imageHeight - LEFT_PANEL_Y - 10;
        this.renderPanel(graphics, this.leftPos + LEFT_PANEL_X, this.topPos + LEFT_PANEL_Y, LEFT_PANEL_WIDTH, rulesPanelHeight);
        this.renderPanel(graphics, this.leftPos + RIGHT_PANEL_X, this.topPos + DETAILS_PANEL_Y, RIGHT_PANEL_WIDTH, DETAILS_PANEL_HEIGHT);
        this.renderPanel(graphics, this.leftPos + RIGHT_PANEL_X, this.topPos + INVENTORY_PANEL_Y, RIGHT_PANEL_WIDTH, INVENTORY_PANEL_HEIGHT);
        this.renderPanel(graphics, this.leftPos + RIGHT_PANEL_X, this.topPos + HELP_PANEL_Y, RIGHT_PANEL_WIDTH,
                this.imageHeight - HELP_PANEL_Y - 10);

        this.renderRuleCards(graphics, controller, focusRow);
        this.renderInventoryBackdrop(graphics);
    }

    private void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        this.border(graphics, x, y, width, height, BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 18, PANEL_HEADER_BG);
        graphics.fill(x + 1, y + 18, x + width - 1, y + 19, SOFT_BORDER);
    }

    private void renderRuleCards(GuiGraphics graphics, FactoryLogicControllerBE controller, int focusRow) {
        int cardLeft = this.leftPos + LEFT_PANEL_X + 6;
        int cardRight = this.leftPos + LEFT_PANEL_X + LEFT_PANEL_WIDTH - 6;

        for (int row = 0; row < this.visibleRowCount; row++) {
            int rowTop = this.topPos + FactoryLogicControllerMenu.ROW_START_Y + row * FactoryLogicControllerMenu.ROW_HEIGHT;
            int rowBottom = rowTop + FactoryLogicControllerMenu.ROW_HEIGHT - 2;
            boolean used = controller.isRowUsed(row);
            boolean focused = row == focusRow;
            boolean hovered = row == this.lastHoveredRow;
            RuleStatus status = controller.getRuntime(row).getStatus();
            int accentColor = used ? this.getStatusColor(status) : UNUSED;
            int cardColor = used ? CARD_BG : CARD_EMPTY_BG;
            if (hovered) {
                cardColor = CARD_HOVER_BG;
            }
            if (focused) {
                cardColor = CARD_ACTIVE_BG;
            }

            graphics.fill(cardLeft, rowTop, cardRight, rowBottom, cardColor);
            graphics.fill(cardLeft, rowTop, cardLeft + 4, rowBottom, accentColor);
            this.border(graphics, cardLeft, rowTop, cardRight - cardLeft, rowBottom - rowTop,
                    focused ? ACCENT : hovered ? TEXT : SOFT_BORDER);

            int slotTop = this.topPos + FactoryLogicControllerMenu.ROW_START_Y + row * FactoryLogicControllerMenu.ROW_HEIGHT
                    + FactoryLogicControllerMenu.SLOT_Y_OFFSET;
            int machineSlotLeft = this.leftPos + FactoryLogicControllerMenu.MACHINE_SLOT_X - 1;
            boolean flywheelOnlyRow = controller.isRowFlywheelLinked(row) && controller.getMachineCardStack(row).isEmpty();
            if (flywheelOnlyRow) {
                // Single-card flywheel control: the machine slot is intentionally unused, mark it disabled.
                graphics.fill(machineSlotLeft, slotTop - 1, machineSlotLeft + 18, slotTop + 17, 0x55101419);
                this.border(graphics, machineSlotLeft, slotTop - 1, 18, 18, SOFT_BORDER);
                graphics.fill(machineSlotLeft + 5, slotTop + 7, machineSlotLeft + 13, slotTop + 9, MUTED);
            } else {
                graphics.fill(machineSlotLeft, slotTop - 1, machineSlotLeft + 18, slotTop + 17, SLOT_BG);
                this.border(graphics, machineSlotLeft, slotTop - 1, 18, 18, BORDER);
            }
            graphics.fill(this.leftPos + FactoryLogicControllerMenu.LINK_SLOT_X - 1, slotTop - 1,
                    this.leftPos + FactoryLogicControllerMenu.LINK_SLOT_X + 17, slotTop + 17, SLOT_BG);
            this.border(graphics, this.leftPos + FactoryLogicControllerMenu.LINK_SLOT_X - 1, slotTop - 1, 18, 18, BORDER);

            graphics.fill(this.leftPos + BUTTON_MINUS_X - 6, rowTop + 4, this.leftPos + BUTTON_RESET_X + BUTTON_RESET_WIDTH + 2,
                    rowBottom - 4, BUTTON_TRAY);
            graphics.fill(this.leftPos + BUTTON_MINUS_X - 7, rowTop + 4, this.leftPos + BUTTON_MINUS_X - 6, rowBottom - 4, SOFT_BORDER);
        }
    }

    private void renderInventoryBackdrop(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = this.leftPos + FactoryLogicControllerMenu.PLAYER_INV_X + col * 18 - 1;
                int y = this.topPos + FactoryLogicControllerMenu.PLAYER_INV_Y + row * 18 - 1;
                graphics.fill(x, y, x + 18, y + 18, SLOT_BG);
                this.border(graphics, x, y, 18, 18, BORDER);
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = this.leftPos + FactoryLogicControllerMenu.PLAYER_INV_X + col * 18 - 1;
            int y = this.topPos + FactoryLogicControllerMenu.PLAYER_INV_Y + 58 - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_BG);
            this.border(graphics, x, y, 18, 18, BORDER);
        }
    }

    private void drawSummaryChip(GuiGraphics graphics, int x, int y, String label, int value, int accentColor) {
        graphics.fill(x, y, x + CHIP_WIDTH, y + CHIP_HEIGHT, 0xAA11171D);
        this.border(graphics, x, y, CHIP_WIDTH, CHIP_HEIGHT, accentColor);
        graphics.drawString(this.font, String.valueOf(value), x + 5, y + 6, accentColor, false);
        graphics.drawString(this.font, label, x + 17, y + 6, DIM, false);
    }

    private void renderDetailsPanel(GuiGraphics graphics, FactoryLogicControllerBE controller, int row) {
        int textX = RIGHT_PANEL_X + 10;
        int textWidth = RIGHT_PANEL_WIDTH - 20;
        int y = DETAILS_PANEL_Y + 28;

        if (row < 0) {
            this.drawWrappedText(graphics,
                    Component.literal("No rule row is available. Insert cards to bring this console online."),
                    textX, y, textWidth, DIM);
            return;
        }

        RuleRuntime runtime = controller.getRuntime(row);
        RuleStatus status = runtime.getStatus();
        int statusColor = this.getStatusColor(status);

        graphics.drawString(this.font, "Bay " + String.format("%02d", row + 1), textX, y, ACCENT, false);
        this.drawStatusPill(graphics, RIGHT_PANEL_X + RIGHT_PANEL_WIDTH - 78, DETAILS_PANEL_Y + 25,
                trim(controller.getRowStatusLabel(row), 66), statusColor);
        y += 14;

        graphics.drawString(this.font, trim(controller.getRowMachineLabel(row), textWidth), textX, y, TEXT, false);
        y += 11;
        graphics.drawString(this.font, trim(controller.getRowInfoLabel(row), textWidth), textX, y, DIM, false);
        y += 11;
        graphics.drawString(this.font, trim(controller.getRowLinkLabel(row), textWidth), textX, y, MUTED, false);
        y += 13;

        if (controller.isRowFlywheelLinked(row)) {
            String mode = controller.getBankStrategy() == FactoryLogicControllerBE.BankStrategy.SEQUENTIAL
                    ? " (one at a time)" : " (shared load)";
            graphics.drawString(this.font, "Kinetic bank: " + controller.getBankStrategy().getDisplayName() + mode,
                    textX, y, ACCENT, false);
            y += 12;
        }

        y = this.drawWrappedText(graphics, Component.literal(controller.getRowDetailLabel(row)), textX, y, textWidth, statusColor);

        long ticksAgo = controller.getLastActivityTicksAgo(row);
        if (ticksAgo >= 0) {
            graphics.drawString(this.font, "Last active: " + (ticksAgo / 20L) + "s ago", textX, y + 2, DIM, false);
            y += 11;
        }

        if (runtime.isManualStopLatched()) {
            graphics.drawString(this.font, "Manual latch engaged", textX, y + 2, COUNTDOWN, false);
        } else if (runtime.isControllerHeldStopped()) {
            graphics.drawString(this.font, "Controller is holding this branch", textX, y + 2, STOPPED, false);
        }
    }

    private void renderHelpPanel(GuiGraphics graphics, FactoryLogicControllerBE controller, int row) {
        int textX = RIGHT_PANEL_X + 10;
        int textWidth = RIGHT_PANEL_WIDTH - 20;
        int y = HELP_PANEL_Y + 28;

        if (row >= 0) {
            RuleRuntime runtime = controller.getRuntime(row);
            graphics.drawString(this.font, "Operator note", textX, y, ACCENT, false);
            y = this.drawWrappedText(graphics,
                    Component.literal(this.getOperatorNote(runtime.getStatus(), runtime)),
                    textX, y + 12, textWidth, DIM);
            y += 8;
        }

        graphics.drawString(this.font, "Setup flow", textX, y, ACCENT, false);
        y = this.drawWrappedText(graphics,
                Component.literal("1. Teach the machine card with a sneak-right-click on the target machine."),
                textX, y + 12, textWidth, DIM);
        y = this.drawWrappedText(graphics,
                Component.literal("2. Mark the watched area with two corners, then insert the card in a bay."),
                textX, y + 4, textWidth, DIM);
        y = this.drawWrappedText(graphics,
                Component.literal("3. Link the card to a clutch, place both cards, then use Hold or Run."),
                textX, y + 4, textWidth, DIM);
        this.drawWrappedText(graphics,
                Component.literal("Flywheel: link the card to a Kinetic Flywheel instead; it stands alone. Use the Bank toggle up top to coordinate several on one network."),
                textX, y + 4, textWidth, MUTED);
    }

    private void drawStatusPill(GuiGraphics graphics, int x, int y, String text, int accentColor) {
        int width = Math.max(48, this.font.width(text) + 10);
        graphics.fill(x, y, x + width, y + 12, 0xAA12181F);
        this.border(graphics, x, y, width, 12, accentColor);
        graphics.drawCenteredString(this.font, text, x + width / 2, y + 2, accentColor);
    }

    private String getOperatorNote(RuleStatus status, RuleRuntime runtime) {
        return switch (status) {
            case RUNNING -> "Fresh activity is keeping this branch enabled.";
            case COUNTDOWN -> "This branch is idling inside the grace window before the timeout trips.";
            case STOPPED -> runtime.isManualStopLatched()
                    ? "This bay is manually latched off. Use Run to return control to automation."
                    : "The branch is currently held. New work or a manual Run will bring it back.";
            case LINK_ERROR -> "The linked clutch cannot be reached. Re-link the clutch card or check the target.";
            case AREA_ERROR -> "The watched area is invalid in this dimension or exceeds the scan limit.";
            case NO_MACHINES -> runtime.isControllerHeldStopped()
                    ? "No matching machines were found and the controller is holding the branch stopped."
                    : "The watched area is clear. The branch will stay ready until machines return.";
            case INCOMPLETE -> "This bay still needs its machine card and clutch link configured.";
        };
    }

    private int resolveFocusedRow(FactoryLogicControllerBE controller) {
        if (this.lastHoveredRow >= 0 && this.lastHoveredRow < this.visibleRowCount) {
            return this.lastHoveredRow;
        }
        if (this.selectedRow >= 0 && this.selectedRow < this.visibleRowCount) {
            return this.selectedRow;
        }
        for (int row = 0; row < this.visibleRowCount; row++) {
            if (controller.isRowUsed(row)) {
                return row;
            }
        }
        return this.visibleRowCount > 0 ? 0 : -1;
    }

    private void updateRowButtonStates() {
        if (this.rowControlButtons == null) {
            return;
        }
        FactoryLogicControllerBE controller = this.menu.getController();
        for (int row = 0; row < this.visibleRowCount && row < this.rowControlButtons.length; row++) {
            Button[] buttons = this.rowControlButtons[row];
            if (buttons == null) {
                continue;
            }
            boolean hasMachineCard = !controller.getMachineCardStack(row).isEmpty();
            buttons[0].active = hasMachineCard;
            buttons[1].active = hasMachineCard;
            buttons[2].active = controller.canManualPause(row);
            buttons[3].active = controller.canManualReset(row);
        }
        if (this.strategyButton != null) {
            boolean show = controller.hasFlywheelRows();
            this.strategyButton.visible = show;
            this.strategyButton.active = show;
            this.strategyButton.setMessage(Component.literal("Bank: " + controller.getBankStrategy().getDisplayName()));
        }
        boolean smart = controller.hasFlywheelRows()
                && controller.getBankStrategy() == FactoryLogicControllerBE.BankStrategy.SMART;
        if (this.rpmDownButton != null) {
            this.rpmDownButton.visible = smart;
            this.rpmDownButton.active = smart;
        }
        if (this.rpmUpButton != null) {
            this.rpmUpButton.visible = smart;
            this.rpmUpButton.active = smart;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double adjustedX = this.renderScale < 1.0f ? mouseX / this.renderScale : mouseX;
        double adjustedY = this.renderScale < 1.0f ? mouseY / this.renderScale : mouseY;
        if (button == 0) {
            int hovered = this.getHoveredRow((int) Math.round(adjustedX), (int) Math.round(adjustedY));
            if (hovered >= 0) {
                this.selectedRow = hovered;
            }
        }
        return super.mouseClicked(adjustedX, adjustedY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.renderScale < 1.0f) {
            return super.mouseDragged(mouseX / this.renderScale, mouseY / this.renderScale,
                    button, dragX / this.renderScale, dragY / this.renderScale);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.renderScale < 1.0f) {
            return super.mouseReleased(mouseX / this.renderScale, mouseY / this.renderScale, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int getHoveredRow(int mouseX, int mouseY) {
        int left = this.leftPos + LEFT_PANEL_X + 6;
        int right = this.leftPos + LEFT_PANEL_X + LEFT_PANEL_WIDTH - 6;
        if (mouseX < left || mouseX >= right) {
            return -1;
        }
        for (int row = 0; row < this.visibleRowCount; row++) {
            int rowTop = this.topPos + FactoryLogicControllerMenu.ROW_START_Y + row * FactoryLogicControllerMenu.ROW_HEIGHT;
            int rowBottom = rowTop + FactoryLogicControllerMenu.ROW_HEIGHT - 2;
            if (mouseY >= rowTop && mouseY < rowBottom) {
                return row;
            }
        }
        return -1;
    }

    private int getStatusColor(RuleStatus status) {
        return switch (status) {
            case RUNNING -> RUNNING;
            case COUNTDOWN -> COUNTDOWN;
            case STOPPED -> STOPPED;
            case LINK_ERROR, AREA_ERROR -> ERROR;
            case NO_MACHINES -> IDLE;
            case INCOMPLETE -> DIM;
        };
    }

    private int drawWrappedText(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : this.font.split(text, maxWidth)) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private String trim(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        int suffixWidth = this.font.width("..");
        if (maxWidth <= suffixWidth) {
            return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth));
        }
        return this.font.plainSubstrByWidth(text, maxWidth - suffixWidth) + "..";
    }

    private enum ButtonStyle {
        SMALL(0xFF2A3440, 0xFF36424E, 0xFF7B8895, 0xFFF2E1C4),
        HOLD(0xFF5A302A, 0xFF734039, 0xFFF0856A, 0xFFFCE7DD),
        RUN(0xFF284734, 0xFF315A42, 0xFF78D39B, 0xFFE8FFF2);

        private final int backgroundColor;
        private final int hoverBackgroundColor;
        private final int borderColor;
        private final int textColor;

        ButtonStyle(int backgroundColor, int hoverBackgroundColor, int borderColor, int textColor) {
            this.backgroundColor = backgroundColor;
            this.hoverBackgroundColor = hoverBackgroundColor;
            this.borderColor = borderColor;
            this.textColor = textColor;
        }
    }

    private static class ConsoleButton extends Button {

        private final ButtonStyle style;

        private ConsoleButton(int x, int y, int width, int height, Component message, OnPress onPress, ButtonStyle style) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int backgroundColor = this.active
                    ? (this.isHoveredOrFocused() ? this.style.hoverBackgroundColor : this.style.backgroundColor)
                    : 0xFF20252B;
            int borderColor = this.active ? this.style.borderColor : 0xFF4E565E;
            int textColor = this.active ? this.style.textColor : 0xFF8D949A;

            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, backgroundColor);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
            graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
            graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
            graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + 2, 0x22FFFFFF);

            graphics.drawCenteredString(minecraft.font, this.getMessage(),
                    this.getX() + this.width / 2, this.getY() + 4, textColor);
        }
    }

    private static int getWindowHeight(int visibleRows) {
        return Math.max(MIN_WINDOW_HEIGHT,
                FactoryLogicControllerMenu.ROW_START_Y + visibleRows * FactoryLogicControllerMenu.ROW_HEIGHT + 14);
    }
}
