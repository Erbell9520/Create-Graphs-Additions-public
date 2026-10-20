package com.createstats.client;

import com.createstats.client.hud.HudAnim;
import com.createstats.display.DisplayTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PinnedHudControlButton extends AbstractButton {
    private final Supplier<DisplayTheme> themeSupplier;
    private final Runnable action;

    private float hoverProgress;
    private float pressProgress;
    private long firstRenderMs = -1L;

    public PinnedHudControlButton(int x, int y, int width, int height,
                                  Supplier<DisplayTheme> themeSupplier,
                                  Component message,
                                  Runnable action) {
        super(x, y, width, height, message);
        this.themeSupplier = themeSupplier;
        this.action = action;
    }

    @Override
    public void onPress() {
        if (this.action != null) {
            this.action.run();
        }
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final DisplayTheme theme = PinnedHudControlChrome.resolveTheme(this.themeSupplier);
        final long now = System.currentTimeMillis();
        if (this.firstRenderMs < 0L) {
            this.firstRenderMs = now;
        }

        final boolean hovered = this.isHoveredOrFocused();
        final boolean pressed = hovered && Minecraft.getInstance().mouseHandler.isLeftPressed();
        final float dt = Math.min(0.08f, partialTick / 20.0f) + 0.016f;
        this.hoverProgress = HudAnim.approach(this.hoverProgress, hovered ? 1.0f : 0.0f, dt * 6.0f);
        this.pressProgress = HudAnim.approach(this.pressProgress, pressed ? 1.0f : 0.0f, dt * 10.0f);

        final int x = this.getX();
        final int y = this.getY() + Math.round(this.pressProgress);
        final int w = this.getWidth();
        final int h = this.getHeight();
        PinnedHudControlChrome.drawChrome(g, theme, x, y, w, h, this.active, this.hoverProgress, this.pressProgress, now - this.firstRenderMs);
        PinnedHudControlChrome.drawLabel(g, theme, x, y, w, h, this.getMessage().getString(), this.active, hovered);
    }
}

final class PinnedHudOpacitySlider extends AbstractSliderButton {
    private final Supplier<DisplayTheme> themeSupplier;
    private final float min;
    private final float max;
    private final Consumer<Float> onChange;
    private float hoverProgress;
    private float pressProgress;
    private long firstRenderMs = -1L;

    PinnedHudOpacitySlider(int x, int y, int width, int height,
                           Supplier<DisplayTheme> themeSupplier,
                           float initial,
                           float min,
                           float max,
                           Consumer<Float> onChange) {
        super(x, y, width, height, Component.empty(), Mth.clamp((initial - min) / Math.max(0.0001f, max - min), 0.0f, 1.0f));
        this.themeSupplier = themeSupplier;
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        final int pct = Math.round(this.currentValue() * 100.0f);
        this.setMessage(Component.literal(String.format(Locale.ROOT, "Opacity %d%%", pct)));
    }

    @Override
    protected void applyValue() {
        if (this.onChange != null) {
            this.onChange.accept(this.currentValue());
        }
    }

