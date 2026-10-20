package com.createstats.vccmk3.runtime;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk3.avionics.AvionicsState;

import javax.annotation.Nullable;

// drives the controls, not the hardware. you see what it asks for
public final class AutoPilot {
    public record Guidance(boolean engaged,
                           double thrustDemand,
                           double yawDemand,
                           double distance,
                           double bearingDeg,
                           String status) {
        public static Guidance idle(String status) {
            return new Guidance(false, 0.0, 0.0, Double.NaN, Double.NaN, status);
        }

        public boolean hasFix() {
            return Double.isFinite(distance);
        }
    }

    public static final double ARRIVAL_RADIUS = 6.0;

    public static final double CRUISE_RADIUS = 60.0;

    private static final double YAW_SATURATION_DEG = 45.0;

    private static final double TURN_FIRST_DEG = 65.0;

    private AutoPilot() {}

    public static Guidance compute(@Nullable net.minecraft.world.level.block.entity.BlockEntity navBlockEntity,
                                   AvionicsState avionics,
                                   boolean isGround,
                                   @Nullable net.minecraft.world.phys.Vec3 craftPos) {
        SableBridge.NavReading nav = SableBridge.readNav(navBlockEntity);
        if (nav == null) {
            return Guidance.idle("No Nav Table linked, or no target set on it");
        }
        if (!Double.isFinite(nav.distance())) {
            return Guidance.idle("Nav Table has a target but no range yet");
        }

        double distance = nav.distance();

        if (craftPos == null || !avionics.headingDeg().present()) {
            return Guidance.idle("No heading. Fit a Gimbal Sensor, or assemble the craft.");
        }
        double dx = nav.target().x - craftPos.x;
        double dz = nav.target().z - craftPos.z;
        if (dx * dx + dz * dz < 1.0e-6) {
            return new Guidance(true, 0.0, 0.0, distance, 0.0, "Over the target");
        }

        double targetBearing = wrap360(Math.toDegrees(Math.atan2(dx, -dz)));
        double bearingError = wrapSigned(targetBearing - avionics.headingDeg().value());

        if (distance <= ARRIVAL_RADIUS) {
            return new Guidance(true, 0.0, 0.0, distance, bearingError,
                    String.format(java.util.Locale.ROOT, "Arrived, holding at %.0f m", distance));
        }

        double yawDemand = clamp(bearingError / YAW_SATURATION_DEG, -1.0, 1.0);

        double approach = clamp((distance - ARRIVAL_RADIUS) / (CRUISE_RADIUS - ARRIVAL_RADIUS), 0.0, 1.0);
        double thrustDemand = approach;

        double misalignment = Math.abs(bearingError);
        if (misalignment > TURN_FIRST_DEG) {
            thrustDemand *= isGround ? 0.35 : 0.15;
        } else if (misalignment > YAW_SATURATION_DEG) {
            double blend = (misalignment - YAW_SATURATION_DEG) / (TURN_FIRST_DEG - YAW_SATURATION_DEG);
            thrustDemand *= 1.0 - 0.8 * blend;
        }

        String status;
        if (Double.isFinite(nav.closingRate()) && nav.closingRate() > 0.0) {
            double ticksToArrival = (distance - ARRIVAL_RADIUS) / Math.max(1.0e-3, nav.closingRate());
            if (ticksToArrival < 20.0) {
                thrustDemand *= clamp(ticksToArrival / 20.0, 0.0, 1.0);
                status = String.format(java.util.Locale.ROOT, "Slowing for arrival, %.0f m", distance);
            } else {
                status = String.format(java.util.Locale.ROOT, "Cruising to target, %.0f m", distance);
            }
        } else {
            status = String.format(java.util.Locale.ROOT, "Running to target, %.0f m", distance);
        }

        if (misalignment > TURN_FIRST_DEG) {
            status = String.format(java.util.Locale.ROOT, "Turning onto target, %.0f deg off", misalignment);
        }

        return new Guidance(true, clamp(thrustDemand, 0.0, 1.0), yawDemand, distance, bearingError, status);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return value < min ? min : Math.min(value, max);
    }

    private static double wrap360(double degrees) {
        double wrapped = degrees % 360.0;
        return wrapped < 0.0 ? wrapped + 360.0 : wrapped;
    }

    private static double wrapSigned(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }
}
