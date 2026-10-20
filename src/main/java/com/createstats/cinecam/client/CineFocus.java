package com.createstats.cinecam.client;

import com.createstats.CreateStats;
import com.createstats.cinecam.CineCameraBE;
import com.createstats.cinecam.core.CineMode;
import com.createstats.cinecam.core.FocusPull;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Method;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class CineFocus {
    private static final float SPREAD = 0.004f;

    static final double LOCK_MARGIN = 0.5;

    @Nullable private static EffectInstance meter;
    @Nullable private static EffectInstance prefilter;
    @Nullable private static EffectInstance bokeh;
    @Nullable private static EffectInstance composite;
    @Nullable private static FloatTarget meterA;
    @Nullable private static FloatTarget meterB;
    @Nullable private static FloatTarget half;
    @Nullable private static FloatTarget blur;
    @Nullable private static TextureTarget full;
    private static boolean flip;
    private static boolean broken;
    private static boolean snap = true;
    private static long lastNanos;

    private CineFocus() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        CineCameraBE cam = CineView.cameraBE();

        if (!CineView.drawingLens() || cam == null || cam.cineMode() != CineMode.FILM || !FocusPull.on(CineView.tStop)) {
            snap = true;
            return;
        }
        if (broken || Iris.shaderPackOn()) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int depth = depthTexture(mc, main);
        if (depth <= 0 || !ready(mc.getResourceManager(), main.width, main.height)) return;

        long nanos = System.nanoTime();
        float blend = snap ? 1f : (float) FocusPull.ease(Math.min(0.1, (nanos - lastNanos) / 1e9), FocusPull.TAU_FOCUS);
        lastNanos = nanos;
        snap = false;
        Matrix4f proj = event.getProjectionMatrix();
        float m22 = proj.m22();
        float m32 = proj.m32();
        float w = main.width;
        float h = main.height;
        float scale = (float) FocusPull.circleScale(CineView.tStop, proj.m11(), h);
        float max = (float) FocusPull.maxCircle(h);
        FloatTarget prev = flip ? meterB : meterA;
        FloatTarget next = flip ? meterA : meterB;
        flip = !flip;
        int colour = main.getColorTextureId();

        int[] scissor = CineView.awayScissor();
        if (scissor != null) RenderSystem.disableScissor();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);

        meter.setSampler("DepthSampler", () -> depth);
        meter.setSampler("PrevSampler", prev::getColorTextureId);
        meter.safeGetUniform("ProjParams").set(m22, m32);
        meter.safeGetUniform("Spread").set(SPREAD * h / w, SPREAD);
        meter.safeGetUniform("Lock").set(CineView.lockDistance, CineView.lockRadius);
        meter.safeGetUniform("Blend").set(blend);
        draw(meter, next);

        prefilter.setSampler("DiffuseSampler", () -> colour);
        prefilter.setSampler("DepthSampler", () -> depth);
        prefilter.setSampler("FocusSampler", next::getColorTextureId);
        prefilter.safeGetUniform("ProjParams").set(m22, m32);
        prefilter.safeGetUniform("FullTexel").set(1f / w, 1f / h);
        prefilter.safeGetUniform("CocScale").set(scale / 2f);
        prefilter.safeGetUniform("MaxCoc").set(max / 2f);
        draw(prefilter, half);

        bokeh.setSampler("HalfSampler", half::getColorTextureId);
        bokeh.safeGetUniform("HalfTexel").set(1f / half.width, 1f / half.height);
        bokeh.safeGetUniform("MaxCoc").set(max / 2f);
        draw(bokeh, blur);

        composite.setSampler("DiffuseSampler", () -> colour);
        composite.setSampler("DepthSampler", () -> depth);
        composite.setSampler("BlurSampler", blur::getColorTextureId);
        composite.setSampler("FocusSampler", next::getColorTextureId);
        composite.safeGetUniform("ProjParams").set(m22, m32);
        composite.safeGetUniform("CocScale").set(scale);
        composite.safeGetUniform("MaxCoc").set(max);
        draw(composite, full);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, full.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, full.width, full.height, 0, 0, main.width, main.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        if (scissor != null) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
    }

    static void draw(EffectInstance fx, RenderTarget out) {
        out.bindWrite(true);
        fx.apply();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(0f, 0f, 0f);
        b.addVertex(1f, 0f, 0f);
        b.addVertex(1f, 1f, 0f);
        b.addVertex(0f, 1f, 0f);
        BufferUploader.draw(b.buildOrThrow());
        fx.clear();
    }

    private static int depthTexture(Minecraft mc, RenderTarget main) {
        if (Minecraft.useShaderTransparency()) {
            RenderTarget translucent = mc.levelRenderer.getTranslucentTarget();
            if (translucent != null) return translucent.getDepthTextureId();
        }
        return main.getDepthTextureId();
    }

    private static boolean ready(ResourceProvider resources, int w, int h) {
        if (meter == null) {
            try {
                meter = new EffectInstance(resources, CreateStats.MOD_ID + ":cine_meter");
                prefilter = new EffectInstance(resources, CreateStats.MOD_ID + ":cine_prefilter");
                bokeh = new EffectInstance(resources, CreateStats.MOD_ID + ":cine_bokeh");
                composite = new EffectInstance(resources, CreateStats.MOD_ID + ":cine_composite");
            } catch (IOException | RuntimeException e) {
                CreateStats.LOGGER.error("Cine Camera: the focus pull's shaders did not load, so Film mode runs without it", e);
                close();
                broken = true;
                return false;
            }
        }
        int hw = Math.max(1, (w + 1) / 2);
        int hh = Math.max(1, (h + 1) / 2);
        if (full == null) {
            meterA = new FloatTarget(1, 1, false);
            meterB = new FloatTarget(1, 1, false);
            half = new FloatTarget(hw, hh, false);
            blur = new FloatTarget(hw, hh, true);
            full = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            snap = true;
        } else if (full.width != w || full.height != h) {
            half.resize(hw, hh, Minecraft.ON_OSX);
            blur.resize(hw, hh, Minecraft.ON_OSX);
            full.resize(w, h, Minecraft.ON_OSX);
        }
        return true;
    }

    static void snap() {
        snap = true;
    }

    static void close() {
        for (EffectInstance fx : new EffectInstance[]{meter, prefilter, bokeh, composite}) {
            if (fx != null) fx.close();
        }
        meter = prefilter = bokeh = composite = null;
        for (RenderTarget t : new RenderTarget[]{meterA, meterB, half, blur, full}) {
            if (t != null) t.destroyBuffers();
        }
        meterA = meterB = half = blur = null;
        full = null;
        broken = false;
        snap = true;
    }

    private static final class Iris {
        @Nullable private static Object api;
        @Nullable private static Method inUse;
        private static boolean looked;

        static boolean shaderPackOn() {
            if (!looked) {
                looked = true;
                try {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    inUse = c.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    api = null;
                }
            }
            if (api == null || inUse == null) return false;
            try {
                return Boolean.TRUE.equals(inUse.invoke(api));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
    }
}
