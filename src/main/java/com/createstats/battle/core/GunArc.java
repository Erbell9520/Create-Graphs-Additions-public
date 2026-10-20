package com.createstats.battle.core;

// training stops, so a bow gun stops shooting its own envelope (happened)
public record GunArc(int from, int to, int low, int high) {
    public static final int STEP = 5;

    public static final int MIN_WIDTH = 10;

    public static final int MIN_SPAN = 5;
    public static final int LOWEST = -90;
    public static final int HIGHEST = 90;

    public static final int AROUND = 60;

    public static final int LIMIT_LOW = -10;
    public static final int LIMIT_HIGH = 30;

    static final double LEG = 150.0;

    static final double SWAP = 10.0;

    public static final double SLACK = 1.0;

    public static final int FIELD_FROM = 0;
    public static final int FIELD_TO = 1;
    public static final int FIELD_LOW = 2;
    public static final int FIELD_HIGH = 3;

    public static final int FIELD_ALL_ROUND = 4;

    public static final int FIELD_AROUND = 5;

    public static final int FIELD_FULL = 6;

    public static final GunArc FREE = new GunArc(180, 180, LOWEST, HIGHEST);

    public GunArc {
        from = norm(from);
        to = norm(to);
        if (from != to && Math.floorMod(to - from, 360) < MIN_WIDTH) to = norm(from + MIN_WIDTH);
        low = Math.max(LOWEST, Math.min(HIGHEST - MIN_SPAN, low));
        high = Math.max(low + MIN_SPAN, Math.min(HIGHEST, high));
    }

    public static int norm(int bearing) {
        int n = Math.floorMod(bearing + 180, 360) - 180;
        return n == -180 ? 180 : n;
    }

    public static double norm(double bearing) {
        double n = ((bearing + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return n <= -180.0 ? n + 360.0 : n;
    }

    public boolean allRound() {
        return from == to;
    }

    public boolean fullElevation() {
        return low <= LOWEST && high >= HIGHEST;
    }

    public boolean free() {
        return allRound() && fullElevation();
    }

    public int width() {
        return allRound() ? 360 : Math.floorMod(to - from, 360);
    }

    double pos(double bearing) {
        return ((bearing - from) % 360.0 + 360.0) % 360.0;
    }

    public boolean trains(double bearing, double slack) {
        if (allRound()) return true;
        double p = pos(bearing);
        return p <= width() + slack || p >= 360.0 - slack;
    }

    public boolean elevates(double elevation, double slack) {
        return elevation >= low - slack && elevation <= high + slack;
    }

    public double aimBearing(double bearing, double barrel) {
        if (trains(bearing, 0.0)) return norm(bearing);
        double w = width();
        double p = pos(bearing);
        double pastTo = p - w;
        double beforeFrom = 360.0 - p;
        boolean toTo = pastTo <= beforeFrom;
        if (Math.abs(pastTo - beforeFrom) < SWAP && trains(barrel, SLACK)) {
            double pb = pos(barrel);
            if (pb > 360.0 - SLACK) pb = 0.0;
            toTo = pb > w / 2.0;
        }
        return toTo ? to : from;
    }

    public double aimElevation(double elevation, double mountLow, double mountHigh) {
        double lo = Math.max(low, mountLow);
        double hi = Math.min(high, mountHigh);
        if (lo > hi) {
            lo = mountLow;
            hi = mountHigh;
        }
        return Math.max(lo, Math.min(hi, elevation));
    }

    public double steer(double barrel, double aim) {
        if (allRound()) return norm(aim);
        double w = width();
        double pb = pos(barrel);
        if (pb > w + SLACK && pb < 360.0 - SLACK) return pb - w <= 360.0 - pb ? to : from;
        if (pb >= 360.0 - SLACK) pb -= 360.0;
        double pa = Math.max(0.0, Math.min(w, pos(aim) >= 360.0 - SLACK ? 0.0 : pos(aim)));
        double d = pa - pb;
        if (Math.abs(d) <= LEG) return norm(aim);
        return norm(from + pb + Math.signum(d) * LEG);
    }

    public GunArc with(int field, int value) {
        return switch (field) {
            case FIELD_FROM -> {
                int f = norm(value);
                int wd = Math.floorMod(to - f, 360);
                yield !allRound() && wd < MIN_WIDTH ? this : new GunArc(f, allRound() ? norm(f + 360 - MIN_WIDTH) : to, low, high);
            }
            case FIELD_TO -> {
                int t = norm(value);
                int wd = Math.floorMod(t - from, 360);
                yield !allRound() && wd < MIN_WIDTH ? this : new GunArc(allRound() ? norm(t - 360 + MIN_WIDTH) : from, t, low, high);
            }
            case FIELD_LOW -> new GunArc(from, to, Math.max(LOWEST, Math.min(high - MIN_SPAN, value)), high);
            case FIELD_HIGH -> new GunArc(from, to, low, Math.min(HIGHEST, Math.max(low + MIN_SPAN, value)));
            case FIELD_ALL_ROUND -> value != 0 ? new GunArc(180, 180, low, high) : this;
            case FIELD_AROUND -> {
                int c = norm(Math.round(value / (float) STEP) * STEP);
                yield new GunArc(norm(c - AROUND), norm(c + AROUND), low, high);
            }
            case FIELD_FULL -> value != 0 ? new GunArc(from, to, LOWEST, HIGHEST) : new GunArc(from, to, LIMIT_LOW, LIMIT_HIGH);
            default -> this;
        };
    }

    public static int pack(int field, int value) {
        return field * 1024 + Math.max(-512, Math.min(511, value)) + 512;
    }

    public static int packedField(int packed) {
        return Math.floorDiv(packed, 1024);
    }

    public static int packedValue(int packed) {
        return Math.floorMod(packed, 1024) - 512;
    }
}
