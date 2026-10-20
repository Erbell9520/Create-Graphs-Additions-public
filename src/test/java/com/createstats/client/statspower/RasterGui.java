package com.createstats.client.statspower;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;

public class RasterGui extends GuiGraphics {
    private BufferedImage image;
    private int[] pixels;
    private int width;
    private int height;
    private PoseStack poseStack;
    private Deque<int[]> scissors;
    private float tintR = 1.0f;
    private float tintG = 1.0f;
    private float tintB = 1.0f;
    private float tintA = 1.0f;
    private Graphics2D text;
    private List<String[]> tooltips;
    private List<Runnable> deferredText;

    private RasterGui() {
        super((net.minecraft.client.Minecraft) null,
                (net.minecraft.client.renderer.MultiBufferSource.BufferSource) null);
        throw new UnsupportedOperationException("allocate through create()");
    }

    public static RasterGui create(int width, int height, int background) {
        try {
            final Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            final RasterGui gui = (RasterGui) unsafe.allocateInstance(RasterGui.class);
            gui.init(width, height, background);
            return gui;
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("cannot allocate a RasterGui", e);
        }
    }

    private void init(int w, int h, int background) {
        this.width = w;
        this.height = h;
        this.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        this.pixels = new int[w * h];
        java.util.Arrays.fill(this.pixels, background | 0xFF000000);
        this.poseStack = new PoseStack();
        this.scissors = new ArrayDeque<>();
        this.tooltips = new ArrayList<>();

        this.deferredText = new ArrayList<>();
        this.text = this.image.createGraphics();
        this.text.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        this.text.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    public BufferedImage finish() {
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                this.image.setRGB(x, y, this.pixels[y * this.width + x]);
            }
        }

        for (final Runnable draw : this.deferredText) {
            draw.run();
        }
        this.deferredText.clear();
        this.text.dispose();
        return this.image;
    }

    public List<String[]> tooltips() {
        return this.tooltips;
    }

    private void plot(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
            return;
        }
        final int[] clip = this.scissors.peek();
        if (clip != null && (x < clip[0] || y < clip[1] || x >= clip[2] || y >= clip[3])) {
            return;
        }
        final int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
            return;
        }
        final int index = y * this.width + x;
        if (a == 0xFF) {
            this.pixels[index] = argb | 0xFF000000;
            return;
        }
        final int dst = this.pixels[index];
        final float f = a / 255.0f;
        final int r = Math.round(((argb >> 16) & 0xFF) * f + ((dst >> 16) & 0xFF) * (1.0f - f));
        final int g = Math.round(((argb >> 8) & 0xFF) * f + ((dst >> 8) & 0xFF) * (1.0f - f));
        final int b = Math.round((argb & 0xFF) * f + (dst & 0xFF) * (1.0f - f));
        this.pixels[index] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void rect(int x1, int y1, int x2, int y2, int argb) {
        final int lx = Math.min(x1, x2);
        final int hx = Math.max(x1, x2);
        final int ly = Math.min(y1, y2);
        final int hy = Math.max(y1, y2);
        for (int y = ly; y < hy; y++) {
            for (int x = lx; x < hx; x++) {
                plot(x, y, argb);
            }
        }
    }

    @Override
    public PoseStack pose() {
        return this.poseStack;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        rect(x1, y1, x2, y2, color);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        rect(x1, y1, x2, y2, color);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        final int ly = Math.min(y1, y2);
        final int hy = Math.max(y1, y2);
        final int span = Math.max(1, hy - ly - 1);
        for (int y = ly; y < hy; y++) {
            final float t = (y - ly) / (float) span;
            rect(Math.min(x1, x2), y, Math.max(x1, x2), y + 1, lerp(top, bottom, t));
        }
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int z, int top, int bottom) {
        fillGradient(x1, y1, x2, y2, top, bottom);
    }

    private static int lerp(int a, int b, float t) {
        final int aa = Math.round(((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        final int ar = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        final int ag = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        final int ab = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        final int[] top = this.scissors.peek();
        int[] next = {x1, y1, x2, y2};
        if (top != null) {
            next = new int[]{Math.max(top[0], x1), Math.max(top[1], y1),
                    Math.min(top[2], x2), Math.min(top[3], y2)};
        }
        this.scissors.push(next);
    }

    @Override
    public void disableScissor() {
        if (!this.scissors.isEmpty()) {
            this.scissors.pop();
        }
    }

    @Override
    public void setColor(float r, float g, float b, float a) {
        this.tintR = r;
        this.tintG = g;
        this.tintB = b;
        this.tintA = a;
    }

    @Override
    public void blit(int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
        final int argb = ((int) (this.tintA * 255) << 24)
                | ((int) (this.tintR * 255) << 16)
                | ((int) (this.tintG * 255) << 8)
                | (int) (this.tintB * 255);
        rect(x, y, x + w, y + h, argb);
    }

    @Override
    public int drawString(Font font, String string, int x, int y, int color) {
        return drawString(font, string, x, y, color, false);
    }

    @Override
    public int drawString(Font font, String string, int x, int y, int color, boolean shadow) {
        if (string == null || string.isEmpty()) {
            return x;
        }
        final int[] clip = this.scissors.isEmpty() ? null : this.scissors.peek().clone();
        final int fx = x;
        final int fy = y;
        final String s = string;
        final int rgb = color & 0xFFFFFF;
        final int alpha = (color >>> 24) == 0 ? 0xFF : (color >>> 24);
        this.deferredText.add(() -> RasterFont.paint(this.text, s, fx, fy, rgb, alpha, clip));
        return x + RasterFont.widthOf(string);
    }

    @Override
    public void renderComponentTooltip(Font font, List<Component> lines, int mouseX, int mouseY) {
        final String[] text = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            text[i] = lines.get(i).getString();
        }
        this.tooltips.add(text);
    }
}
