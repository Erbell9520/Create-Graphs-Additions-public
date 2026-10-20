package com.createstats.vccmk4.air.core;

// only learn from a prop that has caught up with its shaft
public final class PropLearner {
    public static final int MIN_RPM = 2;

    static final double STEADY = 0.005;

    static final int HOLD_TICKS = 3;

    static final int SIGN_READINGS = 10;

    static final int RISE_READINGS = 2;

    static final double RISE = 1.001;

    static final double NO_THRUST = 1.0e-4;

    static final double SAVE_SHARE = 0.05;

    public int sign = 1;

    public boolean signSeen;

    public double gain = Double.NaN;

    public double flow = Double.NaN;

    public double ratio = Double.NaN;

    private int lastApplied;
    private int still;
    private double lastThrust = Double.NaN;
    private int against;
    private int rising;

    public boolean known() {
        return Double.isFinite(gain);
    }

    public boolean observe(int applied, double along, double airflow, double shaft) {
        still = applied == lastApplied ? Math.min(still + 1, 1000) : 0;
        lastApplied = applied;
        double before = lastThrust;
        lastThrust = along;
        boolean up = still >= 1 && Double.isFinite(before) && Double.isFinite(along)
                && Math.abs(along) > RISE * Math.abs(before) + NO_THRUST;
        rising = up ? Math.min(rising + 1, 1000) : 0;
        if (Math.abs(applied) < MIN_RPM) return false;
        boolean changed = false;
        if (Double.isFinite(shaft) && Math.abs(shaft) > 0.5) {
            double r = Math.abs(shaft) / Math.abs(applied);
            if (!Double.isFinite(ratio) || Math.abs(r - ratio) > 0.01 * r) changed = true;
            ratio = r;
        }
        if (!Double.isFinite(along) || Math.abs(along) < NO_THRUST) return changed;
        int s = along * applied > 0.0 ? 1 : -1;
        boolean steady = still >= HOLD_TICKS && Double.isFinite(before) && Math.abs(along - before) <= STEADY * Math.abs(along);
        if (!signSeen) {
            if (!steady && rising < RISE_READINGS) return changed;
            sign = s;
            signSeen = true;
            against = 0;
            changed = true;
        } else if (s != sign) {
            if (steady && ++against >= SIGN_READINGS) {
                sign = s;
                against = 0;
                changed = true;
            }
            return changed;
        } else if (steady) {
            against = 0;
        }
        if (!steady) return changed;
        double g = Math.abs(along) / Math.abs(applied);
        if (!known() || Math.abs(g - gain) > SAVE_SHARE * g) changed = true;
        gain = g;
        if (Double.isFinite(airflow) && Math.abs(airflow) > 1.0e-6) flow = Math.abs(airflow) / Math.abs(applied);
        return changed;
    }

    public static PropLearner restored(int sign, double gain, double flow, double ratio) {
        PropLearner l = new PropLearner();
        l.sign = sign < 0 ? -1 : 1;
        l.signSeen = true;
        l.gain = gain > 0.0 && Double.isFinite(gain) ? gain : Double.NaN;
        l.flow = flow > 0.0 && Double.isFinite(flow) ? flow : Double.NaN;
        l.ratio = ratio > 0.0 && Double.isFinite(ratio) ? ratio : Double.NaN;
        return l;
    }
}
