package com.createstats.cinecam.core;

public final class AutoZoom {
    public enum Framing {
        OFF("off", 0.0),
        WIDE("wide", 0.25),
        MEDIUM("medium", 0.5),
        CLOSE("close", 0.8);

        private final String key;
        private final double share;

        Framing(String key, double share) {
            this.key = key;
            this.share = share;
        }

        public String key() {
            return key;
        }

        public double share() {
            return share;
        }

        public boolean on() {
            return this != OFF;
        }

        public Framing next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Framing byKey(String key) {
            for (Framing f : values()) {
                if (f.key.equals(key)) return f;
            }
            return DEFAULT;
        }

        public static Framing byIndex(int i) {
            return i >= 0 && i < values().length ? values()[i] : DEFAULT;
        }
    }

    public static final Framing DEFAULT = Framing.OFF;

    public static final double STEP = 1.25;
    public static final double MIN_SHARE = 0.05;
    public static final double MAX_SHARE = 0.95;

    public static final double TAU = 0.6;

    private AutoZoom() {
    }

    public static double zoomFor(double distance, double radius, double share, double tanHalf, double max) {
        if (!(distance > 0.0) || !(radius > 0.0) || !(share > 0.0) || !(tanHalf > 0.0)) return 1.0;
        double z = share * distance * tanHalf / radius;
        return Math.max(1.0, Math.min(max, z));
    }

    public static double tanHalf(double fovDeg, double windowAspect, double frameAspect) {
        double t = Math.tan(Math.toRadians(fovDeg) / 2.0);
        return frameAspect > windowAspect && windowAspect > 0.0 ? t * windowAspect / frameAspect : t;
    }

    public static double nudge(double share, int clicks) {
        return Math.max(MIN_SHARE, Math.min(MAX_SHARE, share * Math.pow(STEP, clicks)));
    }
}
