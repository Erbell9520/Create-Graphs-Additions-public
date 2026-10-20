package com.createstats.vcu.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WheelSteer {
    public static final double MAX_ANGLE_DEG = 30.0;

    public static final double STEP_DEG = 2.0;

    public static final double AXLE_TOLERANCE = 0.75;

    public enum Mode {
        FRONT,

        ALL,

        SKID
    }

    public record Wheel(double lateral, double longitudinal) {
    }

    public record Command(double angleDeg, double drive, boolean steered) {
    }

    private WheelSteer() {
    }

    public static List<Command> solve(List<Wheel> wheels, double steer, double throttle, Mode mode) {
        List<Command> out = new ArrayList<>(wheels.size());
        if (wheels.isEmpty()) return out;
        double s = Allocator.clamp(steer, -1.0, 1.0);
        double t = Allocator.clamp(throttle, -1.0, 1.0);

        if (mode == Mode.SKID) {
            for (Wheel w : wheels) {
                double side = w.lateral() < -1.0e-6 ? 1.0 : (w.lateral() > 1.0e-6 ? -1.0 : 0.0);
                out.add(new Command(0.0, Allocator.clamp(t + side * s, -1.0, 1.0), false));
            }
            return out;
        }

        double[] axles = axleCentres(wheels);
        double pivot = pivot(axles, mode);
        double mid = (axles[0] + axles[axles.length - 1]) * 0.5;
        boolean[] steered = new boolean[wheels.size()];
        boolean anySteered = false;
        for (int i = 0; i < wheels.size(); i++) {
            double z = wheels.get(i).longitudinal();

            steered[i] = mode == Mode.ALL
                    ? Math.abs(z - pivot) > AXLE_TOLERANCE * 0.5
                    : z > mid + 1.0e-9;
            anySteered |= steered[i];
        }

        double kappaMax = anySteered ? maxCurvature(wheels, steered, pivot) : 0.0;
        double kappa = s * kappaMax;

        double[] factor = new double[wheels.size()];
        double maxFactor = 0.0;
        for (int i = 0; i < wheels.size(); i++) {
            factor[i] = speedFactor(wheels.get(i), kappa, pivot);
            maxFactor = Math.max(maxFactor, factor[i]);
        }
        if (maxFactor < 1.0e-9) maxFactor = 1.0;

        for (int i = 0; i < wheels.size(); i++) {
            double angle = steered[i] ? headingDeg(wheels.get(i), kappa, pivot) : 0.0;
            angle = Allocator.clamp(angle, -MAX_ANGLE_DEG, MAX_ANGLE_DEG);
            out.add(new Command(angle, t * factor[i] / maxFactor, steered[i]));
        }
        return out;
    }

    public static int signalSteps(double angleDeg) {
        return (int) Math.min(15, Math.round(Math.abs(angleDeg) / STEP_DEG));
    }

    static double[] axleCentres(List<Wheel> wheels) {
        double[] z = new double[wheels.size()];
        for (int i = 0; i < z.length; i++) {
            z[i] = wheels.get(i).longitudinal();
        }
        Arrays.sort(z);
        List<Double> centres = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= z.length; i++) {
            if (i == z.length || z[i] - z[start] > AXLE_TOLERANCE) {
                double sum = 0.0;
                for (int k = start; k < i; k++) sum += z[k];
                centres.add(sum / (i - start));
                start = i;
            }
        }
        double[] out = new double[centres.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = centres.get(out.length - 1 - i);
        }
        return out;
    }

    static double pivot(double[] axlesFrontToBack, Mode mode) {
        double front = axlesFrontToBack[0];
        double rear = axlesFrontToBack[axlesFrontToBack.length - 1];
        double mid = (front + rear) * 0.5;
        if (mode == Mode.ALL) return mid;
        double sum = 0.0;
        int count = 0;
        for (double z : axlesFrontToBack) {
            if (z <= mid + 1.0e-9) {
                sum += z;
                count++;
            }
        }
        return count == 0 ? rear : sum / count;
    }

    static double headingDeg(Wheel w, double kappa, double pivot) {
        if (Math.abs(kappa) < 1.0e-9) return 0.0;
        double side = Math.signum(kappa);
        double radius = 1.0 / Math.abs(kappa);
        double across = radius - side * w.lateral();
        double along = w.longitudinal() - pivot;
        if (across <= 1.0e-6) return side * 90.0;
        return side * Math.toDegrees(Math.atan2(along, across));
    }

    private static double speedFactor(Wheel w, double kappa, double pivot) {
        if (Math.abs(kappa) < 1.0e-9) return 1.0;
        double radius = 1.0 / Math.abs(kappa);
        double across = radius - Math.signum(kappa) * w.lateral();
        double along = w.longitudinal() - pivot;
        return Math.sqrt(across * across + along * along) / radius;
    }

    private static double maxCurvature(List<Wheel> wheels, boolean[] steered, double pivot) {
        double lo = 0.0;
        double hi = 10.0;
        if (sharpest(wheels, steered, hi, pivot) <= MAX_ANGLE_DEG) return hi;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) * 0.5;
            if (sharpest(wheels, steered, mid, pivot) > MAX_ANGLE_DEG) hi = mid;
            else lo = mid;
        }
        return lo;
    }

    private static double sharpest(List<Wheel> wheels, boolean[] steered, double kappa, double pivot) {
        double worst = 0.0;
        for (int i = 0; i < wheels.size(); i++) {
            if (!steered[i]) continue;
            worst = Math.max(worst, Math.abs(headingDeg(wheels.get(i), kappa, pivot)));
        }
        return worst;
    }
}
