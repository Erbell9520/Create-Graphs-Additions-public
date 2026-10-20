package com.createstats.vccmk3.avionics;

import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;

public final class AvionicsSampler {
    public static final Vector3d DEFAULT_NOSE = new Vector3d(0.0, 0.0, -1.0);

    private AvionicsSampler() {}

    public static AvionicsState sample(@Nullable Level level,
                                       @Nullable BlockEntity host,
                                       @Nullable BlockPos gimbalPos,
                                       @Nullable BlockPos altPos,
                                       @Nullable BlockPos velPos,
                                       @Nullable BlockPos opticalPos,
                                       @Nullable Vector3d noseBody) {
        if (level == null || host == null) return AvionicsState.EMPTY;

        final Vector3d nose = normalisedOr(noseBody, DEFAULT_NOSE);
        final Object subLevel = SableBridge.subLevelAccessOf(host);
        final Vec3 worldPos = worldPositionOf(host, subLevel);

        SableBridge.GimbalReading gimbal = SableBridge.readGimbal(beAt(level, gimbalPos));
        SableBridge.AltitudeReading altitude = SableBridge.readAltitude(beAt(level, altPos));
        SableBridge.VelocityReading velocity = SableBridge.readVelocity(beAt(level, velPos));
        SableBridge.OpticalReading optical = SableBridge.readOptical(beAt(level, opticalPos));

        Quaterniond bodyToWorld = subLevel != null ? SableBridge.bodyOrientation(subLevel) : null;
        Vector3d linearVelocity = SableBridge.linearVelocityWorld(host);

        boolean noseAlongX = Math.abs(nose.x) > Math.abs(nose.z);
        double sensorPitch = Double.NaN;
        double sensorRoll = Double.NaN;
        if (gimbal != null) {
            sensorPitch = noseAlongX ? gimbal.zAngleDeg() : gimbal.xAngleDeg();
            sensorRoll = noseAlongX ? gimbal.xAngleDeg() : gimbal.zAngleDeg();
        }

        double derivedPitch = Double.NaN;
        double derivedRoll = Double.NaN;
        double derivedHeading = Double.NaN;
        if (bodyToWorld != null) {
            Quaterniond q = new Quaterniond(bodyToWorld);
            Vector3d forward = q.transform(new Vector3d(nose));
            Vector3d right = q.transform(rightOf(nose));
            Vector3d up = q.transform(new Vector3d(0.0, 1.0, 0.0));
            if (isFinite(forward) && forward.lengthSquared() > 1.0e-9) {
                forward.normalize();
                derivedPitch = Math.toDegrees(Math.asin(clamp(forward.y, -1.0, 1.0)));

                derivedHeading = wrap360(Math.toDegrees(Math.atan2(forward.x, -forward.z)));
            }
            if (isFinite(right) && isFinite(up)) {
                derivedRoll = Math.toDegrees(Math.atan2(-right.y, up.y));
            }
        }

        // along its own facing, how you mount it matters. that's not a bug

        double sensorGroundSpeed = velocity != null ? Math.abs(velocity.alongNormal()) : Double.NaN;
        double derivedGroundSpeed = Double.NaN;
        double derivedClimb = Double.NaN;
        if (linearVelocity != null && isFinite(linearVelocity)) {
            derivedGroundSpeed = Math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z);
            derivedClimb = linearVelocity.y;
        }

        double airspeed = Double.NaN;
        double windSpeed = Double.NaN;
        double windFrom = Double.NaN;
        if (worldPos != null) {
            Vec3 air = SableBridge.airVelocityWorldAt(level, worldPos);
            if (air != null) airspeed = Math.sqrt(air.x * air.x + air.z * air.z);
            Vec3 wind = SableBridge.windWorldAt(level, worldPos);
            if (wind != null) {
                windSpeed = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
                if (windSpeed > 1.0e-4) {
                    windFrom = wrap360(Math.toDegrees(Math.atan2(-wind.x, wind.z)));
                }
            }
        }

        double sensorAltitude = altitude != null ? altitude.worldHeight() : Double.NaN;
        double derivedAltitude = host.getBlockPos().getY();
        if (worldPos != null) derivedAltitude = worldPos.y;

        return new AvionicsState(
                AvionicsChannel.resolve(sensorAltitude, derivedAltitude),
                altitude != null ? AvionicsChannel.sensor(altitude.normalHeight()) : AvionicsChannel.NONE,
                altitude != null ? AvionicsChannel.sensor(altitude.airPressure()) : AvionicsChannel.NONE,
                AvionicsChannel.resolve(sensorPitch, derivedPitch),
                AvionicsChannel.resolve(sensorRoll, derivedRoll),
                AvionicsChannel.derived(derivedHeading),
                AvionicsChannel.resolve(sensorGroundSpeed, derivedGroundSpeed),
                AvionicsChannel.derived(airspeed),
                AvionicsChannel.derived(derivedClimb),
                AvionicsChannel.derived(windSpeed),
                AvionicsChannel.derived(windFrom),
                optical != null ? AvionicsChannel.sensor(optical.distance()) : AvionicsChannel.NONE);
    }

    @Nullable
    private static BlockEntity beAt(Level level, @Nullable BlockPos pos) {
        if (pos == null) return null;
        if (!level.isLoaded(pos)) return null;
        return SableBridge.getBlockEntityOnContraption(level, pos);
    }

    @Nullable
    private static Vec3 worldPositionOf(BlockEntity host, @Nullable Object subLevel) {
        BlockPos pos = host.getBlockPos();
        if (subLevel != null) {
            Vector3d world = SableBridge.localToWorld(subLevel,
                    new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (world != null && isFinite(world)) return new Vec3(world.x, world.y, world.z);
        }
        return Vec3.atCenterOf(pos);
    }

    private static Vector3d rightOf(Vector3d nose) {
        Vector3d right = new Vector3d(0.0, 1.0, 0.0).cross(new Vector3d(nose));
        if (right.lengthSquared() < 1.0e-9) return new Vector3d(1.0, 0.0, 0.0);
        return right.normalize();
    }

    private static Vector3d normalisedOr(@Nullable Vector3d candidate, Vector3d fallback) {
        if (candidate == null || !isFinite(candidate) || candidate.lengthSquared() < 1.0e-9) {
            return new Vector3d(fallback);
        }
        return new Vector3d(candidate).normalize();
    }

    private static boolean isFinite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    private static double wrap360(double degrees) {
        double d = degrees % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }
}
