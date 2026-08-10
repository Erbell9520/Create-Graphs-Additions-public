package com.createstats.pathfinder.client;

import com.createstats.renderer.HologramRenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
final class PathfinderBlockVisuals {
    static final int CYAN_R = 64;
    static final int CYAN_G = 225;
    static final int CYAN_B = 255;
    static final int GREEN_R = 92;
    static final int GREEN_G = 255;
    static final int GREEN_B = 150;
    static final int AMBER_R = 255;
    static final int AMBER_G = 174;
    static final int AMBER_B = 64;
    static final int RED_R = 255;
    static final int RED_G = 88;
    static final int RED_B = 72;

    private static final ResourceLocation BLANK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "create_stats", "textures/special/blank.png");

    private PathfinderBlockVisuals() {
    }

    @SuppressWarnings("null")
    static VertexConsumer glowBuffer(MultiBufferSource buffer) {
        return buffer.getBuffer(RenderType.entityTranslucentEmissive(BLANK_TEXTURE));
    }

    static boolean closeEnough(BlockEntity blockEntity, double distance) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer == null) {
            return true;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return camera.distanceToSqr(center) <= distance * distance;
    }

    static long gameTime(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        return level == null ? 0L : level.getGameTime();
    }

    @SuppressWarnings("null")
    static void orientNorthModel(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotation(facing)));
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    static float yRotation(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
    }

    static float pulse(long gameTime, float partialTick, float speed, float seed) {
        return 0.5f + 0.5f * (float)Math.sin((gameTime + partialTick + seed) * speed);
    }

    static int alpha(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    static void frontQuad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1,
                          float z, int red, int green, int blue, int alpha) {
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        HologramRenderHelper.emitQuad(consumer, matrix, normal,
            x0, y1, z,
            x0, y0, z,
            x1, y0, z,
            x1, y1, z,
            red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT, 0.0f, 0.0f, -1.0f);
    }

    static void topQuad(VertexConsumer consumer, PoseStack.Pose pose, float y, float x0, float z0, float x1, float z1,
                        int red, int green, int blue, int alpha) {
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        HologramRenderHelper.emitQuad(consumer, matrix, normal,
            x0, y, z1,
            x0, y, z0,
            x1, y, z0,
            x1, y, z1,
            red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT, 0.0f, 1.0f, 0.0f);
    }

    static void frontArc(VertexConsumer consumer, PoseStack poseStack, float centerX, float centerY, float z,
                         float innerRadius, float outerRadius, float startAngle, float sweepAngle,
                         int segments, int red, int green, int blue, int alpha) {
        if (alpha <= 0 || sweepAngle <= 0.0f) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, z);
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (int i = 0; i < segments; i++) {
            float segmentStart = startAngle + sweepAngle * i / segments;
            float segmentEnd = startAngle + sweepAngle * (i + 1) / segments;
            HologramRenderHelper.emitArcSegment(consumer, matrix, normal, innerRadius, outerRadius,
                segmentStart, segmentEnd, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        }
        poseStack.popPose();
    }

    static void topRing(VertexConsumer consumer, PoseStack poseStack, float centerX, float y, float centerZ,
                        float innerRadius, float outerRadius, float startAngle, float sweepAngle,
                        int segments, int red, int green, int blue, int alpha) {
        if (alpha <= 0 || sweepAngle <= 0.0f) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(centerX, y, centerZ);
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (int i = 0; i < segments; i++) {
            float segmentStart = startAngle + sweepAngle * i / segments;
            float segmentEnd = startAngle + sweepAngle * (i + 1) / segments;
            HologramRenderHelper.emitHorizontalRingSegment(consumer, matrix, normal, innerRadius, outerRadius,
                segmentStart, segmentEnd, 0.0f, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        }
        poseStack.popPose();
    }
}