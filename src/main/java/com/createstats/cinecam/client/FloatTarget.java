package com.createstats.cinecam.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class FloatTarget extends RenderTarget {
    private final int filter;
    private final int internalFormat;
    private final int format;
    private final int type;

    FloatTarget(int width, int height, boolean linear) {
        this(width, height, linear, GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT);
    }

    FloatTarget(int width, int height, boolean linear, int internalFormat, int format, int type) {
        super(false);
        this.filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        this.internalFormat = internalFormat;
        this.format = format;
        this.type = type;
        resize(width, height, Minecraft.ON_OSX);
    }

    static FloatTarget bytes(int width, int height) {
        return new FloatTarget(width, height, false, GL30.GL_R8, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE);
    }

    @Override
    public void createBuffers(int width, int height, boolean clearError) {
        super.createBuffers(width, height, clearError);
        GlStateManager._bindTexture(colorTextureId);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, this.width, this.height, 0, format, type, null);
        GlStateManager._bindTexture(0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBufferId);
        checkStatus();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        setFilterMode(filter);
        clear(clearError);
    }
}
