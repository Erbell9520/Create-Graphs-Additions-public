package com.createstats.statsmk3.ui;

import java.util.function.Consumer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// not EditBox, that needs a live Screen
@OnlyIn(Dist.CLIENT)
public final class TextField extends Widget {
    public static final int MAX_LENGTH = 32;

    private final Consumer<String> onCommit;

    private String text = "";
    private String placeholder = "";
    private int caret;
    private boolean editing;
    private float scale = Text.META;

    private float blink;

    private final Spring glow = Spring.ui(0.0f);

    private int fill = Tokens.CANVAS;
    private int ink = Tokens.INK;
    private int hint = Tokens.INK_3;
    private int edge = Tokens.LINE_HI;
    private int focus = Tokens.ACCENT;

    public TextField(Consumer<String> onCommit) {
        this.onCommit = onCommit;
    }

    public TextField palette(int fill, int ink, int hint, int edge, int focus) {
        this.fill = fill;
        this.ink = ink;
        this.hint = hint;
        this.edge = edge;
        this.focus = focus;
        return this;
    }

    public TextField textScale(float s) {
        this.scale = s;
        return this;
    }

    public TextField placeholder(String s) {
        this.placeholder = s == null ? "" : s;
        return this;
    }

    public boolean editing() {
        return this.editing;
    }

    public String text() {
        return this.text;
    }

    public void begin(String initial) {
        this.text = initial == null ? "" : clampLength(initial);
        this.caret = this.text.length();
        this.editing = true;
        this.blink = 0.0f;
        this.glow.set(1.0f);
    }

    public void setText(String s) {
        if (this.editing) {
            return;
        }
        this.text = s == null ? "" : clampLength(s);
        this.caret = this.text.length();
    }

    public void commit() {
        if (!this.editing) {
            return;
        }
        this.editing = false;
        this.glow.set(0.0f);
        if (this.onCommit != null) {
            this.onCommit.accept(this.text.trim());
        }
    }

    public void cancel() {
        this.editing = false;
        this.glow.set(0.0f);
    }

    @Override
    protected void onUpdate(float dt) {
        this.glow.step(dt);
        if (this.editing) {
            this.blink += dt;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.editing) {
            return false;
        }
        switch (keyCode) {
            case 257, 335 -> commit();
            case 256 -> cancel();
            case 259 -> {
                if (this.caret > 0) {
                    this.text = this.text.substring(0, this.caret - 1) + this.text.substring(this.caret);
                    this.caret--;
                }
            }
            case 261 -> {
                if (this.caret < this.text.length()) {
                    this.text = this.text.substring(0, this.caret) + this.text.substring(this.caret + 1);
                }
            }
            case 263 -> this.caret = Math.max(0, this.caret - 1);
            case 262 -> this.caret = Math.min(this.text.length(), this.caret + 1);
            case 268 -> this.caret = 0;
            case 269 -> this.caret = this.text.length();
            default -> {
                return false;
            }
        }
        this.blink = 0.0f;
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!this.editing || c < ' ' || c == 127 || c == '§') {
            return false;
        }
        if (this.text.length() >= MAX_LENGTH) {
            return true;
        }
        this.text = this.text.substring(0, this.caret) + c + this.text.substring(this.caret);
        this.caret++;
        this.blink = 0.0f;
        return true;
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (!this.editing) {
            begin(this.text);
        }
        if (this.lastSurface != null) {
            placeCaret(this.lastSurface, mx);
        }
        return true;
    }

    public void placeCaret(PanelSurface ctx, double mx) {
        final float left = this.bounds.x + 6.0f;
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i <= this.text.length(); i++) {
            final float x = left + ctx.textWidth(this.text.substring(0, i), this.scale);
            final float d = Math.abs((float) mx - x);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        this.caret = best;
        this.blink = 0.0f;
    }

    private PanelSurface lastSurface;

    @Override
    public void draw(PanelSurface ctx) {
        this.lastSurface = ctx;
        if (!this.visible || this.bounds.isEmpty()) {
            return;
        }
        final float g = this.glow.value();
        ctx.card(this.bounds, Tokens.R_CHIP, this.fill,
                Tokens.mix(this.edge, this.focus, g));

        final Rect inner = this.bounds.inset(6.0f, 0.0f, 6.0f, 0.0f);
        ctx.clip(inner);

        final float caretW = ctx.textWidth(this.text.substring(0, this.caret), this.scale);
        final float offset = Math.max(0.0f, caretW - (inner.w - 8.0f));

        if (this.text.isEmpty() && !this.placeholder.isEmpty()) {
            ctx.textV(this.placeholder, inner.x, inner.centerY(), this.hint, this.scale);
        } else {
            ctx.textV(this.text, inner.x - offset, inner.centerY(), this.ink, this.scale);
        }

        if (this.editing && (this.blink % 1.0f) < 0.55f) {
            final float x = inner.x + caretW - offset;
            Shapes.rect(ctx, x, inner.centerY() - Text.lineHeight(this.scale) * 0.5f - 1.0f,
                    1.0f, Text.lineHeight(this.scale) + 2.0f, this.focus);
        }
        ctx.unclip();
    }

    private static String clampLength(String s) {
        return s.length() <= MAX_LENGTH ? s : s.substring(0, MAX_LENGTH);
    }
}
