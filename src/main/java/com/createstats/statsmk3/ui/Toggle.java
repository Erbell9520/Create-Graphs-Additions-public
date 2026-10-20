package com.createstats.statsmk3.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Toggle extends Widget {
    public static final float WIDTH = 30.0f;
    public static final float HEIGHT = 17.0f;

    private final BooleanSupplier value;
    private final Consumer<Boolean> onChange;
    private final Spring knob = Spring.ui(0.0f);

    public Toggle(BooleanSupplier value, Consumer<Boolean> onChange) {
        this.value = value;
        this.onChange = onChange;
        this.knob.jump(value.getAsBoolean() ? 1.0f : 0.0f);
    }

    public boolean on() {
        return this.value.getAsBoolean();
    }

    @Override
    protected void onUpdate(float dt) {
        this.knob.set(on() ? 1.0f : 0.0f).step(dt);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (this.onChange != null) {
            this.onChange.accept(!on());
        }
        return true;
    }

    @Override
    public void draw(PanelSurface ctx) {
        if (!this.visible || this.bounds.isEmpty()) {
            return;
        }
        final float k = this.knob.value();
        final float h = hoverAmount();
        final Rect r = this.bounds;
        final float radius = r.h * 0.5f;

        final int track = Tokens.mix(Tokens.OVERLAY, Tokens.ACCENT_DIM, k);
        final int border = Tokens.mix(
                Tokens.mix(Tokens.LINE, Tokens.LINE_HI, h), Tokens.ACCENT_DEEP, k);
        ctx.roundedRect(r, radius, track);
        ctx.roundedRectOutline(r, radius, 1.0f, border);

        final float inset = 1.5f;
        final float knobD = r.h - inset * 2.0f;
        final float travel = r.w - knobD - inset * 2.0f;
        final float kx = r.x + inset + travel * k;
        ctx.circle(kx + knobD * 0.5f, r.y + r.h * 0.5f, knobD * 0.5f,
                Tokens.mix(Tokens.INK_3, Tokens.ACCENT, k));
    }
}
