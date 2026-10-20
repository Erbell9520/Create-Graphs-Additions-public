package com.createstats.vcu.core;

public final class SurfaceGeometry {
    public static final double AXIS_DEAD_FRACTION = 0.2;

    // a tall rudder does roll you, so dedicated means >= half its strongest effect
    public static final double SELF_DOMINANCE = 0.5;

    private SurfaceGeometry() {
    }

    public static V3 plateNormal(V3 hingeAxis, V3 arm, BodyFrame frame) {
        V3 perpendicular = arm.rejectFrom(hingeAxis);
        if (perpendicular.length() > 0.3) {
            return hingeAxis.cross(perpendicular).normalized();
        }
        V3 n = hingeAxis.cross(frame.forward());
        if (n.length() < 0.3) {
            n = frame.up();
        }
        return n.normalized();
    }

    public static V3 forceDerivative(V3 hingeAxis, V3 normal, V3 airflow) {
        V3 dn = hingeAxis.cross(normal);
        return normal.scale(airflow.dot(dn)).add(dn.scale(airflow.dot(normal)));
    }

    public static double[] column(BodyFrame frame, V3 hingePoint, V3 hingeAxis, V3 bodyCentre) {
        V3 arm = bodyCentre.sub(hingePoint);
        V3 n = plateNormal(hingeAxis, arm, frame);
        V3 dF = forceDerivative(hingeAxis, n, frame.forward().scale(-1.0));
        return frame.wrench(bodyCentre, dF);
    }

    public static double[][] gains(double[][] columns) {
        int n = columns.length;
        boolean[][] dedicated = new boolean[n][Axis6.COUNT];
        for (int s = 0; s < n; s++) {
            double strongest = 0.0;
            for (Axis6 axis : Axis6.values()) {
                if (axis.isMoment()) strongest = Math.max(strongest, Math.abs(columns[s][axis.index()]));
            }
            for (Axis6 axis : Axis6.values()) {
                int a = axis.index();
                dedicated[s][a] = axis.isMoment() && strongest > 1.0e-9
                        && Math.abs(columns[s][a]) >= SELF_DOMINANCE * strongest;
            }
        }

        double[][] g = new double[n][Axis6.COUNT];
        for (Axis6 axis : Axis6.values()) {
            if (!axis.isMoment()) continue;
            int a = axis.index();
            boolean anyDedicated = false;
            for (int s = 0; s < n; s++) anyDedicated |= dedicated[s][a];

            double best = 0.0;
            for (int s = 0; s < n; s++) {
                if (anyDedicated && !dedicated[s][a]) continue;
                best = Math.max(best, Math.abs(columns[s][a]));
            }
            if (best < 1.0e-9) continue;
            for (int s = 0; s < n; s++) {
                if (anyDedicated && !dedicated[s][a]) continue;
                double normalised = columns[s][a] / best;
                g[s][a] = Math.abs(normalised) < AXIS_DEAD_FRACTION ? 0.0 : normalised;
            }
        }
        return g;
    }

    public static double deflection(double[] gains, double[] demand) {
        double sum = 0.0;
        for (int a = 0; a < Axis6.COUNT; a++) {
            sum += gains[a] * demand[a];
        }
        return Allocator.clamp(sum, -1.0, 1.0);
    }
}
