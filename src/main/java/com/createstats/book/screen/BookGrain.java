package com.createstats.book.screen;

import com.createstats.book.BookTokens;
import com.createstats.book.layout.Grain;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class BookGrain {
    static final ResourceLocation PAPER = ResourceLocation.fromNamespaceAndPath("create_stats", "book/paper_grain");
    static final ResourceLocation VINYL = ResourceLocation.fromNamespaceAndPath("create_stats", "book/vinyl_grain");

    private static DynamicTexture paper;
    private static DynamicTexture vinyl;

    private BookGrain() {
    }

    static void ensure() {
        final TextureManager tm = Minecraft.getInstance().getTextureManager();
        paper = bake(tm, PAPER, paper, BookTokens.PAPER, BookTokens.PAPER_GRAIN, 21);
        vinyl = bake(tm, VINYL, vinyl, BookTokens.VINYL, BookTokens.VINYL_GRAIN, 11);
    }

    private static DynamicTexture bake(TextureManager tm, ResourceLocation id, DynamicTexture held, int base,
                                       float amp, int seed) {
        final AbstractTexture present = tm.getTexture(id, null);
        if (held != null && present == held) {
            return held;
        }
        final int size = Grain.TILE;
        final NativeImage img = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                final int argb = Grain.colour(base, amp, x, y, BookTokens.GRAIN_CELL, seed);

                final int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                img.setPixelRGBA(x, y, abgr);
            }
        }
        final DynamicTexture tex = new DynamicTexture(img);
        tm.register(id, tex);
        return tex;
    }

    static void tile(GuiGraphics g, ResourceLocation id, int x0, int y0, int x1, int y1) {
        final int t = Grain.TILE;
        for (int y = y0; y < y1; y += t) {
            for (int x = x0; x < x1; x += t) {
                final int w = Math.min(t, x1 - x);
                final int h = Math.min(t, y1 - y);
                g.blit(id, x, y, 0, 0, w, h, t, t);
            }
        }
    }
}
