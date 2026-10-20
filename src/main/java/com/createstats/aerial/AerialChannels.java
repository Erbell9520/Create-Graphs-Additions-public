package com.createstats.aerial;

import java.util.List;

import com.createstats.data.MetricType;

public final class AerialChannels {
    @FunctionalInterface
    public interface Sink {
        void accept(String metricId, String label, float value, MetricType type);
    }

    private AerialChannels() {
    }

    public static void publish(AirframeReadout r, List<AerialAlerts.Alert> alerts, Sink sink) {
        put(sink, "altitude", "Altitude (Y)", r.altitude, MetricType.GENERIC);
        put(sink, "agl", "Height above ground (m)", r.agl, MetricType.GENERIC);
        put(sink, "airspeed", "Airspeed (m/s)", r.airspeed, MetricType.GENERIC);
        put(sink, "ground_speed", "Ground speed (m/s)", r.groundSpeed, MetricType.GENERIC);

        put(sink, "hold_speed", "Slowest speed holding height (m/s)", r.minHoldSpeed, MetricType.GENERIC);
        put(sink, "vertical_speed", "Vertical speed (m/s)", r.verticalSpeed, MetricType.VELOCITY);
        put(sink, "heading", "Heading", r.heading, MetricType.ANGLE_DEGREES);
        put(sink, "pitch", "Pitch", r.pitch, MetricType.ANGLE_DEGREES);
        put(sink, "bank", "Bank", r.bank, MetricType.ANGLE_DEGREES);
        put(sink, "support", "Lift carried (%)", r.support * 100.0, MetricType.FILL_LEVEL);
        put(sink, "lift_reserve", "Lift reserve (%)", r.reserve * 100.0, MetricType.VELOCITY);
        put(sink, "ceiling", "Ceiling (Y)", r.ceilingY, MetricType.GENERIC);
        if (r.airMeasured) {
            put(sink, "wind", "Wind (m/s)", r.windSpeed, MetricType.GENERIC);

            put(sink, "wind_from", "Wind from", r.windFrom, MetricType.ANGLE_DEGREES);
        }
        put(sink, "thrust_weight", "Thrust to weight", r.thrustToWeight, MetricType.GENERIC);
        put(sink, "mass", "Mass (kpg)", r.mass, MetricType.GENERIC);
        if (!r.balloons.isEmpty()) {
            double fill = 0.0;
            int n = 0;
            for (final AirframeReadout.BalloonState b : r.balloons) {
                if (Double.isFinite(b.fill())) {
                    fill += b.fill();
                    n++;
                }
            }
            if (n > 0) {
                put(sink, "balloon_fill", "Balloon fill (%)", fill / n * 100.0, MetricType.FILL_LEVEL);
            }
        }
        if (r.nav != null) {
            put(sink, "target_distance", "Target distance (m)", r.nav.distance(), MetricType.GENERIC);
        }
        if (r.route != null) {
            put(sink, "cross_track", "Cross-track (m)", r.route.crossTrack(), MetricType.VELOCITY);
        }
        put(sink, "endurance", "Endurance (s)", r.enduranceSeconds, MetricType.GENERIC);
        if (!r.traffic.isEmpty()) {
            put(sink, "nearest_traffic", "Nearest ship (m)", r.traffic.get(0).distance(), MetricType.GENERIC);
        }
        put(sink, "alerts", "Flight alerts", countAtLeast(alerts, AerialAlerts.Level.CAUTION), MetricType.ITEM_COUNT);
        helm(r, sink);
    }

    public static final String[] MODES = {"OFF", "HELM", "ANCHOR", "KEEP_COURSE", "GO_TO", "MOORED"};

    static void helm(AirframeReadout r, Sink sink) {
        final AerialSnapshot.Helm h = r.snapshot.helm();
        if (!h.known()) {
            return;
        }
        int mode = -1;
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(h.mode())) {
                mode = i;
            }
        }
        if (mode >= 0) {
            put(sink, "mode", "Mode (0 off, 1 helm, 2 anchor, 3 course, 4 go to, 5 moored)", mode, MetricType.GENERIC);
        }
        put(sink, "pilot", "Pilot at the helm (1 yes)", h.pilot().isEmpty() ? 0.0 : 1.0, MetricType.GENERIC);
        put(sink, "order", "Telegraph order (%)", h.order() * 100.0, MetricType.VELOCITY);
        put(sink, "engines", "Engines' answer (%)", h.engine() * 100.0, MetricType.VELOCITY);
        put(sink, "held_altitude", "Height held (Y)", h.heldAltitude(), MetricType.GENERIC);
        put(sink, "lift_use", "Lift in use (%)", h.liftUse() * 100.0, MetricType.FILL_LEVEL);
        put(sink, "load", "Load weighed (kpg)", r.load, MetricType.GENERIC);
        double gas = 0.0;
        double capacity = 0.0;
        for (final AerialSnapshot.Balloon b : r.snapshot.balloons()) {
            if (Double.isFinite(b.filled()) && Double.isFinite(b.capacity()) && b.capacity() > 0.0) {
                gas += b.filled();
                capacity += b.capacity();
            }
        }
        if (capacity > 0.0) {
            put(sink, "gas", "Gas (% of capacity)", gas / capacity * 100.0, MetricType.FILL_LEVEL);
        }
    }

    static int countAtLeast(List<AerialAlerts.Alert> alerts, AerialAlerts.Level level) {
        int n = 0;
        for (final AerialAlerts.Alert a : alerts) {
            if (a.level().compareTo(level) >= 0) {
                n++;
            }
        }
        return n;
    }

    private static void put(Sink sink, String id, String label, double value, MetricType type) {
        if (Double.isFinite(value)) {
            sink.accept("aero_" + id, label, (float) value, type);
        }
    }
}
