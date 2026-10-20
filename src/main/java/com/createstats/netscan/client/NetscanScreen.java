package com.createstats.netscan.client;

import com.createstats.netscan.NetscanEngine;
import com.createstats.netscan.NetscanHost;
import com.createstats.statsmk3.ui.DrawCtx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class NetscanScreen extends Screen {
    private final NetscanHost host;
    private final NetscanShell shell;
    private long lastFrameNanos;

    public NetscanScreen(NetscanHost host) {
        super(Component.translatable("create_stats.netscan.title"));
        this.host = host;
        this.shell = new NetscanShell(
                new ScanContext(Minecraft.getInstance().font, host.scanPos()));
        refresh();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        this.shell.layout(this.width, this.height);
        NetscanClient.beginWatch(this.host.scanPos());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refresh();
        NetscanClient.tickWatch();
        this.shell.update(frameDelta());
        this.shell.hover(mouseX, mouseY);

        final DrawCtx c = new DrawCtx(g, this.font);
        this.shell.draw(c, mouseX, mouseY);
        c.finish();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void refresh() {
        final ScanContext ctx = this.shell.context();
        ctx.tier = this.host.scannerTier();
        ctx.form = this.host.scannerForm();
        final NetscanEngine engine = this.host.scanEngine();
        ctx.graph = engine.graph();
        ctx.history = engine.history();
        ctx.powered = this.host.scanPowered();
        ctx.seedMissing = this.host.scanSeed() == null;
        ctx.sweeping = engine.sweeping();
        ctx.sweepCount = engine.sweepCount();
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
    public void onClose() {
        NetscanClient.endWatch();
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
