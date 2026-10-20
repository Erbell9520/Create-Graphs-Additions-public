package com.createstats.driverhelmet.client;

import com.createstats.driverhelmet.VisorMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

final class HelmetDraw {
    private HelmetDraw() {
    }

    static void visorPose(PoseStack ps, double angle) {
        final float py = (float) (VisorMotion.PIVOT_Y / 16.0);
        final float pz = (float) (VisorMotion.PIVOT_Z / 16.0);
        ps.translate(0.0f, 0.0f, (float) (-VisorMotion.camPush(angle) / 16.0));
        ps.translate(0.0f, py, pz);
        ps.mulPose(Axis.XP.rotationDegrees((float) angle));
        ps.translate(0.0f, -py, -pz);
    }
}
