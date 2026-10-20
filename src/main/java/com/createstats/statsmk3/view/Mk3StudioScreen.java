package com.createstats.statsmk3.view;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.statsmk3.ui.DrawCtx;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Mk3StudioScreen extends Screen {
    private final StudioShell shell;
    private long lastFrameNanos;

    public Mk3StudioScreen(StatsDisplayPanelBE panel) {
        super(Component.literal("Stats Display Mk III"));
        this.shell = new StudioShell(panel,
                new StudioContext(panel, net.minecraft.client.Minecraft.getInstance().font));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        this.shell.layout(this.width, this.height);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.shell.update(frameDelta());
        this.shell.hover(mouseX, mouseY);

        final DrawCtx c = new DrawCtx(g, this.font);
        this.shell.draw(c, mouseX, mouseY);
        c.finish();

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        this.shell.mouseMoved(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return this.shell.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.shell.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return this.shell.mouseDragged(mx, my, button, dx, dy)
                || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return this.shell.mouseScrolled(mx, my, sy) || super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.shell.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return this.shell.charTyped(c, modifiers) || super.charTyped(c, modifiers);
    }

    @Override
    public void onClose() {
        this.shell.close();
        super.onClose();
    }

    private float frameDelta() {
        final long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) {
            this.lastFrameNanos = now;
            return 1.0f / 60.0f;
        }
        final float dt = (now - this.lastFrameNanos) / 1.0e9f;
        this.lastFrameNanos = now;
        return Math.max(0.0f, Math.min(dt, 0.05f));
    }
}
