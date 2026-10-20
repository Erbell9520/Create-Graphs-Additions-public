package com.createstats.aviatorhelmet.client;

import com.createstats.aviatorhelmet.HudTone;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class HudPen {
    enum Align { LEFT, CENTRE, RIGHT }

    private final GuiGraphics g;
    private final Font font;
    final HudTone tone;

    double warm = 1.0;
    double level = 1.0;

    double glow = 1.0;

    private int ox;
    private int oy;
    private double pass = 1.0;

    HudPen(GuiGraphics g, Font font, HudTone tone) {
        this.g = g;
        this.font = font;
        this.tone = tone;
    }

    Font font() {
        return font;
    }

    void ghost(boolean ghost) {
        ox = ghost ? 1 : 0;
        oy = ghost ? 1 : 0;
        pass = ghost ? 0.22 : 1.0;
    }

    int core() {
        return HudTone.warmed(tone.core, warm);
    }

    int alert() {
        return HudTone.warmed(tone.alert, warm);
    }

    private int dim() {
        return HudTone.warmed(tone.dim, warm);
    }

    private int argb(int rgb, double alpha) {
        final int a = (int) Math.max(0, Math.min(255, Math.round(alpha * level * glow * pass)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    void line(double x0, double y0, double x1, double y1) {
        line(x0, y0, x1, y1, core(), true, 1);
    }

    void thin(double x0, double y0, double x1, double y1) {
        line(x0, y0, x1, y1, core(), false, 1);
    }

    void line(double x0, double y0, double x1, double y1, int rgb, boolean glow, int width) {
        if (glow) {
            stroke(x0, y0, x1, y1, argb(dim(), 70), width + 2);
        }
        stroke(x0, y0, x1, y1, argb(rgb, 240), width);
    }

    void polyline(boolean glow, double... xy) {
        if (glow) {
            for (int i = 0; i + 3 < xy.length; i += 2) {
                stroke(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], argb(dim(), 70), 3);
            }
        }
        for (int i = 0; i + 3 < xy.length; i += 2) {
            stroke(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], argb(core(), 240), 1);
        }
    }

    void rect(int x0, int y0, int x1, int y1) {
        polyline(true, x0, y0, x1, y0, x1, y1, x0, y1, x0, y0);
    }

    // runs of fills so no pixel is drawn twice, translucent strokes stay even
    private void stroke(double fx0, double fy0, double fx1, double fy1, int argb, int width) {
        if ((argb >>> 24) == 0) {
            return;
        }
        final int x0 = (int) Math.round(fx0) + ox;
        final int y0 = (int) Math.round(fy0) + oy;
        final int x1 = (int) Math.round(fx1) + ox;
        final int y1 = (int) Math.round(fy1) + oy;
        final int lo = (width - 1) / 2;
        final int hi = width / 2;
        if (y0 == y1) {
            g.fill(Math.min(x0, x1) - (width > 1 ? lo : 0), y0 - lo, Math.max(x0, x1) + 1 + (width > 1 ? hi : 0), y0 + hi + 1, argb);
            return;
        }
        if (x0 == x1) {
            g.fill(x0 - lo, Math.min(y0, y1) - (width > 1 ? lo : 0), x0 + hi + 1, Math.max(y0, y1) + 1 + (width > 1 ? hi : 0), argb);
            return;
        }
        final int dx = x1 - x0;
        final int dy = y1 - y0;
        if (Math.abs(dx) >= Math.abs(dy)) {
            final int step = dx > 0 ? 1 : -1;
            for (int x = x0; x != x1 + step; x += step) {
                final int y = (int) Math.round(y0 + (double) dy * (x - x0) / dx);
                g.fill(x, y - lo, x + 1, y + hi + 1, argb);
            }
        } else {
            final int step = dy > 0 ? 1 : -1;
            for (int y = y0; y != y1 + step; y += step) {
                final int x = (int) Math.round(x0 + (double) dx * (y - y0) / dy);
                g.fill(x - lo, y, x + hi + 1, y + 1, argb);
            }
        }
    }

    void poly(double... xy) {
        polyColour(core(), xy);
    }

    void polyColour(int rgb, double... xy) {
        final int n = xy.length / 2;
        if (n < 3) {
            return;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, xy[2 * i]);
            maxX = Math.max(maxX, xy[2 * i]);
            minY = Math.min(minY, xy[2 * i + 1]);
            maxY = Math.max(maxY, xy[2 * i + 1]);
        }
        final int argb = argb(rgb, 230);
        if ((argb >>> 24) == 0) {
            return;
        }
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            int run = Integer.MIN_VALUE;
            for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX) + 1; x++) {
                final boolean in = x <= (int) Math.ceil(maxX) && covers(xy, n, x, y);
                if (in && run == Integer.MIN_VALUE) {
                    run = x;
                } else if (!in && run != Integer.MIN_VALUE) {
                    g.fill(run + ox, y + oy, x + ox, y + 1 + oy, argb);
                    run = Integer.MIN_VALUE;
                }
            }
        }
    }

    private static boolean covers(double[] xy, int n, double px, double py) {
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            final double xi = xy[2 * i];
            final double yi = xy[2 * i + 1];
            final double xj = xy[2 * j];
            final double yj = xy[2 * j + 1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
            if (segmentDistance(px, py, xi, yi, xj, yj) <= 0.5) {
                return true;
            }
        }
        return inside;
    }

    private static double segmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        final double vx = bx - ax;
        final double vy = by - ay;
        final double len = vx * vx + vy * vy;
        double t = len > 0.0 ? ((px - ax) * vx + (py - ay) * vy) / len : 0.0;
        t = Math.max(0.0, Math.min(1.0, t));
        return Math.hypot(px - (ax + t * vx), py - (ay + t * vy));
    }

    int text(String s, double x, double y, Align align) {
        return text(s, x, y, align, core(), false);
    }

    int text(String s, double x, double y, Align align, int rgb, boolean dimmed) {
        final int w = font.width(s);
        int tx = (int) Math.round(x);
        final int ty = (int) Math.round(y) + oy;
        if (align == Align.CENTRE) {
            tx -= w / 2;
        } else if (align == Align.RIGHT) {
            tx -= w;
        }
        tx += ox;
        int c = rgb;
        if (dimmed) {
            c = mix(0x000000, rgb, 0.72);
        }
        final int halo = argb(dim(), 50);
        if ((halo >>> 24) >= 4) {
            g.drawString(font, s, tx - 1, ty, halo, false);
            g.drawString(font, s, tx + 1, ty, halo, false);
            g.drawString(font, s, tx, ty - 1, halo, false);
            g.drawString(font, s, tx, ty + 1, halo, false);
        }
        final int main = argb(c, 245);

        if ((main >>> 24) >= 4) {
            g.drawString(font, s, tx, ty, main, false);
        }
        return w;
    }

    void clip(int x0, int y0, int x1, int y1) {
        g.enableScissor(x0 + ox, y0 + oy, x1 + ox, y1 + oy);
    }

    void unclip() {
        g.disableScissor();
    }

    static int mix(int a, int b, double t) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            final int ca = (a >> shift) & 0xFF;
            final int cb = (b >> shift) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }
}
