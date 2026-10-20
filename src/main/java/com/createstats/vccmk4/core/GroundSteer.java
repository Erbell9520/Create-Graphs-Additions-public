package com.createstats.vccmk4.core;

import java.util.ArrayList;
import java.util.List;

public final class GroundSteer {
    public static final double MOUNT_LOCK_DEG = 30.0;

    public static final double STEP_DEG = 2.0;

    public record Command(double angleDeg, double drive, boolean commanded) {
    }

    private GroundSteer() {
    }

    public static List<Command> solve(List<AxleLayout.WheelSpec> wheels, List<AxleLayout.Axle> axles,
                                      AxleRole[] roles, boolean[] driven, SteerPreset preset,
                                      double steer, double throttle, double lockDeg) {
        List<Command> out = new ArrayList<>(wheels.size());
        if (wheels.isEmpty()) return out;
        double s = clamp(steer, -1.0, 1.0);
        double t = clamp(throttle, -1.0, 1.0);
        double lock = clamp(lockDeg, 0.0, MOUNT_LOCK_DEG);
        int[] axleOf = AxleLayout.axleOf(wheels.size(), axles);

        AxleRole[] role = new AxleRole[wheels.size()];
        boolean[] drive = new boolean[wheels.size()];
        for (int i = 0; i < wheels.size(); i++) {
            int a = axleOf[i];
            role[i] = a >= 0 && roles != null && a < roles.length && roles[a] != null ? roles[a] : AxleRole.FIXED;
            drive[i] = a < 0 || driven == null || a >= driven.length || driven[a];
        }

        if (preset == SteerPreset.SKID) {
            for (int i = 0; i < wheels.size(); i++) {
                double side = wheels.get(i).lateral() < -1.0e-6 ? 1.0 : (wheels.get(i).lateral() > 1.0e-6 ? -1.0 : 0.0);
                double d = drive[i] ? clamp(t + side * s, -1.0, 1.0) : 0.0;
                out.add(new Command(0.0, d, role[i] != AxleRole.FREE));
            }
            return out;
        }

        if (preset == SteerPreset.CRAB) {
            for (int i = 0; i < wheels.size(); i++) {
                double angle = role[i] == AxleRole.STEER ? s * lock : 0.0;
                out.add(new Command(angle, drive[i] ? t : 0.0, role[i] != AxleRole.FREE));
            }
            return out;
        }

        double pivot = pivot(axles, roles);
        boolean anySteered = false;
        for (int i = 0; i < wheels.size(); i++) anySteered |= role[i] == AxleRole.STEER;
        double kappaMax = anySteered && lock > 1.0e-9 ? maxCurvature(wheels, role, pivot, lock) : 0.0;
        double kappa = s * kappaMax;

        double[] factor = new double[wheels.size()];
        double maxFactor = 0.0;
        for (int i = 0; i < wheels.size(); i++) {
            factor[i] = speedFactor(wheels.get(i), kappa, pivot);
            maxFactor = Math.max(maxFactor, factor[i]);
        }
        if (maxFactor < 1.0e-9) maxFactor = 1.0;

        for (int i = 0; i < wheels.size(); i++) {
            double angle = role[i] == AxleRole.STEER ? headingDeg(wheels.get(i), kappa, pivot) : 0.0;
            angle = clamp(angle, -lock, lock);
            double d = drive[i] ? t * factor[i] / maxFactor : 0.0;
            out.add(new Command(angle, d, role[i] != AxleRole.FREE));
        }
        return out;
    }

    public static double[] turnCentre(List<AxleLayout.WheelSpec> wheels, List<AxleLayout.Axle> axles, AxleRole[] roles,
                                      SteerPreset preset, double steer, double lockDeg) {
        if (wheels.isEmpty() || preset == SteerPreset.SKID || preset == SteerPreset.CRAB) return null;
        double lock = clamp(lockDeg, 0.0, MOUNT_LOCK_DEG);
        int[] axleOf = AxleLayout.axleOf(wheels.size(), axles);
        AxleRole[] role = new AxleRole[wheels.size()];
        boolean anySteered = false;
        for (int i = 0; i < wheels.size(); i++) {
            int a = axleOf[i];
            role[i] = a >= 0 && roles != null && a < roles.length && roles[a] != null ? roles[a] : AxleRole.FIXED;
            anySteered |= role[i] == AxleRole.STEER;
        }
        if (!anySteered || lock <= 1.0e-9) return null;
        double pivot = pivot(axles, roles);
        double kappa = clamp(steer, -1.0, 1.0) * maxCurvature(wheels, role, pivot, lock);
        if (Math.abs(kappa) < 1.0e-9) return null;
        return new double[]{Math.signum(kappa) / Math.abs(kappa), pivot};
    }

    public static int signalSteps(double angleDeg) {
        return (int) Math.min(15, Math.round(Math.abs(angleDeg) / STEP_DEG));
    }

    public static double pivot(List<AxleLayout.Axle> axles, AxleRole[] roles) {
        if (axles.isEmpty()) return 0.0;
        double sum = 0.0;
        int count = 0;
        for (AxleLayout.Axle a : axles) {
            AxleRole r = roles != null && a.index() < roles.length && roles[a.index()] != null ? roles[a.index()] : AxleRole.FIXED;
            if (r == AxleRole.FIXED) {
                sum += a.longitudinal();
                count++;
            }
        }
        if (count > 0) return sum / count;
        return (axles.get(0).longitudinal() + axles.get(axles.size() - 1).longitudinal()) * 0.5;
    }

    static double headingDeg(AxleLayout.WheelSpec w, double kappa, double pivot) {
        if (Math.abs(kappa) < 1.0e-9) return 0.0;
        double side = Math.signum(kappa);
        double radius = 1.0 / Math.abs(kappa);
        double across = radius - side * w.lateral();
        double along = w.longitudinal() - pivot;
        if (across <= 1.0e-6) return side * 90.0;
        return side * Math.toDegrees(Math.atan2(along, across));
    }

    private static double speedFactor(AxleLayout.WheelSpec w, double kappa, double pivot) {
        if (Math.abs(kappa) < 1.0e-9) return 1.0;
        double radius = 1.0 / Math.abs(kappa);
        double across = radius - Math.signum(kappa) * w.lateral();
        double along = w.longitudinal() - pivot;
        return Math.sqrt(across * across + along * along) / radius;
    }

    private static double maxCurvature(List<AxleLayout.WheelSpec> wheels, AxleRole[] role, double pivot, double lock) {
        double lo = 0.0;
        double hi = 10.0;
        if (sharpest(wheels, role, hi, pivot) <= lock) return hi;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) * 0.5;
            if (sharpest(wheels, role, mid, pivot) > lock) hi = mid;
            else lo = mid;
        }
        return lo;
    }

    private static double sharpest(List<AxleLayout.WheelSpec> wheels, AxleRole[] role, double kappa, double pivot) {
        double worst = 0.0;
        for (int i = 0; i < wheels.size(); i++) {
            if (role[i] != AxleRole.STEER) continue;
            worst = Math.max(worst, Math.abs(headingDeg(wheels.get(i), kappa, pivot)));
        }
        return worst;
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
