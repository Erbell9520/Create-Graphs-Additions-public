package com.createstats.vehiclelight;

import com.createstats.vccmk4.core.LinkSource;

// the headlights look around, then settle. like the real ones
public final class HailSequence {
    public static final double DARK = 0.26;

    public static final double CHECK_SPREAD = 0.30;

    public static final double CHECK_END = 0.70;

    public static final double RUN_END = 2.20;

    public static final double END = 2.60;

    public static final double MAX_SPEED = 3.0;

    public static final double RUN_DOWN = 6.0;
    public static final double RUN_UP = 4.0;
    public static final double RUN_SIDE = 15.0;

    public static final double OVERSHOOT = 0.12;

    static final double BLINK_1 = 0.85;
    static final double BLINK_1_END = 1.05;
    static final double BLINK_2 = 1.20;
    static final double BLINK_2_END = 1.40;

    static final double TAIL_FALL_END = 1.10;

    static final double SCAN_WIDTH = 0.6;

    static final double TAIL_LEVEL = VehicleLightGlow.DIM.level();

    private static final double[][] RUN = {
            {CHECK_END, 0.0, 0.0},
            {0.95, RUN_DOWN, 0.0},
            {1.25, -RUN_UP, 0.0},
            {1.55, 0.0, RUN_SIDE},
            {1.95, 0.0, -RUN_SIDE},
            {RUN_END, 0.0, 0.0},
    };

    private HailSequence() {
    }

    public enum Role {
        HEADLIGHT, REVERSE, TAIL, INDICATOR, OTHER;

        public static Role of(LinkSource source) {
            if (source == null) {
                return null;
            }
            return switch (source) {
                case OFF -> null;
                case HEADLIGHT, DRIVING -> HEADLIGHT;
                case REVERSE -> REVERSE;
                case TAIL_BRAKE, BRAKE -> TAIL;
                case LEFT, RIGHT -> INDICATOR;
                default -> OTHER;
            };
        }

        public static Role byOrdinal(int ordinal) {
            final Role[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
        }
    }

    public record Place(double radial, double lateral) {
        public static final Place MIDDLE = new Place(0.0, 0.0);
    }

    public record Hail(long start, Role role, Place place) {
        public double seconds(long gameTime, float partialTick) {
            final double t = (gameTime - this.start + partialTick) / 20.0;
            return t >= 0.0 && t < END ? t : Double.NaN;
        }
    }

    public record Aim(double down, double left, double settle) {
        public static final Aim LEVELLED = new Aim(0.0, 0.0, 1.0);
    }

    public static Place[] place(double[] lateral, double[] longitudinal) {
        final int n = Math.min(lateral.length, longitudinal.length);
        final Place[] out = new Place[n];
        if (n == 0) {
            return out;
        }
        double loX = Double.POSITIVE_INFINITY;
        double hiX = Double.NEGATIVE_INFINITY;
        double loZ = Double.POSITIVE_INFINITY;
        double hiZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            loX = Math.min(loX, lateral[i]);
            hiX = Math.max(hiX, lateral[i]);
            loZ = Math.min(loZ, longitudinal[i]);
            hiZ = Math.max(hiZ, longitudinal[i]);
        }
        final double midX = (loX + hiX) / 2.0;
        final double midZ = (loZ + hiZ) / 2.0;
        final double half = (hiX - loX) / 2.0;
        double far = 0.0;
        for (int i = 0; i < n; i++) {
            far = Math.max(far, Math.hypot(lateral[i] - midX, longitudinal[i] - midZ));
        }
        for (int i = 0; i < n; i++) {
            final double r = far > 1.0e-6 ? Math.hypot(lateral[i] - midX, longitudinal[i] - midZ) / far : 0.0;
            final double x = half > 1.0e-6 ? (lateral[i] - midX) / half : 0.0;
            out[i] = new Place(Math.min(1.0, r), Math.max(-1.0, Math.min(1.0, x)));
        }
        return out;
    }

    public static boolean signalling(Role role, VehicleLightGlow state) {
        return (role == Role.INDICATOR || role == Role.REVERSE) && state != VehicleLightGlow.OFF;
    }

    public static double lens(Role role, double t, Place place, double real, boolean signal) {
        if (role == null || !(t >= 0.0) || t >= END) {
            return Double.NaN;
        }
        if (signal && role != Role.TAIL) {
            return Double.NaN;
        }
        if (t < DARK) {
            return 0.0;
        }
        if (t < CHECK_END) {
            final double radial = place == null ? 0.0 : place.radial();
            if (t - DARK < CHECK_SPREAD * radial) {
                return 0.0;
            }
            return role == Role.HEADLIGHT ? real : 1.0;
        }
        return switch (role) {
            case INDICATOR -> {
                if (t >= BLINK_2_END) {
                    yield Double.NaN;
                }
                yield (t >= BLINK_1 && t < BLINK_1_END) || t >= BLINK_2 ? 1.0 : 0.0;
            }
            case TAIL -> {
                if (t < TAIL_FALL_END) {
                    final double u = (t - CHECK_END) / (TAIL_FALL_END - CHECK_END);
                    yield 1.0 + (TAIL_LEVEL - 1.0) * u;
                }
                if (t < RUN_END) {
                    final double lateral = place == null ? 0.0 : place.lateral();
                    final double spot = Math.max(0.0, 1.0 - Math.abs(lateral - scanner(t)) / SCAN_WIDTH);
                    yield TAIL_LEVEL + (1.0 - TAIL_LEVEL) * spot;
                }
                yield TAIL_LEVEL;
            }
            default -> Double.NaN;
        };
    }

    static double scanner(double t) {
        final double u = Math.max(0.0, Math.min(1.0, (t - TAIL_FALL_END) / (RUN_END - TAIL_FALL_END)));
        return u <= 0.5 ? -1.0 + 4.0 * u : 3.0 - 4.0 * u;
    }

    public static Aim aim(double t) {
        if (!(t >= 0.0) || t >= END) {
            return Aim.LEVELLED;
        }
        if (t < CHECK_END) {
            return new Aim(0.0, 0.0, 0.0);
        }
        if (t < RUN_END) {
            for (int i = 0; i + 1 < RUN.length; i++) {
                final double[] a = RUN[i];
                final double[] b = RUN[i + 1];
                if (t <= b[0]) {
                    final double s = smooth((t - a[0]) / (b[0] - a[0]));
                    return new Aim(a[1] + (b[1] - a[1]) * s, a[2] + (b[2] - a[2]) * s, 0.0);
                }
            }
            return new Aim(0.0, 0.0, 0.0);
        }
        final double u = (t - RUN_END) / (END - RUN_END);
        final double settle = u <= 0.6
                ? (1.0 + OVERSHOOT) * smooth(u / 0.6)
                : 1.0 + OVERSHOOT - OVERSHOOT * smooth((u - 0.6) / 0.4);
        return new Aim(0.0, 0.0, settle);
    }

    private static double smooth(double u) {
        final double x = Math.max(0.0, Math.min(1.0, u));
        return x * x * (3.0 - 2.0 * x);
    }
}
