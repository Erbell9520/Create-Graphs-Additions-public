package com.createstats.surveyor.core;

public final class UnseenTravel {
    public static final int SETTLE_TICKS = 20;

    public static final double LIFT = 0.15;

    public static final double MARGIN = 16.0;

    private UnseenTravel() {
    }

    public static double advance(PathSpline sp, double s, double speed, double seconds) {
        if (sp.length() <= 0.0 || !(speed > 0.0) || !(seconds > 0.0)) return sp.wrap(s);
        return sp.wrap(s + speed * seconds);
    }

    public static double speedAfter(double speed, double throttle, double brake, PlantModel plant, double seconds) {
        double v = Double.isFinite(speed) ? Math.max(0.0, speed) : 0.0;
        if (!(seconds > 0.0) || !Double.isFinite(seconds)) return v;
        double u = Double.isFinite(throttle) ? Math.max(0.0, Math.min(1.0, throttle)) : 0.0;
        double b = Double.isFinite(brake) ? Math.max(0.0, Math.min(1.0, brake)) : 1.0;
        double beta = Math.max(0.0, plant.tau() / plant.tauBrake() - 1.0);
        double drag = 1.0 + beta * b;
        double steady = plant.k() * u * (1.0 - b) / drag;
        return Math.max(0.0, steady + (v - steady) * Math.exp(-seconds * drag / plant.tau()));
    }

    public static boolean atEnd(PathSpline sp, double s) {
        return !sp.closed() && s >= sp.length() - 1.0e-6;
    }

    public record Place(double x, double y, double z, double heading) {
    }

    public static Place place(PathSpline sp, double s) {
        double w = sp.wrap(s);
        double[] t = sp.tangent(w);
        return new Place(sp.x(w), sp.y(w), sp.z(w), PathSpline.heading(t[0], t[1]));
    }

    public static RigidMove move(double refX, double refY, double refZ, double refHeading, Place to, double groundTo,
                                 double rise, double riseOverLine) {
        double y = Double.isFinite(groundTo) && Double.isFinite(rise) ? groundTo + rise : to.y() + riseOverLine;
        return RigidMove.between(refX, refY, refZ, refHeading, to.x(), y + LIFT, to.z(), to.heading());
    }

    public static boolean riding(double[] load, double[] host) {
        if (load == null || host == null || load.length < 6 || host.length < 6) return false;
        double cx = (load[0] + load[3]) * 0.5;
        double cz = (load[2] + load[5]) * 0.5;
        boolean over = cx >= host[0] && cx <= host[3] && cz >= host[2] && cz <= host[5];
        boolean above = load[1] >= host[1] + 0.5;
        double lv = (load[3] - load[0]) * (load[4] - load[1]) * (load[5] - load[2]);
        double hv = (host[3] - host[0]) * (host[4] - host[1]) * (host[5] - host[2]);
        return over && above && lv < hv;
    }

    public static int[] chunkSpan(double[] box, double margin) {
        return new int[]{
                (int) Math.floor(box[0] - margin) >> 4, (int) Math.floor(box[2] - margin) >> 4,
                (int) Math.floor(box[3] + margin) >> 4, (int) Math.floor(box[5] + margin) >> 4};
    }

    public static boolean intersects(double[] a, double[] b) {
        return a[0] < b[3] && a[3] > b[0] && a[1] < b[4] && a[4] > b[1] && a[2] < b[5] && a[5] > b[2];
    }

    public static boolean onRoad(double[] box, double x, double z, double heading, double front, double rear) {
        double hx = Math.sin(heading), hz = -Math.cos(heading);
        double minAlong = Double.POSITIVE_INFINITY, maxAlong = Double.NEGATIVE_INFINITY;
        double minAcross = Double.POSITIVE_INFINITY, maxAcross = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            double dx = ((i & 1) == 0 ? box[0] : box[3]) - x;
            double dz = ((i & 2) == 0 ? box[2] : box[5]) - z;
            double along = dx * hx + dz * hz, across = dx * -hz + dz * hx;
            minAlong = Math.min(minAlong, along); maxAlong = Math.max(maxAlong, along);
            minAcross = Math.min(minAcross, across); maxAcross = Math.max(maxAcross, across);
        }
        return maxAlong >= -rear && minAlong <= front && maxAcross >= -0.8 && minAcross <= 0.8;
    }

    public static final class Settle {
        private final int need;
        private int run;

        public Settle(int need) {
            this.need = Math.max(1, need);
        }

        public boolean feed(boolean good) {
            run = good ? Math.min(run + 1, need) : 0;
            return run >= need;
        }

        public int run() {
            return run;
        }

        public void reset() {
            run = 0;
        }
    }
}
