package com.createstats.renderer;

import com.createstats.blockentity.BattleScreenDisplayBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;

@OnlyIn(Dist.CLIENT)
public class BattleScreenDisplayRenderer implements BlockEntityRenderer<BattleScreenDisplayBE> {
    private static final ResourceLocation BLANK_TEXTURE = Objects.requireNonNull(ResourceLocation.tryParse("create_stats:textures/special/blank.png"));

    public BattleScreenDisplayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(BattleScreenDisplayBE blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(48);
    }

    @Override
    public void render(BattleScreenDisplayBE display, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        List<BattleScreenDisplayBE.LinkedObserver> observers = display.getLinkedObservers();
        double health = overallHealth(observers);
        int missing = totalMissing(observers);
        int color = healthColor(health, missing);
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        // FE Dependent Mode: a dead tactical screen projects nothing at all, and an unstable
        // supply makes the projection boot and flicker. Returns 1.0 while the mode is off.
        if (com.createstats.power.client.FePowerVisuals.isDead(display.feConsumer())) {
            return;
        }
        final float feBrightness = com.createstats.power.client.FePowerVisuals
                .screenBrightness(display.getBlockPos(), display.feConsumer());
        if (feBrightness < 0.999f) {
            red = Math.round(red * feBrightness);
            green = Math.round(green * feBrightness);
            blue = Math.round(blue * feBrightness);
        }
        long gameTime = display.getLevel() != null ? display.getLevel().getGameTime() : 0L;
        float time = gameTime + partialTick;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.82, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.1f));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(BLANK_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        renderBaseRings(consumer, matrix, normal, red, green, blue, missing);
        renderShipSilhouette(consumer, matrix, normal, red, green, blue, health);
        renderObserverPips(consumer, matrix, normal, observers, time);
        poseStack.popPose();

        // [1]
        try {
            com.createstats.client.battle.BattleTacticalV2PanelHologram.renderAboveBridge(
                    display, poseStack, buffer, packedLight, partialTick);
        } catch (Throwable ignored) {
        }
    }

    private static void renderBaseRings(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int red, int green, int blue, int missing) {
        int alpha = missing > 0 ? 96 : 70;
        for (int index = 0; index < 32; index++) {
            float angle0 = (float) (Math.PI * 2.0 * index / 32.0);
            float angle1 = (float) (Math.PI * 2.0 * (index + 1) / 32.0);
            HologramRenderHelper.emitHorizontalRingSegment(consumer, matrix, normal, 0.35f, 0.43f, angle0, angle1, 0.0f, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        }
        for (int index = 0; index < 24; index++) {
            float angle0 = (float) (Math.PI * 2.0 * index / 24.0);
            float angle1 = (float) (Math.PI * 2.0 * (index + 1) / 24.0);
            HologramRenderHelper.emitHorizontalRingSegment(consumer, matrix, normal, 0.16f, 0.19f, angle0, angle1, 0.16f, red, green, blue, 88, HologramRenderHelper.FULL_BRIGHT);
        }
    }

    private static void renderShipSilhouette(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int red, int green, int blue, double health) {
        int alpha = health < 55.0 ? 172 : 136;
        HologramRenderHelper.emitCube(consumer, matrix, normal, 0.0f, 0.2f, 0.0f, 0.045f, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        HologramRenderHelper.emitLine3D(consumer, matrix, normal, 0.0f, 0.2f, -0.28f, 0.0f, 0.2f, 0.28f, 0.012f, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        HologramRenderHelper.emitLine3D(consumer, matrix, normal, -0.26f, 0.2f, 0.02f, 0.26f, 0.2f, 0.02f, 0.012f, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        HologramRenderHelper.emitLine3D(consumer, matrix, normal, -0.14f, 0.2f, -0.18f, 0.14f, 0.2f, -0.18f, 0.009f, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
    }

    private static void renderObserverPips(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, List<BattleScreenDisplayBE.LinkedObserver> observers, float time) {
        int maxPips = Math.min(8, observers.size());
        for (int index = 0; index < maxPips; index++) {
            BattleScreenDisplayBE.LinkedObserver observer = observers.get(index);
            int color = healthColor(observer.healthPercent(), observer.missingBlocks());
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            float angle = (float) (Math.PI * 2.0 * index / Math.max(1, maxPips)) + time * 0.015f;
            float radius = observer.missingBlocks() > 0 ? 0.36f : 0.28f;
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;
            float size = observer.missingBlocks() > 0 ? 0.025f : 0.017f;
            int alpha = observer.missingBlocks() > 0 ? 220 : 150;
            HologramRenderHelper.emitCube(consumer, matrix, normal, x, 0.12f + index % 3 * 0.035f, z, size, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        }
    }

    private static double overallHealth(List<BattleScreenDisplayBE.LinkedObserver> observers) {
        if (observers == null || observers.isEmpty()) {
            return 100.0;
        }
        int baseline = 0;
        int present = 0;
        double sum = 0.0;
        for (BattleScreenDisplayBE.LinkedObserver observer : observers) {
            baseline += observer.baselineBlocks();
            present += observer.presentBaselineBlocks();
            sum += observer.healthPercent();
        }
        return baseline > 0 ? clamp(present * 100.0 / baseline, 0.0, 100.0) : sum / observers.size();
    }

    private static int totalMissing(List<BattleScreenDisplayBE.LinkedObserver> observers) {
        int missing = 0;
        for (BattleScreenDisplayBE.LinkedObserver observer : observers) {
            missing += observer.missingBlocks();
        }
        return missing;
    }

    private static int healthColor(double healthPercent, int missingBlocks) {
        if (healthPercent >= 85.0 && missingBlocks == 0) {
            return 0x62E8A3;
        }
        if (healthPercent >= 55.0) {
            return 0xFFC15E;
        }
        return 0xFF5D69;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
