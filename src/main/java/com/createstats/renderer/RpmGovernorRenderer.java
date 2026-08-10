package com.createstats.renderer;

import com.createstats.CreateStats;
import com.createstats.client.ModPartialModels;
import com.createstats.flight.FlightConfig;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix4f;

public class RpmGovernorRenderer extends KineticBlockEntityRenderer<RpmGovernorBE> {

    private static final float NEEDLE_PIVOT_X = 8.0f / 16.0f;
    private static final float NEEDLE_PIVOT_Z = 8.0f / 16.0f;
    private static final float NEEDLE_START_ANGLE = (float) (Math.PI * 5.0 / 4.0);
    private static final float WINDOW_WIDTH = 6.0f / 16.0f;
    private static final float WINDOW_HEIGHT = 3.0f / 16.0f;
    private static final float FACE_EPSILON = 1.0f / 512.0f;
    private static final float WINDOW_INSET = 0.8f / 16.0f;
    private static final ResourceLocation WINDOW_LABELS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateStats.MOD_ID, "textures/block/rpm_governor/window_labels.png");
    private static final int WINDOW_LABEL_COUNT = 16;
    private static final float WINDOW_LABEL_TEXTURE_WIDTH = 128.0f;
    private static final float WINDOW_LABEL_TEXTURE_HEIGHT = 1024.0f;
    private static final float WINDOW_LABEL_FRAME_HEIGHT = WINDOW_LABEL_TEXTURE_HEIGHT / WINDOW_LABEL_COUNT;

    public RpmGovernorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(RpmGovernorBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        renderShaftHalves(be, ms, buffer, light);
        renderNeedle(be, ms, buffer, light);
        renderWindowLabels(be, ms, buffer, light);
    }

    private void renderShaftHalves(RpmGovernorBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        Block block = be.getBlockState().getBlock();
        Direction.Axis boxAxis = ((IRotate) block).getRotationAxis(be.getBlockState());
        BlockPos pos = be.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());

        for (Direction direction : Iterate.directions) {
            Direction.Axis axis = direction.getAxis();
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

    private void renderNeedle(RpmGovernorBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        float normalized = be.getAppliedRpm() / (float) Math.max(1, FlightConfig.RPM_CLAMP);
        float progress = Mth.clamp((normalized + 1.0f) * 0.5f, 0.0f, 1.0f);
        float angle = NEEDLE_START_ANGLE - NeedlePhysics.SWEEP_RADIANS * progress;

        SuperByteBuffer needle = CachedBuffers.partial(ModPartialModels.RPM_GOVERNOR_GAUGE_NEEDLE, be.getBlockState());
        needle.translate(NEEDLE_PIVOT_X, 0.0f, NEEDLE_PIVOT_Z)
                .rotate(angle, Direction.UP)
                .translate(-NEEDLE_PIVOT_X, 0.0f, -NEEDLE_PIVOT_Z)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private void renderWindowLabels(RpmGovernorBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        int labelFrame = getLabelFrame(be.getAppliedRpm());
        Direction.Axis axis = ((IRotate) be.getBlockState().getBlock()).getRotationAxis(be.getBlockState());

        switch (axis) {
            case X -> {
                renderWindowLabel(ms, buffer, labelFrame, Direction.NORTH, light);
                renderWindowLabel(ms, buffer, labelFrame, Direction.SOUTH, light);
            }
            case Y -> {
                renderWindowLabel(ms, buffer, labelFrame, Direction.NORTH, light);
                renderWindowLabel(ms, buffer, labelFrame, Direction.SOUTH, light);
                renderWindowLabel(ms, buffer, labelFrame, Direction.EAST, light);
                renderWindowLabel(ms, buffer, labelFrame, Direction.WEST, light);
            }
            case Z -> {
                renderWindowLabel(ms, buffer, labelFrame, Direction.EAST, light);
                renderWindowLabel(ms, buffer, labelFrame, Direction.WEST, light);
            }
        }
    }

    private static int getLabelFrame(int appliedRpm) {
        float normalized = Math.abs(appliedRpm) / (float) Math.max(1, FlightConfig.RPM_CLAMP);
        return Mth.clamp(Math.round(normalized * (WINDOW_LABEL_COUNT - 1)), 0, WINDOW_LABEL_COUNT - 1);
    }

    private void renderWindowLabel(PoseStack ms, MultiBufferSource buffer, int labelFrame, Direction face, int light) {
        ms.pushPose();
        ms.translate(0.5f, 0.5f, 0.5f);
        rotateToFace(ms, face);
        ms.translate(0.0f, 0.0f, 0.5f - WINDOW_INSET - FACE_EPSILON);
        renderCenteredQuad(ms, buffer, WINDOW_LABELS_TEXTURE, WINDOW_WIDTH, WINDOW_HEIGHT, light, labelFrame);
        ms.popPose();
    }

    private void rotateToFace(PoseStack ms, Direction face) {
        switch (face) {
            case NORTH -> ms.mulPose(Axis.YP.rotationDegrees(180.0f));
            case EAST -> ms.mulPose(Axis.YP.rotationDegrees(-90.0f));
            case WEST -> ms.mulPose(Axis.YP.rotationDegrees(90.0f));
            case UP -> ms.mulPose(Axis.XP.rotationDegrees(-90.0f));
            case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(90.0f));
            default -> {
            }
        }
    }

    private void renderCenteredQuad(PoseStack ms, MultiBufferSource buffer, ResourceLocation texture, float width, float height,
                                    int light, int labelFrame) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(texture));
        Matrix4f pose = ms.last().pose();
        float x0 = -width / 2.0f;
        float x1 = width / 2.0f;
        float y0 = -height / 2.0f;
        float y1 = height / 2.0f;
        float u0 = 0.5f / WINDOW_LABEL_TEXTURE_WIDTH;
        float u1 = 1.0f - u0;
        float v0 = (labelFrame * WINDOW_LABEL_FRAME_HEIGHT + 0.5f) / WINDOW_LABEL_TEXTURE_HEIGHT;
        float v1 = ((labelFrame + 1) * WINDOW_LABEL_FRAME_HEIGHT - 0.5f) / WINDOW_LABEL_TEXTURE_HEIGHT;

        vc.addVertex(pose, x0, y1, 0.0f).setColor(255, 255, 255, 255).setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, x0, y0, 0.0f).setColor(255, 255, 255, 255).setUv(u0, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, x1, y0, 0.0f).setColor(255, 255, 255, 255).setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, x1, y1, 0.0f).setColor(255, 255, 255, 255).setUv(u1, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
    }
}