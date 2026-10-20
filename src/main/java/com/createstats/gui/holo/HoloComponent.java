package com.createstats.gui.holo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class HoloComponent {
    public int x;
    public int y;
    public int w;
    public int h;
    public boolean enabled = true;

    protected final HoloUi.Anim hover = new HoloUi.Anim(0.0f, 120);

    public HoloComponent bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public boolean isOver(double mx, double my) {
        return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
    }

    public void update(int mouseX, int mouseY) {
        this.hover.target(this.enabled && this.isOver(mouseX, mouseY) ? 1.0f : 0.0f);
    }

    public abstract void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha);

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
    }

    public boolean keyPressed(int key, int scancode, int modifiers) {
        return false;
    }

    public boolean charTyped(char c, int modifiers) {
        return false;
    }

    public void loseFocus() {
    }
}
