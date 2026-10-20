package com.createstats.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public final class FaceSlab {
    private FaceSlab() {
    }

    public record Tinted(BlockPos pos, Direction face, int argb) {
    }

    public static void draw(PoseStack poseStack, List<Tinted> faces) {
        if (faces.isEmpty()) {
            return;
        }
        final Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        final Matrix4f mat = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        final BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Tinted t : faces) {
            final float a = ((t.argb() >>> 24) & 0xFF) / 255.0f;
            final float r = ((t.argb() >> 16) & 0xFF) / 255.0f;
            final float g = ((t.argb() >> 8) & 0xFF) / 255.0f;
            final float b = (t.argb() & 0xFF) / 255.0f;
            slab(buffer, mat, t.pos(), t.face(), r, g, b, a);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    public static void slab(BufferBuilder buffer, Matrix4f mat, BlockPos pos, Direction face,
                            float r, float g, float b, float a) {
        final float e = 0.03f;
        final float o = 0.015f;
        final float x0 = pos.getX() + e;
        final float y0 = pos.getY() + e;
        final float z0 = pos.getZ() + e;
        final float x1 = pos.getX() + 1 - e;
        final float y1 = pos.getY() + 1 - e;
        final float z1 = pos.getZ() + 1 - e;
        switch (face) {
            case DOWN -> quad(buffer, mat, x0, y0 + o, z0, x1, y0 + o, z0, x1, y0 + o, z1, x0, y0 + o, z1, r, g, b, a);
            case UP -> quad(buffer, mat, x0, y1 - o, z1, x1, y1 - o, z1, x1, y1 - o, z0, x0, y1 - o, z0, r, g, b, a);
            case NORTH -> quad(buffer, mat, x0, y0, z0 + o, x0, y1, z0 + o, x1, y1, z0 + o, x1, y0, z0 + o, r, g, b, a);
            case SOUTH -> quad(buffer, mat, x1, y0, z1 - o, x1, y1, z1 - o, x0, y1, z1 - o, x0, y0, z1 - o, r, g, b, a);
            case WEST -> quad(buffer, mat, x0 + o, y0, z1, x0 + o, y1, z1, x0 + o, y1, z0, x0 + o, y0, z0, r, g, b, a);
            case EAST -> quad(buffer, mat, x1 - o, y0, z0, x1 - o, y1, z0, x1 - o, y1, z1, x1 - o, y0, z1, r, g, b, a);
        }
    }

    private static void quad(BufferBuilder buffer, Matrix4f mat,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        buffer.addVertex(mat, ax, ay, az).setColor(r, g, b, a);
        buffer.addVertex(mat, bx, by, bz).setColor(r, g, b, a);
        buffer.addVertex(mat, cx, cy, cz).setColor(r, g, b, a);
        buffer.addVertex(mat, dx, dy, dz).setColor(r, g, b, a);
    }
}
