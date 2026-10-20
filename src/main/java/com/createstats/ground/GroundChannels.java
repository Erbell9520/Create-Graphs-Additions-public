package com.createstats.ground;

import java.util.List;

import com.createstats.data.MetricType;

// headline readings as plain channels once a second so the rest of mk3 just works
public final class GroundChannels {
    public static final String PREFIX = "drive_";

    @FunctionalInterface
    public interface Sink {
        void accept(String metricId, String label, float value, MetricType type);
    }

    private GroundChannels() {
    }

    public static void publish(DriveReadout r, List<GroundAlerts.Alert> alerts, Sink sink) {
        final GroundSnapshot s = r.snapshot;

        if (r.state != DriveReadout.State.OFF_CRAFT) {
            put(sink, "speed", "Speed (b/s)", r.speed, MetricType.VELOCITY);
            put(sink, "speed_kmh", "Speed (km/h)", r.speedKmh, MetricType.GENERIC);
            put(sink, "gear", "Gear (P0 H1 N2 D3 R4 B5, 6 idle)", gearCode(s.drive().gear()), MetricType.GENERIC);
            put(sink, "throttle", "Throttle (%)", s.drive().throttle() * 100.0, MetricType.VELOCITY);
            put(sink, "brake", "Braking", s.drive().braking() ? 1.0 : 0.0, MetricType.BOOLEAN_FLAG);
            put(sink, "steer", "Steering (%)", s.drive().steer() * 100.0, MetricType.VELOCITY);
            put(sink, "lateral_g", "Lateral g", r.latG, MetricType.VELOCITY);
            put(sink, "longitudinal_g", "Longitudinal g", r.lonG, MetricType.VELOCITY);
            put(sink, "worst_slip", "Worst wheel slip (%)", r.worstSlip * 100.0, MetricType.VELOCITY);
            put(sink, "least_grip", "Least wheel grip (%)", r.leastGrip * 100.0, MetricType.FILL_LEVEL);
            put(sink, "pitch", "Pitch", r.pitch, MetricType.ANGLE_DEGREES);
            put(sink, "roll", "Roll", r.roll, MetricType.ANGLE_DEGREES);
            put(sink, "heading", "Heading", r.heading, MetricType.ANGLE_DEGREES);
            put(sink, "altitude", "Altitude (Y)", r.altitude, MetricType.GENERIC);

            put(sink, "indicator", "Indicator (-1 left, 1 right)", indicator(s), MetricType.GENERIC);
            put(sink, "boost", "Boost", s.drive().boosting() ? 1.0 : 0.0, MetricType.BOOLEAN_FLAG);
            put(sink, "beam", "Full beam", s.lights().driving() && s.lights().beam() ? 1.0 : 0.0, MetricType.BOOLEAN_FLAG);
        }
        put(sink, "odometer", "Odometer (blocks)", s.odometer().odometer(), MetricType.GENERIC);
        put(sink, "trip", "Trip (blocks)", s.odometer().trip(), MetricType.GENERIC);
        put(sink, "top_speed", "Top speed (b/s)", s.odometer().topSpeed(), MetricType.GENERIC);
        if (r.trailers > 0 && r.state != DriveReadout.State.OFF_CRAFT) {
            put(sink, "articulation", "Sharpest trailer angle", r.worstArticulation, MetricType.ANGLE_DEGREES);
            put(sink, "trailers", "Trailers", r.trailers, MetricType.ITEM_COUNT);
        }
        if (r.model.known()) {
            put(sink, "cruise", "Cruise at full throttle (b/s, modelled)", r.model.cruise(), MetricType.GENERIC);
        }
        put(sink, "alerts", "Vehicle alerts", countAtLeast(alerts, GroundAlerts.Level.CAUTION), MetricType.ITEM_COUNT);
    }

    public static int gearCode(char gear) {
        return switch (gear) {
            case 'P' -> 0;
            case 'H' -> 1;
            case 'N' -> 2;
            case 'D' -> 3;
            case 'R' -> 4;
            case 'B' -> 5;
            default -> 6;
        };
    }

    static int indicator(GroundSnapshot s) {
        if (!s.lights().driving()) {
            return 0;
        }
        final double steer = s.lights().steer();
        return steer > com.createstats.vccmk4.core.LinkLogic.SIGNAL_STEER ? 1
                : steer < -com.createstats.vccmk4.core.LinkLogic.SIGNAL_STEER ? -1 : 0;
    }

    static int countAtLeast(List<GroundAlerts.Alert> alerts, GroundAlerts.Level level) {
        int n = 0;
        for (final GroundAlerts.Alert a : alerts) {
            if (a.level().compareTo(level) >= 0) {
                n++;
            }
        }
        return n;
    }

    private static void put(Sink sink, String id, String label, double value, MetricType type) {
        if (Double.isFinite(value)) {
            sink.accept(PREFIX + id, label, (float) value, type);
        }
    }
}
