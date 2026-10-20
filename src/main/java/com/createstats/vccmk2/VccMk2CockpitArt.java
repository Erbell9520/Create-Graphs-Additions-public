package com.createstats.vccmk2;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class VccMk2CockpitArt {
    private VccMk2CockpitArt() {
    }

    static final int GOLD = 0xFFF0CD78;
    static final int GOLD_DK = 0xFF8A6E3C;
    static final int CYAN = 0xFF78E6CD;
    static final int AMBER = 0xFFF5C064;
    static final int GREEN = 0xFF7BD68A;
    static final int RED = 0xFFE07070;
    static final int TXT = 0xFFE2E7ED;
    static final int DIM = 0xFF8A95A0;
    static final int INSTR_BG = 0xFF070B10;
    static final int METAL = 0xFF2A323D;
    static final int METAL_DK = 0xFF1A222C;
    static final int SKY = 0xFF3E72A8;
    static final int GROUND = 0xFF7A5230;

    static int dim(int argb, double f) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int guard = 0;
        while (guard++ < 4096) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    static void lineT(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        line(g, x0, y0, x1, y1, color);
        line(g, x0 + 1, y0, x1 + 1, y1, color);
        line(g, x0, y0 + 1, x1, y1 + 1, color);
    }

    static void ring(GuiGraphics g, int cx, int cy, int r, int color, int seg) {
        for (int i = 0; i < seg; i++) {
            double a = 2 * Math.PI * i / seg;
            int x = (int) Math.round(cx + Math.cos(a) * r);
            int y = (int) Math.round(cy + Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    static void ringEllipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color, int seg) {
        for (int i = 0; i < seg; i++) {
            double a = 2 * Math.PI * i / seg;
            int x = (int) Math.round(cx + Math.cos(a) * rx);
            int y = (int) Math.round(cy + Math.sin(a) * ry);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    static void drawToggle(GuiGraphics g, Font font, int x, int y, int w, int h,
                           String title, boolean on, double anim, int onColor,
                           String onText, String offText, boolean hover, boolean enabled) {
        g.fill(x, y, x + w, y + h, 0xFF12171F);
        g.fill(x, y, x + w, y + 1, dim(GOLD_DK, 1.0));
        renderOutline(g, x, y, w, h, hover && enabled ? onColor : GOLD_DK);
        int titleCol = enabled ? GOLD : DIM;
        g.drawString(font, title, x + (w - font.width(title)) / 2, y + 5, titleCol, false);

        int tw = 20;
        int th = h - 38;
        int tx = x + (w - tw) / 2;
        int ty = y + 18;
        g.fill(tx, ty, tx + tw, ty + th, 0xFF05080C);
        renderOutline(g, tx, ty, tw, th, GOLD_DK);

        g.drawString(font, "I", tx - 7, ty + 1, on ? onColor : DIM, false);
        g.drawString(font, "O", tx - 8, ty + th - 8, on ? DIM : AMBER, false);

        int knobH = 15;
        int travel = th - knobH - 4;
        int ky = ty + 2 + (int) Math.round((1 - clamp01(anim)) * travel);
        int kc = !enabled ? 0xFF3A4250 : on ? onColor : 0xFF6B7480;
        g.fill(tx + 2, ky, tx + tw - 2, ky + knobH, kc);
        g.fill(tx + 2, ky + 2, tx + tw - 2, ky + 3, dim(kc, 1.4 > 1 ? 1.0 : 1.0));
        g.fill(tx + 2, ky + knobH / 2 - 1, tx + tw - 2, ky + knobH / 2 + 1, dim(kc, 0.55));

        int lampC = !enabled ? 0xFF2A323D : on ? onColor : 0xFF3A4250;
        g.fill(x + w - 13, y + 6, x + w - 6, y + 13, lampC);
        renderOutline(g, x + w - 13, y + 6, 7, 7, dim(lampC, 0.6));

        String st = on ? onText : offText;
        int stCol = !enabled ? DIM : on ? onColor : AMBER;
        g.drawString(font, st, x + (w - font.width(st)) / 2, y + h - 11, stCol, false);
    }

    static void drawRotary(GuiGraphics g, Font font, int x, int y, int w, int h,
                           String title, int index, String[] options, int color, boolean hover, boolean enabled) {
        g.fill(x, y, x + w, y + h, 0xFF0E141B);
        renderOutline(g, x, y, w, h, hover && enabled ? color : GOLD_DK);
        g.drawString(font, title, x + 4, y + 3, enabled ? GOLD : DIM, false);
        int n = Math.max(1, options.length);
        int idx = Math.max(0, Math.min(index, n - 1));
        int cx = x + 16;
        int cy = y + h - 16;
        int r = Math.min(h - 24, 13);
        if (r < 5) {
            r = 5;
        }

        ring(g, cx, cy, r + 1, GOLD_DK, 32);
        for (int i = 0; i < r; i++) {
            g.fill(cx - i, cy - 1, cx + i, cy + 1, 0xFF222A34);
        }
        ring(g, cx, cy, r, dim(color, enabled ? 0.9 : 0.5), 32);

        double start = Math.toRadians(150);
        double sweep = Math.toRadians(240);
        for (int i = 0; i < n; i++) {
            double ang = start + (n == 1 ? 0 : sweep * i / (n - 1));
            int tx = (int) Math.round(cx + Math.cos(ang) * (r + 3));
            int ty = (int) Math.round(cy + Math.sin(ang) * (r + 3));
            g.fill(tx, ty, tx + 1, ty + 1, i == idx ? color : DIM);
        }

        double pa = start + (n == 1 ? 0 : sweep * idx / (n - 1));
        int px = (int) Math.round(cx + Math.cos(pa) * (r - 2));
        int py = (int) Math.round(cy + Math.sin(pa) * (r - 2));
        lineT(g, cx, cy, px, py, enabled ? color : DIM);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, GOLD);

        String lbl = options[idx];
        g.drawString(font, lbl, cx + r + 6, cy - 7, enabled ? color : DIM, false);
        g.drawString(font, (idx + 1) + "/" + n, cx + r + 6, cy + 2, DIM, false);
    }

    static void drawAttitude(GuiGraphics g, Font font, int cx, int cy, int r,
                             double pitchDeg, double rollDeg, boolean live) {
        ring(g, cx, cy, r + 3, METAL_DK, 72);
        ring(g, cx, cy, r + 2, GOLD_DK, 72);
        double roll = Math.toRadians(rollDeg);
        double tan = Math.tan(roll);
        double pitchPx = pitchDeg * (r / 35.0);
        for (int x = -r; x <= r; x++) {
            int col = cx + x;
            double yspan = Math.sqrt(Math.max(0, (double) r * r - (double) x * x));
            int top = (int) Math.round(cy - yspan);
            int bot = (int) Math.round(cy + yspan);
            double hYd = cy + pitchPx + x * tan;
            int hY = (int) Math.round(hYd);
            if (hY < top) {
                hY = top;
            }
            if (hY > bot) {
                hY = bot;
            }
            if (hY > top) {
                g.fill(col, top, col + 1, hY, SKY);
            }
            if (bot > hY) {
                g.fill(col, hY, col + 1, bot, GROUND);
            }
            if (hY > top && hY < bot) {
                g.fill(col, hY - 1, col + 1, hY + 1, 0xFFFFFFFF);
            }
        }

        for (int p = -20; p <= 20; p += 10) {
            if (p == 0) {
                continue;
            }
            int ry = (int) Math.round(cy + pitchPx - p * (r / 35.0));
            if (ry > cy - r + 6 && ry < cy + r - 6) {
                g.fill(cx - 10, ry, cx - 3, ry + 1, 0xCCFFFFFF);
                g.fill(cx + 3, ry, cx + 10, ry + 1, 0xCCFFFFFF);
            }
        }

        for (int a = -60; a <= 60; a += 30) {
            double ar = Math.toRadians(a - 90);
            int tx = (int) Math.round(cx + Math.cos(ar) * (r - 2));
            int ty = (int) Math.round(cy + Math.sin(ar) * (r - 2));
            g.fill(tx, ty, tx + 1, ty + 1, DIM);
        }

        g.fill(cx - 16, cy - 1, cx - 5, cy + 1, GOLD);
        g.fill(cx + 5, cy - 1, cx + 16, cy + 1, GOLD);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, GOLD);

        int px = (int) Math.round(cx + Math.cos(Math.toRadians(rollDeg - 90)) * (r - 4));
        int py = (int) Math.round(cy + Math.sin(Math.toRadians(rollDeg - 90)) * (r - 4));
        g.fill(px - 1, py - 1, px + 2, py + 2, live ? CYAN : AMBER);
        g.drawString(font, "ATTITUDE", cx - font.width("ATTITUDE") / 2, cy + r + 5, DIM, false);
    }

    static void drawDial(GuiGraphics g, Font font, int cx, int cy, int r,
                         double value, double max, String label, String unit, int color) {
        ring(g, cx, cy, r + 2, GOLD_DK, 80);
        ring(g, cx, cy, r, METAL_DK, 80);
        double start = Math.toRadians(135);
        double sweep = Math.toRadians(270);
        int ticks = 10;
        for (int i = 0; i <= ticks; i++) {
            double a = start + sweep * i / ticks;
            int x0 = (int) Math.round(cx + Math.cos(a) * (r - 2));
            int y0 = (int) Math.round(cy + Math.sin(a) * (r - 2));
            int x1 = (int) Math.round(cx + Math.cos(a) * (r - 7));
            int y1 = (int) Math.round(cy + Math.sin(a) * (r - 7));
            line(g, x0, y0, x1, y1, i >= ticks * 0.8 ? RED : DIM);
        }
        double frac = Math.max(0, Math.min(1, max <= 0 ? 0 : value / max));
        double na = start + sweep * frac;
        int nx = (int) Math.round(cx + Math.cos(na) * (r - 6));
        int ny = (int) Math.round(cy + Math.sin(na) * (r - 6));
        lineT(g, cx, cy, nx, ny, color);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, GOLD);
        String v = String.format("%.0f", value);
        g.drawString(font, v, cx - font.width(v) / 2, cy + r / 3, color, false);
        g.drawString(font, unit, cx - font.width(unit) / 2, cy + r / 3 + 10, DIM, false);
        g.drawString(font, label, cx - font.width(label) / 2, cy - r - 11, GOLD, false);
    }

    static void drawCarGauge(GuiGraphics g, Font font, int cx, int cy, int r,
                             double value, double max, double redlineFrac,
                             String label, String unit, int color, boolean live) {
        ring(g, cx, cy, r + 3, METAL_DK, 96);
        ring(g, cx, cy, r + 2, GOLD_DK, 96);
        for (int i = 0; i < r; i += 1) {
            if (i > r - 3) {
                ring(g, cx, cy, i, dim(METAL, 0.7), 64);
            }
        }
        double start = Math.toRadians(135);
        double sweep = Math.toRadians(270);
        int ticks = 12;
        for (int i = 0; i <= ticks; i++) {
            double f = (double) i / ticks;
            double a = start + sweep * f;
            boolean major = i % 2 == 0;
            boolean red = f >= redlineFrac - 1e-6;
            int len = major ? 8 : 5;
            int x0 = (int) Math.round(cx + Math.cos(a) * (r - 2));
            int y0 = (int) Math.round(cy + Math.sin(a) * (r - 2));
            int x1 = (int) Math.round(cx + Math.cos(a) * (r - 2 - len));
            int y1 = (int) Math.round(cy + Math.sin(a) * (r - 2 - len));
            if (major) {
                lineT(g, x0, y0, x1, y1, red ? RED : GOLD);
            } else {
                line(g, x0, y0, x1, y1, red ? dim(RED, 0.8) : DIM);
            }
            if (major) {
                int val = (int) Math.round(max * f);
                String s = Integer.toString(val);
                int lx = (int) Math.round(cx + Math.cos(a) * (r - 2 - len - 8)) - font.width(s) / 2;
                int ly = (int) Math.round(cy + Math.sin(a) * (r - 2 - len - 8)) - 4;
                g.drawString(font, s, lx, ly, red ? RED : DIM, false);
            }
        }

        for (double f = redlineFrac; f <= 1.0; f += 0.015) {
            double a = start + sweep * f;
            int x = (int) Math.round(cx + Math.cos(a) * (r - 1));
            int y = (int) Math.round(cy + Math.sin(a) * (r - 1));
            g.fill(x, y, x + 2, y + 2, RED);
        }
        double frac = Math.max(0, Math.min(1, max <= 0 ? 0 : value / max));
        double na = start + sweep * frac;
        int nx = (int) Math.round(cx + Math.cos(na) * (r - 9));
        int ny = (int) Math.round(cy + Math.sin(na) * (r - 9));
        int needle = live ? (frac >= redlineFrac ? RED : color) : DIM;
        lineT(g, cx, cy, nx, ny, needle);

        lineT(g, cx, cy, (int) Math.round(cx - Math.cos(na) * 9), (int) Math.round(cy - Math.sin(na) * 9), needle);
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, GOLD);
        ring(g, cx, cy, 4, GOLD_DK, 16);
        String v = String.format("%.0f", value);
        g.drawString(font, v, cx - font.width(v) / 2, cy + r / 2 - 2, live ? color : DIM, false);
        if (!unit.isEmpty()) {
            g.drawString(font, unit, cx - font.width(unit) / 2, cy + r / 2 + 8, DIM, false);
        }
        g.drawString(font, label, cx - font.width(label) / 2, cy - r - 11, GOLD, false);
    }

    static void drawGearStack(GuiGraphics g, Font font, int x, int y, int w, int h, int gear, boolean live) {
        String[] labels = {"P", "R", "N", "D"};
        int[] cols = {DIM, AMBER, CYAN, GREEN};
        int n = labels.length;
        int cellH = h / n;
        g.fill(x, y, x + w, y + h, 0xFF05080C);
        renderOutline(g, x, y, w, h, GOLD_DK);
        for (int i = 0; i < n; i++) {
            int cy = y + i * cellH;
            boolean on = i == gear && live;
            int col = cols[i];
            if (on) {
                g.fill(x + 2, cy + 2, x + w - 2, cy + cellH - 2, dim(col, 0.32));
                renderOutline(g, x + 2, cy + 2, w - 4, cellH - 4, col);
            }
            g.drawString(font, labels[i], x + (w - font.width(labels[i])) / 2, cy + (cellH - 8) / 2,
                    on ? col : (i == gear ? dim(col, 0.6) : 0xFF3A4250), false);
        }
    }

    static void drawInclinometer(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 double pitch, double roll, boolean live) {
        g.fill(x, y, x + w, y + h, 0xFF05080C);
        renderOutline(g, x, y, w, h, GOLD_DK);
        g.drawString(font, "INCLINE", x + 4, y + 3, GOLD, false);
        int cx = x + w / 2;
        int cy = y + h / 2 + 6;
        int r = Math.min(w, h) / 2 - 12;
        ring(g, cx, cy, r, GOLD_DK, 48);
        ring(g, cx, cy, r * 2 / 3, dim(GOLD_DK, 0.7), 36);
        g.fill(cx - r, cy, cx + r, cy + 1, dim(GOLD_DK, 0.8));
        g.fill(cx, cy - r, cx + 1, cy + r, dim(GOLD_DK, 0.8));
        int bx = cx + (int) Math.round(Math.max(-1, Math.min(1, roll / 30.0)) * (r - 4));
        int by = cy + (int) Math.round(Math.max(-1, Math.min(1, pitch / 30.0)) * (r - 4));
        int col = live ? CYAN : DIM;
        g.fill(bx - 3, by - 3, bx + 4, by + 4, col);
        renderOutline(g, bx - 3, by - 3, 7, 7, dim(col, 0.6));
        String s = String.format("P%+d  R%+d", (int) Math.round(pitch), (int) Math.round(roll));
        g.drawString(font, s, x + 4, y + h - 10, DIM, false);
    }

    static void drawGMeter(GuiGraphics g, Font font, int x, int y, int w, int h, double accelG, double latG) {
        g.fill(x, y, x + w, y + h, 0xFF05080C);
        renderOutline(g, x, y, w, h, GOLD_DK);
        g.drawString(font, "G-FORCE", x + 4, y + 3, GOLD, false);
        int cx = x + w / 2;
        int cy = y + h / 2 + 6;
        int r = Math.min(w, h) / 2 - 12;
        ring(g, cx, cy, r, GOLD_DK, 40);
        ring(g, cx, cy, r * 2 / 3, dim(GOLD_DK, 0.7), 32);
        g.fill(cx - r, cy, cx + r, cy + 1, dim(GOLD_DK, 0.7));
        g.fill(cx, cy - r, cx + 1, cy + r, dim(GOLD_DK, 0.7));
        double gx = Math.max(-1.5, Math.min(1.5, latG)) / 1.5;
        double gy = Math.max(-1.5, Math.min(1.5, accelG)) / 1.5;
        int dx = cx + (int) Math.round(gx * r);
        int dy = cy - (int) Math.round(gy * r);
        double mag = Math.hypot(accelG, latG);
        int col = mag > 0.9 ? RED : mag > 0.5 ? AMBER : CYAN;
        line(g, cx, cy, dx, dy, dim(col, 0.6));
        g.fill(dx - 2, dy - 2, dx + 3, dy + 3, col);
        g.drawString(font, String.format("%+.1f", accelG), x + 4, y + h - 10, DIM, false);
    }

    static void drawPedalBar(GuiGraphics g, Font font, int x, int y, int w, int h,
                             double v, String label, int color) {
        g.fill(x, y + 10, x + w, y + h, 0xFF05080C);
        renderOutline(g, x, y + 10, w, h - 10, GOLD_DK);
        int fillH = (int) Math.round((h - 14) * Math.max(0, Math.min(1, v)));
        if (fillH > 0) {
            g.fill(x + 2, y + h - 2 - fillH, x + w - 2, y + h - 2, color);
        }
        g.drawString(font, label, x + (w - font.width(label)) / 2, y, GOLD, false);
    }

    static void drawFuelBar(GuiGraphics g, Font font, int x, int y, int w, float amount, float capacity, boolean live) {
        g.drawString(font, "FUEL", x, y, GOLD, false);
        int barX = x + font.width("FUEL") + 6;
        int barW = Math.max(10, w - (barX - x));
        int barY = y + 1;
        int barH = 6;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF05080C);
        renderOutline(g, barX, barY, barW, barH, GOLD_DK);
        boolean has = capacity > 0.5f;
        float pct = has ? Math.max(0f, Math.min(1f, amount / capacity)) : 0f;
        int col = pct < 0.15f ? RED : pct < 0.30f ? AMBER : GREEN;
        int fillW = (int) ((barW - 2) * pct);
        if (fillW > 0) {
            g.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, live ? col : dim(col, 0.6));
        }
        String txt;
        if (has) {
            txt = String.format("%.0f / %.0f mB  (%.0f%%)", amount, capacity, pct * 100f);
        } else if (amount > 0.5f) {
            txt = String.format("%.0f mB", amount);
        } else {
            txt = "no diesel engine on craft";
        }
        g.drawString(font, txt, x, y + 10, has || amount > 0.5f ? TXT : DIM, false);
    }

    static void drawHeadingTape(GuiGraphics g, Font font, int x, int y, int w, int h, double headingDeg) {
        g.fill(x, y, x + w, y + h, INSTR_BG);
        renderOutline(g, x, y, w, h, GOLD_DK);
        int cx = x + w / 2;
        double pxPerDeg = 2.0;
        for (int d = -90; d <= 90; d += 10) {
            double world = headingDeg + d;
            int tx = (int) Math.round(cx + d * pxPerDeg);
            if (tx < x + 4 || tx > x + w - 4) {
                continue;
            }
            int wd = ((int) Math.round(world) % 360 + 360) % 360;
            boolean major = wd % 90 == 0;
            g.fill(tx, y + 2, tx + 1, y + (major ? 10 : 6), major ? GOLD : DIM);
            if (major) {
                String c = wd == 0 ? "N" : wd == 90 ? "E" : wd == 180 ? "S" : "W";
                g.drawString(font, c, tx - font.width(c) / 2, y + 12, GOLD, false);
            }
        }

        g.fill(cx, y - 2, cx + 1, y + h - 2, CYAN);
        g.fill(cx - 2, y - 2, cx + 3, y, CYAN);
        String hd = String.format("%03.0f", ((headingDeg % 360) + 360) % 360);
        g.drawString(font, hd, cx - font.width(hd) / 2, y + h - 10, CYAN, false);
    }

    static final int LEVER_SLOT_TOP = 18;
    static final int LEVER_SLOT_BOT = 16;
    static final int LEVER_HANDLE = 14;

    static void drawLever(GuiGraphics g, Font font, int x, int y, int w, int h,
                          String label, double v, boolean centered, int color, boolean hover) {
        g.fill(x, y, x + w, y + h, 0xFF0E141B);
        renderOutline(g, x, y, w, h, hover ? color : GOLD_DK);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, GOLD, false);
        int slotX = x + w / 2 - 3;
        int slotY = y + LEVER_SLOT_TOP;
        int slotH = h - LEVER_SLOT_TOP - LEVER_SLOT_BOT;
        g.fill(slotX, slotY, slotX + 6, slotY + slotH, 0xFF05080C);
        renderOutline(g, slotX, slotY, 6, slotH, GOLD_DK);
        for (int i = 0; i <= 4; i++) {
            int ty = slotY + slotH * i / 4;
            g.fill(slotX - 5, ty, slotX - 2, ty + 1, DIM);
            g.fill(slotX + 8, ty, slotX + 11, ty + 1, DIM);
        }
        if (centered) {
            int my = slotY + slotH / 2;
            g.fill(slotX - 6, my, slotX + 12, my + 1, GOLD_DK);
        }
        int travel = slotH - LEVER_HANDLE;
        int hy = slotY + (int) Math.round((1 - clamp01(v)) * travel);
        g.fill(slotX - 9, hy, slotX + 15, hy + LEVER_HANDLE, METAL);
        renderOutline(g, slotX - 9, hy, 24, LEVER_HANDLE, color);
        g.fill(slotX - 9, hy + LEVER_HANDLE / 2 - 1, slotX + 15, hy + LEVER_HANDLE / 2 + 1, color);
        String vs = centered ? String.format("%+d", (int) Math.round((v - 0.5) * 200))
                : (int) Math.round(v * 100) + "%";
        g.drawString(font, vs, x + (w - font.width(vs)) / 2, y + h - 10, color, false);
    }

    static void drawWheel(GuiGraphics g, Font font, int cx, int cy, int r,
                          String label, double v, int color, boolean hover) {
        double ang = (v - 0.5) * Math.toRadians(240);

        g.fill(cx - 1, cy - r - 4, cx + 2, cy - r + 2, AMBER);

        ring(g, cx, cy, r, hover ? color : GOLD, 48);
        ring(g, cx, cy, r - 1, GOLD_DK, 48);

        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            double a = -Math.PI / 2 + ang * i / steps;
            int ax = (int) Math.round(cx + Math.cos(a) * (r - 4));
            int ay = (int) Math.round(cy + Math.sin(a) * (r - 4));
            g.fill(ax, ay, ax + 1, ay + 1, dim(CYAN, 0.8));
        }

        g.fill(cx - 6, cy - 6, cx + 6, cy + 6, METAL);
        renderOutline(g, cx - 6, cy - 6, 12, 12, GOLD_DK);
        // one bright spoke so you can see it turn
        for (int i = 0; i < 3; i++) {
            double a = ang + i * 2 * Math.PI / 3 - Math.PI / 2;
            int ex = (int) Math.round(cx + Math.cos(a) * (r - 3));
            int ey = (int) Math.round(cy + Math.sin(a) * (r - 3));
            if (i == 0) {
                lineT(g, cx, cy, ex, ey, color);
                lineT(g, cx, cy, ex, ey, color);
            } else {
                line(g, cx, cy, ex, ey, GOLD_DK);
            }
        }

        double ga = ang - Math.PI / 2;
        int gx = (int) Math.round(cx + Math.cos(ga) * (r - 2));
        int gy = (int) Math.round(cy + Math.sin(ga) * (r - 2));
        g.fill(gx - 3, gy - 3, gx + 4, gy + 4, color);
        renderOutline(g, gx - 3, gy - 3, 7, 7, 0xFF0A0E13);
        center(g, font, label, cx, cy + r + 3, GOLD);
        String vs = v > 0.52 ? "R " + (int) Math.round((v - 0.5) * 200)
                : v < 0.48 ? "L " + (int) Math.round((0.5 - v) * 200) : "CENTER";
        center(g, font, vs, cx, cy + r + 13, color);
    }

    static void drawStick(GuiGraphics g, Font font, int x, int y, int w, int h,
                          String label, double vx, double vy, int color, boolean hover) {
        g.fill(x, y, x + w, y + h, 0xFF0E141B);
        renderOutline(g, x, y, w, h, hover ? color : GOLD_DK);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, GOLD, false);
        int gx = x + 8;
        int gy = y + 16;
        int gw = w - 16;
        int gh = h - 28;
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF05080C);
        renderOutline(g, gx, gy, gw, gh, GOLD_DK);
        g.fill(gx, gy + gh / 2, gx + gw, gy + gh / 2 + 1, dim(GOLD_DK, 0.8));
        g.fill(gx + gw / 2, gy, gx + gw / 2 + 1, gy + gh, dim(GOLD_DK, 0.8));
        int kx = gx + (int) Math.round(clamp01(vx) * (gw - 8));
        int ky = gy + (int) Math.round(clamp01(vy) * (gh - 8));
        line(g, gx + gw / 2, gy + gh / 2, kx + 4, ky + 4, dim(color, 0.7));
        g.fill(kx, ky, kx + 8, ky + 8, color);
        renderOutline(g, kx, ky, 8, 8, dim(color, 0.5));
    }

    static void drawShifter(GuiGraphics g, Font font, int x, int y, int w, int h,
                            String label, int step, String[] det, int color, boolean hover) {
        g.fill(x, y, x + w, y + h, 0xFF0E141B);
        renderOutline(g, x, y, w, h, hover ? color : GOLD_DK);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, GOLD, false);
        int n = Math.max(1, det.length);
        int gx = x + w / 2 - 2;
        int gy = y + 20;
        int gh = h - 34;
        g.fill(gx, gy, gx + 4, gy + gh, 0xFF05080C);
        for (int i = 0; i < det.length; i++) {
            int ty = gy + (n == 1 ? 0 : gh * i / (n - 1));
            g.fill(gx - 3, ty, gx - 1, ty + 1, DIM);
            g.drawString(font, det[i], gx + 9, ty - 3, i == step ? color : DIM, false);
        }
        int ky = gy + (n == 1 ? 0 : gh * step / (n - 1)) - 5;
        g.fill(gx - 5, ky, gx + 9, ky + 10, METAL);
        renderOutline(g, gx - 5, ky, 14, 10, color);
    }

    static void drawCraftGraphic(GuiGraphics g, Font font, VccMk2CraftProfile p,
                                 int x, int y, int w, int h, double t) {
        g.fill(x, y, x + w, y + h, INSTR_BG);
        renderOutline(g, x, y, w, h, GOLD_DK);
        int cx = x + w / 2;
        int cy = y + h / 2;
        int r = Math.min(w, h) / 2 - 16;
        switch (p) {
            case PLANE -> drawPlane(g, font, x, y, w, h, t);
            case MULTIROTOR -> drawRotors(g, font, cx, cy, r, 4, t);
            case VTOL -> drawRotors(g, font, cx, cy, r, 4, t);
            case OTOMOBIL -> drawSteering(g, font, cx, cy, r, t);
            case TANK -> drawTank(g, font, x, y, w, h, t);
            case AIRSHIP -> drawAirship(g, font, cx, cy, Math.min(r + 6, w / 2 - 10), t);
            case BOAT -> drawBoat(g, font, x, y, w, h, t);
            default -> drawGeneric(g, font, cx, cy, r, t);
        }
    }

    private static void drawPlane(GuiGraphics g, Font font, int x, int y, int w, int h, double t) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int wing = Math.min(w / 2 - 14, h / 2 - 6);
        double ail = Math.sin(t * 1.3) * 4;
        double elev = Math.sin(t * 0.9 + 1) * 3;
        double rud = Math.sin(t * 0.7 + 2) * 4;
        g.fill(cx - 4, cy - wing - 6, cx + 4, cy + wing + 12, METAL);
        renderOutline(g, cx - 4, cy - wing - 6, 8, wing * 2 + 18, GOLD_DK);
        g.fill(cx - wing, cy - 3, cx + wing, cy + 3, METAL);
        renderOutline(g, cx - wing, cy - 3, wing * 2, 6, GOLD_DK);
        int aoff = (int) Math.round(ail);
        g.fill(cx - wing, cy + 3, cx - wing + 20, cy + 3 + Math.abs(aoff) + 1, aoff >= 0 ? CYAN : RED);
        g.fill(cx + wing - 20, cy + 3, cx + wing, cy + 3 + Math.abs(aoff) + 1, aoff >= 0 ? RED : CYAN);
        g.fill(cx - 16, cy + wing, cx + 16, cy + wing + 4, METAL);
        g.fill(cx - 16, cy + wing + 4, cx + 16, cy + wing + 5 + (int) Math.round(Math.abs(elev)), CYAN);
        g.fill(cx - 1, cy + wing - 2, cx + 1 + (int) Math.round(rud), cy + wing + 12, CYAN);
        g.fill(cx - 3, cy - wing - 10, cx + 3, cy - wing - 6, GOLD);
        center(g, font, "CONTROL SURFACES", cx, y + h - 11, DIM);
    }

    private static void drawRotors(GuiGraphics g, Font font, int cx, int cy, int r, int n, double t) {
        double spin = t * 7;
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n - Math.PI / 4;
            int mx = (int) Math.round(cx + Math.cos(a) * r);
            int my = (int) Math.round(cy + Math.sin(a) * r);
            lineT(g, cx, cy, mx, my, GOLD_DK);
            ring(g, mx, my, 11, DIM, 24);
            for (int b = 0; b < 2; b++) {
                double ba = spin + i + b * Math.PI / 2;
                int bx = (int) Math.round(mx + Math.cos(ba) * 10);
                int by = (int) Math.round(my + Math.sin(ba) * 10);
                line(g, mx - (bx - mx), my - (by - my), bx, by, CYAN);
            }
        }
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, GOLD);
        center(g, font, "ROTORS", cx, cy + r + 13, DIM);
    }

    private static void drawSteering(GuiGraphics g, Font font, int cx, int cy, int r, double t) {
        double ang = Math.sin(t * 0.8) * 0.5;
        ring(g, cx, cy, r, GOLD, 56);
        ring(g, cx, cy, r - 1, GOLD_DK, 56);
        ring(g, cx, cy, r - 2, GOLD_DK, 56);
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, METAL);
        renderOutline(g, cx - 7, cy - 7, 14, 14, GOLD_DK);
        for (int i = 0; i < 3; i++) {
            double a = ang + i * 2 * Math.PI / 3 - Math.PI / 2;
            int sx = (int) Math.round(cx + Math.cos(a) * (r - 3));
            int sy = (int) Math.round(cy + Math.sin(a) * (r - 3));
            lineT(g, cx, cy, sx, sy, GOLD);
        }
        center(g, font, "STEERING", cx, cy + r + 4, DIM);
    }

    private static void drawTank(GuiGraphics g, Font font, int x, int y, int w, int h, double t) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int bw = Math.min(w - 40, 70);
        int bh = Math.min(h - 30, 54);

        int scroll = (int) (t * 14) % 8;
        for (int side = 0; side < 2; side++) {
            int tx = side == 0 ? cx - bw / 2 - 10 : cx + bw / 2 - 2;
            g.fill(tx, cy - bh / 2, tx + 12, cy + bh / 2, METAL_DK);
            renderOutline(g, tx, cy - bh / 2, 12, bh, GOLD_DK);
            for (int yy = cy - bh / 2 + scroll; yy < cy + bh / 2; yy += 8) {
                g.fill(tx, yy, tx + 12, yy + 1, GOLD_DK);
            }
        }
        g.fill(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, METAL);
        renderOutline(g, cx - bw / 2, cy - bh / 2, bw, bh, GOLD_DK);

        double a = Math.sin(t * 0.5) * 0.4 - Math.PI / 2;
        ring(g, cx, cy, 12, GOLD_DK, 24);
        int gx = (int) Math.round(cx + Math.cos(a) * (bh / 2 + 12));
        int gy = (int) Math.round(cy + Math.sin(a) * (bh / 2 + 12));
        lineT(g, cx, cy, gx, gy, GOLD);
        center(g, font, "TRACKS", cx, y + h - 11, DIM);
    }

    private static void drawAirship(GuiGraphics g, Font font, int cx, int cy, int r, double t) {
        int bob = (int) Math.round(Math.sin(t * 1.1) * 2);
        int rx = r;
        int ry = Math.max(8, r / 2);
        for (int x = -rx; x <= rx; x++) {
            double f = 1 - (double) (x * x) / (double) (rx * rx);
            if (f < 0) {
                continue;
            }
            int yspan = (int) Math.round(ry * Math.sqrt(f));
            int col = cx + x;
            g.fill(col, cy - yspan + bob, col + 1, cy + yspan + bob, METAL);
        }
        ringEllipse(g, cx, cy + bob, rx, ry, GOLD_DK, 80);
        g.fill(cx + rx - 12, cy - 7 + bob, cx + rx, cy + 7 + bob, GOLD_DK);
        g.fill(cx - 9, cy + ry + bob, cx + 9, cy + ry + 6 + bob, METAL_DK);
        renderOutline(g, cx - 9, cy + ry + bob, 18, 6, GOLD_DK);
        center(g, font, "ENVELOPE", cx, cy + ry + 15, DIM);
    }

    private static void drawBoat(GuiGraphics g, Font font, int x, int y, int w, int h, double t) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int bw = Math.min(w - 40, 64);
        int bh = Math.min(h - 30, 30);

        int scroll = (int) (t * 16) % 10;
        for (int yy = cy + bh; yy < y + h - 8; yy += 10) {
            int ww = (yy - cy) / 2;
            g.fill(cx - ww, yy - scroll, cx + ww, yy - scroll + 1, dim(CYAN, 0.5));
        }
        g.fill(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, METAL);

        for (int i = 0; i < bw / 2; i++) {
            int yy = cy - bh / 2 - (bw / 2 - i) / 3;
            g.fill(cx - bw / 2 + i, yy, cx + bw / 2 - i, yy + 1, METAL);
        }
        renderOutline(g, cx - bw / 2, cy - bh / 2, bw, bh, GOLD_DK);

        double a = Math.sin(t * 0.9) * 5;
        g.fill(cx - 1, cy + bh / 2, cx + 1 + (int) Math.round(a), cy + bh / 2 + 10, CYAN);
        center(g, font, "HULL", cx, y + h - 11, DIM);
    }

    private static void drawGeneric(GuiGraphics g, Font font, int cx, int cy, int r, double t) {
        ring(g, cx, cy, r, GOLD_DK, 48);
        double a = t * 1.5;
        lineT(g, cx, cy, (int) Math.round(cx + Math.cos(a) * r), (int) Math.round(cy + Math.sin(a) * r), CYAN);
        lineT(g, cx, cy, (int) Math.round(cx - Math.cos(a) * r), (int) Math.round(cy - Math.sin(a) * r), CYAN);
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, GOLD);
        center(g, font, "CUSTOM", cx, cy + r + 8, DIM);
    }

    static void drawRouteMap(GuiGraphics g, Font font, int x, int y, int w, int h, double t, boolean engaged) {
        g.fill(x, y, x + w, y + h, INSTR_BG);
        renderOutline(g, x, y, w, h, engaged ? GREEN : GOLD_DK);
        for (int i = 0; i <= 6; i++) {
            int gx = x + w * i / 6;
            int gy = y + h * i / 6;
            g.fill(gx, y, gx + 1, y + h, 0xFF111A22);
            g.fill(x, gy, x + w, gy + 1, 0xFF111A22);
        }
        g.drawString(font, engaged ? "ROUTE" : "ROUTE  \u00b7  STANDBY", x + 6, y + 4, engaged ? GREEN : DIM, false);
        int n = 7;
        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            px[i] = x + 18 + (w - 36) * i / (n - 1);
            py[i] = y + h / 2 + (int) Math.round(Math.sin(i * 0.9 + 0.3) * (h * 0.22));
        }
        double p = engaged ? ((t * 0.05) % 1.0) : 0.0;
        int seg = Math.min(n - 2, (int) (p * (n - 1)));
        double frac = p * (n - 1) - seg;
        for (int i = 0; i < n - 1; i++) {
            int col = i < seg ? 0xFF2E6F56 : i == seg ? AMBER : CYAN;
            line(g, px[i], py[i], px[i + 1], py[i + 1], col);
            line(g, px[i], py[i] + 1, px[i + 1], py[i + 1] + 1, col);
        }
        for (int i = 0; i < n; i++) {
            g.fill(px[i] - 1, py[i] - 1, px[i] + 2, py[i] + 2, DIM);
        }
        g.fill(px[0] - 2, py[0] - 2, px[0] + 3, py[0] + 3, GREEN);
        g.fill(px[n - 1] - 2, py[n - 1] - 2, px[n - 1] + 3, py[n - 1] + 3, AMBER);
        if (engaged) {
            int vx = (int) Math.round(px[seg] + (px[seg + 1] - px[seg]) * frac);
            int vy = (int) Math.round(py[seg] + (py[seg + 1] - py[seg]) * frac);
            int pr = 2 + (int) (2 * (0.5 + 0.5 * Math.sin(t * 6)));
            ring(g, vx, vy, pr + 2, CYAN, 12);
            g.fill(vx - 3, vy - 3, vx + 4, vy + 4, 0xFF0A0E13);
            renderOutline(g, vx - 3, vy - 3, 7, 7, CYAN);
        }
    }

    static void drawThrustAssist(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 double t, double thrust, double yaw, boolean engaged) {
        g.fill(x, y, x + w, y + h, INSTR_BG);
        renderOutline(g, x, y, w, h, engaged ? GREEN : GOLD_DK);
        g.drawString(font, engaged ? "THRUST ASSIST  \u00b7  ONLINE" : "THRUST ASSIST  \u00b7  STANDBY",
                x + 6, y + 4, engaged ? GREEN : DIM, false);
        int cx = x + w / 2;
        int cy = y + h / 2 + 6;
        int bw = Math.min(w - 90, 150);
        int bh = 18;
        g.fill(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, METAL);
        renderOutline(g, cx - bw / 2, cy - bh / 2, bw, bh, GOLD_DK);
        g.fill(cx - bw / 4, cy - bh / 2 - 8, cx + bw / 4, cy - bh / 2, METAL_DK);
        g.fill(cx, y + 20, cx + 1, y + h - 12, dim(GOLD_DK, 0.8));
        double left = Math.max(-1, Math.min(1, thrust + yaw * 0.7));
        double right = Math.max(-1, Math.min(1, thrust - yaw * 0.7));
        drawPod(g, font, cx - bw / 2 - 28, cy, "L", left);
        drawPod(g, font, cx + bw / 2 + 12, cy, "R", right);
        if (engaged) {
            for (int i = 0; i < 6; i++) {
                double ph = (t * 0.8 + i / 6.0) % 1.0;
                int fy = (int) (cy - bh / 2 - ph * (h * 0.34));
                int a = (int) (170 * (1 - ph));
                g.fill(cx - 1, fy, cx + 2, fy + 3, (a << 24) | 0x7BD68A);
            }
        }
    }

    private static void drawPod(GuiGraphics g, Font font, int x, int cy, String label, double v) {
        int bh = 46;
        int bw = 14;
        int top = cy - bh / 2;
        g.fill(x, top, x + bw, top + bh, 0xFF05080C);
        renderOutline(g, x, top, bw, bh, GOLD_DK);
        int len = (int) Math.round(Math.abs(v) * (bh / 2 - 2));
        int col = v >= 0 ? CYAN : RED;
        if (v >= 0) {
            g.fill(x + 2, cy - len, x + bw - 2, cy, col);
        } else {
            g.fill(x + 2, cy, x + bw - 2, cy + len, col);
        }
        g.fill(x, cy, x + bw, cy + 1, GOLD_DK);
        center(g, font, label, x + bw / 2, top - 10, GOLD);
    }

    // idle cards get a frozen clock so the hangar reads calm
    static void drawProfileIcon(GuiGraphics g, VccMk2CraftProfile p, int x, int y, int w, int h,
                                double clock, boolean animate, int col) {
        double t = animate ? clock : 0.0;
        int mv = animate ? CYAN : dim(col, 0.8);
        int cx = x + w / 2;
        int cy = y + h / 2;
        int r = Math.max(6, Math.min(w, h) / 2 - 2);
        switch (p) {
            case AIRSHIP -> iconAirship(g, cx, cy, r, t, col, mv);
            case MULTIROTOR -> iconRotors(g, cx, cy, r, t, col, mv, false);
            case VTOL -> iconRotors(g, cx, cy, r, t, col, mv, true);
            case PLANE -> iconPlane(g, cx, cy, r, t, col, mv);
            case OTOMOBIL -> iconCar(g, cx, cy, r, t, col, mv);
            case TANK -> iconTank(g, cx, cy, r, t, col, mv);
            case BOAT -> iconBoat(g, cx, cy, r, t, col, mv);
            default -> iconCustom(g, cx, cy, r, t, col, mv);
        }
    }

    private static void iconAirship(GuiGraphics g, int cx, int cy, int r, double t, int col, int mv) {
        int bob = (int) Math.round(Math.sin(t * 1.2) * 1.5);
        int rx = r;
        int ry = Math.max(4, r / 2);
        for (int x = -rx; x <= rx; x++) {
            double f = 1 - (double) (x * x) / (double) (rx * rx);
            if (f < 0) {
                continue;
            }
            int ys = (int) Math.round(ry * Math.sqrt(f));
            g.fill(cx + x, cy - ys + bob, cx + x + 1, cy + ys + bob, METAL);
        }
        ringEllipse(g, cx, cy + bob, rx, ry, col, 56);
        g.fill(cx + rx - 4, cy - 3 + bob, cx + rx, cy + 3 + bob, mv);
        g.fill(cx - 3, cy + ry + bob, cx + 3, cy + ry + 3 + bob, dim(col, 0.7));
    }

    private static void iconRotors(GuiGraphics g, int cx, int cy, int r, double t, int col, int mv, boolean vtol) {
        double spin = t * 7;
        int arm = r - 2;
        int br = Math.max(3, r / 3);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 - Math.PI / 4;
            int mx = (int) Math.round(cx + Math.cos(a) * arm);
            int my = (int) Math.round(cy + Math.sin(a) * arm);
            line(g, cx, cy, mx, my, dim(col, 0.7));
            ring(g, mx, my, br, dim(col, 0.85), 14);
            for (int b = 0; b < 2; b++) {
                double ba = spin + i + b * Math.PI / 2;
                int bx = (int) Math.round(mx + Math.cos(ba) * br);
                int by = (int) Math.round(my + Math.sin(ba) * br);
                line(g, mx - (bx - mx), my - (by - my), bx, by, mv);
            }
        }
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, col);
        if (vtol) {
            int ny = cy - r;
            lineT(g, cx, ny - 1, cx - 4, ny + 4, mv);
            lineT(g, cx, ny - 1, cx + 4, ny + 4, mv);
        }
    }

    private static void iconPlane(GuiGraphics g, int cx, int cy, int r, double t, int col, int mv) {
        int wing = r - 1;
        int ail = (int) Math.round(Math.abs(Math.sin(t * 1.3)) * 3);
        g.fill(cx - 2, cy - wing - 1, cx + 2, cy + wing + 2, METAL);
        renderOutline(g, cx - 2, cy - wing - 1, 4, wing * 2 + 3, dim(col, 0.7));
        g.fill(cx - wing, cy - 2, cx + wing, cy + 2, METAL);
        renderOutline(g, cx - wing, cy - 2, wing * 2, 4, dim(col, 0.7));
        g.fill(cx - wing, cy + 2, cx - wing + 5, cy + 3 + ail, mv);
        g.fill(cx + wing - 5, cy + 2, cx + wing, cy + 3 + ail, mv);
        g.fill(cx - 5, cy + wing - 2, cx + 5, cy + wing + 1, METAL);
        g.fill(cx - 2, cy - wing - 3, cx + 2, cy - wing - 1, col);
    }

    private static void iconCar(GuiGraphics g, int cx, int cy, int r, double t, int col, int mv) {
        double steer = Math.sin(t * 0.9) * 2;
        int bw = Math.max(5, r - 2);
        int bh = r;
        g.fill(cx - bw, cy - bh, cx + bw, cy + bh, METAL);
        renderOutline(g, cx - bw, cy - bh, bw * 2, bh * 2, col);
        g.fill(cx - bw + 2, cy - bh + 3, cx + bw - 2, cy - 1, dim(mv, 0.7));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int wx = cx + sx * bw;
                int sh = sy < 0 ? (int) Math.round(steer) : 0;
                g.fill(wx - 2 + sh, cy + sy * (bh - 3) - 3, wx + 2 + sh, cy + sy * (bh - 3) + 3, METAL_DK);
                renderOutline(g, wx - 2 + sh, cy + sy * (bh - 3) - 3, 4, 6, dim(col, 0.7));
            }
        }
    }

    private static void iconTank(GuiGraphics g, int cx, int cy, int r, double t, int col, int mv) {
        int bw = Math.max(4, r - 4);
        int bh = r;
        int scroll = (int) (t * 12) % 6;
        for (int side = -1; side <= 1; side += 2) {
            int tx = side < 0 ? cx - bw - 5 : cx + bw - 1;
            g.fill(tx, cy - bh, tx + 6, cy + bh, METAL_DK);
            renderOutline(g, tx, cy - bh, 6, bh * 2, dim(col, 0.7));
            for (int yy = cy - bh + scroll; yy < cy + bh; yy += 6) {
                g.fill(tx, yy, tx + 6, yy + 1, dim(col, 0.6));
            }
        }
        g.fill(cx - bw, cy - bh + 2, cx + bw, cy + bh - 2, METAL);
        renderOutline(g, cx - bw, cy - bh + 2, bw * 2, bh * 2 - 4, col);
        double a = Math.sin(t * 0.5) * 0.4 - Math.PI / 2;
        ring(g, cx, cy, Math.max(3, r / 3), dim(col, 0.8), 16);
        int gx = (int) Math.round(cx + Math.cos(a) * (bh + 2));
        int gy = (int) Math.round(cy + Math.sin(a) * (bh + 2));
        lineT(g, cx, cy, gx, gy, mv);
    }

    private static void iconBoat(GuiGraphics g, int cx, int cy, int r, double t, int col, int mv) {
        int bw = Math.max(4, r - 2);
        int bh = r;
        for (int yy = -bh; yy <= bh; yy++) {
            int taper = yy < 0 ? (int) ((-yy) * bw / (double) bh / 1.6) : 0;
            g.fill(cx - bw + taper, cy + yy, cx + bw - taper, cy + yy + 1, METAL);
        }
        g.fill(cx - 1, cy - bh - 2, cx + 1, cy - bh, col);
        renderOutline(g, cx - bw, cy - bh, bw * 2, bh * 2, dim(col, 0.7));
        int scroll = (int) (t * 14) % 8;
        for (int yy = cy + bh; yy < cy + bh + r; yy += 6) {
            int ww = (yy - cy) / 2;
            g.fill(cx - ww, yy - scroll, cx + ww, yy - scroll + 1, dim(mv, 0.6));
        }
        double a = Math.sin(t * 0.9) * 3;
        g.fill(cx - 1, cy + bh, cx + 1 + (int) Math.round(a), cy + bh + 4, mv);
    }

    private static void iconCustom(GuiGraphics g, int cx, int cy, int r, double t, int col, int mv) {
        ring(g, cx, cy, r - 1, dim(col, 0.6), 32);
        double a = t * 1.4;
        lineT(g, cx, cy, (int) Math.round(cx + Math.cos(a) * (r - 2)), (int) Math.round(cy + Math.sin(a) * (r - 2)), mv);
        lineT(g, cx, cy, (int) Math.round(cx - Math.cos(a) * (r - 2)), (int) Math.round(cy - Math.sin(a) * (r - 2)), mv);
        lineT(g, cx, cy, (int) Math.round(cx + Math.cos(a + 1.57) * (r - 2)),
                (int) Math.round(cy + Math.sin(a + 1.57) * (r - 2)), dim(mv, 0.7));
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, col);
    }

    private static void center(GuiGraphics g, Font font, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    private static void renderOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
