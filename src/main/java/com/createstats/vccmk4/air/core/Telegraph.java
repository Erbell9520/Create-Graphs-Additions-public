package com.createstats.vccmk4.air.core;

public final class Telegraph {
    public enum Notch {
        FULL_ASTERN(-1.0, "FULL AST"), HALF_ASTERN(-0.5, "HALF AST"), SLOW_ASTERN(-0.25, "SLOW AST"), STOP(0.0, "STOP"),
        SLOW(0.25, "SLOW"), HALF(0.5, "HALF"), FULL(1.0, "FULL");

        public final double value;

        public final String label;

        Notch(double value, String label) {
            this.value = value;
            this.label = label;
        }

        public static Notch byOrdinal(int i) {
            Notch[] v = values();
            return i >= 0 && i < v.length ? v[i] : STOP;
        }
    }

    public static final double RATE = 1.0 / 40.0;

    public static final int DETENT_TICKS = 8;

    private static final double EPS = 1.0e-9;

    private double position;
    private int detent;
    private int lastDir;

    private int rings;

    public double position() {
        return position;
    }

    public Notch notch() {
        for (Notch n : Notch.values()) if (Math.abs(n.value - position) < 1.0e-6) return n;
        return null;
    }

    public boolean stopped() {
        return Math.abs(position) < 1.0e-6;
    }

    public int rings() {
        return rings;
    }

    public void set(double value) {
        double before = position;
        position = clamp(Double.isFinite(value) ? value : 0.0);
        detent = 0;
        if (Math.abs(before - position) > EPS && notch() != null) rings++;
    }

    public void ring(Notch n) {
        if (n == null) return;
        position = n.value;
        detent = DETENT_TICKS;
        rings++;
    }

    public double update(double axis) {
        int dir = axis > 0.5 ? 1 : axis < -0.5 ? -1 : 0;
        if (dir == 0) {
            lastDir = 0;
            detent = 0;
            return position;
        }
        if (dir != lastDir) {
            lastDir = dir;
            Notch next = nextNotch(position, dir);
            if (next != null) {
                position = next.value;
                rings++;
            }
            detent = DETENT_TICKS;
            return position;
        }
        if (detent > 0) {
            detent--;
            return position;
        }
        double moved = clamp(position + dir * RATE);
        Notch crossed = crossing(position, moved, dir);
        if (crossed != null) {
            moved = crossed.value;
            detent = DETENT_TICKS;
            rings++;
        }
        position = moved;
        return position;
    }

    public static Notch nextNotch(double from, int dir) {
        Notch[] v = Notch.values();
        if (dir > 0) {
            for (Notch n : v) if (n.value > from + EPS) return n;
        } else {
            for (int i = v.length - 1; i >= 0; i--) if (v[i].value < from - EPS) return v[i];
        }
        return null;
    }

    static Notch crossing(double from, double to, int dir) {
        Notch[] v = Notch.values();
        if (dir > 0) {
            for (Notch n : v) if (n.value > from + EPS && n.value <= to + EPS) return n;
        } else {
            for (int i = v.length - 1; i >= 0; i--) {
                if (v[i].value < from - EPS && v[i].value >= to - EPS) return v[i];
            }
        }
        return null;
    }

    public Notch nearest() {
        Notch best = Notch.STOP;
        for (Notch n : Notch.values()) if (Math.abs(n.value - position) < Math.abs(best.value - position)) best = n;
        return best;
    }

    public String label() {
        Notch n = nearest();
        if (Math.abs(n.value - position) < 1.0e-6) return n.label;
        return n.label + (position > n.value ? "+" : "-");
    }

    private static double clamp(double v) {
        return Math.max(Notch.FULL_ASTERN.value, Math.min(Notch.FULL.value, v));
    }
}
