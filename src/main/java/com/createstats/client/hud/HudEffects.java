package com.createstats.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class HudEffects {
    private HudEffects() {}

    public static void conveyorTickBar(GuiGraphics g, int x, int y, int w, int h,
                                       double gameTime, float throughputRate,
                                       int trackColor, int tickColor, int spacing) {
        if (w <= 2 || h <= 2 || spacing < 4) return;
        g.fill(x, y, x + w, y + h, trackColor);

        final float speed = Math.max(0.05f, Math.min(8f, throughputRate));
        final float phase = (float) (((gameTime * speed) % spacing + spacing) % spacing);
        final int tickW = Math.max(1, spacing / 4);
        for (int dx = (int) -phase; dx < w; dx += spacing) {
            final int tx = x + dx;
            if (tx + tickW <= x || tx >= x + w) continue;
            final int x0 = Math.max(x, tx);
            final int x1 = Math.min(x + w, tx + tickW);
            if (x1 > x0) g.fill(x0, y, x1, y + h, tickColor);
        }
    }

    public static void warningRimFlash(GuiGraphics g, int x, int y, int w, int h,
                                       boolean danger, int alarmColor) {
        if (!danger || w <= 4 || h <= 4) return;
        final float pulse = HudAnim.pulse(System.currentTimeMillis(), 600L);
        final int alpha = (int) (90 + 120 * pulse);
        final int color = HudAnim.withAlpha(alarmColor, alpha);
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static int lampBreathAlpha(boolean danger, long gameTime) {
        if (danger) {
            final long h = (gameTime * 1103515245L + 12345L) >>> 16;
            return 180 + (int) (h & 0x3F);
        }
        return 110 + (int) (60 * HudAnim.pulse(System.currentTimeMillis(), 1400L));
    }

    public static void radarSweep(GuiGraphics g, int cx, int cy, int r,
                                  double gameTime, int periodTicks, int sweepColor) {
        if (r <= 2 || periodTicks <= 0) return;
        final float t = (float) ((gameTime % periodTicks) / (double) periodTicks);
        final float angle = (float) (t * Math.PI * 2.0);

        final float cos = (float) Math.cos(angle);
        final float sin = (float) Math.sin(angle);
        for (int s = 1; s <= r; ++s) {
            final int px = cx + Math.round(cos * s);
            final int py = cy + Math.round(sin * s);
            final float fade = 1f - (s / (float) r);
            final int alpha = (int) (40 + 180 * fade);
            g.fill(px, py, px + 1, py + 1, HudAnim.withAlpha(sweepColor, alpha));
        }
    }

    public static float compassNeedleStep(float currentDeg, float targetDeg, float[] velocity,
                                          float dtSeconds, float halfLifeSeconds) {
        float delta = ((targetDeg - currentDeg + 540f) % 360f) - 180f;
        return HudAnim.spring(currentDeg, currentDeg + delta, velocity, dtSeconds, halfLifeSeconds);
    }

    public static void horizonGlint(GuiGraphics g, int x, int y, int w, int h,
                                    float pitchDeg, float rollDeg, int glintColor) {
        if (w <= 4 || h <= 4) return;
        final float phase = HudAnim.clamp01((pitchDeg + rollDeg + 90f) / 180f);
        final int bandX = x + (int) (phase * (w - 6));
        final int bandW = Math.max(2, w / 16);
        g.fill(bandX, y + 1, bandX + bandW, y + h - 1, HudAnim.withAlpha(glintColor, 80));
    }

    public static char splitFlapDigit(char prevChar, char targetChar, long flipStartMs, long flipDurationMs) {
        if (flipStartMs <= 0L || flipDurationMs <= 0L) return targetChar;
        final long now = System.currentTimeMillis();
        if (now - flipStartMs >= flipDurationMs) return targetChar;
        final float t = (now - flipStartMs) / (float) flipDurationMs;
        final int span = Math.abs(targetChar - prevChar);
        if (span == 0) return targetChar;
        final int step = Math.round(t * span);
        return (char) (prevChar < targetChar ? prevChar + step : prevChar - step);
    }

    public static int signalLampColor(String state, int safeGreen, int alertAmber, int dangerRed) {
        final boolean blink = HudAnim.pulse(System.currentTimeMillis(), 800L) > 0.55f;
        if ("blocked".equalsIgnoreCase(state)) return dangerRed;
        if ("waiting".equalsIgnoreCase(state)) return blink ? alertAmber : HudAnim.withAlpha(alertAmber, 120);
        if ("clear".equalsIgnoreCase(state)) return safeGreen;
        return HudAnim.withAlpha(safeGreen, 90);
    }

    public static void trackPulse(GuiGraphics g, int x, int y, int w, int h,
                                  double gameTime, boolean inbound, int trackColor, int pulseColor) {
        if (w <= 4 || h <= 1) return;
        g.fill(x, y, x + w, y + h, trackColor);
        if (!inbound) return;
        final float period = 24f;
        final float t = (float) ((gameTime % period) / period);
        final int bandW = Math.max(4, w / 6);
        final int bx = x + (int) (t * (w + bandW)) - bandW;
        final int x0 = Math.max(x, bx);
        final int x1 = Math.min(x + w, bx + bandW);
        if (x1 > x0) g.fill(x0, y, x1, y + h, pulseColor);
    }

    public static void heartbeatWave(GuiGraphics g, int x, int y, int w, int h,
                                     double gameTime, float amplitude01, int beatPeriodTicks, int color) {
        if (w <= 4 || h <= 4 || beatPeriodTicks <= 0) return;
        final int midY = y + h / 2;
        final float scrollPx = (float) ((gameTime * 2.0) % w);
        for (int dx = 0; dx < w; ++dx) {
            final float u = ((dx + scrollPx) % beatPeriodTicks) / (float) beatPeriodTicks;

            float v;
            if (u > 0.45f && u < 0.55f) {
                final float local = (u - 0.5f) * 20f;
                v = (1f - Math.abs(local));
                if (local > 0f) v *= -1f;
            } else {
                v = 0.05f * (float) Math.sin(u * Math.PI * 4.0);
            }
            final int py = midY - Math.round(v * amplitude01 * (h / 2 - 2));
            g.fill(x + dx, py, x + dx + 1, py + 1, color);
        }
    }

    public static void expandingRings(GuiGraphics g, int cx, int cy, int maxR,
                                      double gameTime, int ringCount, int periodTicks, int color) {
        if (maxR <= 4 || ringCount <= 0 || periodTicks <= 0) return;
        for (int i = 0; i < ringCount; ++i) {
            final float phase = (float) (((gameTime + (i * (double) periodTicks / ringCount)) % periodTicks) / (double) periodTicks);
            final int r = Math.round(phase * maxR);
            final int alpha = (int) (200 * (1f - phase));
            if (r < 2 || alpha <= 0) continue;
            drawCircleOutline(g, cx, cy, r, HudAnim.withAlpha(color, alpha));
        }
    }

    public static void drawCircleOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        int x = r, y = 0, err = 0;
        while (x >= y) {
            g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
            g.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
            g.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
            g.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
            g.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
            g.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);
            g.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
            g.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
            y += 1;
            err += 1 + 2 * y;
            if (2 * (err - x) + 1 > 0) {
                x -= 1;
                err += 1 - 2 * x;
            }
        }
    }

    public static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy, e2;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    public static void drawCenteredText(GuiGraphics g, Font font, String text, int cx, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        final int tx = cx - font.width(text) / 2;
        g.drawString(font, text, tx, y, color, shadow);
    }
}
