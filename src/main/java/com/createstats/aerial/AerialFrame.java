package com.createstats.aerial;

import java.util.List;

public record AerialFrame(AirframeReadout readout, List<AerialAlerts.Alert> alerts, double age, double rungAgo) {
    public AerialFrame {
        alerts = alerts == null ? List.of() : List.copyOf(alerts);
    }

    public AerialFrame(AirframeReadout readout, List<AerialAlerts.Alert> alerts, double age) {
        this(readout, alerts, age, Double.POSITIVE_INFINITY);
    }

    public static AerialFrame of(AerialSnapshot s) {
        final AirframeReadout r = AirframeMath.read(s);
        return new AerialFrame(r, AerialAlerts.evaluate(r, List.of(), 0.0), 0.0);
    }

    public AerialSnapshot snapshot() {
        return this.readout.snapshot;
    }

    public boolean stale() {
        return this.age > AerialAlerts.STALE_SECONDS;
    }

    public AerialAlerts.Level worst() {
        return AerialAlerts.worst(this.alerts);
    }
}
