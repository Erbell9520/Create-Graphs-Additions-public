package com.createstats.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class PeerIdentifyHighlight {
    private PeerIdentifyHighlight() {
    }

    private static final int GRACE_FRAMES = 2;

    private static final java.util.List<BlockPos> TARGETS = new java.util.ArrayList<>(4);

    private static final int MAX_TARGETS = 64;
    private static int color = 0xFF5C90;
    private static int ttl;

    // pins and hover are cleared by different things
    private static final java.util.List<BlockPos> PINNED = new java.util.ArrayList<>(4);
    private static int pinColor = 0xFF5C90;
    private static long pinUntilNanos;

    public static void show(BlockPos pos, int argb) {
        TARGETS.clear();
        if (pos != null) {
            TARGETS.add(pos.immutable());
        }
        color = argb;
        ttl = GRACE_FRAMES;
    }

    public static void show(java.util.List<BlockPos> positions, int argb) {
        TARGETS.clear();
        for (BlockPos pos : positions) {
            if (TARGETS.size() >= MAX_TARGETS) {
                break;
            }
            TARGETS.add(pos.immutable());
        }
        color = argb;
        ttl = GRACE_FRAMES;
    }

    public static void clear() {
        TARGETS.clear();
        ttl = 0;
    }

    public static void pin(java.util.List<BlockPos> positions, int argb, float seconds) {
        PINNED.clear();
        for (BlockPos pos : positions) {
            if (PINNED.size() >= MAX_TARGETS) {
                break;
            }
            PINNED.add(pos.immutable());
        }
        pinColor = argb;
        pinUntilNanos = PINNED.isEmpty() ? 0L
                : System.nanoTime() + (long) (Math.max(0.0f, seconds) * 1.0e9f);
    }

    public static void unpin() {
        PINNED.clear();
        pinUntilNanos = 0L;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final boolean hovering = !TARGETS.isEmpty() && ttl > 0;
        final boolean pinned = !PINNED.isEmpty() && System.nanoTime() < pinUntilNanos;
        if (!hovering && !pinned) {
            return;
        }
        if (ttl > 0) {
            ttl--;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        final VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        if (hovering) {
            draw(lines, pose, camera, TARGETS, color);
        }
        if (pinned) {
            draw(lines, pose, camera, PINNED, pinColor);
        }

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void draw(VertexConsumer lines, PoseStack pose, Vec3 camera,
                             java.util.List<BlockPos> targets, int argb) {
        for (BlockPos target : targets) {
            pose.pushPose();
            pose.translate(target.getX() - camera.x, target.getY() - camera.y, target.getZ() - camera.z);

            box(lines, pose.last().pose(), -0.01f, 1.01f, argb);
            pose.popPose();
        }
    }

    private static void box(VertexConsumer lines, Matrix4f mat, float lo, float hi, int argb) {
        edge(lines, mat, lo, lo, lo, hi, lo, lo, argb);
        edge(lines, mat, hi, lo, lo, hi, lo, hi, argb);
        edge(lines, mat, hi, lo, hi, lo, lo, hi, argb);
        edge(lines, mat, lo, lo, hi, lo, lo, lo, argb);

        edge(lines, mat, lo, hi, lo, hi, hi, lo, argb);
        edge(lines, mat, hi, hi, lo, hi, hi, hi, argb);
        edge(lines, mat, hi, hi, hi, lo, hi, hi, argb);
        edge(lines, mat, lo, hi, hi, lo, hi, lo, argb);

        edge(lines, mat, lo, lo, lo, lo, hi, lo, argb);
        edge(lines, mat, hi, lo, lo, hi, hi, lo, argb);
        edge(lines, mat, hi, lo, hi, hi, hi, hi, argb);
        edge(lines, mat, lo, lo, hi, lo, hi, hi, argb);
    }

    private static void edge(VertexConsumer lines, Matrix4f mat,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             int argb) {
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-5f) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        final float r = ((argb >> 16) & 0xFF) / 255.0f;
        final float g = ((argb >> 8) & 0xFF) / 255.0f;
        final float b = (argb & 0xFF) / 255.0f;
        lines.addVertex(mat, x0, y0, z0).setColor(r, g, b, 0.9f).setNormal(nx, ny, nz);
        lines.addVertex(mat, x1, y1, z1).setColor(r, g, b, 0.9f).setNormal(nx, ny, nz);
    }
}
