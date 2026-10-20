package com.createstats.dynlight;

import net.minecraft.world.phys.Vec3;

public record BeamLight(double x, double y, double z, double dx, double dy, double dz,
                        int level, double reach, double fullDistance, double fadeDistance, int farLevel,
                        double baseRadius, double spread, double maxRadius, int spill) implements Light {
    public BeamLight {
        final double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0e-9 && Double.isFinite(len)) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dx = 0.0;
            dy = 0.0;
            dz = 0.0;
        }
        level = clampLevel(level);
        reach = Math.max(0.0, reach);
        fullDistance = Math.max(0.0, fullDistance);
        fadeDistance = Math.max(fullDistance, fadeDistance);
        farLevel = Math.min(level, clampLevel(farLevel));
        baseRadius = Math.max(0.0, baseRadius);
        spread = Math.max(0.0, spread);
        maxRadius = Math.max(0.0, maxRadius);
        spill = clampLevel(spill);
    }

    @Override
    public boolean isValid() {
        return Light.super.isValid() && (this.dx != 0.0 || this.dy != 0.0 || this.dz != 0.0);
    }

    public int levelAt(double distance) {
        return BeamShape.levelAt(distance, this.level, this.fullDistance, this.fadeDistance, this.farLevel,
                this.reach);
    }

    public double poolRadiusAt(double distance) {
        return BeamShape.poolRadius(distance, this.baseRadius, this.spread, this.maxRadius);
    }

    public static Builder from(Vec3 origin, Vec3 direction) {
        return new Builder(origin.x, origin.y, origin.z, direction.x, direction.y, direction.z);
    }

    public static Builder from(double x, double y, double z, double dx, double dy, double dz) {
        return new Builder(x, y, z, dx, dy, dz);
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(EmissionPlan.MAX_LEVEL, level));
    }

    public static final class Builder {
        private final double x;
        private final double y;
        private final double z;
        private final double dx;
        private final double dy;
        private final double dz;
        private int level = 12;
        private double reach = 24.0;
        private double fullDistance = Double.NaN;
        private double fadeDistance = Double.NaN;
        private int farLevel = 2;
        private double baseRadius = 0.5;
        private double spread = 0.15;
        private double maxRadius = 4.0;
        private int spill;

        private Builder(double x, double y, double z, double dx, double dy, double dz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder reach(double reach) {
            this.reach = reach;
            return this;
        }

        public Builder fullTo(double distance) {
            this.fullDistance = distance;
            return this;
        }

        public Builder fadeTo(double distance, int level) {
            this.fadeDistance = distance;
            this.farLevel = level;
            return this;
        }

        public Builder pool(double baseRadius, double spread, double maxRadius) {
            this.baseRadius = baseRadius;
            this.spread = spread;
            this.maxRadius = maxRadius;
            return this;
        }

        public Builder spill(int level) {
            this.spill = level;
            return this;
        }

        public BeamLight build() {
            final double full = Double.isNaN(this.fullDistance) ? this.reach * 0.5 : this.fullDistance;
            final double fade = Double.isNaN(this.fadeDistance) ? this.reach : this.fadeDistance;
            return new BeamLight(this.x, this.y, this.z, this.dx, this.dy, this.dz, this.level, this.reach,
                    full, fade, this.farLevel, this.baseRadius, this.spread, this.maxRadius, this.spill);
        }
    }
}