    private float currentValue() {
        return this.min + (this.max - this.min) * (float) this.value;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final DisplayTheme theme = PinnedHudControlChrome.resolveTheme(this.themeSupplier);
        final long now = System.currentTimeMillis();
        if (this.firstRenderMs < 0L) {
            this.firstRenderMs = now;
        }

        final boolean hovered = this.isHoveredOrFocused();
        final boolean pressed = hovered && Minecraft.getInstance().mouseHandler.isLeftPressed();
        final float dt = Math.min(0.08f, partialTick / 20.0f) + 0.016f;
        this.hoverProgress = HudAnim.approach(this.hoverProgress, hovered ? 1.0f : 0.0f, dt * 6.0f);
        this.pressProgress = HudAnim.approach(this.pressProgress, pressed ? 1.0f : 0.0f, dt * 10.0f);

        final int x = this.getX();
        final int y = this.getY() + Math.round(this.pressProgress);
        final int w = this.getWidth();
        final int h = this.getHeight();
        PinnedHudControlChrome.drawChrome(g, theme, x, y, w, h, this.active, this.hoverProgress, this.pressProgress, now - this.firstRenderMs);

        final int labelColor = this.active
                ? PinnedHudControlChrome.withAlpha(hovered ? theme.text : theme.dim, 235)
                : PinnedHudControlChrome.withAlpha(theme.dim, 130);
        g.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), x + w / 2, y + 3, labelColor);

        final int trackX = x + 8;
        final int trackY = y + h - 5;
        final int trackW = Math.max(18, w - 16);
        final int filled = Math.round(trackW * (float) this.value);
        g.fill(trackX, trackY, trackX + trackW, trackY + 2, PinnedHudControlChrome.withAlpha(theme.border, 145));
        if (filled > 0) {
            g.fill(trackX, trackY, trackX + filled, trackY + 2, PinnedHudControlChrome.withAlpha(theme.accent, 215));
        }
        final int knobX = trackX + filled;
        final float pulse = 0.55f + 0.45f * HudAnim.pulse(now, 1100L);
        final int glow = PinnedHudControlChrome.withAlpha(theme.accent, Math.round(60 + 70 * pulse * (0.35f + this.hoverProgress * 0.65f)));
        g.fill(knobX - 4, trackY - 3, knobX + 5, trackY + 5, glow);
        g.fill(knobX - 2, trackY - 1, knobX + 3, trackY + 3, PinnedHudControlChrome.withAlpha(theme.tabSel, 240));
        g.fill(knobX - 1, trackY, knobX + 2, trackY + 2, 0xFFFFFFFF);
    }
}

final class PinnedHudControlChrome {
    private PinnedHudControlChrome() {
    }

    static DisplayTheme resolveTheme(Supplier<DisplayTheme> supplier) {
        if (supplier != null) {
            try {
                final DisplayTheme theme = supplier.get();
                if (theme != null) {
                    return theme;
                }
            } catch (Throwable ignored) {
            }
        }
        return DisplayTheme.DARK;
    }

    static void drawChrome(GuiGraphics g, DisplayTheme theme, int x, int y, int w, int h,
                           boolean active, float hover, float press, long elapsedMs) {
        final int border = withAlpha(active ? theme.border : theme.dim, active ? 228 : 148);
        final int top = withAlpha(theme.tabBg, active ? 244 : 214);
        final int bottom = withAlpha(theme.bg, active ? 244 : 214);
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);

        final int accentRail = withAlpha(theme.accent, active ? 190 : 120);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, accentRail);

        final int topHighlight = withAlpha(HudAnim.blendArgb(theme.text, theme.tabSel, 0.35f), Math.round(60 + 90 * hover));
        g.fill(x + 3, y + 1, x + w - 1, y + 2, topHighlight);

        if (hover > 0.01f) {
            final int glow = withAlpha(theme.accent, Math.round(90 + 70 * hover * (1.0f - press * 0.4f)));
            g.fill(x + 3, y + 2, x + w - 1, y + 3, glow);
            g.fill(x + 3, y + h - 3, x + w - 1, y + h - 2, glow);
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, glow);
        }

        final int shimmerOffset = (int) ((elapsedMs % 2200L) * (w + 18L) / 2200L) - 9;
        final int shimmerX0 = Math.max(x + 4, x + shimmerOffset);
        final int shimmerX1 = Math.min(x + w - 3, shimmerX0 + 10);
        if (shimmerX1 > shimmerX0) {
            g.fill(shimmerX0, y + 1, shimmerX1, y + 2, withAlpha(0xFFFFFFFF, Math.round(32 + 30 * hover)));
        }
    }

    static void drawLabel(GuiGraphics g, DisplayTheme theme, int x, int y, int w, int h, String label, boolean active, boolean hovered) {
        final int color = active
                ? withAlpha(hovered ? theme.text : theme.dim, hovered ? 245 : 225)
                : withAlpha(theme.dim, 130);
        g.drawCenteredString(Minecraft.getInstance().font, label, x + w / 2, y + (h - 8) / 2, color);
    }

    static int withAlpha(int argb, int alpha) {
        alpha = Mth.clamp(alpha, 0, 255);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
