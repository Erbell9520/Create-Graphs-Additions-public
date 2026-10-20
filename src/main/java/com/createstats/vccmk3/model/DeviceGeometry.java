package com.createstats.vccmk3.model;

import com.createstats.vccmk3.avionics.SensorSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

// knowing where a prop is and not which way it pushes was most of what was wrong
public record DeviceGeometry(
        @Nullable Vec3 thrust,
        @Nullable Vec3 hinge,
        int commandSign,
        SensorSource source,
        String note
) {
    public static final DeviceGeometry UNKNOWN =
            new DeviceGeometry(null, null, 1, SensorSource.NONE, "not probed");

    private static final double MIN_LENGTH = 1.0e-6;

    public DeviceGeometry {
        thrust = normalised(thrust);
        hinge = normalised(hinge);
        commandSign = commandSign < 0 ? -1 : 1;
        if (source == null) source = SensorSource.NONE;
        if (note == null) note = "";
    }

    public static DeviceGeometry pushing(@Nullable Vec3 direction, SensorSource source) {
        Vec3 unit = normalised(direction);
        if (unit == null) {
            return unavailable("This block reports no thrust direction.");
        }
        return new DeviceGeometry(unit, null, 1, source, "");
    }

    public static DeviceGeometry swinging(@Nullable Vec3 axis, int commandSign, SensorSource source) {
        Vec3 unit = normalised(axis);
        if (unit == null) {
            return unavailable("This bearing reports no hinge axis.");
        }
        return new DeviceGeometry(null, unit, commandSign, source, "");
    }

    public static DeviceGeometry unavailable(String note) {
        return new DeviceGeometry(null, null, 1, SensorSource.NONE, note);
    }

    public static DeviceGeometry notSpatial() {
        return new DeviceGeometry(null, null, 1, SensorSource.NONE,
                "A signal channel has no direction, which is fine.");
    }

    public boolean hasThrust() {
        return thrust != null;
    }

    public boolean hasHinge() {
        return hinge != null;
    }

    public boolean isUsable() {
        return thrust != null || hinge != null;
    }

    @Nullable
    public Vec3 surfaceForce(@Nullable Vec3 forward) {
        if (hinge == null || forward == null) return null;
        return normalised(hinge.cross(forward));
    }

    @Nullable
    private static Vec3 normalised(@Nullable Vec3 vector) {
        if (vector == null) return null;
        double x = vector.x, y = vector.y, z = vector.z;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < MIN_LENGTH) return null;
        return new Vec3(x / length, y / length, z / length);
    }
}
