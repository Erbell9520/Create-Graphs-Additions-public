package com.createstats.statsmk3;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;

public final class GlyphSurface implements PanelSurface {
    private static final String GRID = "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\r\u000e\u000f"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
            + " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u007f";

    private static boolean[][][] glyphs;
    private static int[] advance;
    private static int cell;

    private final RasterSurface r;

    public GlyphSurface(int width, int height, int background) {
        this.r = new RasterSurface(width, height, background);
        load();
    }

    public BufferedImage finish() {
        return this.r.finish();
    }

    private static synchronized void load() {
        if (glyphs != null) {
            return;
        }
        final BufferedImage sheet;
        try {
            sheet = ImageIO.read(new File("assets_ascii.png"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cell = sheet.getWidth() / 16;
        glyphs = new boolean[GRID.length()][cell][cell];
        advance = new int[GRID.length()];
        for (int i = 0; i < GRID.length(); i++) {
            final int ox = (i % 16) * cell;
            final int oy = (i / 16) * cell;
            int right = -1;
            for (int y = 0; y < cell; y++) {
                for (int x = 0; x < cell; x++) {
                    final boolean lit = ((sheet.getRGB(ox + x, oy + y) >>> 24) & 0xFF) > 0;
                    glyphs[i][y][x] = lit;
                    if (lit) {
                        right = Math.max(right, x);
                    }
                }
            }
            advance[i] = GRID.charAt(i) == ' ' ? 4 : right + 2;
        }
    }

    private static int index(char c) {
        final int i = GRID.indexOf(c);
        return i;
    }

    @Override
    public void text(String s, float x, float y, int color, float scale) {
        if (s == null || s.isEmpty() || ((color >>> 24) & 0xFF) == 0) {
            return;
        }

        float px = scale == 1.0f ? Math.round(x) : x;
        final float py = scale == 1.0f ? Math.round(y) : y;
        final float unit = scale * 8.0f / cell;
        for (int n = 0; n < s.length(); n++) {
            final int i = index(s.charAt(n));
            if (i < 0) {
                this.r.text(String.valueOf(s.charAt(n)), px, py, color, scale);
                px += this.r.textWidth(String.valueOf(s.charAt(n)), scale);
                continue;
            }
            final boolean[][] g = glyphs[i];
            for (int gy = 0; gy < cell; gy++) {
                for (int gx = 0; gx < cell; gx++) {
                    if (!g[gy][gx]) {
                        continue;
                    }
                    final float x0 = px + gx * unit;
                    final float y0 = py + gy * unit;
                    this.r.tri(x0, y0, color, x0 + unit, y0, color, x0 + unit, y0 + unit, color);
                    this.r.tri(x0, y0, color, x0 + unit, y0 + unit, color, x0, y0 + unit, color);
                }
            }
            px += advance[i] * scale * 8.0f / cell;
        }
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
        float w = 0.0f;
        for (int n = 0; n < s.length(); n++) {
            final int i = index(s.charAt(n));
            w += i < 0 ? this.r.textWidth(String.valueOf(s.charAt(n)), scale) : advance[i] * scale * 8.0f / cell;
        }
        return w;
    }

    @Override
    public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        this.r.tri(x1, y1, c1, x2, y2, c2, x3, y3, c3);
    }

    @Override
    public void clip(Rect rect) {
        this.r.clip(rect);
    }

    @Override
    public void unclip() {
        this.r.unclip();
    }

    @Override
    public float feather() {
        return this.r.feather();
    }

    @Override
    public boolean culled(Rect rect) {
        return this.r.culled(rect);
    }
}
