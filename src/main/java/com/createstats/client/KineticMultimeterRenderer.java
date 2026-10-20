package com.createstats.client;

import com.createstats.data.KineticMultimeterData;
import com.createstats.data.KineticMultimeterData.Reading;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class KineticMultimeterRenderer extends BlockEntityWithoutLevelRenderer {
    private static KineticMultimeterRenderer instance;

    private static final String BASE = "create_stats:textures/item/";
    private static final ResourceLocation TEX_BODY   = ResourceLocation.parse(BASE + "kinetic_multimeter_body.png");
    private static final ResourceLocation TEX_COG    = ResourceLocation.parse(BASE + "kinetic_multimeter_cog.png");
    private static final ResourceLocation TEX_NEEDLE = ResourceLocation.parse(BASE + "kinetic_multimeter_needle.png");
    private static final ResourceLocation TEX_GLARE  = ResourceLocation.parse(BASE + "kinetic_multimeter_glare.png");
    private static final ResourceLocation TEX_GLOW   = ResourceLocation.parse(BASE + "kinetic_multimeter_glow.png");

    private static final float HUB_X = 15f / 32f - 0.5f;
    private static final float HUB_Y = 0.5f - 11f / 32f;
    private static final float COG_X = 15f / 32f - 0.5f;
    private static final float COG_Y = 0.5f - 21.6f / 32f;

    private static final float Z_STEP = 0.0015f;

    private float cogAngle = 0f;
    private float needleAngle = -135f;
    private float needleVel = 0f;
    private long lastNanos = System.nanoTime();

    public KineticMultimeterRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    public static KineticMultimeterRenderer getInstance() {
        if (instance == null) {
            instance = new KineticMultimeterRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ms,
                             MultiBufferSource buffer, int light, int overlay) {
        Reading r = KineticMultimeterData.read(stack);

        long now = System.nanoTime();
        float dt = Mth.clamp((now - lastNanos) / 1.0e9f, 0f, 0.05f);
        lastNanos = now;
        float t = now / 1.0e9f;

        float fresh;
        Minecraft mc = Minecraft.getInstance();
        if (r.state() == KineticMultimeterData.STATE_NONET) {
            fresh = 0f;
        } else if (mc.level != null) {
            long age = mc.level.getGameTime() - r.time();
            fresh = 1f - Mth.clamp((age - 160f) / 40f, 0f, 1f);
        } else {
            fresh = 1f;
        }

        float rpm = r.rpm() * fresh;
        float absRpm = Math.abs(rpm);
        boolean over = r.state() == KineticMultimeterData.STATE_OVER;

        float cogRate = absRpm < 0.01f ? 20f : Math.signum(rpm) * Mth.clamp(absRpm * 1.2f, 20f, 720f);
        cogAngle = (cogAngle + cogRate * dt) % 360f;

        float f = Mth.clamp((float) (Math.log1p(absRpm) / Math.log1p(256.0)), 0f, 1f);
        float target = -135f + 270f * f;
        if (absRpm < 0.01f) {
            target += Mth.sin(t * 3.14f) * 1.5f;
        }
        if (over) {
            target += Mth.sin(t * 12.56f) * 3f;
        }
        float accel = 90f * (target - needleAngle) - 12f * needleVel;
        needleVel += accel * dt;
        needleAngle += needleVel * dt;

        ms.pushPose();
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.scale(1f, 1f, 1f);

        PoseStack.Pose pose = ms.last();
        int fullBright = LightTexture.FULL_BRIGHT;

        quad(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEX_BODY)), 0f, 0f, 0f, 0f,
            light, overlay, 255, 255, 255, 255);

        quad(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEX_COG)), Z_STEP, cogAngle, COG_X, COG_Y,
            light, overlay, 255, 255, 255, 255);

        quad(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEX_NEEDLE)), 2 * Z_STEP, needleAngle, HUB_X, HUB_Y,
            light, overlay, 255, 255, 255, 255);

        quad(pose, buffer.getBuffer(RenderType.entityTranslucentCull(TEX_GLARE)), 3 * Z_STEP, 0f, 0f, 0f,
            light, overlay, 255, 255, 255, 255);

        int[] tint = stateTint(r.state(), now);
        int alpha = (int) (tint[3] * fresh);
        if (alpha > 0) {
            quad(pose, buffer.getBuffer(RenderType.entityTranslucentEmissive(TEX_GLOW)), 4 * Z_STEP, 0f, 0f, 0f,
                fullBright, overlay, tint[0], tint[1], tint[2], alpha);
        }

        ms.popPose();
    }

    private static int[] stateTint(byte state, long now) {
        float t = now / 1.0e9f;
        return switch (state) {
            case KineticMultimeterData.STATE_OK -> new int[]{95, 176, 74, 60};
            case KineticMultimeterData.STATE_WARN -> new int[]{224, 163, 58, 95};
            case KineticMultimeterData.STATE_OVER -> {
                int a = (int) (110 + 90 * (0.5f + 0.5f * Mth.sin(t * 9f)));
                yield new int[]{224, 70, 58, a};
            }
            default -> new int[]{70, 90, 120, 34};
        };
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float zOff, float rotDeg,
                             float pivX, float pivY, int light, int overlay,
                             int red, int green, int blue, int alpha) {
        float rad = rotDeg * Mth.DEG_TO_RAD;
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);

        float[][] c = {
            {-0.5f,  0.5f, 0f, 0f},
            {-0.5f, -0.5f, 0f, 1f},
            { 0.5f, -0.5f, 1f, 1f},
            { 0.5f,  0.5f, 1f, 0f},
        };
        for (float[] v : c) {
            float dx = v[0] - pivX;
            float dy = v[1] - pivY;
            float rx = pivX + dx * cos - dy * sin;
            float ry = pivY + dx * sin + dy * cos;
            vc.addVertex(pose, rx, ry, zOff)
                .setColor(red, green, blue, alpha)
                .setUv(v[2], v[3])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
        }
    }
}
