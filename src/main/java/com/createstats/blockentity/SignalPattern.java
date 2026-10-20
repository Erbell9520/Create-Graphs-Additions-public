package com.createstats.blockentity;

public enum SignalPattern {
    OFF("Off"),

    STEADY("Steady"),

    BLINK("Blink"),

    FLASH("Double flash"),

    PULSE("Pulse"),

    ROTATE("Rotating"),

    STROBE("Strobe"),

    FAULT("Fault stutter");

    private final String displayName;

    SignalPattern(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public boolean spinsVane() {
        return this == ROTATE;
    }

    public static final double ROTATE_HZ = 0.9D;

    public double brightness(double seconds, double bearing, double jitter) {
        return switch (this) {
            case OFF -> 0.0D;
            case STEADY -> 1.0D;
            case BLINK -> gate(seconds % 1.0D, 0.0D, 0.5D);
            case FLASH -> {
                final double t = seconds % 1.5D;
                yield Math.max(gate(t, 0.0D, 0.10D), gate(t, 0.26D, 0.36D));
            }

            case PULSE -> 0.25D + 0.75D * (0.5D - 0.5D * Math.cos(seconds * (Math.PI * 2.0D / 2.4D)));
            case ROTATE -> {
                final double sweep = seconds * ROTATE_HZ * Math.PI * 2.0D;

                final double lead = lobe(bearing - sweep);
                final double trail = lobe(bearing - sweep + Math.PI);

                yield Math.min(1.0D, 0.18D + 0.92D * (lead + 0.55D * trail));
            }
            case STROBE -> {
                final double t = seconds % 1.25D;
                if (t < 0.045D) {
                    yield 1.0D;
                }

                yield 0.9D * Math.exp(-(t - 0.045D) / 0.075D);
            }
            case FAULT -> {
                final double t = seconds + jitter;
                final long step = (long) Math.floor(t * 12.0D);
                final double roll = hash01(step);
                if (roll < 0.42D) {
                    yield 0.0D;
                }

                if (hash01(step / 7L) < 0.25D) {
                    yield 0.0D;
                }
                yield 0.55D + 0.45D * hash01(step * 3L + 1L);
            }
        };
    }

    private static double gate(double t, double from, double to) {
        final double edge = 0.035D;
        if (t < from - edge || t > to + edge) {
            return 0.0D;
        }
        if (t < from) {
            return (t - (from - edge)) / edge;
        }
        if (t > to) {
            return 1.0D - (t - to) / edge;
        }
        return 1.0D;
    }

    private static double lobe(double angle) {
        final double c = Math.cos(angle);
        if (c <= 0.0D) {
            return 0.0D;
        }

        return c * c * c;
    }

    private static double hash01(long n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        return ((x >>> 11) & 0x1FFFFFFFFFFFFFL) / (double) 0x20000000000000L;
    }
}
