package com.createstats.ground;

import java.util.List;

// world free so every page renders in a test
public record GroundFrame(DriveReadout readout, List<GroundAlerts.Alert> alerts, double age, long tick,
                          float partialTick) {
    public GroundFrame {
        alerts = alerts == null ? List.of() : List.copyOf(alerts);
    }

    public static GroundFrame of(GroundSnapshot s) {
        final DriveReadout r = DriveMath.read(s);
        return new GroundFrame(r, GroundAlerts.evaluate(r, List.of(), 0.0), 0.0, s.gameTime(), 0.0f);
    }

    public GroundSnapshot snapshot() {
        return this.readout.snapshot;
    }

    public boolean stale() {
        return this.age > GroundAlerts.STALE_SECONDS;
    }

    public GroundAlerts.Level worst() {
        return GroundAlerts.worst(this.alerts);
    }

    public GroundAlerts.Alert headline() {
        return GroundAlerts.headline(this.alerts);
    }

    public double sinceHelm() {
        final long helm = snapshot().drive().helmTick();
        if (helm == Long.MIN_VALUE || !snapshot().drive().driving()) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, (this.tick - helm + this.partialTick) / 20.0);
    }
}
