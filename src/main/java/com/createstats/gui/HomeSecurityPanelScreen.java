package com.createstats.gui;

import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.client.PeerIdentifyHighlight;
import com.createstats.gui.security.SecurityContext;
import com.createstats.gui.security.SecurityShell;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.statsmk3.ui.DrawCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HomeSecurityPanelScreen extends Screen
        implements com.createstats.client.LocalGuiDensity {
    @Override
    public int maxEffectiveGuiScale() {
        return 3;
    }

    private static final int POLL_TICKS = 10;

    private final SecurityShell shell;
    private final net.minecraft.core.BlockPos pos;
    private long lastFrameNanos;
    private int pollCounter;

    public HomeSecurityPanelScreen(HomeSecurityPanelBE panel) {
        super(Component.translatable("block.create_stats.home_security_panel"));
        this.pos = panel.getBlockPos();
        this.shell = new SecurityShell(
                new SecurityContext(panel, Minecraft.getInstance().font));
    }

    public net.minecraft.core.BlockPos target() {
        return this.pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        this.shell.layout(this.width, this.height);

        this.shell.context().poll();
    }

    public void accept(SecuritySyncPacket packet) {
        this.shell.context().accept(packet);
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.pollCounter >= POLL_TICKS) {
            this.pollCounter = 0;
            this.shell.context().poll();
        }
    }

    // empty on purpose. Screen.render already calls it, so a backdrop here painted OVER the panel. 70% black wash, very moody, very wrong
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB4101418);
        this.shell.update(frameDelta());

        final DrawCtx c = new DrawCtx(g, this.font);
        this.shell.draw(c, mouseX, mouseY);
        c.finish();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private float frameDelta() {
        final long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) {
            this.lastFrameNanos = now;
            return 0.0f;
        }
        final float dt = (now - this.lastFrameNanos) / 1.0e9f;
        this.lastFrameNanos = now;

        return Math.min(dt, 0.1f);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return this.shell.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
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
    public boolean charTyped(char codePoint, int modifiers) {
        return this.shell.charTyped(codePoint, modifiers)
                || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        PeerIdentifyHighlight.clear();
        super.onClose();
    }
}
