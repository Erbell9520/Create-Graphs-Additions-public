package com.createstats.client.statspower;

import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// shapes stolen from real operator panels, people who had to read them in a hurry settled these
@OnlyIn(Dist.CLIENT)
public final class PowerArt {
    private PowerArt() {
    }

    public static final float CONTINUOUS_LOAD = 0.8f;

    public static int alpha(int argb, int a) {
        return (argb & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int mix(int a, int b, float t) {
        final float f = Mth.clamp(t, 0.0f, 1.0f);
        final int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * f);
        final int ar = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        final int ag = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        final int ab = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    public static int scale(int argb, float f) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = Mth.clamp((int) (((argb >> 16) & 0xFF) * f), 0, 255);
        final int g = Mth.clamp((int) (((argb >> 8) & 0xFF) * f), 0, 255);
        final int b = Mth.clamp((int) ((argb & 0xFF) * f), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int loadTint(float fraction) {
        if (fraction >= 1.0f) {
            return PduTheme.CRIT;
        }
        if (fraction >= CONTINUOUS_LOAD) {
            return mix(PduTheme.WARN, PduTheme.CRIT, (fraction - CONTINUOUS_LOAD) / (1.0f - CONTINUOUS_LOAD));
        }
        if (fraction >= 0.55f) {
            return mix(PduTheme.OK, PduTheme.WARN, (fraction - 0.55f) / (CONTINUOUS_LOAD - 0.55f));
        }
        return PduTheme.OK;
    }

    public static int chargeTint(float pct) {
        if (pct <= 10.0f) {
            return PduTheme.CRIT;
        }
        if (pct <= 35.0f) {
            return mix(PduTheme.CRIT, PduTheme.WARN, (pct - 10.0f) / 25.0f);
        }
        if (pct <= 60.0f) {
            return mix(PduTheme.WARN, PduTheme.OK, (pct - 35.0f) / 25.0f);
        }
        return PduTheme.OK;
    }

    public static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int x = x1;
        int y = y1;
        final int dx = Math.abs(x2 - x1);
        final int dy = -Math.abs(y2 - y1);
        final int sx = x1 < x2 ? 1 : -1;
        final int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        for (int guard = 0; guard < 4096; guard++) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) {
                return;
            }
            final int e2 = err << 1;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    public static void dashedH(GuiGraphics g, int x1, int x2, int y, int color, int on, int off) {
        for (int x = x1; x < x2; x += on + off) {
            g.fill(x, y, Math.min(x + on, x2), y + 1, color);
        }
    }

    public static void dashedV(GuiGraphics g, int x, int y1, int y2, int color, int on, int off) {
        for (int y = y1; y < y2; y += on + off) {
            g.fill(x, y, x + 1, Math.min(y + on, y2), color);
        }
    }

    public static void hatch(GuiGraphics g, int x, int y, int w, int h, int color, float phase) {
        if (w <= 0 || h <= 0) {
            return;
        }
        final int spacing = 4;
        final int drift = Math.floorMod((int) phase, spacing);
        g.enableScissor(x, y, x + w, y + h);
        for (int i = -h - spacing; i < w + spacing; i += spacing) {
            final int sx = x + i + drift;
            line(g, sx, y + h, sx + h, y, color);
        }
        g.disableScissor();
    }

    public static PduRect card(GuiGraphics g, PduRect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_PANEL);
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), PduTheme.STROKE);
        return r.inset(PduTheme.BASE + 1);
    }

    public static PduRect cardTitled(GuiGraphics g, Font font, PduRect r, String title,
                                     String right, int accent) {
        card(g, r);
        final int pad = PduTheme.BASE + 1;
        final int ty = r.y() + pad;
        g.fill(r.x() + pad, ty + 1, r.x() + pad + 2, ty + 8, accent);
        g.drawString(font, title, r.x() + pad + 6, ty, PduTheme.TEXT_DIM, false);

        final int titleEnd = r.x() + pad + 6 + font.width(title);
        if (right != null && !right.isEmpty()
                && r.right() - pad - font.width(right) > titleEnd + 6) {
            g.drawString(font, right, r.right() - pad - font.width(right), ty, PduTheme.TEXT_FAINT, false);
        }
        final int headerBottom = ty + 11;
        g.fill(r.x() + pad, headerBottom, r.right() - pad, headerBottom + 1,
                alpha(PduTheme.STROKE, 0x99));
        return new PduRect(r.x() + pad, headerBottom + 4,
                Math.max(0, r.w() - pad * 2), Math.max(0, r.bottom() - pad - headerBottom - 4));
    }

    public static int chip(GuiGraphics g, Font font, int x, int y, String text, int fg, int bg) {
        final int w = font.width(text) + 8;
        g.fill(x, y, x + w, y + 11, bg);
        PduTheme.outline(g, x, y, w, 11, alpha(fg, 0x66));
        g.drawString(font, text, x + 4, y + 2, fg, false);
        return w;
    }

    public static void pips(GuiGraphics g, int x, int y, int filled, int total, int on, int off) {
        final int max = Math.min(total, 24);
        for (int i = 0; i < max; i++) {
            final int px = x + i * 5;
            g.fill(px, y, px + 3, y + 6, i < filled ? on : off);
        }
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int tint) {
        g.fill(x, y, x + w, y + h, PduTheme.BG_DEEP);
        final int fill = Math.round(w * Mth.clamp(fraction, 0.0f, 1.0f));
        if (fill > 0) {
            g.fillGradient(x, y, x + fill, y + h, scale(tint, 1.15f), scale(tint, 0.75f));
        }
        PduTheme.outline(g, x, y, w, h, alpha(PduTheme.STROKE, 0xCC));
    }

    public static void scheduleBar(GuiGraphics g, int x, int y, int w, int h,
                                   float served, float demand, float rating, float phase) {
        g.fill(x, y, x + w, y + h, PduTheme.BG_DEEP);
        final float ref = rating > 0.0f ? rating : Math.max(demand, Math.max(served, 1.0f));
        final float servedFrac = Mth.clamp(served / ref, 0.0f, 1.0f);
        final float demandFrac = Mth.clamp(demand / ref, 0.0f, 1.0f);

        if (demandFrac > servedFrac) {
            final int dx0 = x + Math.round(w * servedFrac);
            final int dx1 = x + Math.round(w * demandFrac);
            g.fill(dx0, y, dx1, y + h, alpha(PduTheme.WARN, 0x2E));
            hatch(g, dx0, y, dx1 - dx0, h, alpha(PduTheme.WARN, 0x70), phase);
        }

        final int fill = Math.round(w * servedFrac);
        final int tint = loadTint(rating > 0.0f ? served / rating : 0.0f);
        if (fill > 0) {
            g.fillGradient(x, y, x + fill, y + h, scale(tint, 1.18f), scale(tint, 0.72f));

            g.fill(x + fill - 1, y, x + fill, y + h, scale(tint, 1.6f));
        }

        if (rating > 0.0f) {
            final int tickX = x + Math.round(w * CONTINUOUS_LOAD);
            dashedV(g, tickX, y, y + h, alpha(PduTheme.TEXT_FAINT, 0xAA), 2, 2);
            if (served > rating) {
                final float over = Mth.clamp((served - rating) / rating, 0.0f, 1.0f);
                final int ox = x + w;
                final int ow = Math.max(2, Math.round(w * 0.22f * over));
                hatch(g, ox, y, ow, h, PduTheme.CRIT, phase * 2.0f);
                PduTheme.outline(g, ox, y, ow, h, PduTheme.CRIT);
            }
        }
        PduTheme.outline(g, x, y, w, h, alpha(PduTheme.STROKE, 0xCC));
    }

    public static void socColumn(GuiGraphics g, Font font, PduRect r, float pct, float chargeRate,
                                 float[] floors, int[] floorTints, float phase) {
        final int x = r.x();
        final int y = r.y();
        final int w = r.w();
        final int h = r.h();
        g.fill(x, y, x + w, y + h, PduTheme.BG_DEEP);

        final float clamped = Mth.clamp(pct, 0.0f, 100.0f);
        final int level = Math.round(h * clamped / 100.0f);
        final int top = y + h - level;
        final int tint = chargeTint(clamped);
        if (level > 0) {
            g.fillGradient(x + 1, top, x + w - 1, y + h, scale(tint, 1.25f), scale(tint, 0.55f));

            final int bands = Math.max(1, level / 6);
            for (int i = 0; i < bands; i++) {
                final float t = (i / (float) bands) + (phase * (chargeRate > 0.5f ? 0.012f : 0.004f));
                final int by = y + h - Math.round(level * (t - Mth.floor(t)));
                if (by > top && by < y + h) {
                    g.fill(x + 1, by, x + w - 1, by + 1, alpha(0xFFFFFF, chargeRate > 0.5f ? 0x2A : 0x16));
                }
            }
            g.fill(x + 1, top, x + w - 1, top + 1, scale(tint, 1.8f));
        }

        for (int p = 10; p < 100; p += 10) {
            final int gy = y + h - Math.round(h * p / 100.0f);
            final int len = p % 25 == 0 ? 5 : 3;
            g.fill(x + w - len - 1, gy, x + w - 1, gy + 1, alpha(PduTheme.TEXT_FAINT, 0x77));
        }

        if (floors != null) {
            for (int i = 0; i < floors.length; i++) {
                final float f = floors[i];
                if (f <= 0.0f || f >= 100.0f) {
                    continue;
                }
                final int fy = y + h - Math.round(h * f / 100.0f);
                final int ft = floorTints != null && i < floorTints.length ? floorTints[i] : PduTheme.WARN;
                dashedH(g, x, x + w, fy, ft, 3, 2);
                g.fill(x - 3, fy - 1, x, fy + 2, ft);
            }
        }
        PduTheme.outline(g, x, y, w, h, PduTheme.STROKE_HOT);

        final String read = PowerFmt.pct0(clamped);
        if (font.width(read) <= w - 2) {
            g.drawString(font, read, x + w / 2 - font.width(read) / 2,
                    y + h / 2 - 4, clamped > 55.0f ? PduTheme.BG_DEEP : PduTheme.TEXT, false);
        }
    }

    public static void dial(GuiGraphics g, Font font, int cx, int cy, int radius, float fraction,
                            String value, String unit, int tint, float bandStart) {
        final float start = (float) Math.toRadians(150.0);
        final float sweep = (float) Math.toRadians(240.0);
        final int steps = Math.max(48, radius * 4);
        for (int i = 0; i <= steps; i++) {
            final float t = i / (float) steps;
            final float a = start + sweep * t;
            final int px = cx + Math.round(Mth.cos(a) * radius);
            final int py = cy + Math.round(Mth.sin(a) * radius);
            final boolean lit = t <= Mth.clamp(fraction, 0.0f, 1.0f);
            final int c = lit ? tint
                    : (bandStart >= 0.0f && t >= bandStart ? alpha(PduTheme.CRIT, 0x55)
                    : alpha(PduTheme.STROKE, 0xEE));
            g.fill(px - 1, py - 1, px + 1, py + 1, c);
            if (lit) {
                final int ix = cx + Math.round(Mth.cos(a) * (radius - 2));
                final int iy = cy + Math.round(Mth.sin(a) * (radius - 2));
                g.fill(ix, iy, ix + 1, iy + 1, scale(tint, 0.7f));
            }
        }

        final float a = start + sweep * Mth.clamp(fraction, 0.0f, 1.0f);
        line(g, cx + Math.round(Mth.cos(a) * 3), cy + Math.round(Mth.sin(a) * 3),
                cx + Math.round(Mth.cos(a) * (radius - 4)), cy + Math.round(Mth.sin(a) * (radius - 4)),
                scale(tint, 1.4f));
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, PduTheme.TEXT_DIM);

        if (value != null) {
            g.drawString(font, value, cx - font.width(value) / 2, cy + radius / 3, PduTheme.TEXT, false);
        }
        if (unit != null) {
            g.drawString(font, unit, cx - font.width(unit) / 2, cy + radius / 3 + 10,
                    PduTheme.TEXT_FAINT, false);
        }
    }

    public static void currentDots(GuiGraphics g, int x1, int y1, int x2, int y2,
                                   float intensity, int tint, float phase, boolean reduceMotion) {
        final float mag = Mth.clamp(intensity, 0.0f, 1.0f);
        if (mag <= 0.001f) {
            return;
        }
        final int span = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (span <= 0) {
            return;
        }
        final int spacing = Math.max(5, Math.round(14 - 8 * mag));
        final float speed = reduceMotion ? 0.0f : (0.12f + 0.55f * mag);
        final float drift = (phase * speed) % spacing;
        for (float d = drift; d < span; d += spacing) {
            final float t = d / span;
            final int px = Math.round(Mth.lerp(t, x1, x2));
            final int py = Math.round(Mth.lerp(t, y1, y2));
            g.fill(px - 1, py - 1, px + 1, py + 1, alpha(scale(tint, 1.5f), 0xFF));
            g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
        }
    }

    public static void breaker(GuiGraphics g, int cx, int cy, boolean closed, boolean tripped,
                               boolean live, int tint) {
        final int r = 5;
        g.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, PduTheme.BG_DEEP);
        PduTheme.outline(g, cx - r, cy - r, r * 2 + 1, r * 2 + 1,
                tripped ? PduTheme.CRIT : (closed ? tint : PduTheme.TEXT_FAINT));
        if (tripped) {
            line(g, cx - 3, cy - 3, cx + 3, cy + 3, PduTheme.CRIT);
            line(g, cx + 3, cy - 3, cx - 3, cy + 3, PduTheme.CRIT);
            return;
        }
        if (closed) {
            g.fill(cx, cy - 4, cx + 1, cy + 5, live ? scale(tint, 1.4f) : tint);
        } else {
            line(g, cx, cy + 4, cx + 4, cy - 3, PduTheme.TEXT_FAINT);
            g.fill(cx, cy + 3, cx + 1, cy + 5, PduTheme.TEXT_FAINT);
        }
    }

    public static void odometer(GuiGraphics g, Font font, int x, int y, double value, int digits,
                                int tint, boolean rolling) {
        final int cellW = font.width("0") + 4;
        final int cellH = 13;
        final long whole = (long) Math.floor(Math.max(0.0, value));
        final float frac = rolling ? (float) (value - whole) : 0.0f;

        for (int i = 0; i < digits; i++) {
            final int place = digits - 1 - i;
            final long divisor = (long) Math.pow(10, place);
            final int digit = (int) ((whole / Math.max(1L, divisor)) % 10);
            final int cx = x + i * (cellW + 1);
            g.fill(cx, y, cx + cellW, y + cellH, PduTheme.BG_DEEP);
            PduTheme.outline(g, cx, y, cellW, cellH, alpha(PduTheme.STROKE, 0xDD));
            g.enableScissor(cx + 1, y + 1, cx + cellW - 1, y + cellH - 1);
            final int offset = place == 0 ? Math.round(frac * (cellH - 2)) : 0;
            g.drawString(font, String.valueOf(digit), cx + 2, y + 3 - offset, tint, false);
            if (offset > 0) {
                g.drawString(font, String.valueOf((digit + 1) % 10), cx + 2,
                        y + 3 - offset + (cellH - 2), tint, false);
            }
            g.disableScissor();

            g.fill(cx + 1, y + 1, cx + cellW - 1, y + 4, alpha(0xFFFFFF, 0x0E));
        }
    }

    public static void plotFrame(GuiGraphics g, PduRect r, int rows) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_DEEP);
        for (int i = 1; i < rows; i++) {
            final int gy = r.y() + r.h() * i / rows;
            g.fill(r.x() + 1, gy, r.right() - 1, gy + 1, alpha(PduTheme.STROKE, 0x88));
        }
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), PduTheme.STROKE);
    }

    public static void loadProfile(GuiGraphics g, PduRect r, float[] served, float[] demand,
                                   float axisMax, int tint, float phase) {
        final int n = served == null ? 0 : served.length;
        if (n < 2 || axisMax <= 0.0f) {
            return;
        }
        final int base = r.bottom() - 1;
        final int top = r.y() + 1;
        final int usable = base - top;
        g.enableScissor(r.x() + 1, top, r.right() - 1, base + 1);

        int prevSx = r.x() + 1;
        int prevSy = base;
        int prevDy = base;
        for (int i = 0; i < n; i++) {
            final int px = r.x() + 1 + Math.round((r.w() - 2) * (i / (float) (n - 1)));
            final int sy = base - Math.round(usable * Mth.clamp(served[i] / axisMax, 0.0f, 1.0f));
            final int dy = demand != null && i < demand.length
                    ? base - Math.round(usable * Mth.clamp(demand[i] / axisMax, 0.0f, 1.0f)) : sy;
            if (i > 0) {
                final int cols = Math.max(1, px - prevSx);
                for (int c = 0; c < cols; c++) {
                    final float t = c / (float) cols;
                    final int cx = prevSx + c;
                    final int cy = Math.round(Mth.lerp(t, prevSy, sy));
                    final int cdy = Math.round(Mth.lerp(t, prevDy, dy));
                    g.fillGradient(cx, cy, cx + 1, base + 1, alpha(tint, 0xB4), alpha(tint, 0x30));
                    if (cdy < cy - 1) {
                        g.fill(cx, cdy, cx + 1, cy, alpha(PduTheme.WARN, 0x26));
                    }
                }
                line(g, prevSx, prevSy, px, sy, scale(tint, 1.5f));
                if (demand != null) {
                    line(g, prevSx, prevDy, px, dy, alpha(PduTheme.WARN, 0xCC));
                }
            }
            prevSx = px;
            prevSy = sy;
            prevDy = dy;
        }

        if (demand != null) {
            for (int i = 1; i < n; i++) {
                if (i >= demand.length) {
                    break;
                }
                if (demand[i] - served[i] <= axisMax * 0.02f) {
                    continue;
                }
                final int px = r.x() + 1 + Math.round((r.w() - 2) * (i / (float) (n - 1)));
                final int sy = base - Math.round(usable * Mth.clamp(served[i] / axisMax, 0.0f, 1.0f));
                final int dy = base - Math.round(usable * Mth.clamp(demand[i] / axisMax, 0.0f, 1.0f));
                if (dy < sy) {
                    hatch(g, px - 1, dy, 2, sy - dy, alpha(PduTheme.WARN, 0x66), phase);
                }
            }
        }
        g.disableScissor();
    }

    // the sign is the message. magnitude alone can't say filling or emptying
    public static void bipolar(GuiGraphics g, PduRect r, float[] data, float axisMax,
                               int upTint, int downTint) {
        final int n = data == null ? 0 : data.length;
        if (n < 2 || axisMax <= 0.0f) {
            return;
        }
        final int mid = r.y() + r.h() / 2;
        final int half = Math.max(1, r.h() / 2 - 1);
        g.enableScissor(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1);
        for (int i = 0; i < n; i++) {
            final int px = r.x() + 1 + Math.round((r.w() - 2) * (i / (float) Math.max(1, n - 1)));
            final float v = Mth.clamp(data[i] / axisMax, -1.0f, 1.0f);
            final int py = mid - Math.round(half * v);
            if (v >= 0.0f) {
                g.fillGradient(px, py, px + 1, mid + 1, alpha(upTint, 0xC8), alpha(upTint, 0x40));
            } else {
                g.fillGradient(px, mid, px + 1, py + 1, alpha(downTint, 0x40), alpha(downTint, 0xC8));
            }
            g.fill(px, py, px + 1, py + 1, scale(v >= 0.0f ? upTint : downTint, 1.6f));
        }
        g.disableScissor();

        g.fill(r.x() + 1, mid, r.right() - 1, mid + 1, alpha(PduTheme.TEXT_FAINT, 0xBB));
    }

    public static void sparkline(GuiGraphics g, PduRect r, float[] data, int tint, boolean fill) {
        final int n = data == null ? 0 : data.length;
        if (n < 2) {
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (final float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (max - min < 0.0001f) {
            max = min + 1.0f;
        }
        final int base = r.bottom();
        int prevX = r.x();
        int prevY = base - Math.round(r.h() * (data[0] - min) / (max - min));
        for (int i = 1; i < n; i++) {
            final int px = r.x() + Math.round(r.w() * (i / (float) (n - 1)));
            final int py = base - Math.round(r.h() * (data[i] - min) / (max - min));
            if (fill) {
                for (int x = prevX; x <= px; x++) {
                    final float t = px == prevX ? 0.0f : (x - prevX) / (float) (px - prevX);
                    final int y = Math.round(Mth.lerp(t, prevY, py));
                    g.fill(x, y, x + 1, base, alpha(tint, 0x33));
                }
            }
            line(g, prevX, prevY, px, py, tint);
            prevX = px;
            prevY = py;
        }
    }

    public static void timeAxis(GuiGraphics g, Font font, PduRect r, int spanSeconds, String note) {
        final String left = "-" + PowerFmt.ago(spanSeconds);
        g.drawString(font, left, r.x(), r.bottom() + 2, PduTheme.TEXT_FAINT, false);
        g.drawString(font, "now", r.right() - font.width("now"), r.bottom() + 2,
                PduTheme.TEXT_FAINT, false);
        if (note != null && !note.isEmpty()) {
            g.drawString(font, note, r.centerX() - font.width(note) / 2, r.bottom() + 2,
                    PduTheme.TEXT_FAINT, false);
        }
    }

    public static void valueAxis(GuiGraphics g, Font font, PduRect r, float axisMax, String unit) {
        g.drawString(font, PowerFmt.compact(axisMax), r.right() + 3, r.y(), PduTheme.TEXT_FAINT, false);
        if (r.h() >= 34) {
            g.drawString(font, PowerFmt.compact(axisMax * 0.5f), r.right() + 3,
                    r.centerY() - 4, PduTheme.TEXT_FAINT, false);
        }
        if (unit != null && !unit.isEmpty()) {
            g.drawString(font, unit, r.right() + 3, r.bottom() - 8, alpha(PduTheme.TEXT_FAINT, 0x99),
                    false);
        }
    }

    public static void kpi(GuiGraphics g, Font font, PduRect r, String label, String value,
                           String unit, int tint, float[] history, float delta) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_PANEL);
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), PduTheme.STROKE);

        g.fillGradient(r.x(), r.y() + 1, r.x() + 2, r.bottom() - 1, scale(tint, 1.3f), scale(tint, 0.6f));

        final int pad = 5;
        g.drawString(font, label, r.x() + pad, r.y() + 4, PduTheme.TEXT_DIM, false);

        if (history != null && history.length > 1 && r.h() >= 34) {
            final PduRect spark = new PduRect(r.x() + pad, r.bottom() - 13,
                    Math.max(6, r.w() - pad * 2), 9);
            sparkline(g, spark, history, alpha(tint, 0xAA), true);
        }

        final int valueY = r.y() + Math.max(15, r.h() / 2 - 7);
        g.drawString(font, value, r.x() + pad, valueY, PduTheme.TEXT, false);
        final int unitX = r.x() + pad + font.width(value) + 3;
        if (unit != null && !unit.isEmpty() && unitX + font.width(unit) < r.right() - pad) {
            g.drawString(font, unit, unitX, valueY, PduTheme.TEXT_FAINT, false);
        }

        if (!Float.isNaN(delta) && Math.abs(delta) > 0.0001f && r.w() >= 70) {
            final boolean up = delta > 0.0f;
            final String d = (up ? "\u25b2 " : "\u25bc ") + compactDelta(delta);
            final int dc = up ? PduTheme.OK : PduTheme.CRIT;
            final int dx = r.right() - pad - font.width(d);
            if (dx > r.x() + pad + font.width(label)) {
                g.drawString(font, d, dx, r.y() + 4, alpha(dc, 0xDD), false);
            }
        }
    }

    private static String compactDelta(float delta) {
        return PowerFmt.compact(Math.abs(delta));
    }

    public static void balanceBar(GuiGraphics g, Font font, PduRect r, float served, float shed,
                                  float generation, float budget, float ceiling, float phase) {
        final float span = niceMax(Math.max(Math.max(served + shed, generation),
                Math.max(budget, ceiling)) * 1.05f);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_DEEP);

        final int w = r.w() - 2;
        final int servedW = Math.round(w * Mth.clamp(served / span, 0.0f, 1.0f));
        final int shedW = Math.round(w * Mth.clamp(shed / span, 0.0f, 1.0f));
        final int genW = Math.round(w * Mth.clamp(generation / span, 0.0f, 1.0f));

        if (genW > servedW) {
            g.fill(r.x() + 1 + servedW, r.y() + 1, r.x() + 1 + genW, r.bottom() - 1,
                    alpha(PduTheme.INFO, 0x22));
        }
        if (servedW > 0) {
            g.fillGradient(r.x() + 1, r.y() + 1, r.x() + 1 + servedW, r.bottom() - 1,
                    scale(PduTheme.OK, 1.15f), scale(PduTheme.OK, 0.7f));
        }
        if (shedW > 0) {
            final int sx = r.x() + 1 + servedW;
            g.fill(sx, r.y() + 1, sx + shedW, r.bottom() - 1, alpha(PduTheme.WARN, 0x33));
            hatch(g, sx, r.y() + 1, shedW, r.h() - 2, alpha(PduTheme.WARN, 0x99), phase);
        }

        final int[] taken = new int[6];
        java.util.Arrays.fill(taken, Integer.MIN_VALUE);
        marker(g, font, r, ceiling / span, PduTheme.CRIT, "wiring", taken);
        marker(g, font, r, budget / span, PduTheme.ACCENT, "budget", taken);
        marker(g, font, r, generation / span, PduTheme.INFO, "supply", taken);
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), PduTheme.STROKE);
    }

    private static void marker(GuiGraphics g, Font font, PduRect r, float fraction, int tint,
                               String label, int[] taken) {
        if (fraction <= 0.0f || fraction > 1.0f) {
            return;
        }
        final int mx = r.x() + 1 + Math.round((r.w() - 2) * fraction);
        dashedV(g, mx, r.y(), r.bottom(), tint, 2, 2);
        g.fill(mx - 1, r.y() - 2, mx + 2, r.y() + 1, tint);
        final int lw = font.width(label);
        final int lx = Mth.clamp(mx - lw / 2, r.x(), r.right() - lw);
        for (int i = 0; i < taken.length; i += 2) {
            if (taken[i] == Integer.MIN_VALUE) {
                taken[i] = lx - 3;
                taken[i + 1] = lx + lw + 3;
                g.drawString(font, label, lx, r.bottom() + 2, alpha(tint, 0xCC), false);
                return;
            }
            if (lx < taken[i + 1] && lx + lw > taken[i]) {
                return;
            }
        }
    }

    public static float[] tail(com.createstats.data.TimeSeriesStore store, int want) {
        if (store == null || store.getSampleCount() <= 0) {
            return new float[0];
        }
        final float[] all = store.getData();
        if (all.length <= want) {
            return all;
        }
        final float[] out = new float[want];
        System.arraycopy(all, all.length - want, out, 0, want);
        return out;
    }

    private static final double[] LADDER = {1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0};

    public static float niceMax(float raw) {
        if (raw <= 0.0f) {
            return 1.0f;
        }
        final double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        final double normalised = raw / magnitude;

        for (final double step : LADDER) {
            if (normalised <= step + 1.0e-9) {
                return (float) (step * magnitude);
            }
        }
        return (float) (10.0 * magnitude);
    }

    public static float peak(float[]... series) {
        float max = 0.0f;
        for (final float[] s : series) {
            if (s == null) {
                continue;
            }
            for (final float v : s) {
                max = Math.max(max, Math.abs(v));
            }
        }
        return max;
    }
}
