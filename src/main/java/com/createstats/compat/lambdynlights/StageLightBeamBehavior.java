package com.createstats.compat.lambdynlights;

import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/** [1] */
public final class StageLightBeamBehavior implements DynamicLightBehavior {

    /** Minimum brightness past the far distance. */
    private static final double MIN_BRIGHTNESS = 1.0;
    /** [2] */
    public static final double MAX_CONE_RADIUS = 16.0;
    /** [3] */
    private static final double END_FADE = 4.0;
    /** [4] */
    private static final double RIM_FADE = 0.4;
    /** [5] */
    public static final int OCCLUDED_OVERSHOOT = 3;

    // Head (source) position in world space.
    private double sx;
    private double sy;
    private double sz;
    // Unit beam direction in world space.
    private double dx;
    private double dy;
    private double dz;
    private int range;
    private int peakDistance;
    private int farDistance;
    private int brightness;
    private int focus;
    /** Per-light max radius (blocks) of the lit pool; caps {@link #coneRadius}. */
    private int lightSpread;
    /** Whether the beam is stopped by a solid block (wall/floor) at {@link #range}, vs. ending in open air. */
    private boolean occluded;

    private boolean removed;
    private boolean changed = true;

    // Change detection (block-granularity on source, plus range/brightness/direction).
    private int lastSbx;
    private int lastSby;
    private int lastSbz;
    private int lastRange;
    private int lastPeakDistance;
    private int lastFarDistance;
    private int lastBrightness;
    private int lastFocus;
    private int lastLightSpread;
    private boolean lastOccluded;
    private double lastDx;
    private double lastDy;
    private double lastDz;

    public StageLightBeamBehavior(double sx, double sy, double sz,
                                  double dx, double dy, double dz, int range, int peakDistance,
                                  int farDistance, int brightness, int focus, int lightSpread, boolean occluded) {
        this.set(sx, sy, sz, dx, dy, dz, range, peakDistance, farDistance, brightness, focus, lightSpread, occluded);
        this.snapshot();
    }

    /** Update the cone's source, direction, range, tier falloff, brightness, focus and occlusion. */
    public void update(double sx, double sy, double sz,
                       double dx, double dy, double dz, int range, int peakDistance,
                       int farDistance, int brightness, int focus, int lightSpread, boolean occluded) {
        this.set(sx, sy, sz, dx, dy, dz, range, peakDistance, farDistance, brightness, focus, lightSpread, occluded);
        if (Mth.floor(sx) != this.lastSbx || Mth.floor(sy) != this.lastSby || Mth.floor(sz) != this.lastSbz
                || range != this.lastRange || peakDistance != this.lastPeakDistance
                || farDistance != this.lastFarDistance || brightness != this.lastBrightness
                || focus != this.lastFocus || lightSpread != this.lastLightSpread || occluded != this.lastOccluded
                || Math.abs(dx - this.lastDx) > 0.02 || Math.abs(dy - this.lastDy) > 0.02
                || Math.abs(dz - this.lastDz) > 0.02) {
            this.snapshot();
            this.changed = true;
        }
    }

    private void set(double sx, double sy, double sz,
                     double dx, double dy, double dz, int range, int peakDistance,
                     int farDistance, int brightness, int focus, int lightSpread, boolean occluded) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) {
            len = 1.0;
            dx = 0.0;
            dy = 1.0;
            dz = 0.0;
        }
        this.dx = dx / len;
        this.dy = dy / len;
        this.dz = dz / len;
        this.range = Math.max(1, range);
        this.peakDistance = Math.max(1, peakDistance);
        this.farDistance = Math.max(this.peakDistance, farDistance);
        this.brightness = Mth.clamp(brightness, 0, 15);
        this.focus = Mth.clamp(focus, 1, 10);
        this.lightSpread = Mth.clamp(lightSpread, 2, (int) MAX_CONE_RADIUS);
        this.occluded = occluded;
    }

    private void snapshot() {
        this.lastSbx = Mth.floor(this.sx);
        this.lastSby = Mth.floor(this.sy);
        this.lastSbz = Mth.floor(this.sz);
        this.lastRange = this.range;
        this.lastPeakDistance = this.peakDistance;
        this.lastFarDistance = this.farDistance;
        this.lastBrightness = this.brightness;
        this.lastFocus = this.focus;
        this.lastLightSpread = this.lightSpread;
        this.lastOccluded = this.occluded;
        this.lastDx = this.dx;
        this.lastDy = this.dy;
        this.lastDz = this.dz;
    }

    /** Flag this behavior to be discarded by LambDynamicLights on its next pass. */
    public void markRemoved() {
        this.removed = true;
        this.changed = true;
    }

    /** [6] */
    private double coneRadius(double t) {
        return Math.min(1.0 + t * this.focus / 26.0, Math.min((double) this.lightSpread, MAX_CONE_RADIUS));
    }

    @Override
    public double lightAtPos(BlockPos pos, double falloffRatio) {
        final double px = pos.getX() + 0.5 - this.sx;
        final double py = pos.getY() + 0.5 - this.sy;
        final double pz = pos.getZ() + 0.5 - this.sz;

        // Distance along the beam axis.
        final double t = px * this.dx + py * this.dy + pz * this.dz;
        if (t < 0.0 || t > this.range) {
            return 0.0;
        }
        // Perpendicular distance from the beam axis.
        final double cx = px - t * this.dx;
        final double cy = py - t * this.dy;
        final double cz = pz - t * this.dz;
        final double perp = Math.sqrt(cx * cx + cy * cy + cz * cz);
        final double radius = coneRadius(t);
        if (perp > radius) {
            return 0.0;
        }
        // Brightness by distance along the beam: full to the configured peak, then taper to the tier max.
        final double b;
        if (t <= this.peakDistance) {
            b = this.brightness;
        } else {
            final double width = Math.max(1.0, this.farDistance - this.peakDistance);
            final double k = Math.min(1.0, (t - this.peakDistance) / width);
            b = Mth.lerp(k, this.brightness, MIN_BRIGHTNESS);
        }
        // [7]
        final double edge = 1.0 - (perp / radius);
        final double rim = Math.min(1.0, edge / RIM_FADE);
        // [8]
        final double headFade = Math.min(END_FADE, this.range * 0.5);
        final double farWidth = this.occluded ? OCCLUDED_OVERSHOOT : Math.min(END_FADE, this.range * 0.5);
        double ends = headFade <= 0.0 ? 1.0 : Math.min(1.0, t / headFade);
        if (farWidth > 0.0) {
            ends = Math.min(ends, Math.min(1.0, (this.range - t) / farWidth));
        }
        final double v = b * rim * Math.max(0.0, ends);
        return Math.max(0.0, Math.min(15.0, v));
    }

    @Override
    public BoundingBox getBoundingBox() {
        final double ex = this.sx + this.dx * this.range;
        final double ey = this.sy + this.dy * this.range;
        final double ez = this.sz + this.dz * this.range;
        final double r = coneRadius(this.range);
        final int minX = Mth.floor(Math.min(this.sx, ex) - r);
        final int minY = Mth.floor(Math.min(this.sy, ey) - r);
        final int minZ = Mth.floor(Math.min(this.sz, ez) - r);
        final int maxX = Mth.ceil(Math.max(this.sx, ex) + r);
        final int maxY = Mth.ceil(Math.max(this.sy, ey) + r);
        final int maxZ = Mth.ceil(Math.max(this.sz, ez) + r);
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public boolean hasChanged() {
        if (this.changed) {
            this.changed = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean isRemoved() {
        return this.removed;
    }
}
