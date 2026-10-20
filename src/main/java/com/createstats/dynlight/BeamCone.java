package com.createstats.dynlight;

public record BeamCone(double innerDeg, double outerDeg, double hotDeg, double hotBoost, double fullTo, double reach,
                       boolean dipped, double gain, int colour) {
    public static final double CUT_BELOW_HORIZON = 1.0;

    public static final double KICK_UP = 15.0;

    public static final double CUT_SOFT = 1.0;

    public static final double PLANT_INCIDENCE = 0.5;

    private static final double KICK_SLOPE = Math.tan(Math.toRadians(KICK_UP));

    public BeamCone {
        innerDeg = Math.max(0.0, innerDeg);
        outerDeg = Math.max(innerDeg + 1.0e-6, outerDeg);
        hotDeg = Math.max(0.0, Math.min(innerDeg, hotDeg));
        hotBoost = Math.max(0.0, Math.min(1.0, hotBoost));
        fullTo = Math.max(0.0, fullTo);
        reach = Math.max(fullTo + 1.0e-6, reach);
        gain = Math.max(0.0, gain);
    }

    public double red() {
        return ((this.colour >> 16) & 0xFF) / 255.0;
    }

    public double green() {
        return ((this.colour >> 8) & 0xFF) / 255.0;
    }

    public double blue() {
        return (this.colour & 0xFF) / 255.0;
    }

    public double nearEdge(double height, double tipDeg) {
        return height / Math.tan(Math.toRadians(tipDeg + this.outerDeg));
    }

    public Aimed aim(double lx, double ly, double lz, double ax, double ay, double az) {
        return new Aimed(this, lx, ly, lz, ax, ay, az);
    }

    public static double expose(double k) {
        return k <= 0.0 ? 0.0 : 1.0 - Math.exp(-k);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0 : 1.0;
        }
        final double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double daylight(int blockLight, int skyLight, double skyDarken) {
        final double sky = brightness(skyLight) * (Math.max(0.0, Math.min(1.0, skyDarken)) * 0.95 + 0.05);
        return Math.max(0.0, Math.min(1.0, 1.0 - (brightness(blockLight) + sky)));
    }

    static double brightness(int level) {
        final double f = Math.max(0, Math.min(15, level)) / 15.0;
        return f / (4.0 - 3.0 * f);
    }

    public static double fogFade(double distance, double fogStart, double fogEnd) {
        if (distance <= fogStart) {
            return 1.0;
        }
        if (distance >= fogEnd) {
            return 0.0;
        }
        return smoothstep(fogEnd, fogStart, distance);
    }

    public static final class Aimed {
        private final double lx;
        private final double ly;
        private final double lz;
        private final double ax;
        private final double ay;
        private final double az;

        private final boolean hasForward;
        private final double fx;
        private final double fz;
        private final double rx;
        private final double rz;
        private final double cosOuter;
        private final double cosInner;
        private final double cosHot;
        private final double hotBoost;
        private final double fullTo;
        private final double reach;
        private final boolean dipped;

        Aimed(BeamCone cone, double lx, double ly, double lz, double ax, double ay, double az) {
            this.lx = lx;
            this.ly = ly;
            this.lz = lz;
            final double al = Math.sqrt(ax * ax + ay * ay + az * az);
            final boolean ok = al > 1.0e-9 && Double.isFinite(al);
            this.ax = ok ? ax / al : 0.0;
            this.ay = ok ? ay / al : 0.0;
            this.az = ok ? az / al : 0.0;
            final double hl = Math.sqrt(this.ax * this.ax + this.az * this.az);
            this.hasForward = hl > 1.0e-6;
            this.fx = this.hasForward ? this.ax / hl : 0.0;
            this.fz = this.hasForward ? this.az / hl : 0.0;

            this.rx = -this.fz;
            this.rz = this.fx;
            this.cosOuter = Math.cos(Math.toRadians(cone.outerDeg()));
            this.cosInner = Math.cos(Math.toRadians(cone.innerDeg()));
            this.cosHot = Math.cos(Math.toRadians(cone.hotDeg()));
            this.hotBoost = cone.hotBoost();
            this.fullTo = cone.fullTo();
            this.reach = cone.reach();
            this.dipped = cone.dipped();
        }

        public double axisX() {
            return this.ax;
        }

        public double axisY() {
            return this.ay;
        }

        public double axisZ() {
            return this.az;
        }

        public double at(double px, double py, double pz, double nx, double ny, double nz) {
            final double vx = px - this.lx;
            final double vy = py - this.ly;
            final double vz = pz - this.lz;
            final double d2 = vx * vx + vy * vy + vz * vz;
            if (!(d2 > 1.0e-6) || d2 > this.reach * this.reach) {
                return 0.0;
            }
            final double d = Math.sqrt(d2);
            final double c = (vx * this.ax + vy * this.ay + vz * this.az) / d;
            if (c <= this.cosOuter) {
                return 0.0;
            }
            final double incidence = Double.isNaN(nx) ? PLANT_INCIDENCE
                    : Math.max(0.0, -(nx * vx + ny * vy + nz * vz) / d);
            if (incidence <= 0.0) {
                return 0.0;
            }
            double cut = 1.0;
            if (this.dipped && this.hasForward) {
                final double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, vy / d))));
                final double forward = vx * this.fx + vz * this.fz;
                final double right = vx * this.rx + vz * this.rz;
                final double lateral = Math.toDegrees(Math.atan2(right, forward));
                final double limit = -CUT_BELOW_HORIZON + Math.max(0.0, lateral) * KICK_SLOPE;
                cut = smoothstep(limit + CUT_SOFT * 0.5, limit - CUT_SOFT * 0.5, elevation);
                if (cut <= 0.0) {
                    return 0.0;
                }
            }
            final double angular = smoothstep(this.cosOuter, this.cosInner, c) * (1.0 - this.hotBoost)
                    + this.hotBoost * smoothstep(this.cosInner, this.cosHot, c);
            final double distance = d <= this.fullTo ? 1.0 : smoothstep(this.reach, this.fullTo, d);
            return angular * distance * incidence * cut;
        }
    }
}
