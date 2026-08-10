package com.createstats.renderer;

import com.createstats.blockentity.ControlClutchBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class ControlClutchRenderer extends KineticBlockEntityRenderer<ControlClutchBE> {

    private static final float GAUGE_SCALE = 0.75f;
    private static final float NEEDLE_PIVOT_X = 8.0f / 16.0f;
    private static final float NEEDLE_PIVOT_Z = 8.0f / 16.0f;
    private static final float NEEDLE_START_ANGLE = (float) (Math.PI * 5.0 / 4.0);
    private static final float PX = 1.0f / 16.0f;
    private static final float OUTSET = 1.0f / 512.0f;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final ResourceLocation WHITE_TEX =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/special/blank.png");
    private static final IndicatorColors WAIT_COLORS = new IndicatorColors(0x24, 0x1A, 0x05, 0xFF, 0xD4, 0x45);
    private static final IndicatorColors DISCONNECTED_COLORS = new IndicatorColors(0x0C, 0x10, 0x18, 0x50, 0x72, 0xA8);
    private static final IndicatorColors RUN_COLORS = new IndicatorColors(0x0B, 0x1B, 0x11, 0x72, 0xF1, 0x92);
    private static final IndicatorColors STOP_COLORS = new IndicatorColors(0x23, 0x08, 0x0A, 0xF2, 0x4D, 0x52);

    public ControlClutchRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ControlClutchBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
                              int overlay) {
        boolean visualize = be.getLevel() != null && VisualizationManager.supportsVisualization(be.getLevel());
        if (!visualize) {
            renderShaftHalves(be, ms, buffer, light);
            renderNeedle(be, partialTicks, ms, buffer, light);
        }

        renderLinkIndicator(be, ms, buffer, light);
    }

    private void renderShaftHalves(ControlClutchBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        Block block = be.getBlockState().getBlock();
        Axis boxAxis = ((IRotate) block).getRotationAxis(be.getBlockState());
        BlockPos pos = be.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());

        for (Direction direction : Iterate.directions) {
            Axis axis = direction.getAxis();
            if (boxAxis != axis) {
                continue;
            }

            float offset = getRotationOffsetForPosition(be, pos, axis);
            float angle = (time * be.getSpeed() * 3.0f / 10.0f) % 360.0f;
            float modifier = be.getRotationSpeedModifier(direction);
            angle *= modifier;
            angle += offset;
            angle = angle / 180.0f * (float) Math.PI;

            SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), direction);
            kineticRotationTransform(shaft, be, axis, angle, light);
            shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    private void renderNeedle(ControlClutchBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light) {
        float progress = Mth.lerp(partialTicks, be.prevDialState, be.dialState);
        float angle = NEEDLE_START_ANGLE - NeedlePhysics.SWEEP_RADIANS * progress;

        SuperByteBuffer needle = CachedBuffers.partial(ModPartialModels.AUTO_TRANSMISSION_CLUTCH_GAUGE_NEEDLE, be.getBlockState());
        ms.pushPose();
        ms.translate(NEEDLE_PIVOT_X, 0.0f, NEEDLE_PIVOT_Z);
        ms.scale(GAUGE_SCALE, 1.0f, GAUGE_SCALE);
        ms.translate(-NEEDLE_PIVOT_X, 0.0f, -NEEDLE_PIVOT_Z);
        needle.translate(NEEDLE_PIVOT_X, 0.0f, NEEDLE_PIVOT_Z)
                .rotate(angle, Direction.UP)
                .translate(-NEEDLE_PIVOT_X, 0.0f, -NEEDLE_PIVOT_Z)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

    private void renderLinkIndicator(ControlClutchBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        if (be.getLevel() == null) {
            return;
        }

        float renderTime = AnimationTickHolder.getRenderTime(be.getLevel());
        Matrix4f pose = ms.last().pose();
        Matrix3f normal = ms.last().normal();
        VertexConsumer housing = buffer.getBuffer(RenderType.entityTranslucent(WHITE_TEX));
        VertexConsumer lamp = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE_TEX));
        Axis axis = ((IRotate) be.getBlockState().getBlock()).getRotationAxis(be.getBlockState());
        IndicatorStyle style = getIndicatorStyle(be, renderTime);

        renderLampFace(Direction.EAST, pose, normal, housing, lamp, style, light);
        if (axis == Axis.Y) {
            renderLampFace(Direction.WEST, pose, normal, housing, lamp, style, light);
        }
        if (axis != Axis.X) {
            renderLampFace(Direction.SOUTH, pose, normal, housing, lamp, style, light);
            if (axis == Axis.Y) {
                renderLampFace(Direction.NORTH, pose, normal, housing, lamp, style, light);
            }
        }

        renderBeacon(pose, normal, housing, lamp, style, light);
    }

    private IndicatorStyle getIndicatorStyle(ControlClutchBE be, float renderTime) {
        float phaseOffset = stablePhaseOffset(be);
        float pairingBoost = 0.0f;

        if (be.isControllerLinked() && be.getPairFeedbackTicks() > 0) {
            float cycle = frac(renderTime / 4.0f + phaseOffset);
            float flash = cycle < 0.42f ? 0.28f : 0.08f;
            float envelope = 0.55f + 0.45f * Mth.clamp(be.getPairFeedbackTicks() / 32.0f, 0.0f, 1.0f);
            pairingBoost = flash * envelope;
        }

        if (be.isControllerLinked()) {
            float signalStrength = Mth.clamp(be.getLastControllerSignal() / 15.0f, 0.0f, 1.0f);
            if (!be.isControllerRequestingRun()) {
                float cycle = frac(renderTime / 28.0f + phaseOffset);
                float stopPulse = shapedPulse(cycle, 0.08f, 0.18f);
                float brightness = Mth.clamp(0.34f + stopPulse * 0.18f + signalStrength * 0.08f + pairingBoost, 0.0f, 0.82f);
                float beacon = Mth.clamp(0.16f + stopPulse * 0.26f + pairingBoost * 0.75f, 0.0f, 0.72f);
                return buildStyle(STOP_COLORS, brightness, FULL_BRIGHT, beacon, FULL_BRIGHT);
            }

            if (be.getLastControllerSignal() <= 0) {
                float cycle = frac(renderTime / 24.0f + phaseOffset);
                float warmPulse = shapedPulse(cycle, 0.12f, 0.20f);
                float brightness = Mth.clamp(0.24f + warmPulse * 0.22f + pairingBoost * 0.75f, 0.0f, 0.78f);
                float beacon = Mth.clamp(0.10f + warmPulse * 0.20f + pairingBoost * 0.55f, 0.0f, 0.58f);
                return buildStyle(WAIT_COLORS, brightness, FULL_BRIGHT, beacon, FULL_BRIGHT);
            }

            if (be.isControllerRequestingRun()) {
                float cycle = frac(renderTime / (be.isNetworkRunning() ? 18.0f : 24.0f) + phaseOffset);
                float heartbeat = shapedPulse(cycle, 0.04f, 0.12f);
                heartbeat = Math.max(heartbeat, shapedPulse(cycle, 0.24f, 0.08f) * 0.7f);
                float base = be.isNetworkRunning() ? 0.36f : 0.24f;
                float amplitude = be.isNetworkRunning() ? 0.62f : 0.42f;
                float brightness = Mth.clamp((base + heartbeat * amplitude) * (0.78f + 0.22f * signalStrength) + pairingBoost,
                        0.0f, 1.0f);
                float beacon = Mth.clamp(0.22f + heartbeat * 0.9f + pairingBoost * 0.8f, 0.0f, 1.0f);
                return buildStyle(RUN_COLORS, brightness, FULL_BRIGHT, beacon, FULL_BRIGHT);
            }

        }

        float cycle = frac(renderTime / 20.0f + phaseOffset);
        float blink = cycle < 0.48f ? 1.0f : 0.12f;
        float brightness = Mth.clamp(0.08f + blink * 0.76f, 0.0f, 0.9f);
        float beacon = Mth.clamp(0.04f + blink * 0.46f, 0.0f, 0.62f);
        return buildStyle(DISCONNECTED_COLORS, brightness, FULL_BRIGHT, beacon, FULL_BRIGHT);
    }

    private IndicatorStyle buildStyle(IndicatorColors colors, float lampBrightness, int lampLight,
                                      float beaconBrightness, int beaconLight) {
        int housingAlpha = alphaFor(lampBrightness, 0.34f, 0x72, 0xCA);
        int lampAlpha = alphaFor(lampBrightness, 0.0f, 0x26, 0xF0);
        int beaconHousingAlpha = alphaFor(beaconBrightness, 0.0f, 0x18, 0x96);
        int beaconCoreAlpha = alphaFor(beaconBrightness, 0.0f, 0x10, 0xFF);
        return new IndicatorStyle(colors, housingAlpha, lampAlpha, lampLight, beaconHousingAlpha, beaconCoreAlpha,
                beaconLight);
    }

    private void renderBeacon(Matrix4f pose, Matrix3f normal, VertexConsumer housing, VertexConsumer lamp,
                              IndicatorStyle style, int blockLight) {
        IndicatorColors colors = style.colors();
        float outerMin = 10.85f * PX;
        float outerMax = 11.95f * PX;
        float outerBottom = 15.3f * PX;
        float outerTop = 15.95f * PX;
        float innerMin = 11.05f * PX;
        float innerMax = 11.75f * PX;
        float innerBottom = 15.42f * PX;
        float innerTop = 16.02f * PX;

        quadEast(housing, pose, normal, outerMax + OUTSET * 2.5f, outerMin, outerBottom, outerMax, outerTop,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);
        quadWest(housing, pose, normal, outerMin - OUTSET * 2.5f, outerMin, outerBottom, outerMax, outerTop,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);
        quadSouth(housing, pose, normal, outerMax + OUTSET * 2.5f, outerMin, outerBottom, outerMax, outerTop,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);
        quadNorth(housing, pose, normal, outerMin - OUTSET * 2.5f, outerMin, outerBottom, outerMax, outerTop,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);
        quadUp(housing, pose, normal, outerTop + OUTSET * 3.0f, outerMin, outerMin, outerMax, outerMax,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);

        quadEast(lamp, pose, normal, innerMax + OUTSET * 4.0f, innerMin, innerBottom, innerMax, innerTop,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
        quadWest(lamp, pose, normal, innerMin - OUTSET * 4.0f, innerMin, innerBottom, innerMax, innerTop,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
        quadSouth(lamp, pose, normal, innerMax + OUTSET * 4.0f, innerMin, innerBottom, innerMax, innerTop,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
        quadNorth(lamp, pose, normal, innerMin - OUTSET * 4.0f, innerMin, innerBottom, innerMax, innerTop,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
        quadUp(lamp, pose, normal, innerTop + OUTSET * 4.5f, innerMin, innerMin, innerMax, innerMax,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
    }

    private void renderLampFace(Direction face, Matrix4f pose, Matrix3f normal,
                                VertexConsumer housing, VertexConsumer lamp,
                                IndicatorStyle style, int blockLight) {
        float outerMin = 12.125f * PX;
        float outerMax = 14.125f * PX;
        float outerBottom = 8.625f * PX;
        float outerTop = 10.625f * PX;
        float innerMin = 12.45f * PX;
        float innerMax = 13.8f * PX;
        float innerBottom = 8.95f * PX;
        float innerTop = 10.3f * PX;
        IndicatorColors colors = style.colors();

        switch (face) {
            case EAST -> {
                quadEast(lamp, pose, normal, 15.0f * PX + OUTSET * 2.0f, innerMin, innerBottom, innerMax, innerTop,
                        colors.coreR, colors.coreG, colors.coreB, style.lampA, style.lampLight);
            }
            case WEST -> {
                quadWest(lamp, pose, normal, 1.0f * PX - OUTSET * 2.0f, innerMin, innerBottom, innerMax, innerTop,
                        colors.coreR, colors.coreG, colors.coreB, style.lampA, style.lampLight);
            }
            case SOUTH -> {
                quadSouth(lamp, pose, normal, 15.0f * PX + OUTSET * 2.0f, innerMin, innerBottom, innerMax, innerTop,
                        colors.coreR, colors.coreG, colors.coreB, style.lampA, style.lampLight);
            }
            case NORTH -> {
                quadNorth(lamp, pose, normal, 1.0f * PX - OUTSET * 2.0f, innerMin, innerBottom, innerMax, innerTop,
                        colors.coreR, colors.coreG, colors.coreB, style.lampA, style.lampLight);
            }
            default -> {
            }
        }
    }

    private static void quadUp(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                               float y, float x0, float z0, float x1, float z1,
                               int r, int g, int b, int a, int light) {
        vtx(vc, pose, normal, x0, y, z1, r, g, b, a, light, 0.0f, 1.0f, 0.0f);
        vtx(vc, pose, normal, x0, y, z0, r, g, b, a, light, 0.0f, 1.0f, 0.0f);
        vtx(vc, pose, normal, x1, y, z0, r, g, b, a, light, 0.0f, 1.0f, 0.0f);
        vtx(vc, pose, normal, x1, y, z1, r, g, b, a, light, 0.0f, 1.0f, 0.0f);
    }

    private static void quadNorth(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                                  float z, float x0, float y0, float x1, float y1,
                                  int r, int g, int b, int a, int light) {
        vtx(vc, pose, normal, x0, y1, z, r, g, b, a, light, 0.0f, 0.0f, -1.0f);
        vtx(vc, pose, normal, x0, y0, z, r, g, b, a, light, 0.0f, 0.0f, -1.0f);
        vtx(vc, pose, normal, x1, y0, z, r, g, b, a, light, 0.0f, 0.0f, -1.0f);
        vtx(vc, pose, normal, x1, y1, z, r, g, b, a, light, 0.0f, 0.0f, -1.0f);
    }

    private static void quadSouth(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                                  float z, float x0, float y0, float x1, float y1,
                                  int r, int g, int b, int a, int light) {
        vtx(vc, pose, normal, x1, y1, z, r, g, b, a, light, 0.0f, 0.0f, 1.0f);
        vtx(vc, pose, normal, x1, y0, z, r, g, b, a, light, 0.0f, 0.0f, 1.0f);
        vtx(vc, pose, normal, x0, y0, z, r, g, b, a, light, 0.0f, 0.0f, 1.0f);
        vtx(vc, pose, normal, x0, y1, z, r, g, b, a, light, 0.0f, 0.0f, 1.0f);
    }

    private static void quadEast(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                                 float x, float z0, float y0, float z1, float y1,
                                 int r, int g, int b, int a, int light) {
        vtx(vc, pose, normal, x, y1, z0, r, g, b, a, light, 1.0f, 0.0f, 0.0f);
        vtx(vc, pose, normal, x, y0, z0, r, g, b, a, light, 1.0f, 0.0f, 0.0f);
        vtx(vc, pose, normal, x, y0, z1, r, g, b, a, light, 1.0f, 0.0f, 0.0f);
        vtx(vc, pose, normal, x, y1, z1, r, g, b, a, light, 1.0f, 0.0f, 0.0f);
    }

    private static void quadWest(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                                 float x, float z0, float y0, float z1, float y1,
                                 int r, int g, int b, int a, int light) {
        vtx(vc, pose, normal, x, y1, z1, r, g, b, a, light, -1.0f, 0.0f, 0.0f);
        vtx(vc, pose, normal, x, y0, z1, r, g, b, a, light, -1.0f, 0.0f, 0.0f);
        vtx(vc, pose, normal, x, y0, z0, r, g, b, a, light, -1.0f, 0.0f, 0.0f);
        vtx(vc, pose, normal, x, y1, z0, r, g, b, a, light, -1.0f, 0.0f, 0.0f);
    }

    private static void vtx(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                            float x, float y, float z,
                            int r, int g, int b, int a, int light,
                            float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }

    private static float stablePhaseOffset(ControlClutchBE be) {
        long seed = be.getBlockPos().asLong();
        seed ^= seed >>> 33;
        return (seed & 1023L) / 1024.0f;
    }

    private static float shapedPulse(float phase, float center, float width) {
        float wrapped = Math.abs(frac(phase - center + 0.5f) - 0.5f);
        float pulse = 1.0f - wrapped / width;
        pulse = Mth.clamp(pulse, 0.0f, 1.0f);
        return pulse * pulse * (3.0f - 2.0f * pulse);
    }

    private static float frac(float value) {
        return value - Mth.floor(value);
    }

    private static int alphaFor(float brightness, float floor, int min, int max) {
        float normalized = Mth.clamp((brightness - floor) / Math.max(0.0001f, 1.0f - floor), 0.0f, 1.0f);
        return Mth.clamp(Mth.floor(Mth.lerp(normalized, min, max)), 0, 255);
    }

    private record IndicatorStyle(IndicatorColors colors, int housingA, int lampA, int lampLight,
                                  int beaconHousingA, int beaconCoreA, int beaconLight) {
    }

    private record IndicatorColors(int shellR, int shellG, int shellB, int coreR, int coreG, int coreB) {
    }
}
