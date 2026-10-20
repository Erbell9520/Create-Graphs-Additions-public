package com.createstats.aviatorhelmet.client;

import com.createstats.aviatorhelmet.GoggleMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class GoggleDraw {
    static final float LAMP_X = 10.74f;
    static final float LAMP_Y = 9.63f;
    static final float LAMP_Z = 1.42f;

    static final float LAMP_SIZE = 0.9f;

    private GoggleDraw() {
    }

    static void gogglePose(PoseStack ps, double angle, double push) {
        ps.translate(0.0f, 0.0f, (float) (-push / 16.0));
        strapPose(ps, angle);
    }

    static void strapPose(PoseStack ps, double angle) {
        final float py = (float) (GoggleMotion.PIVOT_Y / 16.0);
        final float pz = (float) (GoggleMotion.PIVOT_Z / 16.0);
        ps.translate(0.0f, py, pz);
        ps.mulPose(Axis.XP.rotationDegrees((float) angle));
        ps.translate(0.0f, -py, -pz);
    }

    static void lamp(VertexConsumer vc, PoseStack.Pose pose, int rgb, float level) {
        final int r = (int) (((rgb >> 16) & 0xFF) * level);
        final int g = (int) (((rgb >> 8) & 0xFF) * level);
        final int b = (int) ((rgb & 0xFF) * level);
        final float h = LAMP_SIZE / 2.0f;
        final float x = LAMP_X / 16.0f;
        final float y0 = (LAMP_Y - h) / 16.0f;
        final float y1 = (LAMP_Y + h) / 16.0f;
        final float z0 = (LAMP_Z - h) / 16.0f;
        final float z1 = (LAMP_Z + h) / 16.0f;
        corner(vc, pose, x, y1, z0, 0.0f, 0.0f, r, g, b);
        corner(vc, pose, x, y0, z0, 0.0f, 1.0f, r, g, b);
        corner(vc, pose, x, y0, z1, 1.0f, 1.0f, r, g, b);
        corner(vc, pose, x, y1, z1, 1.0f, 0.0f, r, g, b);
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int r, int g, int b) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 1.0f, 0.0f, 0.0f);
    }
}
