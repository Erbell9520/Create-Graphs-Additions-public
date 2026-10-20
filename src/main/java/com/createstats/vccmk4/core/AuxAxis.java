package com.createstats.vccmk4.core;

public final class AuxAxis {
    public static final double SLEW_DEG = 3.0;
    public static final int DEFAULT_TRAVEL_DEG = 45;
    public static final int MIN_TRAVEL_DEG = 5;
    public static final int MAX_TRAVEL_DEG = 180;

    public record Setting(int travelDeg, boolean latched, boolean inverted) {
        public static final Setting LATCHED = new Setting(DEFAULT_TRAVEL_DEG, true, false);
        public static final Setting SPRUNG = new Setting(DEFAULT_TRAVEL_DEG, false, false);

        public Setting sane() {
            int t = Math.max(MIN_TRAVEL_DEG, Math.min(MAX_TRAVEL_DEG, travelDeg));
            return t == travelDeg ? this : new Setting(t, latched, inverted);
        }

        public Setting withTravel(int deg) {
            return new Setting(deg, latched, inverted).sane();
        }

        public Setting withLatched(boolean v) {
            return new Setting(travelDeg, v, inverted);
        }

        public Setting withInverted(boolean v) {
            return new Setting(travelDeg, latched, v);
        }
    }

    private double angle;

    public double angle() {
        return angle;
    }

    public void set(double degrees) {
        angle = degrees;
    }

    public double step(Setting setting, double input) {
        Setting s = setting.sane();
        double in = GroundSteer.clamp(setting.inverted() ? -input : input, -1.0, 1.0);
        double travel = s.travelDeg();
        double target = s.latched() ? angle + in * SLEW_DEG : in * travel;
        target = GroundSteer.clamp(target, -travel, travel);
        angle += GroundSteer.clamp(target - angle, -SLEW_DEG, SLEW_DEG);
        return angle;
    }
}
