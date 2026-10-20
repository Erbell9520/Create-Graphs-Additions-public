package com.createstats.vccmk3.runtime;

import com.createstats.pathfinder.model.RouteGuidance;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public final class RouteFollower {
    private static final double LOOKAHEAD = 18.0;

    private static final double ARRIVAL_RADIUS = 5.0;

    private static final double YAW_SATURATION_DEG = 40.0;

    private static final double TURN_FIRST_DEG = 60.0;

    public record Progress(boolean active,
                           int waypointIndex,
                           int waypointCount,
                           double distanceToEnd,
                           double thrustDemand,
                           double yawDemand,
                           String status) {
        public static Progress inactive(String status) {
            return new Progress(false, 0, 0, Double.NaN, 0.0, 0.0, status);
        }
    }

    private RouteFollower() {}

    public static Progress follow(@Nullable RouteGuidance guidance,
                                  @Nullable Vec3 craftPos,
                                  double headingDeg,
                                  double groundSpeed) {
        if (guidance == null) return Progress.inactive("No route from the Pathfinder");
        List<Vec3> waypoints = guidance.waypoints();
        if (waypoints.size() < 2) return Progress.inactive("Route has no usable waypoints");
        if (craftPos == null) return Progress.inactive("Cannot locate the craft");
        if (!Double.isFinite(headingDeg)) {
            return Progress.inactive("No heading. Fit a Gimbal Sensor or assemble the craft.");
        }

        int bestSegment = 0;
        double bestDistanceSq = Double.MAX_VALUE;
        Vec3 bestPoint = waypoints.get(0);
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vec3 projected = closestPointOnSegment(waypoints.get(i), waypoints.get(i + 1), craftPos);
            double distanceSq = projected.distanceToSqr(craftPos);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestSegment = i;
                bestPoint = projected;
            }
        }

        double remaining = distanceAlong(waypoints, bestSegment, bestPoint);
        if (remaining <= ARRIVAL_RADIUS) {
            return new Progress(true, waypoints.size() - 1, waypoints.size(), remaining, 0.0, 0.0,
                    "Route complete");
        }

        Vec3 target = pointAhead(waypoints, bestSegment, bestPoint, LOOKAHEAD);

        double dx = target.x - craftPos.x;
        double dz = target.z - craftPos.z;
        if (dx * dx + dz * dz < 1.0e-6) {
            return new Progress(true, bestSegment, waypoints.size(), remaining, 0.0, 0.0,
                    "Holding on the route");
        }

        double targetBearing = wrap360(Math.toDegrees(Math.atan2(dx, -dz)));
        double error = wrapSigned(targetBearing - headingDeg);

        double yawDemand = clamp(error / YAW_SATURATION_DEG, -1.0, 1.0);

        double thrustDemand = 1.0;
        Float targetSpeed = speedFor(guidance, bestSegment);
        if (targetSpeed != null && targetSpeed > 0.01f && Double.isFinite(groundSpeed)) {
            double error2 = (targetSpeed - groundSpeed) / Math.max(0.5, targetSpeed);
            thrustDemand = clamp(0.5 + error2, 0.0, 1.0);
        }

        if (remaining < 25.0) {
            thrustDemand *= clamp((remaining - ARRIVAL_RADIUS) / 20.0, 0.0, 1.0);
        }

        double misalignment = Math.abs(error);
        String status;
        if (misalignment > TURN_FIRST_DEG) {
            thrustDemand *= 0.15;
            status = String.format(java.util.Locale.ROOT, "Turning onto route, %.0f deg off", misalignment);
        } else if (misalignment > YAW_SATURATION_DEG) {
            double blend = (misalignment - YAW_SATURATION_DEG) / (TURN_FIRST_DEG - YAW_SATURATION_DEG);
            thrustDemand *= 1.0 - 0.8 * blend;
            status = String.format(java.util.Locale.ROOT, "Correcting onto route, %.0f m to run", remaining);
        } else if (remaining < 25.0) {
            status = String.format(java.util.Locale.ROOT, "Slowing for the last waypoint, %.0f m", remaining);
        } else {
            status = String.format(java.util.Locale.ROOT, "Waypoint %d of %d, %.0f m to run",
                    bestSegment + 2, waypoints.size(), remaining);
        }

        return new Progress(true, bestSegment + 1, waypoints.size(), remaining,
                clamp(thrustDemand, 0.0, 1.0), yawDemand, status);
    }

    private static Vec3 closestPointOnSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double lengthSq = ab.lengthSqr();
        if (lengthSq < 1.0e-9) return a;
        double t = clamp(p.subtract(a).dot(ab) / lengthSq, 0.0, 1.0);
        return a.add(ab.scale(t));
    }

    private static double distanceAlong(List<Vec3> waypoints, int segment, Vec3 from) {
        double total = from.distanceTo(waypoints.get(segment + 1));
        for (int i = segment + 1; i < waypoints.size() - 1; i++) {
            total += waypoints.get(i).distanceTo(waypoints.get(i + 1));
        }
        return total;
    }

    private static Vec3 pointAhead(List<Vec3> waypoints, int segment, Vec3 from, double distance) {
        Vec3 cursor = from;
        double left = distance;
        for (int i = segment; i < waypoints.size() - 1; i++) {
            Vec3 next = waypoints.get(i + 1);
            double step = cursor.distanceTo(next);
            if (step >= left) {
                Vec3 direction = next.subtract(cursor);
                double length = direction.length();
                if (length < 1.0e-9) return next;
                return cursor.add(direction.scale(left / length));
            }
            left -= step;
            cursor = next;
        }
        return waypoints.get(waypoints.size() - 1);
    }

    @Nullable
    private static Float speedFor(RouteGuidance guidance, int segment) {
        List<Float> speeds = guidance.targetSpeeds();
        if (speeds.isEmpty()) return null;
        return speeds.get(Math.min(segment, speeds.size() - 1));
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
