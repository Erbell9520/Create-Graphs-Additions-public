package com.createstats.gui.meter.plate;

import com.createstats.renderer.BitmapFont;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

// monospaced so digits sit in columns
@OnlyIn(Dist.CLIENT)
public final class GlassFont {
    public static final int PITCH = 6;
    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;

    private static final int COLS = 16;
    private static final int CELL_H = 9;
    private static final int TEX_W = COLS * PITCH;
    private static final int TEX_H = 6 * CELL_H;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "dynamic/meter_glass_font");

    private static DynamicTexture texture;

    private GlassFont() {
    }

    public static void ensure() {
        final TextureManager tm = Minecraft.getInstance().getTextureManager();
        final AbstractTexture present = tm.getTexture(TEXTURE, null);
        if (texture != null && present == texture) {
            return;
        }
        final NativeImage img = new NativeImage(TEX_W, TEX_H, true);
        for (int idx = 0; idx < 95; idx++) {
            final byte[] rows = BitmapFont.glyphRows((char) (' ' + idx));
            final int cx = (idx % COLS) * PITCH;
            final int cy = (idx / COLS) * CELL_H;
            for (int r = 0; r < GLYPH_H; r++) {
                for (int c = 0; c < GLYPH_W; c++) {
                    if ((rows[r] & (1 << (GLYPH_W - 1 - c))) != 0) {
                        img.setPixelRGBA(cx + c, cy + r, 0xFFFFFFFF);
                    }
                }
            }
        }
        texture = new DynamicTexture(img);
        tm.register(TEXTURE, texture);
    }

    public static int width(String text) {
        return text.isEmpty() ? 0 : text.length() * PITCH - 1;
    }

    public static int width(String text, int scale) {
        return width(text) * scale;
    }

    public static int draw(GuiGraphics g, String text, int x, int y, int argb, int scale) {
        g.setColor(((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f,
                ((argb >>> 24) & 0xFF) / 255.0f);
        for (int i = 0; i < text.length(); i++) {
            int idx = text.charAt(i) - ' ';
            if (idx < 0 || idx > 94) {
                idx = 31;
            }
            if (idx == 0) {
                continue;
            }
            g.blit(TEXTURE, x + i * PITCH * scale, y, GLYPH_W * scale, GLYPH_H * scale,
                    (idx % COLS) * PITCH, (idx / COLS) * CELL_H, GLYPH_W, GLYPH_H, TEX_W, TEX_H);
        }
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        return width(text, scale);
    }

    public static int glow(GuiGraphics g, String text, int x, int y, PlateInk.Ink ink, int scale) {
        draw(g, text, x - 1, y, ink.halo(), scale);
        draw(g, text, x + 1, y, ink.halo(), scale);
        draw(g, text, x, y - 1, ink.halo(), scale);
        draw(g, text, x, y + 1, ink.halo(), scale);
        return draw(g, text, x, y, ink.main(), scale);
    }

    public static List<String> split(String text, int maxW) {
        final List<String> out = new ArrayList<>();
        String line = "";
        for (final String word : text.split(" ")) {
            final String cand = line.isEmpty() ? word : line + " " + word;
            if (width(cand) <= maxW || line.isEmpty()) {
                line = cand;
            } else {
                out.add(line);
                line = word;
            }
        }
        if (!line.isEmpty()) {
            out.add(line);
        }
        return out;
    }
}
