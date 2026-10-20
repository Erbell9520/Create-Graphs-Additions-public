package com.createstats.vccmk4.air.core;

import com.createstats.vcu.core.Allocator;
import com.createstats.vcu.core.Axis6;
import com.createstats.vcu.core.BodyFrame;
import com.createstats.vcu.core.V3;

import java.util.List;

// box constrained weighted least squares over six axes, fuzzed on 20k problems
public final class ThrustSplit {
    private ThrustSplit() {
    }

    public record Actuator(double[] column, double lo, double hi, boolean exact) {
        public Actuator(double[] column, double lo, double hi) {
            this(column, lo, hi, false);
        }

        double low() {
            return exact ? Math.min(lo, hi) : Math.min(0.0, lo);
        }

        double high() {
            return exact ? Math.max(lo, hi) : Math.max(0.0, hi);
        }
    }

    public record Result(double[] force, double[] achieved) {
    }

    public static double[] column(BodyFrame frame, List<V3> positions, List<V3> directions, double[] shares) {
        double[] col = new double[Axis6.COUNT];
        for (int i = 0; i < positions.size(); i++) {
            double[] w = frame.wrench(positions.get(i), directions.get(i).normalized());
            for (int a = 0; a < Axis6.COUNT; a++) col[a] += shares[i] * w[a];
        }
        return col;
    }

    public static Result solve(List<Actuator> actuators, double[] target, double[] care, double forceScale,
                               double momentScale) {
        int n = actuators.size();
        double[][] b = matrix(actuators);
        double[] lo = new double[n];
        double[] hi = new double[n];
        for (int i = 0; i < n; i++) {
            lo[i] = actuators.get(i).low();
            hi[i] = actuators.get(i).high();
        }
        double f = forceScale > 0.0 ? forceScale : 1.0;
        double m = momentScale > 0.0 ? momentScale : 1.0;
        double[] weight = new double[Axis6.COUNT];
        for (Axis6 a : Axis6.values()) {
            double c = Math.max(0.0, care[a.index()]);
            weight[a.index()] = a.isMoment() ? c * Allocator.MOMENT_PRIORITY / (m * m) : c / (f * f);
        }
        if (n == 0) return new Result(new double[0], new double[Axis6.COUNT]);
        Allocator.Result r = Allocator.solve(b, target, weight, lo, hi);
        return new Result(r.commands(), r.achieved());
    }

    public static double[] authority(List<Actuator> actuators, boolean positive) {
        double[][] b = matrix(actuators);
        int n = actuators.size();
        double[] lo = new double[n];
        double[] hi = new double[n];
        for (int i = 0; i < n; i++) {
            lo[i] = actuators.get(i).low();
            hi[i] = actuators.get(i).high();
        }
        if (n == 0) return new double[Axis6.COUNT];
        return positive ? Allocator.authorityPositive(b, lo, hi) : Allocator.authorityNegative(b, lo, hi);
    }

    private static double[][] matrix(List<Actuator> actuators) {
        int n = actuators.size();
        double[][] b = new double[Axis6.COUNT][n];
        for (int i = 0; i < n; i++) {
            double[] col = actuators.get(i).column();
            for (int a = 0; a < Axis6.COUNT; a++) b[a][i] = col[a];
        }
        return b;
    }
}
