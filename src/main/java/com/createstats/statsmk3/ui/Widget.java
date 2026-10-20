package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// retained, owns its springs so things can ease
@OnlyIn(Dist.CLIENT)
public abstract class Widget {
    protected Rect bounds = Rect.EMPTY;
    protected boolean visible = true;
    protected boolean enabled = true;

    protected final Spring hover = Spring.ui(0.0f);

    protected final Spring press = Spring.ui(0.0f);

    private boolean hovered;
    private boolean pressed;

    public Rect bounds() {
        return this.bounds;
    }

    public Widget setBounds(Rect r) {
        this.bounds = r == null ? Rect.EMPTY : r;
        return this;
    }

    public boolean visible() {
        return this.visible;
    }

    public Widget setVisible(boolean v) {
        this.visible = v;
        if (!v) {
            this.hovered = false;
            this.pressed = false;
        }
        return this;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public Widget setEnabled(boolean v) {
        this.enabled = v;
        if (!v) {
            this.hovered = false;
            this.pressed = false;
        }
        return this;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public float hoverAmount() {
        return this.hover.value();
    }

    public float pressAmount() {
        return this.press.value();
    }

    public void update(float dt) {
        this.hover.set(this.hovered ? 1.0f : 0.0f).step(dt);
        this.press.set(this.pressed ? 1.0f : 0.0f).step(dt);
        onUpdate(dt);
    }

    protected void onUpdate(float dt) {
    }

    public abstract void draw(PanelSurface ctx);

    public boolean mouseMoved(double mx, double my) {
        final boolean was = this.hovered;
        this.hovered = this.visible && this.enabled && this.bounds.contains(mx, my);
        if (!this.hovered && was) {
            this.pressed = false;
        }
        return this.hovered;
    }

    public boolean mouseDown(double mx, double my, int button) {
        if (!this.visible || !this.enabled || !this.bounds.contains(mx, my)) {
            return false;
        }
        this.pressed = true;
        return onMouseDown(mx, my, button);
    }

    public boolean mouseUp(double mx, double my, int button) {
        final boolean wasPressed = this.pressed;
        this.pressed = false;
        if (!this.visible || !this.enabled) {
            return false;
        }
        if (wasPressed && this.bounds.contains(mx, my)) {
            return onClick(mx, my, button);
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char c, int modifiers) {
        return false;
    }

    protected boolean onMouseDown(double mx, double my, int button) {
        return true;
    }

    protected boolean onClick(double mx, double my, int button) {
        return false;
    }
}
