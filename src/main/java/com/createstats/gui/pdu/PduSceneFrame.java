package com.createstats.gui.pdu;

import net.minecraft.core.BlockPos;

public final class PduSceneFrame {
    private PduSceneFrame() {
    }

    public static final float START_YAW = 35.0f;

    public static final float START_PITCH = 28.0f;

    public static final double FILL = 0.52;

    public record Frame(double cx, double cy, double cz, double extent) {
        public double scale(int w, int h, float zoom) {
            return Math.min(w, h) * FILL / extent * zoom;
        }
    }

    public static Frame of(Iterable<BlockPos> positions) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        boolean any = false;
        for (BlockPos p : positions) {
            any = true;
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        if (!any) {
            return null;
        }

        return new Frame((minX + maxX + 1.0) * 0.5, (minY + maxY + 1.0) * 0.5, (minZ + maxZ + 1.0) * 0.5,
                Math.max(1.0, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) + 1.0));
    }
}
