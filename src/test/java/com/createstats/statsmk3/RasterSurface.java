package com.createstats.statsmk3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;

public final class RasterSurface implements PanelSurface {
    public static final int SS = 3;

    private final int w;
    private final int h;
    private final BufferedImage image;
    private final Graphics2D g;
    private final Deque<Rect> clips = new ArrayDeque<>();

    public RasterSurface(int width, int height) {
        this(width, height, 0x000000);
    }

    public RasterSurface(int width, int height, int background) {
        this.w = width * SS;
        this.h = height * SS;
        this.image = new BufferedImage(this.w, this.h, BufferedImage.TYPE_INT_RGB);
        this.g = this.image.createGraphics();
        this.g.setColor(new Color(background));
        this.g.fillRect(0, 0, this.w, this.h);
        this.g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        this.g.scale(SS, SS);
    }

    public BufferedImage finish() {
        this.g.dispose();
        final BufferedImage out = new BufferedImage(this.w / SS, this.h / SS,
                BufferedImage.TYPE_INT_RGB);
        final Graphics2D d = out.createGraphics();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        d.drawImage(this.image, 0, 0, out.getWidth(), out.getHeight(), null);
        d.dispose();
        return out;
    }

    @Override
    public void tri(float x1, float y1, int c1, float x2, float y2, int c2,
                    float x3, float y3, int c3) {
        if (((c1 >>> 24) & 0xFF) == 0 && ((c2 >>> 24) & 0xFF) == 0 && ((c3 >>> 24) & 0xFF) == 0) {
            return;
        }
        fill(x1 * SS, y1 * SS, c1, x2 * SS, y2 * SS, c2, x3 * SS, y3 * SS, c3);
    }

    private void fill(float ax, float ay, int c1, float bx, float by, int c2,
                      float cx, float cy, int c3) {
        final float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1.0e-7f) {
            return;
        }
        if (area < 0.0f) {
            fill(ax, ay, c1, cx, cy, c3, bx, by, c2);
            return;
        }

        final Rect clip = this.clips.peek();
        final float loX = clip == null ? 0.0f : clip.x * SS;
        final float hiX = clip == null ? this.w : clip.right() * SS;
        final float loY = clip == null ? 0.0f : clip.y * SS;
        final float hiY = clip == null ? this.h : clip.bottom() * SS;
        final int minX = Math.max((int) Math.floor(Math.max(0.0f, loX)),
                (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        final int maxX = Math.min((int) Math.ceil(Math.min(this.w - 1.0f, hiX)),
                (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        final int minY = Math.max((int) Math.floor(Math.max(0.0f, loY)),
                (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        final int maxY = Math.min((int) Math.ceil(Math.min(this.h - 1.0f, hiY)),
                (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        final float inv = 1.0f / area;

        final boolean tlAB = topLeft(bx - ax, by - ay);
        final boolean tlBC = topLeft(cx - bx, cy - by);
        final boolean tlCA = topLeft(ax - cx, ay - cy);

        for (int py = minY; py <= maxY; py++) {
            final float sy = py + 0.5f;
            for (int px = minX; px <= maxX; px++) {
                final float sx = px + 0.5f;
                final float eAB = (bx - ax) * (sy - ay) - (by - ay) * (sx - ax);
                final float eBC = (cx - bx) * (sy - by) - (cy - by) * (sx - bx);
                final float eCA = (ax - cx) * (sy - cy) - (ay - cy) * (sx - cx);
                if (!covers(eAB, tlAB) || !covers(eBC, tlBC) || !covers(eCA, tlCA)) {
                    continue;
                }
                final float u = eBC * inv;
                final float v = eCA * inv;
                final float t = eAB * inv;
                blend(px, py,
                        lerp(c1, c2, c3, u, v, t, 24),
                        lerp(c1, c2, c3, u, v, t, 16),
                        lerp(c1, c2, c3, u, v, t, 8),
                        lerp(c1, c2, c3, u, v, t, 0));
            }
        }
    }

    private static boolean topLeft(float dx, float dy) {
        return dy < 0.0f || (dy == 0.0f && dx > 0.0f);
    }

    private static boolean covers(float edge, boolean isTopLeft) {
        return edge > 0.0f || (edge == 0.0f && isTopLeft);
    }

    private static float lerp(int c1, int c2, int c3, float u, float v, float t, int shift) {
        return ((c1 >>> shift) & 0xFF) * u + ((c2 >>> shift) & 0xFF) * v
                + ((c3 >>> shift) & 0xFF) * t;
    }

    private void blend(int x, int y, float a, float r, float gr, float b) {
        final float alpha = a / 255.0f;
        if (alpha <= 0.002f) {
            return;
        }
        final int dst = this.image.getRGB(x, y);
        final int nr = clamp(r * alpha + ((dst >> 16) & 0xFF) * (1.0f - alpha));
        final int ng = clamp(gr * alpha + ((dst >> 8) & 0xFF) * (1.0f - alpha));
        final int nb = clamp(b * alpha + (dst & 0xFF) * (1.0f - alpha));
        this.image.setRGB(x, y, nr << 16 | ng << 8 | nb);
    }

    private static int clamp(float v) {
        return v <= 0.0f ? 0 : v >= 255.0f ? 255 : Math.round(v);
    }

    @Override
    public void text(String s, float x, float y, int color, float scale) {
        if (s == null || s.isEmpty() || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        this.g.setColor(new Color(color, true));
        this.g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(1, Math.round(8.0f * scale))));
        final Rect clip = this.clips.peek();
        if (clip != null) {
            this.g.setClip((int) Math.floor(clip.x), (int) Math.floor(clip.y),
                    (int) Math.ceil(clip.w) + 1, (int) Math.ceil(clip.h) + 1);
        }

        this.g.drawString(s, x, y + 7.0f * scale);
        this.g.setClip(null);
    }

    @Override
    public void textCenter(String s, float centerX, float y, int color, float scale) {
        text(s, centerX - textWidth(s, scale) * 0.5f, y, color, scale);
    }

    @Override
    public void textRight(String s, float rightX, float y, int color, float scale) {
        text(s, rightX - textWidth(s, scale), y, color, scale);
    }

    @Override
    public float textWidth(String s, float scale) {
        if (s == null) {
            return 0.0f;
        }
        int width = 0;
        for (int i = 0; i < s.length(); i++) {
            width += switch (s.charAt(i)) {
                case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 2;
                case 'l', '`' -> 3;
                case ' ', 't', 'I', '[', ']' -> 4;
                case 'f', 'k', '(', ')', '{', '}', '<', '>', '"', '*' -> 5;
                case '@' -> 7;
                default -> 6;
            };
        }
        return width * scale;
    }

    @Override
    public float feather() {
        return 0.5f;
    }

    @Override
    public void clip(Rect r) {
        final Rect current = this.clips.peek();
        this.clips.push(current == null ? r : current.intersect(r));
    }

    @Override
    public void unclip() {
        if (!this.clips.isEmpty()) {
            this.clips.pop();
        }
    }

    @Override
    public boolean culled(Rect r) {
        final Rect current = this.clips.peek();
        return r.isEmpty() || (current != null && current.intersect(r).isEmpty());
    }
}
