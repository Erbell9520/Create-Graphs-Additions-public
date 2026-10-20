package com.createstats.statsmk3.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Slider extends Widget {
    public static final float HEIGHT = 14.0f;

    private final Supplier<Float> value;
    private final Consumer<Float> onChange;
    private final Spring knob = Spring.ui(0.0f);
    private boolean dragging;

    public Slider(Supplier<Float> value, Consumer<Float> onChange) {
        this.value = value;
        this.onChange = onChange;
        this.knob.jump(Easing.clamp01(value.get()));
    }

    @Override
    protected void onUpdate(float dt) {
        this.knob.set(Easing.clamp01(this.value.get())).step(dt);
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        this.dragging = true;
        applyAt(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!this.dragging) {
            return false;
        }
        applyAt(mx);
        return true;
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        this.dragging = false;
        return super.mouseUp(mx, my, button);
    }

    private void applyAt(double mx) {
        if (this.bounds.w <= 0 || this.onChange == null) {
            return;
        }
        this.onChange.accept(Easing.clamp01((float) ((mx - this.bounds.x) / this.bounds.w)));
    }

    @Override
    public void draw(PanelSurface ctx) {
        if (!this.visible || this.bounds.isEmpty()) {
            return;
        }
        final float v = this.knob.value();
        final float cy = this.bounds.centerY();
        final float trackH = 3.0f;

        // disabled has to look disabled

        final int fill = this.enabled ? Tokens.ACCENT : Tokens.LINE_HI;
        final int knobColor = this.enabled ? Tokens.ACCENT : Tokens.LINE_HI;

        ctx.roundedRect(new Rect(this.bounds.x, cy - trackH * 0.5f, this.bounds.w, trackH),
                trackH * 0.5f, Tokens.LINE);
        ctx.roundedRect(new Rect(this.bounds.x, cy - trackH * 0.5f, this.bounds.w * v, trackH),
                trackH * 0.5f, fill);

        final float kr = 6.5f + (this.enabled ? hoverAmount() : 0.0f) * 1.0f;
        final float kx = this.bounds.x + this.bounds.w * v;
        ctx.circle(kx, cy, kr + 2.0f, Tokens.CANVAS);
        ctx.circle(kx, cy, kr, knobColor);
        if (!this.enabled) {
            ctx.circle(kx, cy, kr - 2.0f, Tokens.CANVAS);
        }
    }
}
