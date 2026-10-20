package com.createstats.book.scene;

import java.util.List;

// same transform for the scene and the pins or the pins drift
public record BlockView(double yaw, double pitch, double scale, double offsetX, double offsetY) {
    public double pixel() {
        return scale / 16.0;
    }

    public double[] turn(double x, double y, double z) {
        final double yr = Math.toRadians(yaw);
        final double pr = Math.toRadians(pitch);
        final double x1 = x * Math.cos(yr) + z * Math.sin(yr);
        final double z1 = -x * Math.sin(yr) + z * Math.cos(yr);
        final double y2 = y * Math.cos(pr) - z1 * Math.sin(pr);
        final double z2 = y * Math.sin(pr) + z1 * Math.cos(pr);
        return new double[]{x1, y2, z2};
    }

    public double[] project(double x, double y, double z) {
        final double[] t = turn(x, y, z);
        return new double[]{offsetX + scale * t[0], offsetY - scale * t[1], scale * t[2]};
    }

    public double[] projectPx(double x, double y, double z) {
        return project(x / 16.0, y / 16.0, z / 16.0);
    }

    public boolean facesViewer(double nx, double ny, double nz) {
        return turn(nx, ny, nz)[2] > 1.0e-6;
    }

    public double[] boundsPx(double[] box) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            final double[] p = projectPx(box[(i & 1) == 0 ? 0 : 3], box[(i & 2) == 0 ? 1 : 4],
                    box[(i & 4) == 0 ? 2 : 5]);
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    public static BlockView fit(double yaw, double pitch, List<double[]> boxes, int width, int height, int margin) {
        final BlockView unit = new BlockView(yaw, pitch, 1.0, 0.0, 0.0);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (final double[] b : boxes) {
            final double[] r = unit.boundsPx(b);
            minX = Math.min(minX, r[0]);
            minY = Math.min(minY, r[1]);
            maxX = Math.max(maxX, r[2]);
            maxY = Math.max(maxY, r[3]);
        }
        final double spanX = Math.max(1.0e-6, maxX - minX);
        final double spanY = Math.max(1.0e-6, maxY - minY);
        final double scale = Math.min((width - 2.0 * margin) / spanX, (height - 2.0 * margin) / spanY);

        final double offsetX = width / 2.0 - scale * (minX + maxX) / 2.0;
        final double offsetY = height / 2.0 - scale * (minY + maxY) / 2.0;
        return new BlockView(yaw, pitch, scale, offsetX, offsetY);
    }
}
