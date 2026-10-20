package com.createstats.renderer;

import com.createstats.block.LoadBalancerBlock;
import com.createstats.blockentity.LoadBalancerBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class LoadBalancerRenderer extends KineticBlockEntityRenderer<LoadBalancerBE> {
    private static final float PX = 1.0f / 16.0f;
    private static final float OUTSET = 1.0f / 64.0f;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final ResourceLocation WHITE_TEX =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/special/blank.png");
    private static final IndicatorColors OFF_COLORS = new IndicatorColors(0x12, 0x12, 0x12, 0x2B, 0x2B, 0x2B);
    private static final IndicatorColors OK_COLORS = new IndicatorColors(0x0C, 0x1E, 0x13, 0x72, 0xF1, 0x92);
    private static final IndicatorColors WARN_COLORS = new IndicatorColors(0x24, 0x1A, 0x05, 0xFF, 0xD4, 0x45);
    private static final IndicatorColors ERROR_COLORS = new IndicatorColors(0x23, 0x08, 0x0A, 0xF2, 0x4D, 0x52);

    private static final int INPUT_R = 0x5A;
    private static final int INPUT_G = 0xA9;
    private static final int INPUT_B = 0xD6;
    private static final int INPUT_A = 0xD0;

    public LoadBalancerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(LoadBalancerBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
                              int overlay) {
        boolean visualize = be.getLevel() != null && VisualizationManager.supportsVisualization(be.getLevel());
        if (!visualize) {
            renderShaftHalves(be, ms, buffer, light);
        }
        renderStatusIndicators(be, ms, buffer, light);
    }

    private void renderShaftHalves(LoadBalancerBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        final BlockState state = be.getBlockState();
        final BlockPos pos = be.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());

        for (Direction direction : getPorts(state)) {
            final Direction.Axis axis = direction.getAxis();
            SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, direction);

            float speed = be.getSpeed() * be.getRotationSpeedModifier(direction);
            float offset = getRotationOffsetForPosition(be, pos, axis);
            float angle = (time * speed * 3.0f / 10.0f) % 360.0f;
            angle = angle / 180.0f * (float) Math.PI;
            angle += offset;

            kineticRotationTransform(shaft, be, axis, angle, light);
            shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    private void renderStatusIndicators(LoadBalancerBE be, PoseStack ms, MultiBufferSource buffer, int blockLight) {
        if (be.getLevel() == null) {
            return;
        }

        float renderTime = AnimationTickHolder.getRenderTime(be.getLevel());
        Matrix4f pose = ms.last().pose();
        Matrix3f normal = ms.last().normal();

        VertexConsumer housing = Buffers.live(buffer, RenderType.entityTranslucent(WHITE_TEX));
        VertexConsumer lamp = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE_TEX));

        IndicatorStyle centerStyle = getCenterStyle(be, renderTime);
        IndicatorStyle leftStyle = getBranchStyle(be, renderTime, be.isLeftBranchAttached());
        IndicatorStyle rightStyle = getBranchStyle(be, renderTime, be.isRightBranchAttached());
        BlockState state = be.getBlockState();

        renderFaceWindow(LoadBalancerBlock.getFrontFacing(state), pose, normal, housing, lamp, centerStyle, blockLight,
                4.0f * PX, 12.0f * PX, 5.0f * PX, 11.0f * PX,
                4.5f * PX, 11.5f * PX, 5.5f * PX, 10.5f * PX);
        renderFaceWindow(LoadBalancerBlock.getLeftOutputFacing(state), pose, normal, housing, lamp, leftStyle, blockLight,
                5.75f * PX, 10.25f * PX, 5.5f * PX, 10.5f * PX,
                6.15f * PX, 9.85f * PX, 5.9f * PX, 10.1f * PX);
        renderFaceWindow(LoadBalancerBlock.getRightOutputFacing(state), pose, normal, housing, lamp, rightStyle, blockLight,
                5.75f * PX, 10.25f * PX, 5.5f * PX, 10.5f * PX,
                6.15f * PX, 9.85f * PX, 5.9f * PX, 10.1f * PX);
        renderInputMarker(LoadBalancerBlock.getInputFacing(state), pose, normal, housing, blockLight);
        renderTopBeacon(pose, normal, housing, lamp, centerStyle, blockLight);
    }

    private void renderInputMarker(Direction face, Matrix4f pose, Matrix3f normal,
                                   VertexConsumer housing, int blockLight) {
        final float depth = OUTSET * 1.5f;

        faceQuad(face, housing, pose, normal, 3.0f * PX, 4.5f * PX, 5.5f * PX, 10.5f * PX, depth,
                INPUT_R, INPUT_G, INPUT_B, INPUT_A, blockLight);
        faceQuad(face, housing, pose, normal, 11.5f * PX, 13.0f * PX, 5.5f * PX, 10.5f * PX, depth,
                INPUT_R, INPUT_G, INPUT_B, INPUT_A, blockLight);
    }

    private static void faceQuad(Direction face, VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                                 float min, float max, float bottom, float top, float depth,
                                 int r, int g, int b, int a, int light) {
        switch (face) {
            case EAST -> quadEast(vc, pose, normal, 15.0f * PX + depth, min, bottom, max, top,
                    r, g, b, a, light);
            case WEST -> quadWest(vc, pose, normal, 1.0f * PX - depth, min, bottom, max, top,
                    r, g, b, a, light);
            case SOUTH -> quadSouth(vc, pose, normal, 15.0f * PX + depth, min, bottom, max, top,
                    r, g, b, a, light);
            case NORTH -> quadNorth(vc, pose, normal, 1.0f * PX - depth, min, bottom, max, top,
                    r, g, b, a, light);
            default -> {
            }
        }
    }

    private IndicatorStyle getCenterStyle(LoadBalancerBE be, float renderTime) {
        float phase = stablePhaseOffset(be);
        LoadBalancerBE.ReportedState state = be.getReportedState();
        return switch (state) {
            case DISABLED -> buildStyle(OFF_COLORS, 0.08f, 0.02f);
            case TOPOLOGY_LIMIT -> {
                float blink = blink(renderTime / 10.0f + phase);
                yield buildStyle(ERROR_COLORS, 0.24f + blink * 0.76f, 0.16f + blink * 0.84f);
            }
            case WRONG_INPUT, MERGED, BRANCH_SOURCE -> buildStyle(ERROR_COLORS, 0.82f, 0.62f);

            case NO_BRANCHES, ONE_BRANCH -> {
                float blink = blink(renderTime / 12.0f + phase);
                yield buildStyle(WARN_COLORS, 0.20f + blink * 0.80f, 0.10f + blink * 0.55f);
            }
            case IDLE -> buildStyle(OK_COLORS, 0.26f, 0.10f);
            case BALANCING -> {
                float pulse = heartbeat(renderTime / 18.0f + phase);
                yield buildStyle(OK_COLORS, 0.46f + pulse * 0.54f, 0.24f + pulse * 0.76f);
            }
            case PASS_THROUGH -> buildStyle(OK_COLORS, 0.68f, 0.34f);
        };
    }

    private IndicatorStyle getBranchStyle(LoadBalancerBE be, float renderTime, boolean attached) {
        float phase = stablePhaseOffset(be) + (attached ? 0.0f : 0.19f);
        LoadBalancerBE.ReportedState state = be.getReportedState();
        return switch (state) {
            case DISABLED -> buildStyle(OFF_COLORS, 0.06f, 0.0f);
            case TOPOLOGY_LIMIT -> {
                float blink = blink(renderTime / 10.0f + phase);
                yield buildStyle(ERROR_COLORS, 0.18f + blink * 0.82f, 0.0f);
            }
            case WRONG_INPUT, MERGED, BRANCH_SOURCE -> buildStyle(ERROR_COLORS, 0.84f, 0.0f);
            case NO_BRANCHES, ONE_BRANCH -> {
                if (attached) {
                    yield buildStyle(OK_COLORS, 0.58f, 0.0f);
                }
                float blink = blink(renderTime / 12.0f + phase);
                yield buildStyle(WARN_COLORS, 0.20f + blink * 0.80f, 0.0f);
            }
            case IDLE -> buildStyle(OK_COLORS, 0.18f, 0.0f);
            case BALANCING -> {
                float pulse = heartbeat(renderTime / 18.0f + phase);
                yield buildStyle(OK_COLORS, 0.40f + pulse * 0.50f, 0.0f);
            }
            case PASS_THROUGH -> buildStyle(OK_COLORS, 0.58f, 0.0f);
        };
    }

    private IndicatorStyle buildStyle(IndicatorColors colors, float lampBrightness, float beaconBrightness) {
        int housingAlpha = alphaFor(lampBrightness, 0.0f, 0x18, 0xCC);
        int lampAlpha = alphaFor(lampBrightness, 0.0f, 0x00, 0xF6);
        int beaconHousingAlpha = alphaFor(beaconBrightness, 0.0f, 0x10, 0xA4);
        int beaconCoreAlpha = alphaFor(beaconBrightness, 0.0f, 0x00, 0xFF);
        return new IndicatorStyle(colors, housingAlpha, lampAlpha, FULL_BRIGHT, beaconHousingAlpha, beaconCoreAlpha, FULL_BRIGHT);
    }

    private void renderFaceWindow(Direction face, Matrix4f pose, Matrix3f normal,
                                  VertexConsumer housing, VertexConsumer lamp,
                                  IndicatorStyle style, int blockLight,
                                  float outerMin, float outerMax, float outerBottom, float outerTop,
                                  float innerMin, float innerMax, float innerBottom, float innerTop) {
        IndicatorColors colors = style.colors();
        switch (face) {
            case EAST -> {
                quadEast(housing, pose, normal, 15.0f * PX + OUTSET * 1.5f, outerMin, outerBottom, outerMax, outerTop,
                        colors.shellR, colors.shellG, colors.shellB, style.housingA, blockLight);
                quadEast(lamp, pose, normal, 15.0f * PX + OUTSET * 2.5f, innerMin, innerBottom, innerMax, innerTop,
                        colors.coreR, colors.coreG, colors.coreB, style.lampA, style.lampLight);
            }
            case WEST -> {
                quadWest(housing, pose, normal, 1.0f * PX - OUTSET * 1.5f, outerMin, outerBottom, outerMax, outerTop,
                        colors.shellR, colors.shellG, colors.shellB, style.housingA, blockLight);
                quadWest(lamp, pose, normal, 1.0f * PX - OUTSET * 2.5f, innerMin, innerBottom, innerMax, innerTop,
                        colors.coreR, colors.coreG, colors.coreB, style.lampA, style.lampLight);
            }
            case SOUTH -> {
                quadSouth(housing, pose, normal, 15.0f * PX + OUTSET * 1.5f, outerMin, outerBottom, outerMax, outerTop,
                        colors.shellR, colors.shellG, colors.shellB, style.housingA, blockLight);
                quadSouth(lamp, pose, normal, 15.0f * PX + OUTSET * 2.5f, innerMin, innerBottom, innerMax, innerTop,
                        colors.coreR, colors.coreG, colors.coreB, style.lampA, style.lampLight);
            }
            case NORTH -> {
                quadNorth(housing, pose, normal, 1.0f * PX - OUTSET * 1.5f, outerMin, outerBottom, outerMax, outerTop,
                        colors.shellR, colors.shellG, colors.shellB, style.housingA, blockLight);
                quadNorth(lamp, pose, normal, 1.0f * PX - OUTSET * 2.5f, innerMin, innerBottom, innerMax, innerTop,
                        colors.coreR, colors.coreG, colors.coreB, style.lampA, style.lampLight);
            }
            default -> {
            }
        }
    }

    private void renderTopBeacon(Matrix4f pose, Matrix3f normal, VertexConsumer housing, VertexConsumer lamp,
                                 IndicatorStyle style, int blockLight) {
        IndicatorColors colors = style.colors();
        float outerMin = 7.0f * PX;
        float outerMax = 9.0f * PX;
        float outerBottom = 14.0f * PX;
        float outerTop = 15.0f * PX;
        float innerMin = 7.3f * PX;
        float innerMax = 8.7f * PX;
        float innerBottom = 14.2f * PX;
        float innerTop = 15.15f * PX;

        quadEast(housing, pose, normal, outerMax + OUTSET * 2.0f, outerMin, outerBottom, outerMax, outerTop,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);
        quadWest(housing, pose, normal, outerMin - OUTSET * 2.0f, outerMin, outerBottom, outerMax, outerTop,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);
        quadSouth(housing, pose, normal, outerMax + OUTSET * 2.0f, outerMin, outerBottom, outerMax, outerTop,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);
        quadNorth(housing, pose, normal, outerMin - OUTSET * 2.0f, outerMin, outerBottom, outerMax, outerTop,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);
        quadUp(housing, pose, normal, outerTop + OUTSET * 2.5f, outerMin, outerMin, outerMax, outerMax,
                colors.shellR, colors.shellG, colors.shellB, style.beaconHousingA, blockLight);

        quadEast(lamp, pose, normal, innerMax + OUTSET * 3.0f, innerMin, innerBottom, innerMax, innerTop,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
        quadWest(lamp, pose, normal, innerMin - OUTSET * 3.0f, innerMin, innerBottom, innerMax, innerTop,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
        quadSouth(lamp, pose, normal, innerMax + OUTSET * 3.0f, innerMin, innerBottom, innerMax, innerTop,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
        quadNorth(lamp, pose, normal, innerMin - OUTSET * 3.0f, innerMin, innerBottom, innerMax, innerTop,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
        quadUp(lamp, pose, normal, innerTop + OUTSET * 3.5f, innerMin, innerMin, innerMax, innerMax,
                colors.coreR, colors.coreG, colors.coreB, style.beaconCoreA, style.beaconLight);
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

    private static float stablePhaseOffset(LoadBalancerBE be) {
        long seed = be.getBlockPos().asLong();
        seed ^= seed >>> 33;
        return (seed & 1023L) / 1024.0f;
    }

    private static float heartbeat(float phase) {
        float pulse = shapedPulse(phase, 0.08f, 0.12f);
        pulse = Math.max(pulse, shapedPulse(phase, 0.28f, 0.08f) * 0.7f);
        return pulse;
    }

    private static float blink(float phase) {
        float cycle = frac(phase);
        return cycle < 0.52f ? 1.0f : 0.08f;
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

    private static Direction[] getPorts(BlockState state) {
        return new Direction[] {
            LoadBalancerBlock.getInputFacing(state),
            LoadBalancerBlock.getLeftOutputFacing(state),
            LoadBalancerBlock.getRightOutputFacing(state)
        };
    }

    private record IndicatorColors(int shellR, int shellG, int shellB, int coreR, int coreG, int coreB) {
    }

    private record IndicatorStyle(IndicatorColors colors, int housingA, int lampA, int lampLight,
                                  int beaconHousingA, int beaconCoreA, int beaconLight) {
    }
}
