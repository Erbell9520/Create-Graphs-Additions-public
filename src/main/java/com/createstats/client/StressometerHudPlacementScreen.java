package com.createstats.client;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.StressometerHudState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StressometerHudPlacementScreen extends Screen {
    private static final int HANDLE_SIZE = 10;

    private final StatsDisplayPanelBE panel;
    private final StressometerHudSettingsScreen parentScreen;
    private final StressometerHudState draftState;
    private boolean dragging;
    private boolean resizing;
    private int dragOffsetX;
    private int dragOffsetY;
    private StressometerHudState.Bounds.ResolvedBounds startBounds;
    private int dragStartMouseX;
    private int dragStartMouseY;

    public StressometerHudPlacementScreen(StatsDisplayPanelBE panel, StressometerHudSettingsScreen parentScreen, StressometerHudState draftState) {
        super(Component.literal("Adjust Stressometer HUD"));
        this.panel = panel.getControllerBE();
        this.parentScreen = parentScreen;
        this.draftState = draftState.copy();
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.finish()).bounds(10, 10, 50, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.cancel()).bounds(64, 10, 54, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            this.draftState.resetBoundsToDefault(this.width, this.height);
        }).bounds(122, 10, 50, 18).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        StressometerHudOverlay.renderPreview(g, this.panel, this.draftState);
        super.render(g, mouseX, mouseY, partialTick);
        final StressometerHudState.Bounds.ResolvedBounds bounds = this.draftState.resolveBounds(this.width, this.height);
        this.drawOutline(g, bounds, mouseX, mouseY);
        g.fill(0, this.height - 24, this.width, this.height, 0x78000000);
        g.drawString(this.font, "Drag inside the HUD to move it. Drag the lower-right handle to resize. Esc cancels.", 8, this.height - 17, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        final StressometerHudState.Bounds.ResolvedBounds bounds = this.draftState.resolveBounds(this.width, this.height);
        if (this.isInsideResizeHandle((int) mouseX, (int) mouseY, bounds)) {
            this.resizing = true;
            this.startBounds = bounds;
            this.dragStartMouseX = (int) mouseX;
            this.dragStartMouseY = (int) mouseY;
            return true;
        }
        if (this.isInsideBounds((int) mouseX, (int) mouseY, bounds)) {
            this.dragging = true;
            this.dragOffsetX = (int) mouseX - bounds.x();
            this.dragOffsetY = (int) mouseY - bounds.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging) {
            final StressometerHudState.Bounds.ResolvedBounds bounds = this.draftState.resolveBounds(this.width, this.height);
            this.draftState.setBoundsFromPixels((int) mouseX - this.dragOffsetX, (int) mouseY - this.dragOffsetY, bounds.width(), bounds.height(), this.width, this.height);
            return true;
        }
        if (this.resizing && this.startBounds != null) {
            final int width = this.startBounds.width() + (int) mouseX - this.dragStartMouseX;
            final int height = this.startBounds.height() + (int) mouseY - this.dragStartMouseY;
            this.draftState.setBoundsFromPixels(this.startBounds.x(), this.startBounds.y(), width, height, this.width, this.height);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        this.resizing = false;
        this.startBounds = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void finish() {
        this.parentScreen.applyPlacementDraft(this.draftState);
        this.minecraft.setScreen(this.parentScreen);
    }

    private void cancel() {
        this.minecraft.setScreen(this.parentScreen);
    }

    private boolean isInsideBounds(int mouseX, int mouseY, StressometerHudState.Bounds.ResolvedBounds bounds) {
        return mouseX >= bounds.x() && mouseX < bounds.x() + bounds.width()
                && mouseY >= bounds.y() && mouseY < bounds.y() + bounds.height();
    }

    private boolean isInsideResizeHandle(int mouseX, int mouseY, StressometerHudState.Bounds.ResolvedBounds bounds) {
        return mouseX >= bounds.x() + bounds.width() - HANDLE_SIZE
                && mouseX < bounds.x() + bounds.width() + 2
                && mouseY >= bounds.y() + bounds.height() - HANDLE_SIZE
                && mouseY < bounds.y() + bounds.height() + 2;
    }

    private void drawOutline(GuiGraphics g, StressometerHudState.Bounds.ResolvedBounds bounds, int mouseX, int mouseY) {
        final int borderColor = this.isInsideBounds(mouseX, mouseY, bounds) ? 0xFF33E0A0 : 0xFFD6B84A;
        this.border(g, bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2, borderColor);
        final int handleColor = this.isInsideResizeHandle(mouseX, mouseY, bounds) ? 0xFFFFFFFF : 0xFF33E0A0;
        g.fill(bounds.x() + bounds.width() - HANDLE_SIZE, bounds.y() + bounds.height() - HANDLE_SIZE,
                bounds.x() + bounds.width(), bounds.y() + bounds.height(), handleColor);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
