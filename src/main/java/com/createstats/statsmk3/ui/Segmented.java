package com.createstats.statsmk3.ui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Segmented extends Widget {
    private final String[] options;
    private final IntSupplier selected;
    private final IntConsumer onChange;
    private final Spring thumb = Spring.ui(0.0f);
    private float scale = Text.META;
    private int hoveredIndex = -1;

    public Segmented(String[] options, IntSupplier selected, IntConsumer onChange) {
        this.options = options == null ? new String[0] : options;
        this.selected = selected;
        this.onChange = onChange;
        this.thumb.jump(selected.getAsInt());
    }

    public Segmented textScale(float s) {
        this.scale = s;
        return this;
    }

    public float measure(PanelSurface ctx, float padPerSide) {
        float total = 6.0f;
        for (final String o : this.options) {
            total += ctx.textWidth(o, this.scale) + padPerSide * 2.0f;
        }
        return total;
    }

    @Override
    protected void onUpdate(float dt) {
        this.thumb.set(this.selected.getAsInt()).step(dt);
    }

    @Override
    public boolean mouseMoved(double mx, double my) {
        final boolean inside = super.mouseMoved(mx, my);
        this.hoveredIndex = inside ? indexAt(mx) : -1;
        return inside;
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        final int i = indexAt(mx);
        if (i >= 0 && this.onChange != null) {
            this.onChange.accept(i);
            return true;
        }
        return false;
    }

    private int indexAt(double mx) {
        if (this.options.length == 0 || this.bounds.w <= 0) {
            return -1;
        }
        final float seg = segWidth();
        final int i = (int) ((mx - this.bounds.x - 3.0f) / seg);
        return i < 0 || i >= this.options.length ? -1 : i;
    }

    private float segWidth() {
        return (this.bounds.w - 6.0f) / Math.max(1, this.options.length);
    }

    @Override
    public void draw(PanelSurface ctx) {
        if (!this.visible || this.bounds.isEmpty() || this.options.length == 0) {
            return;
        }
        ctx.card(this.bounds, Tokens.R_CARD + 1.0f, Tokens.RAISED, Tokens.LINE);

        final float seg = segWidth();
        final float thumbX = this.bounds.x + 3.0f + this.thumb.value() * seg;
        final Rect thumbRect = new Rect(thumbX, this.bounds.y + 3.0f, seg, this.bounds.h - 6.0f);
        ctx.roundedRect(thumbRect, Tokens.R_CHIP + 2.0f, Tokens.OVERLAY);

        final float ty = this.bounds.centerY() - Text.lineHeight(this.scale) * 0.5f;
        for (int i = 0; i < this.options.length; i++) {
            final float cx = this.bounds.x + 3.0f + seg * (i + 0.5f);
            final boolean isSel = i == this.selected.getAsInt();
            final int color = isSel ? Tokens.INK
                    : (i == this.hoveredIndex ? Tokens.INK_2 : Tokens.INK_3);
            ctx.textCenter(this.options[i], cx, ty, color, this.scale);
        }
    }
}
