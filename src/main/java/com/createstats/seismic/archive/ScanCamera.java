package com.createstats.seismic.archive;

public final class ScanCamera {
    public static final double PLAN = 90.0;
    public static final double SECTION = 0.0;

    public final double yaw;
    public final double pitch;

    public final double scale;

    public final double cx;
    public final double cy;
    public final double cz;

    public final double sx;
    public final double sy;

    private final double cosYaw;
    private final double sinYaw;
    private final double cosPitch;
    private final double sinPitch;

    public ScanCamera(double yaw, double pitch, double scale, double cx, double cy, double cz, double sx, double sy) {
        this.yaw = wrap(yaw);
        this.pitch = Math.max(SECTION, Math.min(PLAN, pitch));
        this.scale = scale;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.sx = sx;
        this.sy = sy;
        final double a = Math.toRadians(this.yaw);
        final double p = Math.toRadians(this.pitch);
        this.cosYaw = Math.cos(a);
        this.sinYaw = Math.sin(a);
        this.cosPitch = Math.cos(p);
        this.sinPitch = Math.sin(p);
    }

    public static double wrap(double deg) {
        final double w = deg % 360.0;
        return w < 0.0 ? w + 360.0 : w;
    }

    public void project(double x, double y, double z, double[] out) {
        final double dx = x - cx;
        final double dy = y - cy;
        final double dz = z - cz;

        final double right = dx * cosYaw + dz * sinYaw;
        final double away = dx * sinYaw - dz * cosYaw;
        final double up = dy * cosPitch + away * sinPitch;
        out[0] = sx + right * scale;
        out[1] = sy - up * scale;
        out[2] = away * cosPitch - dy * sinPitch;
    }

    public double[] unproject(double screenX, double screenY) {
        final double r = (screenX - sx) / scale;
        final double u = (sy - screenY) / scale;

        return new double[] {cx + cosYaw * r + sinYaw * sinPitch * u, cy + cosPitch * u,
                cz + sinYaw * r - cosYaw * sinPitch * u};
    }

    public double[] groundDirection(double bearing) {
        final double b = Math.toRadians(bearing);
        final double dx = Math.sin(b);
        final double dz = -Math.cos(b);
        final double right = dx * cosYaw + dz * sinYaw;
        final double away = dx * sinYaw - dz * cosYaw;
        final double sxv = right;
        final double syv = -away * sinPitch;
        final double len = Math.sqrt(sxv * sxv + syv * syv);
        return len < 1.0e-9 ? new double[] {0.0, 0.0} : new double[] {sxv / len, syv / len};
    }

    public ScanCamera orbit(double dYaw, double dPitch) {
        return new ScanCamera(yaw + dYaw, pitch + dPitch, scale, cx, cy, cz, sx, sy);
    }

    public ScanCamera centredOn(double x, double y, double z) {
        return new ScanCamera(yaw, pitch, scale, x, y, z, sx, sy);
    }

    public static ScanCamera fit(double yaw, double pitch, double x0, double y0, double z0, double x1, double y1,
                                 double z1, double rx, double ry, double rw, double rh, double margin, double zoom) {
        final double mx = (x0 + x1) * 0.5;
        final double my = (y0 + y1) * 0.5;
        final double mz = (z0 + z1) * 0.5;
        final ScanCamera unit = new ScanCamera(yaw, pitch, 1.0, mx, my, mz, 0.0, 0.0);
        double lx = Double.MAX_VALUE;
        double ly = Double.MAX_VALUE;
        double hx = -Double.MAX_VALUE;
        double hy = -Double.MAX_VALUE;
        final double[] p = new double[3];
        for (int i = 0; i < 8; i++) {
            unit.project((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1, p);
            lx = Math.min(lx, p[0]);
            hx = Math.max(hx, p[0]);
            ly = Math.min(ly, p[1]);
            hy = Math.max(hy, p[1]);
        }
        final double clear = Math.min(rw, rh) * margin;
        final double w = Math.max(1.0e-6, hx - lx);
        final double h = Math.max(1.0e-6, hy - ly);
        final double s = Math.max(1.0e-6, Math.min((rw - clear * 2.0) / w, (rh - clear * 2.0) / h))
                * Math.max(0.05, zoom);

        final double ox = rx + rw * 0.5 - (lx + hx) * 0.5 * s;
        final double oy = ry + rh * 0.5 - (ly + hy) * 0.5 * s;
        return new ScanCamera(yaw, pitch, s, mx, my, mz, ox, oy);
    }

    public boolean fromAbove() {
        return pitch > 1.0e-6;
    }
}
