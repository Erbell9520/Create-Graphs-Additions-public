package com.createstats.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

class InkButton extends AbstractButton {
    enum Style {
        PRIMARY,
        NORMAL,
        QUIET
    }

    private final Runnable onPress;
    private final Style style;
    private int accent;
    private float hoverAnim;
    private float pressAnim;
    private long lastFrame = Long.MIN_VALUE;

    InkButton(int x, int y, int width, int height, Component label, Style style, Runnable onPress) {
        super(x, y, width, height, label);
        this.style = style;
        this.onPress = onPress;
        this.accent = UiFx.BRASS;
    }

    static InkButton of(int x, int y, int w, int h, String label, Runnable onPress) {
        return new InkButton(x, y, w, h, Component.literal(label), Style.NORMAL, onPress);
    }

    static InkButton primary(int x, int y, int w, int h, String label, Runnable onPress) {
        return new InkButton(x, y, w, h, Component.literal(label), Style.PRIMARY, onPress);
    }

    static InkButton quiet(int x, int y, int w, int h, String label, Runnable onPress) {
        return new InkButton(x, y, w, h, Component.literal(label), Style.QUIET, onPress);
    }

    InkButton accent(int color) {
        this.accent = color;
        return this;
    }

    @Override
    public void onPress() {
        this.pressAnim = 1.0f;
        this.onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = this.lastFrame == Long.MIN_VALUE ? 0f
                : Math.min(4f, (now - this.lastFrame) / 50_000_000.0f);
        this.lastFrame = now;
        boolean hot = this.isHoveredOrFocused() && this.active;
        this.hoverAnim = UiFx.approach(this.hoverAnim, hot ? 1f : 0f, dt, 0.55f);
        this.pressAnim = Math.max(0f, this.pressAnim - dt * 0.14f);

        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        int sink = this.pressAnim > 0.55f ? 1 : 0;

        int face;
        int ink;
        if (!this.active) {
            face = UiFx.PAGE_LO;
            ink = UiFx.INK_FAINT;
        } else if (this.style == Style.PRIMARY) {
            face = UiFx.lerpColor(this.accent, UiFx.BRASS_HI, this.hoverAnim * 0.55f);
            ink = UiFx.INK;
        } else {
            face = UiFx.lerpColor(UiFx.PAGE_HI, 0xFFFFF8E8, this.hoverAnim);
            ink = UiFx.INK;
        }

        if (this.style == Style.QUIET) {
            if (this.hoverAnim > 0.01f) {
                g.fill(x, y + sink, x + w, y + h + sink,
                        UiFx.multiplyAlpha(0xFFFFFFFF, this.hoverAnim * 0.55f));
                UiFx.outline(g, x, y + sink, w, h,
                        UiFx.multiplyAlpha(UiFx.BRASS, this.hoverAnim));
            } else {
                UiFx.outline(g, x, y, w, h, UiFx.RULE);
            }
        } else {
            UiFx.raised(g, x, y + sink, w, h, face);
            UiFx.outline(g, x, y + sink, w, h,
                    this.active ? UiFx.lerpColor(UiFx.BRASS_LO, UiFx.BRASS,
                            this.style == Style.PRIMARY ? 1f : this.hoverAnim)
                            : UiFx.PAGE_SUNK);
        }
        if (!this.active) {
            UiFx.hatch(g, x + 1, y + 1, w - 2, h - 2, 0x14000000, 4);
        }

        var font = net.minecraft.client.Minecraft.getInstance().font;

        String full = this.getMessage().getString();
        String label = full;
        if (font.width(label) > w - 6) {
            label = font.plainSubstrByWidth(full, Math.max(0, w - 6 - font.width("\u2026"))) + "\u2026";
        }
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + (h - 8) / 2 + sink, ink, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
