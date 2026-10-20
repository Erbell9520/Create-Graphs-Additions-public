package com.createstats.seismic;

public final class SeismicTrace {
    static final double BLOW_PERIOD = 2.2;
    static final double BLOW_DECAY = 3.5;

    static final double ECHO_PERIOD = 1.6;
    static final double ECHO_DECAY = 2.2;

    static final double HUM = 0.02;

    private SeismicTrace() {
    }

    public static double at(double when, double impact, double strength, int[] echoes) {
        double a = hum(when);
        final double t = when - impact;
        if (t >= 0.0 && strength > 0.0) {
            a += strength * swing(t, BLOW_PERIOD, BLOW_DECAY);
        }
        if (echoes != null && t >= 0.0) {
            for (int i = 0; i + 1 < echoes.length; i += 2) {
                final double dt = t - echoes[i];
                if (dt >= 0.0 && dt < ECHO_DECAY * 8.0) {
                    a += echoes[i + 1] / 1000.0 * swing(dt, ECHO_PERIOD, ECHO_DECAY);
                }
            }
        }
        return Math.max(-1.0, Math.min(1.0, a));
    }

    public static double blowStrength(int reach) {
        if (reach <= 0) {
            return 0.25;
        }
        final double f = Math.min(1.0, reach / (double) SeismicCharge.MAX_RADIUS);
        return 0.40 + 0.60 * f;
    }

    public static int echoAmplitude(int count, double nearest) {
        final double size = Math.min(0.85, 0.22 + 0.11 * (Math.log(Math.max(1, count)) / Math.log(2.0)));
        final double spread = Math.sqrt(20.0 / (20.0 + Math.max(0.0, nearest)));
        return (int) Math.round(Math.max(0.10, Math.min(0.85, size * spread)) * 1000.0);
    }

    static double swing(double t, double period, double decay) {
        return Math.sin(2.0 * Math.PI * t / period) * Math.exp(-t / decay);
    }

    static double hum(double t) {
        final double s = t * 4.0;
        final long i = (long) Math.floor(s);
        final double f = s - i;
        final double e = f * f * (3.0 - 2.0 * f);
        final double a = hash(i);
        final double b = hash(i + 1);
        return HUM * ((a + (b - a) * e) * 2.0 - 1.0);
    }

    private static double hash(long i) {
        long h = i * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h & 0xFFFFL) / 65535.0;
    }
}
