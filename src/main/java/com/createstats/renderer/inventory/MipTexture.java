package com.createstats.renderer.inventory;

import com.createstats.renderer.BitmapFont;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MipTexture extends AbstractTexture {
    private final int width;
    private final int height;
    private final int maxLevel;
    private final NativeImage[] images;

    public MipTexture(int width, int height, int maxLevel) {
        RenderSystem.assertOnRenderThreadOrInit();
        this.width = width;
        this.height = height;
        this.maxLevel = maxLevel;
        this.images = new NativeImage[maxLevel + 1];
        for (int level = 0; level <= maxLevel; level++) {
            this.images[level] = new NativeImage(NativeImage.Format.RGBA, width >> level, height >> level, true);
        }
        TextureUtil.prepareImage(NativeImage.InternalGlFormat.RGBA, getId(), maxLevel, width, height);
    }

    @Override
    public void load(ResourceManager manager) {
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public void upload(int[] argb) {
        final int[][] levels = MipChain.build(argb, this.width, this.height, this.maxLevel);
        bind();
        for (int level = 0; level <= this.maxLevel; level++) {
            final NativeImage image = this.images[level];
            final int w = this.width >> level;
            final int h = this.height >> level;
            final int[] px = levels[level];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    image.setPixelRGBA(x, y, BitmapFont.argbToAbgr(px[y * w + x]));
                }
            }
            image.upload(level, 0, 0, false);
        }
    }

    @Override
    public void close() {
        for (int level = 0; level <= this.maxLevel; level++) {
            if (this.images[level] != null) {
                this.images[level].close();
                this.images[level] = null;
            }
        }
        releaseId();
    }
}
