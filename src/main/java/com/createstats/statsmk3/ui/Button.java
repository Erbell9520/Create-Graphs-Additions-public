package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Button extends Widget {
    public enum Style {
        PRIMARY,

        SECONDARY,

        GHOST
    }

    private String label;
    private Style style = Style.SECONDARY;
    private Runnable action;
    private float scale = Text.META;

    public Button(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public Button style(Style s) {
        this.style = s;
        return this;
    }

    public Button textScale(float s) {
        this.scale = s;
        return this;
    }

    public Button setLabel(String s) {
        this.label = s;
        return this;
    }

    public String label() {
        return this.label;
    }

    public Button onClick(Runnable r) {
        this.action = r;
        return this;
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button == 0 && this.action != null) {
            this.action.run();
            return true;
        }
        return false;
    }

    @Override
    public void draw(PanelSurface ctx) {
        if (!this.visible || this.bounds.isEmpty()) {
            return;
        }
        final float h = hoverAmount();
        final Rect r = this.bounds.inset(pressAmount() * 0.75f);

        switch (this.style) {
            case PRIMARY -> {
                final int fill = Tokens.mix(Tokens.ACCENT, 0xFFFF7AA5, h);
                ctx.roundedRect(r, Tokens.R_CARD * 0.85f, this.enabled ? fill : Tokens.LINE);
                ctx.textCenter(this.label, r.centerX(),
                        r.centerY() - Text.lineHeight(this.scale) * 0.5f,
                        this.enabled ? 0xFF1A0710 : Tokens.INK_3, this.scale);
            }
            case SECONDARY -> {
                ctx.card(r, Tokens.R_CARD * 0.85f,
                        Tokens.mix(Tokens.RAISED, Tokens.OVERLAY, h),
                        Tokens.mix(Tokens.LINE, Tokens.LINE_HI, h));
                ctx.textCenter(this.label, r.centerX(),
                        r.centerY() - Text.lineHeight(this.scale) * 0.5f,
                        this.enabled ? Tokens.mix(Tokens.INK_2, Tokens.INK, h) : Tokens.INK_3,
                        this.scale);
            }
            case GHOST -> {
                if (h > 0.01f) {
                    ctx.roundedRect(r, Tokens.R_CARD * 0.85f, Tokens.alpha(Tokens.RAISED, h));
                }
                ctx.textCenter(this.label, r.centerX(),
                        r.centerY() - Text.lineHeight(this.scale) * 0.5f,
                        Tokens.mix(Tokens.INK_3, Tokens.INK, h), this.scale);
            }
        }
    }
}
