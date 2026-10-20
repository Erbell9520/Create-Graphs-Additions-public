package com.createstats.client;

import com.createstats.display.DisplayTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

public class HudStudioEntryButton extends AbstractButton {
    private static final Component TOOLTIP = Component.literal("Open HUD Studio, design your in-game overlay");

    private final Supplier<DisplayTheme> themeSupplier;
    private final Supplier<Boolean> unsavedSupplier;
    private final Runnable onPress;

    private long firstRenderMs = -1L;
    private float hoverProgress = 0f;
    private float pressProgress = 0f;
    private boolean wasHovered = false;

    public HudStudioEntryButton(int x, int y, int width, int height,
                                Supplier<DisplayTheme> themeSupplier,
                                Supplier<Boolean> unsavedSupplier,
                                Runnable onPress) {
        super(x, y, width, height, Component.literal("HUD"));
        this.themeSupplier = themeSupplier;
        this.unsavedSupplier = unsavedSupplier != null ? unsavedSupplier : () -> Boolean.FALSE;
        this.onPress = onPress;
        this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(TOOLTIP));
    }

    @Override
    public void onPress() {
        if (this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final DisplayTheme theme = resolveTheme();
        final long now = System.currentTimeMillis();
        if (this.firstRenderMs < 0L) {
            this.firstRenderMs = now;
        }

        final boolean hovered = this.isHovered();
        final boolean pressed = hovered && net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed();

        final float dt = Math.min(0.1f, partialTick / 20f) + 0.016f;
        this.hoverProgress = approach(this.hoverProgress, hovered ? 1f : 0f, dt * 6f);
        this.pressProgress = approach(this.pressProgress, pressed ? 1f : 0f, dt * 12f);
        if (hovered && !this.wasHovered) {
        }
        this.wasHovered = hovered;

        final int x = this.getX();
        final int y = this.getY();
        final int w = this.getWidth();
        final int h = this.getHeight();
        final int extend = Math.round(this.hoverProgress * 2f);
        final int sink = Math.round(this.pressProgress * 1f);

        renderTab(g, theme, x - extend, y + sink, w + extend, h, this.hoverProgress, this.pressProgress);
        renderGlyph(g, theme, x - extend, y + sink, w + extend, h, hovered, now);
        renderIdleGlint(g, x - extend, y + sink, w + extend, h, now);

        if (Boolean.TRUE.equals(this.unsavedSupplier.get())) {
            renderUnsavedRivet(g, theme, x - extend + (w + extend) - 3, y + sink - 1, now);
        }
    }

    private DisplayTheme resolveTheme() {
        if (this.themeSupplier != null) {
            try {
                final DisplayTheme t = this.themeSupplier.get();
                if (t != null) return t;
            } catch (Throwable ignored) {
            }
        }
        return DisplayTheme.DARK;
    }

    private static float approach(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return current;
    }

    private static void renderTab(GuiGraphics g, DisplayTheme theme, int x, int y, int w, int h, float hover, float press) {
        final int border = withAlpha(theme.border, 235);
        final int bgTop = withAlpha(theme.tabBg, 245);
        final int bgBot = withAlpha(theme.bg, 245);
        final int notchW = 3;

        g.fillGradient(x + notchW, y + 1, x + w - 1, y + h - 1, bgTop, bgBot);

        g.fill(x + 1, y + 2, x + 1 + notchW, y + h - 2, bgBot);
        g.fill(x, y + 4, x + 1, y + h - 4, bgBot);

        g.fill(x + notchW, y, x + w, y + 1, border);

        g.fill(x + notchW, y + h - 1, x + w, y + h, border);

        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);

        g.fill(x + notchW, y + 1, x + notchW + 1, y + 2, border);
        g.fill(x + 1, y + 2, x + 2, y + 4, border);
        g.fill(x, y + 4, x + 1, y + h - 4, border);
        g.fill(x + 1, y + h - 4, x + 2, y + h - 2, border);
        g.fill(x + notchW, y + h - 2, x + notchW + 1, y + h - 1, border);

        final int highlight = withAlpha(blend(theme.text, theme.tabSel, 0.35f), Math.round(70 + 90 * hover));
        g.fill(x + notchW + 1, y + 1, x + w - 1, y + 2, highlight);

        if (hover > 0.01f) {
            final int glow = withAlpha(theme.accent, Math.round(140 * hover * (1f - press * 0.4f)));
            g.fill(x + notchW + 1, y + 2, x + w - 2, y + 3, glow);
            g.fill(x + notchW + 1, y + h - 3, x + w - 2, y + h - 2, glow);
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, glow);
        }

        if (press > 0.01f) {
            final int shade = withAlpha(0xFF000000, Math.round(60 * press));
            g.fill(x + notchW, y + 1, x + w - 1, y + 2, shade);
        }
    }

    private static void renderGlyph(GuiGraphics g, DisplayTheme theme, int x, int y, int w, int h, boolean hovered, long now) {
        final int cx = x + w / 2;
        final int cy = y + h / 2;
        final int accent = hovered ? brighten(theme.accent, 35) : theme.accent;
        final int dim = withAlpha(theme.text, hovered ? 230 : 190);

        g.fill(cx - 4, cy - 3, cx - 3, cy - 2, dim);
        g.fill(cx - 3, cy - 2, cx - 2, cy - 1, dim);
        g.fill(cx - 2, cy - 1, cx - 1, cy, dim);
        g.fill(cx - 1, cy, cx, cy + 2, dim);

        g.fill(cx + 3, cy - 3, cx + 4, cy - 2, dim);
        g.fill(cx + 2, cy - 2, cx + 3, cy - 1, dim);
        g.fill(cx + 1, cy - 1, cx + 2, cy, dim);
        g.fill(cx, cy, cx + 1, cy + 2, dim);

        g.fill(cx - 1, cy - 4, cx + 1, cy - 3, accent);

        g.fill(cx - 1, cy + 2, cx, cy + 3, accent);
        g.fill(cx, cy + 2, cx + 1, cy + 3, accent);

        final int rx = x + w - 5;
        final int ry = cy - 2;
        g.fill(rx, ry, rx + 4, ry + 1, dim);
        g.fill(rx, ry + 3, rx + 4, ry + 4, dim);
        g.fill(rx, ry, rx + 1, ry + 4, dim);
        g.fill(rx + 3, ry, rx + 4, ry + 4, dim);
        g.fill(rx + 1, ry + 1, rx + 3, ry + 3, withAlpha(accent, hovered ? 220 : 150));
    }

    private void renderIdleGlint(GuiGraphics g, int x, int y, int w, int h, long now) {
        final long elapsed = now - this.firstRenderMs;
        final float phase = (elapsed % 3000L) / 3000f;
        if (phase > 0.6f) return;
        final float pos = phase / 0.6f;
        final int glintX = x + 4 + Math.round(pos * (w - 8));
        final int alpha = Math.round(140 * (1f - Math.abs(pos - 0.5f) * 2f));
        if (alpha <= 0) return;
        g.fill(glintX, y + 1, glintX + 2, y + 2, withAlpha(0xFFFFFFFF, alpha));
    }

    private static void renderUnsavedRivet(GuiGraphics g, DisplayTheme theme, int x, int y, long now) {
        final int amber = 0xFFFFB347;
        final int pulse = (int) ((Math.sin(now / 350.0) + 1.0) * 60.0);
        g.fill(x - 1, y, x + 2, y + 1, amber);
        g.fill(x - 2, y + 1, x + 3, y + 3, amber);
        g.fill(x - 1, y + 3, x + 2, y + 4, amber);
        g.fill(x, y + 1, x + 1, y + 3, withAlpha(0xFFFFFFFF, 200));
        g.fill(x - 2, y, x + 3, y + 4, withAlpha(amber, 40 + pulse));
    }

    private static int withAlpha(int argb, int alpha) {
        alpha = Mth.clamp(alpha, 0, 255);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static int brighten(int argb, int amount) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = Mth.clamp(((argb >> 16) & 0xFF) + amount, 0, 255);
        final int gC = Mth.clamp(((argb >> 8) & 0xFF) + amount, 0, 255);
        final int b = Mth.clamp((argb & 0xFF) + amount, 0, 255);
        return (a << 24) | (r << 16) | (gC << 8) | b;
    }

    private static int blend(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        final int aa = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
        final int ar = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        final int ag = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        final int ab = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }
}
